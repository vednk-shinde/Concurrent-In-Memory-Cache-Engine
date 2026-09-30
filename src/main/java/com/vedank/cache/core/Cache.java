package com.vedank.cache.core;

import com.vedank.cache.metrics.CacheMetrics;

/**
 * Public contract for the cache engine. Every eviction strategy (LRU, LFU, ...)
 * implements this same interface so callers can swap strategies without
 * changing any calling code.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Cache<K, V> {

    /**
     * Inserts or updates a value with no expiry.
     */
    void put(K key, V value);

    /**
     * Inserts or updates a value that automatically expires after {@code ttlMillis}
     * milliseconds. A non-positive TTL means "no expiry".
     */
    void put(K key, V value, long ttlMillis);

    /**
     * Returns the value for {@code key}, or {@code null} on a cache miss
     * (key absent, or present but expired).
     */
    V get(K key);

    /**
     * Removes {@code key} and returns its value, or {@code null} if absent.
     */
    V remove(K key);

    /**
     * True if {@code key} is present and not expired.
     */
    boolean containsKey(K key);

    /**
     * Number of live (non-expired) entries currently stored.
     */
    int size();

    /**
     * Removes every entry and resets eviction-policy bookkeeping. Metrics are
     * NOT reset, since they describe the cache's lifetime behavior.
     */
    void clear();

    /**
     * Runtime counters (hits, misses, evictions, hit rate, ...).
     */
    CacheMetrics metrics();
}
