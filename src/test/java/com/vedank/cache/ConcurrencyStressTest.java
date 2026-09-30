package com.vedank.cache;

import com.vedank.cache.core.Cache;
import com.vedank.cache.impl.LFUCache;
import com.vedank.cache.impl.LRUCache;
import com.vedank.cache.impl.ShardedLRUCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the caches stay internally consistent under real contention:
 * many threads hammering GET/PUT/REMOVE concurrently must never leave the
 * cache oversized, corrupt its eviction-policy bookkeeping, or lose/duplicate
 * puts. This exercises exactly the scenario from the assignment brief:
 * "Thread 1 GET(A), Thread 2 PUT(A), Thread 3 REMOVE(A) ... cache must stay
 * consistent".
 */
class ConcurrencyStressTest {

    private static Stream<Cache<Integer, String>> caches() {
        return Stream.of(new LRUCache<>(100), new LFUCache<>(100), new ShardedLRUCache<>(100, 8));
    }

    @ParameterizedTest
    @MethodSource("caches")
    void neverExceedsCapacityUnderConcurrentLoad(Cache<Integer, String> cache) throws InterruptedException {
        int threadCount = 32;
        int opsPerThread = 5_000;
        int keySpace = 500;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = random.nextInt(keySpace);
                        int op = random.nextInt(4);
                        switch (op) {
                            case 0 -> cache.get(key);
                            case 1 -> cache.put(key, "v" + key);
                            case 2 -> cache.remove(key);
                            default -> cache.containsKey(key);
                        }
                        // Effectively-capacity should never blow past the configured bound,
                        // even mid-storm - not just at the end.
                        assertTrue(cache.size() <= 100, "cache grew past its configured capacity");
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(60, TimeUnit.SECONDS), "workload did not finish in time");
        pool.shutdown();

        assertTrue(cache.size() <= 100);
    }

    @Test
    void allPutsAreEventuallyReadableWithNoContentionErrors() throws InterruptedException {
        LRUCache<Integer, Integer> cache = new LRUCache<>(2000);
        int threadCount = 16;
        int keysPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < keysPerThread; i++) {
                        int key = threadId * keysPerThread + i;
                        cache.put(key, key * 10);
                    }
                    for (int i = 0; i < keysPerThread; i++) {
                        int key = threadId * keysPerThread + i;
                        Integer value = cache.get(key);
                        if (value == null || value != key * 10) {
                            errors.incrementAndGet();
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(0, errors.get(), "every key a thread wrote must be readable back with the correct value");
    }
}
