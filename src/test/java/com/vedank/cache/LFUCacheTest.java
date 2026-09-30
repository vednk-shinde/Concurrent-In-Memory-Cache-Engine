package com.vedank.cache;

import com.vedank.cache.impl.LFUCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LFUCacheTest {

    @Test
    void putAndGetRoundTrips() {
        LFUCache<String, String> cache = new LFUCache<>(3);
        cache.put("a", "1");
        assertEquals("1", cache.get("a"));
    }

    @Test
    void evictsLeastFrequentlyUsedWhenOverCapacity() {
        LFUCache<String, String> cache = new LFUCache<>(3);
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");

        // A accessed 3x, B accessed 2x, C accessed 0x (besides the initial put)
        cache.get("A");
        cache.get("A");
        cache.get("A");
        cache.get("B");
        cache.get("B");

        cache.put("D", "4"); // C has the lowest frequency -> evicted

        assertNull(cache.get("C"));
        assertNotNull(cache.get("A"));
        assertNotNull(cache.get("B"));
        assertNotNull(cache.get("D"));
        assertEquals(1, cache.metrics().getEvictions());
    }

    @Test
    void tiesAtSameFrequencyEvictLeastRecentlyAddedToThatTier() {
        LFUCache<String, String> cache = new LFUCache<>(2);
        cache.put("A", "1"); // freq 1
        cache.put("B", "2"); // freq 1, tied with A but added after it

        cache.put("C", "3"); // both A and B are at freq 1; A was added to that tier first -> A evicted

        assertNull(cache.get("A"));
        assertNotNull(cache.get("B"));
        assertNotNull(cache.get("C"));
    }

    @Test
    void frequencyIncreasesOnEveryAccessAndProtectsHotKeys() {
        LFUCache<String, String> cache = new LFUCache<>(2);
        cache.put("HOT", "h");
        cache.put("COLD", "c");

        for (int i = 0; i < 10; i++) {
            cache.get("HOT");
        }

        cache.put("NEW", "n"); // COLD (freq 1) must be evicted, not HOT (freq 11)

        assertNull(cache.get("COLD"));
        assertNotNull(cache.get("HOT"));
        assertNotNull(cache.get("NEW"));
    }

    @Test
    void removeClearsFrequencyBookkeeping() {
        LFUCache<String, String> cache = new LFUCache<>(3);
        cache.put("A", "1");
        cache.get("A");
        cache.remove("A");

        cache.put("A", "2"); // re-inserted key must start fresh at frequency 1
        assertEquals("2", cache.get("A"));
    }
}
