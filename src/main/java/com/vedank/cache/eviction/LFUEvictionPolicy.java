package com.vedank.cache.eviction;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Least-Frequently-Used eviction using the classic O(1) LFU structure:
 *
 * <pre>
 *   keyFreq:    A -> 3, B -> 1, C -> 2
 *   freqBuckets: 1 -> [B]
 *                2 -> [C]
 *                3 -> [A]
 *   minFreq: 1
 * </pre>
 *
 * Each frequency maps to a {@link LinkedHashSet} of keys at that frequency,
 * which preserves insertion order so that within a frequency tier, ties are
 * broken by "least recently added to this tier" (an LRU tiebreak nested
 * inside LFU). {@code minFreq} is tracked incrementally so {@code evict()}
 * never has to scan every bucket.
 *
 * <p>Not thread-safe; the owning cache must externally synchronize access.
 */
public final class LFUEvictionPolicy<K> implements EvictionPolicy<K> {

    private final Map<K, Integer> keyFreq = new HashMap<>();
    private final Map<Integer, LinkedHashSet<K>> freqBuckets = new HashMap<>();
    private int minFreq = 0;

    @Override
    public void onAccess(K key) {
        bump(key);
    }

    @Override
    public void onInsert(K key) {
        if (keyFreq.containsKey(key)) {
            bump(key);
            return;
        }
        keyFreq.put(key, 1);
        freqBuckets.computeIfAbsent(1, f -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    @Override
    public K evict() {
        LinkedHashSet<K> bucket = freqBuckets.get(minFreq);
        if (bucket == null || bucket.isEmpty()) {
            return null; // empty policy
        }
        K victim = bucket.iterator().next();
        bucket.remove(victim);
        keyFreq.remove(victim);
        if (bucket.isEmpty()) {
            freqBuckets.remove(minFreq);
            // the tier we just drained may not have been the last one left,
            // so find the new lowest remaining frequency
            minFreq = freqBuckets.isEmpty() ? 0 : freqBuckets.keySet().stream().min(Integer::compareTo).orElse(0);
        }
        return victim;
    }

    @Override
    public void onRemove(K key) {
        Integer freq = keyFreq.remove(key);
        if (freq == null) {
            return;
        }
        LinkedHashSet<K> bucket = freqBuckets.get(freq);
        if (bucket != null) {
            bucket.remove(key);
            if (bucket.isEmpty()) {
                freqBuckets.remove(freq);
                if (minFreq == freq) {
                    minFreq = keyFreq.isEmpty() ? 0 : freqBuckets.keySet().stream().min(Integer::compareTo).orElse(0);
                }
            }
        }
    }

    @Override
    public int size() {
        return keyFreq.size();
    }

    @Override
    public void clear() {
        keyFreq.clear();
        freqBuckets.clear();
        minFreq = 0;
    }

    private void bump(K key) {
        Integer freq = keyFreq.get(key);
        if (freq == null) {
            return;
        }
        LinkedHashSet<K> currentBucket = freqBuckets.get(freq);
        if (currentBucket != null) {
            currentBucket.remove(key);
            if (currentBucket.isEmpty()) {
                freqBuckets.remove(freq);
                if (minFreq == freq) {
                    minFreq = freq + 1;
                }
            }
        }
        int newFreq = freq + 1;
        keyFreq.put(key, newFreq);
        freqBuckets.computeIfAbsent(newFreq, f -> new LinkedHashSet<>()).add(key);
    }
}
