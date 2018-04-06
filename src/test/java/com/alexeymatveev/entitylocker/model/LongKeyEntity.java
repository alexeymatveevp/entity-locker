package com.alexeymatveev.entitylocker.model;

import java.util.Objects;

/**
 * Created by Alexey Matveev on 4/4/2018.
 */
public class LongKeyEntity {

    private Long id;

    private String headline;

    public LongKeyEntity(String headline) {
        this.headline = headline;
    }

    public LongKeyEntity(Long id, String headline) {
        this.id = id;
        this.headline = headline;
    }

    public LongKeyEntity clone() {
        return new LongKeyEntity(this.id, this.headline);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LongKeyEntity that = (LongKeyEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(headline, that.headline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, headline);
    }

    @Override
    public String toString() {
        return "LongKeyEntity{" +
                "id=" + id +
                ", headline='" + headline + '\'' +
                '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }
}
