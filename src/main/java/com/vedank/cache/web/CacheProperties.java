package com.vedank.cache.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code cache.*} keys from application.yml, e.g.:
 * <pre>
 * cache:
 *   strategy: LRU        # or LFU
 *   capacity: 1000
 *   shard-count: 16       # only used when strategy is SHARDED_LRU
 *   default-ttl-millis: -1
 *   active-expiry-enabled: true
 *   active-expiry-sweep-interval-millis: 1000
 * </pre>
 */
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    public enum Strategy { LRU, LFU, SHARDED_LRU }

    private Strategy strategy = Strategy.LRU;
    private int capacity = 1000;
    private int shardCount = 16;
    private long defaultTtlMillis = -1;
    private boolean activeExpiryEnabled = true;
    private long activeExpirySweepIntervalMillis = 1000;

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getShardCount() {
        return shardCount;
    }

    public void setShardCount(int shardCount) {
        this.shardCount = shardCount;
    }

    public long getDefaultTtlMillis() {
        return defaultTtlMillis;
    }

    public void setDefaultTtlMillis(long defaultTtlMillis) {
        this.defaultTtlMillis = defaultTtlMillis;
    }

    public boolean isActiveExpiryEnabled() {
        return activeExpiryEnabled;
    }

    public void setActiveExpiryEnabled(boolean activeExpiryEnabled) {
        this.activeExpiryEnabled = activeExpiryEnabled;
    }

    public long getActiveExpirySweepIntervalMillis() {
        return activeExpirySweepIntervalMillis;
    }

    public void setActiveExpirySweepIntervalMillis(long activeExpirySweepIntervalMillis) {
        this.activeExpirySweepIntervalMillis = activeExpirySweepIntervalMillis;
    }
}
