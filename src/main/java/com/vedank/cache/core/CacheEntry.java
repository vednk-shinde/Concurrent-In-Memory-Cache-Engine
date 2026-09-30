package com.vedank.cache.core;

/**
 * A single stored value plus the bookkeeping needed for TTL expiry.
 *
 * <p>This class is intentionally dumb: it knows nothing about eviction order
 * or frequency, that is the {@link com.vedank.cache.eviction.EvictionPolicy}'s
 * job. Keeping the two concerns separate is what lets the same entry type be
 * reused by LRU, LFU, or any future eviction strategy.
 */
public final class CacheEntry<K, V> {

    private final K key;
    private final V value;
    private final long createdAt;
    /** Absolute expiry timestamp in millis since epoch, or -1 if it never expires. */
    private final long expiresAt;

    public CacheEntry(K key, V value, long ttlMillis) {
        this.key = key;
        this.value = value;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = ttlMillis > 0 ? this.createdAt + ttlMillis : -1;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean hasTtl() {
        return expiresAt > 0;
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }
}
