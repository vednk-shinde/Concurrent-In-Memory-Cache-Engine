package com.vedank.cache.eviction;

/**
 * Strategy interface for deciding *which key* to evict when a cache is full.
 *
 * <p>Implementations only track ordering/frequency metadata about keys - they
 * never touch the actual stored values. That separation is what lets
 * {@code AbstractCache} plug in {@link LRUEvictionPolicy} or
 * {@link LFUEvictionPolicy} interchangeably without either policy knowing
 * anything about {@code CacheEntry} or {@code V}.
 *
 * <p>Implementations are NOT thread-safe by themselves; the owning cache is
 * responsible for synchronizing all calls into a policy.
 *
 * @param <K> key type
 */
public interface EvictionPolicy<K> {

    /**
     * Called whenever an existing key is read or refreshed (a GET hit, or a
     * PUT that updates a key already present).
     */
    void onAccess(K key);

    /**
     * Called when a brand-new key is added to the cache.
     */
    void onInsert(K key);

    /**
     * Chooses and removes a victim key from this policy's own bookkeeping,
     * returning it so the caller can remove it from the actual data store.
     * Returns {@code null} if the policy is tracking no keys.
     */
    K evict();

    /**
     * Called when a key leaves the cache for any reason other than
     * {@link #evict()} choosing it - explicit REMOVE, CLEAR, or TTL expiry -
     * so the policy can drop its bookkeeping for that key.
     */
    void onRemove(K key);

    /**
     * Number of keys currently tracked by this policy.
     */
    int size();

    /**
     * Drops all bookkeeping.
     */
    void clear();
}
