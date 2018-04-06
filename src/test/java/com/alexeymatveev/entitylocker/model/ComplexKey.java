package com.alexeymatveev.entitylocker.model;

import java.util.Objects;

/**
 * Created by Alexey Matveev on 4/4/2018.
 */
public class ComplexKey {

    private Object keyPartObject;

    private String keyPartString;

    private Long keyPartLong;

    public ComplexKey(Object keyPartObject, String keyPartString, Long keyPartLong) {
        this.keyPartObject = keyPartObject;
        this.keyPartString = keyPartString;
        this.keyPartLong = keyPartLong;
    }

    public ComplexKey clone() {
        return new ComplexKey(this.keyPartObject, this.keyPartString, this.keyPartLong);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplexKey that = (ComplexKey) o;
        return Objects.equals(keyPartObject, that.keyPartObject) &&
                Objects.equals(keyPartString, that.keyPartString) &&
                Objects.equals(keyPartLong, that.keyPartLong);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyPartObject, keyPartString, keyPartLong);
    }
}
