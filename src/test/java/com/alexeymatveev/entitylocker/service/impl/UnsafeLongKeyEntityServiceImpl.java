package com.alexeymatveev.entitylocker.service.impl;

import com.alexeymatveev.entitylocker.model.LongKeyEntity;
import com.alexeymatveev.entitylocker.service.LongKeyEntityService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Alexey Matveev on 4/4/2018.
 */
public class UnsafeLongKeyEntityServiceImpl implements LongKeyEntityService {

    private AtomicLong identifier = new AtomicLong();

    private Map<Long, LongKeyEntity> MEMORY_ENTITY_STORAGE = new ConcurrentHashMap<>();

    @Override
    public Long create(LongKeyEntity entity) {
        Long id = identifier.incrementAndGet();
        entity.setId(id);
        // clone to emulate remote storage
        MEMORY_ENTITY_STORAGE.put(id, entity.clone());
        return id;
    }

    @Override
    public LongKeyEntity get(Long id) {
        LongKeyEntity entity = MEMORY_ENTITY_STORAGE.get(id);
        // clone to emulate remote storage
        return entity == null ? null : entity.clone();
    }

    @Override
    public List<Long> listIds() {
        return new ArrayList<>(MEMORY_ENTITY_STORAGE.keySet());
    }

    @Override
    public void update(LongKeyEntity entity) {
        // clone to emulate remote storage
        MEMORY_ENTITY_STORAGE.put(entity.getId(), entity.clone());
    }

    @Override
    public void delete(Long id) {
        MEMORY_ENTITY_STORAGE.remove(id);
    }

}
