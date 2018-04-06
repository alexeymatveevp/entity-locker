package com.alexeymatveev.entitylocker.model;

import java.util.Objects;

/**
 * Created by Alexey Matveev on 4/4/2018.
 */
public class ComplexKeyEntity {

    private ComplexKey id;

    private String text;

    public ComplexKeyEntity(ComplexKey id, String text) {
        this.id = id;
        this.text = text;
    }

    public ComplexKeyEntity(String text) {
        this.text = text;
    }

    public ComplexKeyEntity clone() {
        return new ComplexKeyEntity(this.id == null ? null : this.id.clone(), this.text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplexKeyEntity that = (ComplexKeyEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, text);
    }

    @Override
    public String toString() {
        return "ComplexKeyEntity{" +
                "id=" + id +
                ", text='" + text + '\'' +
                '}';
    }

    public ComplexKey getId() {
        return id;
    }

    public void setId(ComplexKey id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
