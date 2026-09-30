package com.vedank.cache.core;

/**
 * Immutable configuration for a cache instance. Built with {@link Builder}.
 */
public final class CacheConfig {

    private final int capacity;
    private final long defaultTtlMillis;
    private final boolean activeExpiryEnabled;
    private final long activeExpirySweepIntervalMillis;
    private final int shardCount;

    private CacheConfig(Builder builder) {
        this.capacity = builder.capacity;
        this.defaultTtlMillis = builder.defaultTtlMillis;
        this.activeExpiryEnabled = builder.activeExpiryEnabled;
        this.activeExpirySweepIntervalMillis = builder.activeExpirySweepIntervalMillis;
        this.shardCount = builder.shardCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public long getDefaultTtlMillis() {
        return defaultTtlMillis;
    }

    public boolean isActiveExpiryEnabled() {
        return activeExpiryEnabled;
    }

    public long getActiveExpirySweepIntervalMillis() {
        return activeExpirySweepIntervalMillis;
    }

    public int getShardCount() {
        return shardCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int capacity = 1000;
        private long defaultTtlMillis = -1; // no expiry by default
        private boolean activeExpiryEnabled = false;
        private long activeExpirySweepIntervalMillis = 1000;
        private int shardCount = 16;

        public Builder capacity(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be > 0");
            }
            this.capacity = capacity;
            return this;
        }

        public Builder defaultTtlMillis(long defaultTtlMillis) {
            this.defaultTtlMillis = defaultTtlMillis;
            return this;
        }

        public Builder activeExpiryEnabled(boolean activeExpiryEnabled) {
            this.activeExpiryEnabled = activeExpiryEnabled;
            return this;
        }

        public Builder activeExpirySweepIntervalMillis(long millis) {
            this.activeExpirySweepIntervalMillis = millis;
            return this;
        }

        public Builder shardCount(int shardCount) {
            if (shardCount <= 0) {
                throw new IllegalArgumentException("shardCount must be > 0");
            }
            this.shardCount = shardCount;
            return this;
        }

        public CacheConfig build() {
            return new CacheConfig(this);
        }
    }
}
