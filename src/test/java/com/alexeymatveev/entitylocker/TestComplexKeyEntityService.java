package com.alexeymatveev.entitylocker;

import com.alexeymatveev.entitylocker.model.ComplexKey;
import com.alexeymatveev.entitylocker.model.ComplexKeyEntity;
import com.alexeymatveev.entitylocker.service.BaseEntityService;
import com.alexeymatveev.entitylocker.service.impl.UnsafeComplexKeyEntityServiceImpl;

/**
 * Run this class to run tests in {@link BaseEntityTest} on {@link ComplexKeyEntity} entity.
 *
 * Created by Alexey Matveev on 4/4/2018.
 */
public class TestComplexKeyEntityService extends BaseEntityTest<ComplexKey, ComplexKeyEntity> {

    public static void main(String[] args) {
        TestComplexKeyEntityService instance = new TestComplexKeyEntityService();
        System.out.println("# Running " + ComplexKeyEntity.class.getSimpleName() + " entity tests");
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
    ComplexKeyEntity createRandomEntity() {
        return new ComplexKeyEntity(randomString());
    }

    @Override
    BaseEntityService<ComplexKey, ComplexKeyEntity> getEntityService() {
        return new UnsafeComplexKeyEntityServiceImpl();
    }

    @Override
    void setEntityId(ComplexKeyEntity entity, ComplexKey id) {
        entity.setId(id);
    }

    @Override
    ComplexKey getEntityId(ComplexKeyEntity entity) {
        return entity.getId();
    }

    @Override
    ComplexKeyEntity cloneEntity(ComplexKeyEntity entity) {
        return entity.clone();
    }

    @Override
    void changeEntity(ComplexKeyEntity entity) {
        entity.setText(randomString());
    }

    @Override
    boolean checkEquals(ComplexKeyEntity e1, ComplexKeyEntity e2) {
        if (e1 == null) return e2 == null;
        else return e1.equals(e2);
    }
}
