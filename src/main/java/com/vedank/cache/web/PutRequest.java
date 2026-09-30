package com.vedank.cache.web;

/** Request body for {@code PUT /api/cache/{key}}. {@code ttlMillis} is optional. */
public class PutRequest {
    private Object value;
    private Long ttlMillis;

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Long getTtlMillis() {
        return ttlMillis;
    }

    public void setTtlMillis(Long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }
}
