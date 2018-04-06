package com.alexeymatveev.entitylocker;

import com.alexeymatveev.entitylocker.model.ComplexKeyEntity;
import com.alexeymatveev.entitylocker.model.LongKeyEntity;
import com.alexeymatveev.entitylocker.service.BaseEntityService;
import com.alexeymatveev.entitylocker.service.impl.UnsafeLongKeyEntityServiceImpl;

/**
 * Run this class to run tests in {@link BaseEntityTest} on {@link LongKeyEntity} entity.
 *
 * Created by Alexey Matveev on 4/4/2018.
 */
public class TestLongKeyEntityService extends BaseEntityTest<Long, LongKeyEntity> {

    public static void main(String[] args) {
        TestLongKeyEntityService instance = new TestLongKeyEntityService();
        System.out.println("# Running " + LongKeyEntity.class.getSimpleName() + " entity tests");
        instance.runTest("testCreateManyAndCrossConcurrentUpdates");
        instance.runTest("testCreateUpdateDeleteManyConcurrentEntities");
        instance.runTest("testReentrant");
        instance.runTest("testLockingTimeout");
        // this test produces deadlock using entity locker
//        instance.runTest("testDeadlockOutsideEntityLocker");
        instance.runTest("testGlobalLock");
        instance.runTest("testGlobalLockEscalation");
    }

    @Override
    LongKeyEntity createRandomEntity() {
        return new LongKeyEntity(randomString());
    }

    @Override
    BaseEntityService<Long, LongKeyEntity> getEntityService() {
        return new UnsafeLongKeyEntityServiceImpl();
    }

    @Override
    void setEntityId(LongKeyEntity entity, Long id) {
        entity.setId(id);
    }

    @Override
    Long getEntityId(LongKeyEntity entity) {
        return entity.getId();
    }

    @Override
    LongKeyEntity cloneEntity(LongKeyEntity entity) {
        return entity.clone();
    }

    @Override
    void changeEntity(LongKeyEntity entity) {
        entity.setHeadline(randomString());
    }

    @Override
    boolean checkEquals(LongKeyEntity e1, LongKeyEntity e2) {
        if (e1 == null) return e2 == null;
        else return e1.equals(e2);
    }


}
