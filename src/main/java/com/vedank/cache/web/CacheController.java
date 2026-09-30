package com.vedank.cache.web;

import com.vedank.cache.core.Cache;
import com.vedank.cache.metrics.CacheMetrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Thin REST wrapper around the {@link Cache} engine:
 *
 * <pre>
 * PUT    /api/cache/{key}          body: {"value": ..., "ttlMillis": 60000}
 * GET    /api/cache/{key}
 * DELETE /api/cache/{key}
 * GET    /api/cache/{key}/exists
 * GET    /api/cache/size
 * POST   /api/cache/clear
 * GET    /api/cache/metrics
 * </pre>
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final Cache<String, Object> cache;

    public CacheController(Cache<String, Object> cache) {
        this.cache = cache;
    }

    @PutMapping("/{key}")
    public ResponseEntity<Void> put(@PathVariable String key, @RequestBody PutRequest request) {
        if (request.getTtlMillis() != null) {
            cache.put(key, request.getValue(), request.getTtlMillis());
        } else {
            cache.put(key, request.getValue());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String key) {
        Object value = cache.get(key);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> remove(@PathVariable String key) {
        cache.remove(key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{key}/exists")
    public ResponseEntity<Map<String, Boolean>> exists(@PathVariable String key) {
        return ResponseEntity.ok(Map.of("exists", cache.containsKey(key)));
    }

    @GetMapping("/size")
    public ResponseEntity<Map<String, Integer>> size() {
        return ResponseEntity.ok(Map.of("size", cache.size()));
    }

    @PostMapping("/clear")
    public ResponseEntity<Void> clear() {
        cache.clear();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics")
    public ResponseEntity<CacheMetrics> metrics() {
        return ResponseEntity.ok(cache.metrics());
    }
}
