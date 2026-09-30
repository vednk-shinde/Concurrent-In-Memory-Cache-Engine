package com.vedank.cache.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free runtime counters for a cache instance. Every counter is an
 * {@link AtomicLong} so metrics can be updated from any thread without
 * contending with the cache's own data lock.
 */
public final class CacheMetrics {

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    private final AtomicLong removals = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong expirations = new AtomicLong();

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordPut() {
        puts.incrementAndGet();
    }

    public void recordRemoval() {
        removals.incrementAndGet();
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public void recordExpiration() {
        expirations.incrementAndGet();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public long getPuts() {
        return puts.get();
    }

    public long getRemovals() {
        return removals.get();
    }

    public long getEvictions() {
        return evictions.get();
    }

    public long getExpirations() {
        return expirations.get();
    }

    /** Adds another snapshot's counters into this one. Used to aggregate metrics across shards. */
    public void add(CacheMetrics other) {
        hits.addAndGet(other.getHits());
        misses.addAndGet(other.getMisses());
        puts.addAndGet(other.getPuts());
        removals.addAndGet(other.getRemovals());
        evictions.addAndGet(other.getEvictions());
        expirations.addAndGet(other.getExpirations());
    }

    public long getTotalRequests() {
        return hits.get() + misses.get();
    }

    public double getHitRate() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }

    public double getMissRate() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) misses.get() / total;
    }

    @Override
    public String toString() {
        return String.format(
                "CacheMetrics{hits=%d, misses=%d, hitRate=%.2f%%, puts=%d, removals=%d, evictions=%d, expirations=%d}",
                getHits(), getMisses(), getHitRate() * 100, getPuts(), getRemovals(), getEvictions(), getExpirations());
    }
}
