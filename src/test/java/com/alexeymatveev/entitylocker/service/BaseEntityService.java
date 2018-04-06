package com.alexeymatveev.entitylocker.service;

import java.util.List;

/**
 * Created by Alexey Matveev on 4/4/2018.
 */
public interface BaseEntityService<K, T> {

    K create(T entity);

    T get(K id);

    List<K> listIds();

    void update(T entity);

    void delete(K id);

}
