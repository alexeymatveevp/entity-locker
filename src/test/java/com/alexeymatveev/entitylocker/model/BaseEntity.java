package com.alexeymatveev.entitylocker.model;

/**
 * Created by Alexey Matveev on 4/4/2018.
 */
public interface BaseEntity<K> {

    K getId();

    void setId(K id);
}
