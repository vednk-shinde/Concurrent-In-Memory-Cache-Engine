package com.vedank.cache.benchmark;

import com.vedank.cache.core.Cache;
import com.vedank.cache.core.CacheConfig;
import com.vedank.cache.impl.AbstractCache;
import com.vedank.cache.impl.LFUCache;
import com.vedank.cache.impl.LRUCache;
import com.vedank.cache.impl.ShardedLRUCache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone (no JMH, no Spring) concurrency benchmark. Spins up N threads
 * that each hammer a cache with a mixed GET/PUT/REMOVE workload biased
 * towards GET (a realistic cache access pattern), then reports throughput,
 * average latency and hit rate - exactly the numbers you need to justify a
 * "single lock vs sharded lock" design discussion.
 *
 * <p>Run it directly:
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.vedank.cache.benchmark.CacheBenchmark
 * </pre>
 * or from your IDE (it has a plain {@code main}, no test runner needed).
 */
public final class CacheBenchmark {

    private static final int KEY_SPACE = 5_000;
    private static final double GET_RATIO = 0.80;
    private static final double PUT_RATIO = 0.15;
    // remaining 0.05 -> REMOVE

    public static void main(String[] args) throws InterruptedException {
        int[] threadCounts = {1, 8, 32, 100};
        int opsPerThread = 20_000;
        int capacity = 1_000;

        System.out.println("=== Concurrent Cache Engine Benchmark ===");
        System.out.printf("keySpace=%d, capacity=%d, opsPerThread=%d, workload=%.0f%% GET / %.0f%% PUT / %.0f%% REMOVE%n%n",
                KEY_SPACE, capacity, opsPerThread, GET_RATIO * 100, PUT_RATIO * 100, (1 - GET_RATIO - PUT_RATIO) * 100);

        for (int threads : threadCounts) {
            run("LRUCache (single lock)", new LRUCache<>(capacity), threads, opsPerThread);
            run("LFUCache (single lock)", new LFUCache<>(capacity), threads, opsPerThread);
            run("ShardedLRUCache (16 shards)", new ShardedLRUCache<>(capacity, 16), threads, opsPerThread);
            System.out.println();
        }
    }

    private static void run(String label, Cache<Integer, String> cache, int threadCount, int opsPerThread)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicLong totalLatencyNanos = new AtomicLong();
        AtomicLong totalOps = new AtomicLong();

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                ready.countDown();
                await(start);
                long threadLatency = 0;
                for (int i = 0; i < opsPerThread; i++) {
                    int key = random.nextInt(KEY_SPACE);
                    double roll = random.nextDouble();
                    long opStart = System.nanoTime();
                    if (roll < GET_RATIO) {
                        cache.get(key);
                    } else if (roll < GET_RATIO + PUT_RATIO) {
                        cache.put(key, "value-" + key);
                    } else {
                        cache.remove(key);
                    }
                    threadLatency += System.nanoTime() - opStart;
                }
                totalLatencyNanos.addAndGet(threadLatency);
                totalOps.addAndGet(opsPerThread);
                done.countDown();
            });
        }

        await(ready);
        long wallStart = System.nanoTime();
        start.countDown();
        done.await();
        long wallElapsedNanos = System.nanoTime() - wallStart;
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        if (cache instanceof AbstractCache<?, ?> abstractCache) {
            abstractCache.shutdown();
        } else if (cache instanceof ShardedLRUCache<?, ?> sharded) {
            sharded.shutdown();
        }

        double wallSeconds = wallElapsedNanos / 1_000_000_000.0;
        double throughput = totalOps.get() / wallSeconds;
        double avgLatencyMicros = (totalLatencyNanos.get() / (double) totalOps.get()) / 1_000.0;

        System.out.printf(
                "%-30s | threads=%-4d | throughput=%,10.0f ops/sec | avgLatency=%6.2f us | hitRate=%5.1f%% | evictions=%d%n",
                label, threadCount, throughput, avgLatencyMicros,
                cache.metrics().getHitRate() * 100, cache.metrics().getEvictions());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
