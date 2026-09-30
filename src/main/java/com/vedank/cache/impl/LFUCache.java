package com.vedank.cache.impl;

import com.vedank.cache.core.CacheConfig;
import com.vedank.cache.eviction.LFUEvictionPolicy;

/**
 * Cache that evicts the Least-Frequently-Used key once it exceeds capacity,
 * breaking ties between equally-frequent keys by recency within that
 * frequency tier. See {@link com.vedank.cache.eviction.LFUEvictionPolicy}.
 */
public final class LFUCache<K, V> extends AbstractCache<K, V> {

    public LFUCache(CacheConfig config) {
        super(new LFUEvictionPolicy<>(), config);
    }

    public LFUCache(int capacity) {
        this(CacheConfig.builder().capacity(capacity).build());
    }
}
