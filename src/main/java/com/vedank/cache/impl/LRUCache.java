package com.vedank.cache.impl;

import com.vedank.cache.core.CacheConfig;
import com.vedank.cache.eviction.LRUEvictionPolicy;

/**
 * Cache that evicts the Least-Recently-Used key once it exceeds capacity.
 * See {@link com.vedank.cache.eviction.LRUEvictionPolicy} for the underlying
 * HashMap + doubly linked list mechanics.
 */
public final class LRUCache<K, V> extends AbstractCache<K, V> {

    public LRUCache(CacheConfig config) {
        super(new LRUEvictionPolicy<>(), config);
    }

    public LRUCache(int capacity) {
        this(CacheConfig.builder().capacity(capacity).build());
    }
}
