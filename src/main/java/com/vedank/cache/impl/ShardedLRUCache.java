package com.vedank.cache.impl;

import com.vedank.cache.core.Cache;
import com.vedank.cache.core.CacheConfig;
import com.vedank.cache.metrics.CacheMetrics;

import java.util.ArrayList;
import java.util.List;

/**
 * "Version 2" concurrency model: lock striping. Instead of one lock guarding
 * every key ({@link LRUCache} / {@link AbstractCache}), the keyspace is
 * partitioned into {@code shardCount} independent {@link LRUCache} shards,
 * each with its own lock and its own share of the total capacity:
 *
 * <pre>
 *   GET(A) ---\
 *   PUT(B) ----+--> hash(key) % N --> shard[i] (own lock, own LRU list)
 *   GET(C) ---/
 * </pre>
 *
 * Two threads touching different shards never block each other, which is
 * why this scales better under high concurrency than the single-lock
 * version - at the cost of a slightly weaker global LRU ordering (eviction
 * is only "least recently used within its shard", not globally). The
 * benchmark module measures exactly this trade-off.
 */
public final class ShardedLRUCache<K, V> implements Cache<K, V> {

    private final List<LRUCache<K, V>> shards;
    private final int shardCount;

    public ShardedLRUCache(CacheConfig config) {
        this.shardCount = config.getShardCount();
        int perShardCapacity = Math.max(1, config.getCapacity() / shardCount);
        CacheConfig shardConfig = CacheConfig.builder()
                .capacity(perShardCapacity)
                .defaultTtlMillis(config.getDefaultTtlMillis())
                .activeExpiryEnabled(config.isActiveExpiryEnabled())
                .activeExpirySweepIntervalMillis(config.getActiveExpirySweepIntervalMillis())
                .build();
        this.shards = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            shards.add(new LRUCache<>(shardConfig));
        }
    }

    public ShardedLRUCache(int totalCapacity, int shardCount) {
        this(CacheConfig.builder().capacity(totalCapacity).shardCount(shardCount).build());
    }

    private LRUCache<K, V> shardFor(K key) {
        int h = key.hashCode();
        h ^= (h >>> 16); // spread bits so sequential keys don't all land in shard 0
        return shards.get((h & 0x7fffffff) % shardCount);
    }

    @Override
    public void put(K key, V value) {
        shardFor(key).put(key, value);
    }

    @Override
    public void put(K key, V value, long ttlMillis) {
        shardFor(key).put(key, value, ttlMillis);
    }

    @Override
    public V get(K key) {
        return shardFor(key).get(key);
    }

    @Override
    public V remove(K key) {
        return shardFor(key).remove(key);
    }

    @Override
    public boolean containsKey(K key) {
        return shardFor(key).containsKey(key);
    }

    @Override
    public int size() {
        int total = 0;
        for (LRUCache<K, V> shard : shards) {
            total += shard.size();
        }
        return total;
    }

    @Override
    public void clear() {
        for (LRUCache<K, V> shard : shards) {
            shard.clear();
        }
    }

    @Override
    public CacheMetrics metrics() {
        CacheMetrics merged = new CacheMetrics();
        for (LRUCache<K, V> shard : shards) {
            merged.add(shard.metrics());
        }
        return merged;
    }

    public void shutdown() {
        for (LRUCache<K, V> shard : shards) {
            shard.shutdown();
        }
    }
}
