package com.alexeymatveev.entitylocker;

import com.alexeymatveev.entitylocker.service.BaseEntityService;
import com.alexeymatveev.entitylocker.service.ComplexKeyEntityService;
import com.alexeymatveev.entitylocker.service.LongKeyEntityService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base test class for testing different entity services:
 * - {@link LongKeyEntityService}
 * - {@link ComplexKeyEntityService}
 *
 * Tests include locking / unlocking protected code blocks with {@link EntityLocker}.
 *
 * Try running the tests with / without lock / unlock methods.
 *
 * Try changing the parameters of tests as described in comments.
 *
 * Created by Alexey Matveev on 4/4/2018.
 */
public abstract class BaseEntityTest<K, T> {

    // increase number of threads to increase the possible load on service
    private int numberOfThreads = 1000;

    // if we increase the number of simultaneous entities (say 50 or 1000) updating then even for
    // unsafe version of service the number of dirty reads is decreasing (or even absent)
    private int numberOfEntities = 4; // change to 50 with disabled locking to see less errors

    // time for 3rd test which thread waits for the lock to be acquired
    // change the timeout to see that more messages that lock was not acquired due to timeout
    private int lockTimeoutMilliseconds = 200; // change to 1 or 2

    // max number of locks each thread may hold until escalating to global lock
    private int globalEscalationThreshold = 2;

    /**
     * Creates N entities and starts randomly updating them and getting back.
     * If not protect update/get code with entity locker the immediately returned value may be different.
     * Comment lock / unlock methods out it out to see assertion errors.
     */
    protected void testCreateManyAndCrossConcurrentUpdates() throws InterruptedException {
        final BaseEntityService<K, T> entityService = getEntityService();
        // create entity locker to lock protected code
        final EntityLocker<K> entityLocker = new EntityLocker<>();

        List<T> entities = new ArrayList<>();
        System.out.println("Creating " + numberOfEntities + " entities, updating " + numberOfThreads + " times");
        for (int i = 0; i< numberOfEntities; i++) {
            final T randomEntity = createRandomEntity();
            K entityId = entityService.create(randomEntity);
            setEntityId(randomEntity, entityId);
            entities.add(randomEntity);
        }
        CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        for (int i=0; i<numberOfThreads; i++) {
            Thread t = new Thread(() -> {
                try {
                    // update random entity's headline
                    T entity = cloneEntity(entities.get(randomNumber(0, numberOfEntities - 1)));
                    changeEntity(entity);

                    K entityId = getEntityId(entity);
                    // comment out lock & unlock methods to see assertion fails
                    entityLocker.lock(entityId);
                    entityService.update(entity);
                    T updatedEntity = entityService.get(entityId);
                    entityLocker.unlock(entityId);

                    assertTrue(checkEquals(entity, updatedEntity), "Entity was changed after update by other thread");
                } finally {
                    countDownLatch.countDown();
                }
            });
            t.start();
        }
        countDownLatch.await();
    }

    /**
     * Creates / updates and deletes entities simultaneously.
     * The random distribution actions is the following:
     * - 10% created
     * - 70% updated
     * - 20% deleted
     * Using entity locker to wrap protected / transactional code.
     * Comment lock / unlock methods out it out to see assertion errors.
     */
    protected void testCreateUpdateDeleteManyConcurrentEntities() throws InterruptedException {
        final BaseEntityService<K, T> entityService = getEntityService();
        // create entity locker to lock protected code
        final EntityLocker<K> entityLocker = new EntityLocker<>();

        AtomicInteger created = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger deleted = new AtomicInteger();

        System.out.println("Performing " + numberOfThreads + " different actions");
        CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        for (int i=0; i<numberOfThreads; i++) {
            Thread t = new Thread(() -> {
                try {
                    // choose random action with random factor:
                    // - 10% create
                    // - 70% update
                    // - 20% delete
                    int action = randomNumber(0, 9);
                    if (action < 1) {
                        // CREATE
                        created.incrementAndGet();
                        entityService.create(createRandomEntity());
                    } else if (action < 8) {
                        // UPDATE
                        updated.incrementAndGet();
                        List<K> ids = entityService.listIds();
                        if (ids != null && ids.size() > 0) {
                            K idToUpdate = ids.get(randomNumber(0, ids.size() - 1));
                            try {
                                entityLocker.lock(idToUpdate); // comment me out
                                T entityToUpdate = entityService.get(idToUpdate);
                                // check in case it was deleted
                                if (entityToUpdate != null) {
                                    changeEntity(entityToUpdate);
                                    entityService.update(entityToUpdate);
                                    T updatedEntity = entityService.get(idToUpdate);

                                    assertTrue(checkEquals(entityToUpdate, updatedEntity), "Updated entity is not matching itself");
                                }
                            } finally {
                                entityLocker.unlock(idToUpdate); // comment me out
                            }
                        }
                    } else {
                        // DELETE
                        deleted.incrementAndGet();
                        List<K> ids = entityService.listIds();
                        if (ids != null && ids.size() > 0) {
                            K idToDelete = ids.get(randomNumber(0, ids.size() - 1));
                            try {
                                entityLocker.lock(idToDelete); // comment me out
                                T entityToDelete = entityService.get(idToDelete);
                                // check in case it was deleted
                                if (entityToDelete != null) {
                                    entityService.delete(idToDelete);
                                    T deletedEntity = entityService.get(idToDelete);

                                    assertTrue(deletedEntity == null, "Entity was not deleted");
                                }
                            } finally {
                                entityLocker.unlock(idToDelete); // comment me out
                            }
                        }
                    }
                } finally {
                    countDownLatch.countDown();
                }
            });
            t.start();
        }
        countDownLatch.await();
        System.out.println("Create actions: " + created.get());
        System.out.println("Update actions: " + updated.get());
        System.out.println("Delete actions: " + deleted.get());
    }

    /**
     * Test verifies that it's possible to acquire the same lock twice.
     * If locking same entity id twice and unlocking once - the lock will still be acquired.
     * Only after number of locks / unlocks are equal the lock is released.
     */
    protected void testReentrant() throws InterruptedException {
        final BaseEntityService<K, T> entityService = getEntityService();
        // create entity locker to lock protected code
        final EntityLocker<K> entityLocker = new EntityLocker<>();

        final T randomEntity = createRandomEntity();
        K entityId = entityService.create(randomEntity);
        setEntityId(randomEntity, entityId);

        // lock 3 times and check unlock status
        System.out.println("Locking single entity several times, checking reentrant is possible");
        entityLocker.lock(entityId);
        entityLocker.lock(entityId);
        entityLocker.lock(entityId);
        entityLocker.unlock(entityId);
        assertTrue(entityLocker.isLocked(entityId), "Entity is not locked by this thread, but should be locked 2 times more");
        entityLocker.unlock(entityId);
        entityLocker.unlock(entityId);
        assertTrue(!entityLocker.isLocked(entityId), "Entity locked by this thread, but should already be unlocked");

        // lock 2 times, check that it's locked for other thread
        entityLocker.lock(entityId);
        entityLocker.lock(entityId);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                boolean lockAcquired = entityLocker.tryLock(entityId, 5, TimeUnit.SECONDS);
                assertTrue(!lockAcquired, "It is possible to acquire already locked entity's lock");
            } finally {
                countDownLatch.countDown();
            }
        });
        t.start();
        countDownLatch.await();
        entityLocker.unlock(entityId);
        entityLocker.unlock(entityId);
    }

    /**
     * Create one entity and update it many times with {@link EntityLocker#tryLock} and locking timeout.
     * Change the timeout in {@link BaseEntityTest#lockTimeoutMilliseconds} and see errors in the console output.
     */
    protected void testLockingTimeout() throws InterruptedException {
        final BaseEntityService<K, T> entityService = getEntityService();
        // create entity locker to lock protected code
        final EntityLocker<K> entityLocker = new EntityLocker<>();

        CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        System.out.println("Create 1 entity, update " + numberOfThreads + " times with timeout " + lockTimeoutMilliseconds + "ms");
        T randomEntity = createRandomEntity();
        K id = entityService.create(randomEntity);
        for (int i=0; i<numberOfThreads; i++) {
            Thread t = new Thread(() -> {
                try {
                    if (entityLocker.tryLock(id, lockTimeoutMilliseconds, TimeUnit.MILLISECONDS)) {
                        entityService.update(randomEntity);
                        entityLocker.unlock(id);
                    } else {
                        System.out.println("Lock for entity id " + id + " was not acquired due to timeout");
                    }
                } finally {
                    countDownLatch.countDown();
                }
            });
            t.start();
        }
        countDownLatch.await();
    }

    /**
     * The following test produces deadlock using entity locker.
     * (Do not run it unless you want to see deadlock)
     */
    protected void testDeadlockOutsideEntityLocker() throws InterruptedException {
        final BaseEntityService<K, T> entityService = getEntityService();
        // create entity locker to lock protected code
        final EntityLocker<K> entityLocker = new EntityLocker<>();

        final T re1 = createRandomEntity();
        K id1 = entityService.create(re1);
        setEntityId(re1, id1);

        final T re2 = createRandomEntity();
        K id2 = entityService.create(re2);
        setEntityId(re2, id2);

        CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        for (int i=0; i<numberOfThreads; i++) {
            Thread t = new Thread(() -> {
                try {
                    entityLocker.lock(id1);
                    entityLocker.lock(id2);
                    entityLocker.unlock(id2);
                    entityLocker.unlock(id1);
//                    if (randomNumber(0, 1) == 0) {
//
//                    } else {
//                        entityLocker.lock(id2);
//                        entityLocker.lock(id1);
//                        entityLocker.unlock(id1);
//                        entityLocker.unlock(id2);
//                    }
                } finally {
                    countDownLatch.countDown();
                }
            });
            t.start();
        }
        countDownLatch.await();
    }

    /**
     * Test checks that global lock prevents all other thread activities.
     * 2 entities are created and there are 3 different thread groups:
     * - threads updating entity 1
     * - threads updating entity 2
     * - threads that obtain global lock and checking that entities are not changing
     */
    protected void testGlobalLock() throws InterruptedException {
        final BaseEntityService<K, T> entityService = getEntityService();
        final EntityLocker<K> entityLocker = new EntityLocker<>();

        System.out.println("Constantly updating 2 entities, 3rd thread group acquires global lock, sleeps, and checks that entities were not updated meanwhile");
        final T re1 = createRandomEntity();
        K id1 = entityService.create(re1);
        setEntityId(re1, id1);

        final T re2 = createRandomEntity();
        K id2 = entityService.create(re2);
        setEntityId(re2, id2);

        CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);
        for (int i=0; i<numberOfThreads; i++) {
            Thread t = new Thread(() -> {
                try {
                    // thread groups:
                    // - 30% - updating entity 1
                    // - 60% - updating entity 2
                    // - 10% - global lock and checking all entity no changes
                    int action = randomNumber(0, 9);
                    if (action < 9) {
                        K id = action < 3 ? id1 : id2; // either re1 or re2 id (30% / 60%)
                        entityLocker.lock(id);
                        T entity = entityService.get(id);
                        changeEntity(entity);
                        entityService.update(entity);
                        entityLocker.unlock(id);
                    } else {
                        entityLocker.globalLock();
                        T entity1 = entityService.get(id1);
                        T entity2 = entityService.get(id2);
                        long delay = 20L;
                        Thread.sleep(delay);
                        T entity1Changed = entityService.get(id1);
                        T entity2Changed = entityService.get(id2);
                        assertTrue(checkEquals(entity1, entity1Changed), "Entity 1 was changed during global lock");
                        assertTrue(checkEquals(entity2, entity2Changed), "Entity 2 was changed during global lock");
                        entityLocker.globalUnlock();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
            t.start();
        }
        countDownLatch.await();
    }

    /**
     * Test that if single thread acquires too much locks all those locks are unlocked
     * and global lock is acquired instead
     */
    protected void testGlobalLockEscalation() throws InterruptedException {
        final BaseEntityService<K, T> entityService = getEntityService();
        final EntityLocker<K> entityLocker = new EntityLocker<>();

        // set the threshold number of simultaneous locks one thread may hold before escalation to global
        entityLocker.setGlobalEscalationThreshold(globalEscalationThreshold);

        System.out.println("Creating " + numberOfEntities + " entities, single thread acquiring lock on all of them; global lock threshold: " + globalEscalationThreshold);

        List<T> entities = new ArrayList<>();
        for (int i = 0; i<numberOfEntities; i++) {
            final T randomEntity = createRandomEntity();
            K entityId = entityService.create(randomEntity);
            setEntityId(randomEntity, entityId);
            entities.add(randomEntity);
            // acquire lock on each entity id
            entityLocker.lock(entityId);
        }

        // check lock is global
        assertTrue(entityLocker.isGlobalLock(), "Lock is not escalated to global after thread locked " + numberOfEntities + " entity ids");

        // try to unlock everything to reset global lock
        for (T entity : entities) {
            entityLocker.unlock(getEntityId(entity));
        }

        // check no global lock
        assertTrue(!entityLocker.isGlobalLock(), "Global escalated lock is not released after unlocking all locks");

    }

    protected void runTest(String methodName) {
        try {
            Method testMethod = getMethod(this.getClass(), methodName);
            if (testMethod != null) {
                testMethod.invoke(this);
                System.out.println("\nTest " + methodName + " completed\n--\n");
            } else {
                System.out.println("Test method: " + methodName + " not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Method getMethod(Class<?> clazz, String name) {
        if (clazz.equals(Object.class)) {
            return null;
        }
        try {
            return clazz.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            return getMethod(clazz.getSuperclass(), name);
        }
    }

    abstract T createRandomEntity();

    abstract BaseEntityService<K, T> getEntityService();

    abstract void setEntityId(T entity, K id);

    abstract K getEntityId(T entity);

    abstract T cloneEntity(T entity);

    abstract void changeEntity(T entity);

    abstract boolean checkEquals(T e1, T e2);

    String randomString() {
        return UUID.randomUUID().toString();
    }

    int randomNumber(int start, int end) {
        return ThreadLocalRandom.current().nextInt(start, end + 1);
    }

    void assertTrue(boolean condition, String message) {
        if (!condition) {
            System.out.println("Assertion error!: " + message);
        }
    }

}
