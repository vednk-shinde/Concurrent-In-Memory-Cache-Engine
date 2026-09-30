package com.vedank.cache;

import com.vedank.cache.core.CacheConfig;
import com.vedank.cache.impl.LRUCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TTLExpiryTest {

    @Test
    void entryWithoutTtlNeverExpires() throws InterruptedException {
        LRUCache<String, String> cache = new LRUCache<>(3);
        cache.put("A", "1"); // no TTL
        Thread.sleep(50);
        assertEquals("1", cache.get("A"));
    }

    @Test
    void entryExpiresLazilyOnGetAfterTtlElapses() throws InterruptedException {
        LRUCache<String, String> cache = new LRUCache<>(3);
        cache.put("A", "1", 50); // 50ms TTL

        assertEquals("1", cache.get("A")); // still fresh
        Thread.sleep(120);

        assertNull(cache.get("A"), "expired entry must be a cache miss");
        assertEquals(1, cache.metrics().getExpirations());
        assertEquals(0, cache.size(), "expired entry must be evicted from the store on access");
    }

    @Test
    void containsKeyAlsoHonorsExpiry() throws InterruptedException {
        LRUCache<String, String> cache = new LRUCache<>(3);
        cache.put("A", "1", 50);
        assertTrue(cache.containsKey("A"));
        Thread.sleep(120);
        assertFalse(cache.containsKey("A"));
    }

    @Test
    void activeExpirySweepsWithoutWaitingForAGet() throws InterruptedException {
        CacheConfig config = CacheConfig.builder()
                .capacity(10)
                .activeExpiryEnabled(true)
                .activeExpirySweepIntervalMillis(30)
                .build();
        LRUCache<String, String> cache = new LRUCache<>(config);
        try {
            cache.put("A", "1", 50);
            Thread.sleep(250); // several sweep cycles

            assertEquals(0, cache.size(), "background sweeper should have removed the expired entry");
            assertTrue(cache.metrics().getExpirations() >= 1);
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void defaultTtlFromConfigAppliesWhenNoneSpecifiedPerPut() throws InterruptedException {
        CacheConfig config = CacheConfig.builder().capacity(10).defaultTtlMillis(50).build();
        LRUCache<String, String> cache = new LRUCache<>(config);

        cache.put("A", "1"); // uses the 50ms default
        assertEquals("1", cache.get("A"));
        Thread.sleep(120);
        assertNull(cache.get("A"));
    }
}
