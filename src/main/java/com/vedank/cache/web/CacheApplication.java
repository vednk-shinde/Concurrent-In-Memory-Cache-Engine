package com.vedank.cache.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Optional Spring Boot entry point that exposes the cache engine over REST.
 * The core engine (com.vedank.cache.core / eviction / impl) has zero
 * dependency on Spring - this module is purely a thin HTTP wrapper.
 */
@SpringBootApplication
public class CacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(CacheApplication.class, args);
    }
}
