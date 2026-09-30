package com.vedank.cache.impl;

import com.vedank.cache.core.Cache;
import com.vedank.cache.core.CacheConfig;
import com.vedank.cache.core.CacheEntry;
import com.vedank.cache.eviction.EvictionPolicy;
import com.vedank.cache.metrics.CacheMetrics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * "Version 1" concurrency model: a single {@link ReentrantLock} guards the
 * key -> entry map AND the {@link EvictionPolicy}'s bookkeeping together, so
 * every GET/PUT/REMOVE/CLEAR is a single atomic critical section.
 *
 * <pre>
 *   GET ----+
 *   PUT ----+---> one ReentrantLock ---> HashMap + EvictionPolicy
 *   REMOVE -+
 *   CLEAR --+
 * </pre>
 *
 * This is simple to reason about and perfectly correct, but every operation
 * - even two unrelated GETs - serializes on the same lock. {@link ShardedLRUCache}
 * is "Version 2": it partitions the keyspace across many independent
 * instances of this class to reduce contention. See the benchmark module for
 * a throughput comparison of the two.
 *
 * <p>Concrete subclasses just supply an {@link EvictionPolicy}; all map
 * management, TTL handling and metrics live here so LRU/LFU share one
 * implementation (matches the {@code Cache<K,V> <- LRUCache / LFUCache}
 * hierarchy from the design doc).
 */
public abstract class AbstractCache<K, V> implements Cache<K, V> {

    private final Map<K, CacheEntry<K, V>> store = new HashMap<>();
    private final EvictionPolicy<K> evictionPolicy;
    private final CacheConfig config;
    private final CacheMetrics metrics = new CacheMetrics();
    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService activeExpiryScheduler;

    protected AbstractCache(EvictionPolicy<K> evictionPolicy, CacheConfig config) {
        this.evictionPolicy = evictionPolicy;
        this.config = config;
        this.activeExpiryScheduler = config.isActiveExpiryEnabled() ? startActiveExpiry(config) : null;
    }

    private ScheduledExecutorService startActiveExpiry(CacheConfig config) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-active-expiry");
            t.setDaemon(true);
            return t;
        });
        long interval = config.getActiveExpirySweepIntervalMillis();
        scheduler.scheduleAtFixedRate(this::sweepExpiredEntries, interval, interval, TimeUnit.MILLISECONDS);
        return scheduler;
    }

    @Override
    public void put(K key, V value) {
        put(key, value, config.getDefaultTtlMillis());
    }

    @Override
    public void put(K key, V value, long ttlMillis) {
        lock.lock();
        try {
            CacheEntry<K, V> entry = new CacheEntry<>(key, value, ttlMillis);
            CacheEntry<K, V> previous = store.put(key, entry);
            if (previous == null) {
                evictionPolicy.onInsert(key);
                evictIfOverCapacity(key);
            } else {
                evictionPolicy.onAccess(key);
            }
            metrics.recordPut();
        } finally {
            lock.unlock();
        }
    }

    private void evictIfOverCapacity(K justInserted) {
        while (store.size() > config.getCapacity()) {
            K victim = evictionPolicy.evict();
            if (victim == null || victim.equals(justInserted)) {
                return; // nothing left to evict, or capacity is smaller than 1 live entry
            }
            store.remove(victim);
            metrics.recordEviction();
        }
    }

    @Override
    public V get(K key) {
        lock.lock();
        try {
            CacheEntry<K, V> entry = store.get(key);
            if (entry == null) {
                metrics.recordMiss();
                return null;
            }
            if (entry.isExpired()) {
                expireEntry(key);
                metrics.recordMiss();
                return null;
            }
            evictionPolicy.onAccess(key);
            metrics.recordHit();
            return entry.getValue();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V remove(K key) {
        lock.lock();
        try {
            CacheEntry<K, V> entry = store.remove(key);
            if (entry == null) {
                return null;
            }
            evictionPolicy.onRemove(key);
            metrics.recordRemoval();
            return entry.isExpired() ? null : entry.getValue();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsKey(K key) {
        lock.lock();
        try {
            CacheEntry<K, V> entry = store.get(key);
            if (entry == null) {
                return false;
            }
            if (entry.isExpired()) {
                expireEntry(key);
                return false;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return store.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            store.clear();
            evictionPolicy.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CacheMetrics metrics() {
        return metrics;
    }

    /** Stops the background TTL-sweep thread, if active expiry was enabled. */
    public void shutdown() {
        if (activeExpiryScheduler != null) {
            activeExpiryScheduler.shutdownNow();
        }
    }

    private void expireEntry(K key) {
        store.remove(key);
        evictionPolicy.onRemove(key);
        metrics.recordExpiration();
    }

    private void sweepExpiredEntries() {
        lock.lock();
        try {
            Iterator<Map.Entry<K, CacheEntry<K, V>>> it = store.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<K, CacheEntry<K, V>> e = it.next();
                if (e.getValue().isExpired()) {
                    it.remove();
                    evictionPolicy.onRemove(e.getKey());
                    metrics.recordExpiration();
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
