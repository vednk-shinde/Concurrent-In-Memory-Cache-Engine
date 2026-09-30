package com.vedank.cache;

import com.vedank.cache.impl.ShardedLRUCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShardedLRUCacheTest {

    @Test
    void putAndGetRoundTripAcrossShards() {
        ShardedLRUCache<Integer, String> cache = new ShardedLRUCache<>(100, 8);
        for (int i = 0; i < 50; i++) {
            cache.put(i, "v" + i);
        }
        for (int i = 0; i < 50; i++) {
            assertEquals("v" + i, cache.get(i));
        }
    }

    @Test
    void sizeAggregatesAcrossAllShards() {
        ShardedLRUCache<Integer, String> cache = new ShardedLRUCache<>(160, 8);
        for (int i = 0; i < 40; i++) {
            cache.put(i, "v" + i);
        }
        assertEquals(40, cache.size());
    }

    @Test
    void clearEmptiesEveryShard() {
        ShardedLRUCache<Integer, String> cache = new ShardedLRUCache<>(100, 4);
        for (int i = 0; i < 20; i++) {
            cache.put(i, "v" + i);
        }
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void metricsAreAggregatedFromAllShards() {
        ShardedLRUCache<Integer, String> cache = new ShardedLRUCache<>(100, 4);
        cache.put(1, "a");
        cache.get(1);      // hit on whatever shard key 1 lands in
        cache.get(999);    // miss

        assertEquals(1, cache.metrics().getHits());
        assertEquals(1, cache.metrics().getMisses());
    }

    @Test
    void perShardCapacityBoundsTotalSize() {
        // 8 shards, capacity 80 -> 10 per shard -> total effective capacity is <= 80
        ShardedLRUCache<Integer, String> cache = new ShardedLRUCache<>(80, 8);
        for (int i = 0; i < 1000; i++) {
            cache.put(i, "v" + i);
        }
        assertTrue(cache.size() <= 80);
    }
}
