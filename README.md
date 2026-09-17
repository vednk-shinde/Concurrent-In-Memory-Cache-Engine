# Concurrent In-Memory Cache Engine

A cache engine built **from scratch** in Java - no Redis, no Caffeine, no
`Map.of()` shortcuts for the hard parts. It implements O(1) LRU and LFU
eviction, TTL-based expiry, thread-safe concurrent access, runtime metrics,
and an optional Spring Boot REST API on top.

```
Application
     |
     v
+---------------------------+
|      Concurrent Cache     |
|                           |
|  HashMap                  |
|  + Doubly Linked List     |
|  + TTL                    |
|  + Eviction (LRU / LFU)   |
|  + Thread Safety          |
|  + Metrics                |
+---------------------------+
     |
     v
  Cache Result
```

## Why build this instead of using Redis/Caffeine?

Because the point isn't the cache, it's what building one from primitives
proves: real, working knowledge of hash maps, linked lists, eviction
algorithms, concurrency primitives, and the trade-offs between them, not just
"I know what a HashMap is."

## Features

| Operation      | Semantics                                        | Complexity |
|----------------|---------------------------------------------------|------------|
| `put(k, v)`    | insert/update, no expiry                          | O(1)       |
| `put(k, v, ttl)`| insert/update with a millisecond TTL             | O(1)       |
| `get(k)`       | read, `null` on miss or expiry                    | O(1)       |
| `remove(k)`    | delete, returns old value                         | O(1)       |
| `containsKey(k)`| presence check, expiry-aware                     | O(1)       |
| `size()`       | live entry count                                  | O(1)       |
| `clear()`      | drop everything                                   | O(1)*      |

\* amortized; internally clears a HashMap.

## The data structures

### 1. HashMap - O(1) key lookup

`AbstractCache` stores `key -> CacheEntry<K, V>` in a plain `HashMap`. This is
the raw data; it knows nothing about ordering or frequency.

### 2. Doubly linked list - O(1) LRU ordering

[`LRUEvictionPolicy`](src/main/java/com/vedank/cache/eviction/LRUEvictionPolicy.java)
keeps a second, independent `HashMap<K, Node>` pointing into a doubly linked
list ordered from most- to least-recently-used:

```
MRU                         LRU
 v                           v
[A] <-> [C] <-> [B] <-> [D]
```

Accessing `C` unlinks it and re-links it at the head - four pointer writes,
O(1), regardless of cache size. Eviction just unlinks the tail.

### 3. Frequency buckets - O(1) LFU ordering

[`LFUEvictionPolicy`](src/main/java/com/vedank/cache/eviction/LFUEvictionPolicy.java)
tracks `key -> frequency` and `frequency -> LinkedHashSet<key>`, plus a
`minFreq` counter maintained incrementally so eviction never scans:

```
keyFreq:      A -> 3, B -> 1, C -> 2
freqBuckets:  1 -> [B]
              2 -> [C]
              3 -> [A]
minFreq: 1
```

Ties within a frequency tier are broken by insertion order into that tier
(an LRU tiebreak nested inside LFU) via `LinkedHashSet`.

### LRU vs LFU - the actual trade-off

| | LRU | LFU |
|---|---|---|
| Evicts | oldest-unused key | rarest-used key |
| Good for | recency-biased workloads (session data, "recently viewed") | stable hot/cold key distributions (popular product pages) |
| Bad for | one-off scans that flush out genuinely hot keys | new keys that are legitimately hot but haven't built up frequency yet ("cache pollution" from a burst) |
| Cost per access | O(1), 1 map + list | O(1), 2 maps + bucket maintenance |

## TTL and expiry

Every entry carries `createdAt` / `expiresAt`
([`CacheEntry`](src/main/java/com/vedank/cache/core/CacheEntry.java)).
Expiry is handled two ways, both configurable via `CacheConfig`:

- **Lazy**: `get()` / `containsKey()` check `isExpired()` and evict on read.
- **Active**: an optional daemon `ScheduledExecutorService` sweeps the whole
  map on an interval, so memory isn't held hostage by keys nobody reads again.

```java
cache.put("user:101", user, 60_000); // TTL = 60s
...
cache.get("user:101"); // after 60s: expired -> removed -> null (cache miss)
```

## Object-oriented design

```
Cache<K,V>                    EvictionPolicy<K>
   ^                              ^
   |                              |
AbstractCache<K,V>       LRUEvictionPolicy<K>
   ^                     LFUEvictionPolicy<K>
   |
LRUCache<K,V>  LFUCache<K,V>

ShardedLRUCache<K,V> implements Cache<K,V> directly
  (composes N independent LRUCache shards)

CacheEntry<K,V>   CacheConfig   CacheMetrics
```

`EvictionPolicy<K>` is the interface that makes this composable:

```java
public interface EvictionPolicy<K> {
    void onAccess(K key);
    void onInsert(K key);
    K evict();
    void onRemove(K key);
}
```

`AbstractCache` owns the `HashMap<K, CacheEntry<K,V>>`, TTL handling, metrics
and locking; it delegates *only* the "which key goes next" decision to
whichever `EvictionPolicy` it's constructed with. `LRUCache` and `LFUCache`
are ~10-line classes that just plug in a policy - neither duplicates any
map/lock/TTL logic.

## Concurrency: two versions, benchmarked against each other

**Version 1 - single lock** ([`AbstractCache`](src/main/java/com/vedank/cache/impl/AbstractCache.java)):
one `ReentrantLock` guards the `HashMap` and the `EvictionPolicy` together for
every `GET` / `PUT` / `REMOVE` / `CLEAR`. Simple to reason about, always
correct, but every operation - even two unrelated `GET`s - serializes.

```
GET ----+
PUT ----+---> one ReentrantLock ---> HashMap + EvictionPolicy
REMOVE -+
CLEAR --+
```

**Version 2 - lock striping** ([`ShardedLRUCache`](src/main/java/com/vedank/cache/impl/ShardedLRUCache.java)):
the keyspace is partitioned across N independent `LRUCache` shards, each with
its own lock and its own slice of capacity:

```
GET(A) ---\
PUT(B) ----+--> hash(key) % N --> shard[i] (own lock, own LRU list)
GET(C) ---/
```

Two threads touching different shards never block each other. The cost:
eviction is only "least-recently-used *within its shard*", not globally, so
LRU ordering is slightly weaker in exchange for much better throughput under
contention. This mirrors why `ConcurrentHashMap` itself moved from a single
lock to segment/bin-level locking internally.

## Benchmarking

[`CacheBenchmark`](src/main/java/com/vedank/cache/benchmark/CacheBenchmark.java)
is a small, dependency-free harness (no JMH needed) that runs an 80% GET /
15% PUT / 5% REMOVE workload at 1, 8, 32 and 100 concurrent threads against
`LRUCache`, `LFUCache` and `ShardedLRUCache`, and reports:

- throughput (ops/sec)
- average latency (microseconds)
- cache hit rate
- eviction count

Run it with:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.vedank.cache.benchmark.CacheBenchmark
```

Sample shape of the output:

```
LRUCache (single lock)         | threads=100  | throughput=   812,345 ops/sec | avgLatency=  0.12 us | hitRate= 79.8% | evictions=18234
ShardedLRUCache (16 shards)    | threads=100  | throughput= 2,145,982 ops/sec | avgLatency=  0.05 us | hitRate= 78.1% | evictions=18401
```

(Exact numbers depend on your machine - that's the point: run it and use your
own numbers as the "here's a real trade-off I measured" talking point.)

## REST API (optional Spring Boot layer)

The core engine (`com.vedank.cache.core` / `.eviction` / `.impl`) has **zero**
Spring dependency. [`com.vedank.cache.web`](src/main/java/com/vedank/cache/web)
is a thin, swappable HTTP wrapper around it.

```
PUT    /api/cache/{key}          body: {"value": ..., "ttlMillis": 60000}
GET    /api/cache/{key}
DELETE /api/cache/{key}
GET    /api/cache/{key}/exists
GET    /api/cache/size
POST   /api/cache/clear
GET    /api/cache/metrics
```

Configure the backing strategy in [`application.yml`](src/main/resources/application.yml):

```yaml
cache:
  strategy: LRU          # LRU | LFU | SHARDED_LRU
  capacity: 1000
  shard-count: 16
  default-ttl-millis: -1
  active-expiry-enabled: true
  active-expiry-sweep-interval-millis: 1000
```

Run it:

```bash
mvn spring-boot:run
```

```bash
curl -X PUT localhost:8080/api/cache/greeting -H "Content-Type: application/json" -d "{\"value\":\"hello\",\"ttlMillis\":60000}"
curl localhost:8080/api/cache/greeting
curl localhost:8080/api/cache/metrics
```

## Docker

```bash
docker build -t concurrent-cache-engine .
docker run -p 8080:8080 concurrent-cache-engine
```

## Project layout

```
src/main/java/com/vedank/cache/
  core/        Cache<K,V>, CacheEntry, CacheConfig
  eviction/    EvictionPolicy, LRUEvictionPolicy, LFUEvictionPolicy
  impl/        AbstractCache, LRUCache, LFUCache, ShardedLRUCache
  metrics/     CacheMetrics
  web/         Spring Boot REST wrapper (optional)
  benchmark/   Standalone concurrency benchmark (no JMH/Spring needed)
src/test/java/com/vedank/cache/
  EvictionPolicyTest, LRUCacheTest, LFUCacheTest, TTLExpiryTest,
  ConcurrencyStressTest, ShardedLRUCacheTest, CacheMetricsTest,
  web/CacheControllerTest
```

## Building and testing

```bash
mvn test      # run the JUnit 5 suite (unit + concurrency stress + MockMvc REST tests)
mvn package   # build the executable Spring Boot jar
```

## Possible next steps

- A write-through/write-back layer backed by a real datastore
- A `ReadWriteLock`-based variant of `AbstractCache` for read-heavy workloads,
  benchmarked against the single-`ReentrantLock` version
- Persistence/snapshotting for restart recovery
- Pluggable serialization for the REST layer's `Object` values
