package com.vedank.cache;

import com.vedank.cache.impl.LRUCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LRUCacheTest {

    @Test
    void putAndGetRoundTrips() {
        LRUCache<String, String> cache = new LRUCache<>(3);
        cache.put("a", "1");
        assertEquals("1", cache.get("a"));
    }

    @Test
    void missingKeyReturnsNullAndRecordsMiss() {
        LRUCache<String, String> cache = new LRUCache<>(3);
        assertNull(cache.get("missing"));
        assertEquals(1, cache.metrics().getMisses());
        assertEquals(0, cache.metrics().getHits());
    }

    @Test
    void evictsLeastRecentlyUsedWhenOverCapacity() {
        LRUCache<String, String> cache = new LRUCache<>(3);
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");
        // access A and B so C becomes the LRU entry
        cache.get("A");
        cache.get("B");

        cache.put("D", "4"); // should evict C

        assertNull(cache.get("C"));
        assertEquals("1", cache.get("A"));
        assertEquals("2", cache.get("B"));
        assertEquals("4", cache.get("D"));
        assertEquals(1, cache.metrics().getEvictions());
    }

    @Test
    void updatingExistingKeyRefreshesRecency() {
        LRUCache<String, String> cache = new LRUCache<>(2);
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("A", "1-updated"); // A is now most-recently-used

        cache.put("C", "3"); // should evict B, not A

        assertNull(cache.get("B"));
        assertEquals("1-updated", cache.get("A"));
        assertEquals("3", cache.get("C"));
    }

    @Test
    void removeDeletesEntryAndPolicyState() {
        LRUCache<String, String> cache = new LRUCache<>(3);
        cache.put("A", "1");
        assertEquals("1", cache.remove("A"));
        assertNull(cache.get("A"));
        assertFalse(cache.containsKey("A"));
    }

    @Test
    void clearEmptiesCacheButKeepsMetrics() {
        LRUCache<String, String> cache = new LRUCache<>(3);
        cache.put("A", "1");
        cache.get("A");
        cache.clear();

        assertEquals(0, cache.size());
        assertFalse(cache.containsKey("A"));
        assertEquals(1, cache.metrics().getHits(), "metrics should survive a clear()");
    }

    @Test
    void sizeNeverExceedsCapacity() {
        LRUCache<Integer, Integer> cache = new LRUCache<>(5);
        for (int i = 0; i < 100; i++) {
            cache.put(i, i);
            assertTrue(cache.size() <= 5);
        }
        assertEquals(5, cache.size());
    }
}
