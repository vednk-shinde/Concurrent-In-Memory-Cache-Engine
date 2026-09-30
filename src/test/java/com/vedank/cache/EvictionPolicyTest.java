package com.vedank.cache;

import com.vedank.cache.eviction.EvictionPolicy;
import com.vedank.cache.eviction.LFUEvictionPolicy;
import com.vedank.cache.eviction.LRUEvictionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the eviction policies in isolation, independent of any Cache/HashMap
 * wiring - pure ordering/frequency logic.
 */
class EvictionPolicyTest {

    @Test
    void lruEvictsInInsertionOrderWhenNeverAccessed() {
        EvictionPolicy<String> policy = new LRUEvictionPolicy<>();
        policy.onInsert("A");
        policy.onInsert("B");
        policy.onInsert("C");

        assertEquals("A", policy.evict());
        assertEquals("B", policy.evict());
        assertEquals("C", policy.evict());
        assertNull(policy.evict());
    }

    @Test
    void lruOnAccessMovesKeyToMostRecentlyUsed() {
        EvictionPolicy<String> policy = new LRUEvictionPolicy<>();
        policy.onInsert("A");
        policy.onInsert("B");
        policy.onInsert("C");

        policy.onAccess("A"); // A: MRU now, eviction order becomes B, C, A

        assertEquals("B", policy.evict());
        assertEquals("C", policy.evict());
        assertEquals("A", policy.evict());
    }

    @Test
    void lruOnRemoveDropsKeyFromBothMapAndList() {
        EvictionPolicy<String> policy = new LRUEvictionPolicy<>();
        policy.onInsert("A");
        policy.onInsert("B");
        policy.onRemove("A");

        assertEquals(1, policy.size());
        assertEquals("B", policy.evict());
    }

    @Test
    void lfuEvictsLowestFrequencyFirst() {
        EvictionPolicy<String> policy = new LFUEvictionPolicy<>();
        policy.onInsert("A");
        policy.onInsert("B");
        policy.onInsert("C");

        policy.onAccess("A");
        policy.onAccess("A");
        policy.onAccess("B");
        // frequencies: A=3, B=2, C=1

        assertEquals("C", policy.evict());
        assertEquals("B", policy.evict());
        assertEquals("A", policy.evict());
    }

    @Test
    void lfuTiesBreakByInsertionOrderWithinTier() {
        EvictionPolicy<String> policy = new LFUEvictionPolicy<>();
        policy.onInsert("A"); // freq 1
        policy.onInsert("B"); // freq 1

        assertEquals("A", policy.evict()); // both at freq 1, A was added to that tier first
        assertEquals("B", policy.evict());
    }

    @Test
    void lfuSizeAndClearWork() {
        EvictionPolicy<String> policy = new LFUEvictionPolicy<>();
        policy.onInsert("A");
        policy.onInsert("B");
        assertEquals(2, policy.size());

        policy.clear();
        assertEquals(0, policy.size());
        assertNull(policy.evict());
    }
}
