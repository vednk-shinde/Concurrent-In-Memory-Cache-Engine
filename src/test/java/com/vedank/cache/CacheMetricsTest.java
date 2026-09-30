package com.vedank.cache;

import com.vedank.cache.metrics.CacheMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheMetricsTest {

    @Test
    void hitRateIsZeroWithNoRequests() {
        CacheMetrics metrics = new CacheMetrics();
        assertEquals(0.0, metrics.getHitRate());
    }

    @Test
    void hitRateReflectsHitsOverTotalRequests() {
        CacheMetrics metrics = new CacheMetrics();
        metrics.recordHit();
        metrics.recordHit();
        metrics.recordHit();
        metrics.recordMiss();

        assertEquals(0.75, metrics.getHitRate());
        assertEquals(0.25, metrics.getMissRate());
        assertEquals(4, metrics.getTotalRequests());
    }

    @Test
    void addMergesTwoSnapshots() {
        CacheMetrics a = new CacheMetrics();
        a.recordHit();
        a.recordEviction();

        CacheMetrics b = new CacheMetrics();
        b.recordMiss();
        b.recordEviction();

        CacheMetrics merged = new CacheMetrics();
        merged.add(a);
        merged.add(b);

        assertEquals(1, merged.getHits());
        assertEquals(1, merged.getMisses());
        assertEquals(2, merged.getEvictions());
    }
}
