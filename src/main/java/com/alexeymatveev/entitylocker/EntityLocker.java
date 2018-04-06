package com.alexeymatveev.entitylocker;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class that provides synchronization mechanism similar to row-level DB locking.
 * Class is supposed to be used by components which are responsible for managing storage / caching of different types of entities.
 *
 * Class deals with entity ids (or keys) and relates on .equals() method of those ids.
 *
 * Utility provided basic locking by id / key as well as global lock for all entity ids.
 *
 * Created by Alexey Matveev on 4/4/2018.
 */
public class EntityLocker<K> {

    /* Map of entity locks. Each entity has it's own associated lock. Key is entity key, value is the lock. */
    private Map<K, ReentrantLock> entitiesLockingMap = new ConcurrentHashMap<>();

    /* Lock which prevents threads to create same entity lock twice. */
    private ReentrantLock createEntityLockLock = new ReentrantLock();

    /* Synchronization lock for all unlocking methods */
    private ReentrantLock unlockLock = new ReentrantLock();

    /* Global lock which prevent any thread to acquire any entity lock until released. */
    private ReentrantLock globalLock = new ReentrantLock();

    /* Lock which happens before global - used to acquire global lock. */
    private ReentrantLock beforeGlobalLock = new ReentrantLock();

    /* Number of threads which currently hold unique entity locks. */
    private AtomicLong entityLockCount = new AtomicLong();

    private ThreadLocal<Set<K>> currentThreadKeysLocked = ThreadLocal.withInitial(HashSet::new);

    private ThreadLocal<Boolean> escalateGlobal = ThreadLocal.withInitial(() -> false);

    private int globalEscalationThreshold = 3;

    /**
     * Locks the entity by id, preventing other threads to lock the same id until released.
     * Behaves the same as {@link ReentrantLock#lock} but on entity id level.
     * Will also be blocked if a global lock is currently locked until it's released.
     * @param id entity id
     */
    public void lock(K id) {
        if (id == null) {
            throw new NullPointerException("Trying to lock entity with null ID");
        }
        if (escalateGlobal.get()) {
            // if global escalated - don't lock
            System.out.println("Global escalated - won't lock id " + id);
        } else {
            // otherwise try acquire the lock
            beforeGlobalLock.lock();
            globalLock.lock();
            Set<K> keysLocked = currentThreadKeysLocked.get();
            if (keysLocked.size() < globalEscalationThreshold) {
                // if current threads locks < threshold - acquire entity id lock
                getOrCreateEntityLock(id).lock();
                entityLockCount.incrementAndGet();
                if (!keysLocked.contains(id)) {
                    keysLocked.add(id);
                }
                globalLock.unlock();
                beforeGlobalLock.unlock();
            } else {
                // escalate to global lock
                globalLock.unlock();
                beforeGlobalLock.unlock();
                escalateGlobal.set(true);
                globalLock();
            }
        }
    }

    /**
     * Checks whether entity with id is locked.
     * @param id entity id
     * @return true - if either entity lock or global lock is acquired by another thread
     *         false - otherwise
     */
    public boolean isLocked(K id) {
        return globalLock.isLocked() || getOrCreateEntityLock(id).isLocked();
    }

    /**
     * Tries to acquire the lock of entity.
     * Behaves the same as {@link ReentrantLock#tryLock} but on entity id level.
     * If lock could not be acquired in the provided time frame false is returned.
     *
     * Also will be blocked if a global lock is already locked.
     * In this case the time of waiting for global lock will be subtracted from provided timeout.
     *
     * @param id entity id
     * @param timeout timeout duration
     * @param timeUnit timeout time unit
     * @return true - if lock was acquired
     *         false - otherwise
     */
    public boolean tryLock(K id, long timeout, TimeUnit timeUnit) {
        if (id == null) {
            throw new NullPointerException("Trying to lock entity with null ID");
        }
        long before = System.nanoTime();
        try {
            globalLock.tryLock(timeout, timeUnit);
            long nanosTimeoutLeft = System.nanoTime() - before - timeUnit.toNanos(timeout);
            boolean locked = getOrCreateEntityLock(id).tryLock(nanosTimeoutLeft, timeUnit);
            if (locked) entityLockCount.incrementAndGet();
            return locked;
        } catch (InterruptedException e) {
            System.out.println("Thread was interrupted while trying to acquire the lock for entity id " + id);
            return false;
        } finally {
            globalLock.unlock();
        }
    }

    /**
     * Releases the lock for entity with id.
     * @param id entity id
     */
    public void unlock(K id) {
        unlockLock.lock();
        try {
            Set<K> keysLocked = currentThreadKeysLocked.get();
            ReentrantLock entityLock = getOrCreateEntityLock(id);
            if (entityLock != null && entityLock.isLocked()) {
                entityLock.unlock();
                keysLocked.remove(id);
                entityLockCount.decrementAndGet();
            }
            // if thread lock was escalated to global but all locks were released - reset the state
            if (escalateGlobal.get() && keysLocked.size() == 0) {
                globalLock.unlock();
                escalateGlobal.set(false);
            }
        } finally {
            unlockLock.unlock();
        }
    }

    /**
     * Global exclusive lock for all entity ids.
     * The lock will wait until all other entity id locks are released.
     * After that it will try to acquire the global lock.
     */
    public void globalLock() {
        // acquire lock before global so all threads will finish their code and wait for this lock
        beforeGlobalLock.lock();
        // consider amount of acquired locks minus locks hold by current thread
        long locksRemaining = entityLockCount.get() - currentThreadKeysLocked.get().size();
        // wait until all threads release their entity locks
        while (locksRemaining != 0) {
            globalLock.unlock();
            // too much logging so commented out
//            System.out.println("global lock waiting for other locks to unlock, remaining locks: " + locksRemaining);
            globalLock.lock();
            locksRemaining = entityLockCount.get() - currentThreadKeysLocked.get().size();
        }
        // release before global lock but global lock is already acquired
        // by thread at this point and no more threads can lock any entity id
        beforeGlobalLock.unlock();
    }

    /**
     * Releases the global lock.
     * If there was a global lock escalation - unlock all locks.
     */
    public void globalUnlock() {
        unlockLock.lock();
        try {
            if (escalateGlobal.get()) {
                Set<K> keysLocked = currentThreadKeysLocked.get();
                keysLocked.forEach(key -> getOrCreateEntityLock(key).unlock());
                keysLocked.clear();
            }
            globalLock.unlock();
        } finally {
            unlockLock.unlock();
        }
    }

    /**
     * Checks whether the global lock is currently locked.
     * @return true if locked, false otherwise
     */
    public boolean isGlobalLock() {
        return globalLock.isLocked();
    }

    /**
     * Gets the lock object for entity id.
     * Ensures that the lock exists, otherwise creates it.
     * @param id - id of locking entity
     * @return entity lock
     */
    private ReentrantLock getOrCreateEntityLock(K id) {
        createEntityLockLock.lock();
        ReentrantLock entityLock = entitiesLockingMap.get(id);
        if (entityLock == null) {
            entityLock = new ReentrantLock();
            entitiesLockingMap.put(id, entityLock);
        }
        createEntityLockLock.unlock();
        return entityLock;
    }

    public void setGlobalEscalationThreshold(int globalEscalationThreshold) {
        this.globalEscalationThreshold = globalEscalationThreshold;
    }
}
