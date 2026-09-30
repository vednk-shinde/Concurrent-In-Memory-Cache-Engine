package com.vedank.cache.web;

import com.vedank.cache.core.Cache;
import com.vedank.cache.core.CacheConfig;
import com.vedank.cache.impl.LFUCache;
import com.vedank.cache.impl.LRUCache;
import com.vedank.cache.impl.ShardedLRUCache;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires a single {@code Cache<String, Object>} bean, chosen and sized from
 * {@code application.yml} (see {@link CacheProperties}), for
 * {@link CacheController} to serve over REST.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheWebConfig {

    @Bean
    public Cache<String, Object> cache(CacheProperties props) {
        CacheConfig config = CacheConfig.builder()
                .capacity(props.getCapacity())
                .shardCount(props.getShardCount())
                .defaultTtlMillis(props.getDefaultTtlMillis())
                .activeExpiryEnabled(props.isActiveExpiryEnabled())
                .activeExpirySweepIntervalMillis(props.getActiveExpirySweepIntervalMillis())
                .build();

        return switch (props.getStrategy()) {
            case LRU -> new LRUCache<>(config);
            case LFU -> new LFUCache<>(config);
            case SHARDED_LRU -> new ShardedLRUCache<>(config);
        };
    }
}
