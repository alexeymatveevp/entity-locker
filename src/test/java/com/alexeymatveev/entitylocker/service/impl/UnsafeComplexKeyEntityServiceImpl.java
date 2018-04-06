package com.alexeymatveev.entitylocker.service.impl;

import com.alexeymatveev.entitylocker.model.ComplexKey;
import com.alexeymatveev.entitylocker.model.ComplexKeyEntity;
import com.alexeymatveev.entitylocker.service.ComplexKeyEntityService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Alexey Matveev on 4/4/2018.
 */
public class UnsafeComplexKeyEntityServiceImpl implements ComplexKeyEntityService {

    private Map<ComplexKey, ComplexKeyEntity> MEMORY_ENTITY_STORAGE = new ConcurrentHashMap<>();

    @Override
    public ComplexKey create(ComplexKeyEntity entity) {
        ComplexKey id = new ComplexKey(new Object(), UUID.randomUUID().toString(), (long)(Math.random()*1000));
        entity.setId(id);
        ComplexKeyEntity clone = entity.clone();
        MEMORY_ENTITY_STORAGE.put(clone.getId(), clone);
        return id;
    }

    @Override
    public ComplexKeyEntity get(ComplexKey id) {
        ComplexKeyEntity entity = MEMORY_ENTITY_STORAGE.get(id);
        return entity == null ? null : entity.clone();
    }

    @Override
    public List<ComplexKey> listIds() {
        return new ArrayList<>(MEMORY_ENTITY_STORAGE.keySet());
    }

    @Override
    public void update(ComplexKeyEntity entity) {
        ComplexKeyEntity clone = entity.clone();
        MEMORY_ENTITY_STORAGE.put(clone.getId(), clone);
    }

    @Override
    public void delete(ComplexKey id) {
        MEMORY_ENTITY_STORAGE.remove(id);
    }
}
