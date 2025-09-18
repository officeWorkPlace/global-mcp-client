package com.deepai.mcpclient.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Cache configuration with proper size limits and TTL to prevent memory leaks
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

    /**
     * Cache manager with Caffeine implementation for high performance caching
     * with size limits and time-based expiration
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Default cache configuration for all caches
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)                    // Max 1000 entries per cache
            .expireAfterWrite(Duration.ofMinutes(15))  // TTL of 15 minutes
            .expireAfterAccess(Duration.ofMinutes(5))  // Expire if not accessed for 5 minutes
            .recordStats());                      // Enable cache statistics

        // Define specific cache names
        cacheManager.setCacheNames(List.of("toolResults", "conversationContexts", "availableTools", "toolsCache"));

        return cacheManager;
    }

    /**
     * Specialized cache configuration for tool results (smaller cache, shorter TTL)
     */
    @Bean("toolResultsCacheManager")
    public CacheManager toolResultsCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("toolResults");

        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(500)                     // Tool results cache - smaller size
            .expireAfterWrite(Duration.ofMinutes(5))   // Shorter TTL for tool results
            .expireAfterAccess(Duration.ofMinutes(2))  // Quick eviction if not accessed
            .recordStats());

        return cacheManager;
    }

    /**
     * Cache configuration for conversation contexts (larger cache, longer TTL)
     */
    @Bean("conversationCacheManager")
    public CacheManager conversationCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("conversationContexts");

        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(2000)                    // Larger cache for conversations
            .expireAfterWrite(Duration.ofHours(1))     // 1 hour TTL for conversations
            .expireAfterAccess(Duration.ofMinutes(30)) // 30 minutes idle eviction
            .recordStats());

        return cacheManager;
    }

    /**
     * Cache configuration for available tools (medium cache, medium TTL)
     */
    @Bean("toolsCacheManager")
    public CacheManager toolsCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("availableTools", "toolsCache");

        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(100)                     // Tools don't change often
            .expireAfterWrite(Duration.ofMinutes(10))  // 10 minutes TTL for tool discovery
            .expireAfterAccess(Duration.ofMinutes(5))  // 5 minutes idle eviction
            .recordStats());

        return cacheManager;
    }
}