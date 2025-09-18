package com.deepai.mcpclient.health;

import com.deepai.mcpclient.service.ResilienceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive system health indicator
 * Provides overall system status including memory, CPU, caches, and resilience
 */
@Component("system")
public class SystemHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(SystemHealthIndicator.class);

    private final ResilienceService resilienceService;
    private final CacheManager cacheManager;

    @Autowired
    public SystemHealthIndicator(ResilienceService resilienceService, CacheManager cacheManager) {
        this.resilienceService = resilienceService;
        this.cacheManager = cacheManager;
    }

    @Override
    public Health health() {
        try {
            Health.Builder healthBuilder = Health.up();
            boolean isHealthy = true;

            // Check system resources
            Map<String, Object> systemMetrics = getSystemMetrics();
            healthBuilder.withDetail("system", systemMetrics);

            // Check memory usage
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            if (memoryUsagePercent > 90) {
                healthBuilder = Health.down();
                isHealthy = false;
            } else if (memoryUsagePercent > 80) {
                healthBuilder.withDetail("memory_warning", "High memory usage: " + String.format("%.1f%%", memoryUsagePercent));
            }

            healthBuilder.withDetail("memory_usage_percent", String.format("%.1f%%", memoryUsagePercent));
            healthBuilder.withDetail("memory_used_mb", usedMemory / 1024 / 1024);
            healthBuilder.withDetail("memory_max_mb", maxMemory / 1024 / 1024);

            // Check resilience status
            ResilienceService.ResilienceStatus resilienceStatus = resilienceService.getResilienceStatus();
            healthBuilder.withDetail("resilience", Map.of(
                "healthy", resilienceStatus.isHealthy(),
                "gemini_permits", resilienceStatus.getGeminiApiPermits(),
                "user_permits", resilienceStatus.getUserRequestPermits(),
                "tool_permits", resilienceStatus.getToolExecutionPermits(),
                "gemini_circuit", resilienceStatus.getGeminiApiState().toString(),
                "mcp_circuit", resilienceStatus.getMcpServiceState().toString()
            ));

            if (!resilienceStatus.isHealthy()) {
                healthBuilder.withDetail("resilience_warning", "Some resilience components are degraded");
            }

            // Check cache status
            Map<String, Object> cacheStatus = getCacheStatus();
            healthBuilder.withDetail("caches", cacheStatus);

            // Overall health determination
            healthBuilder.withDetail("overall_healthy", isHealthy);
            healthBuilder.withDetail("timestamp", System.currentTimeMillis());

            return healthBuilder.build();

        } catch (Exception e) {
            logger.error("❌ System health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Health check failed")
                .build();
        }
    }

    private Map<String, Object> getSystemMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Runtime runtime = Runtime.getRuntime();

            metrics.put("available_processors", osBean.getAvailableProcessors());
            metrics.put("system_load_average", osBean.getSystemLoadAverage());
            metrics.put("total_memory_mb", runtime.totalMemory() / 1024 / 1024);
            metrics.put("free_memory_mb", runtime.freeMemory() / 1024 / 1024);
            metrics.put("max_memory_mb", runtime.maxMemory() / 1024 / 1024);

            // JVM uptime
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            metrics.put("uptime_minutes", uptime / 60000);

        } catch (Exception e) {
            logger.warn("Could not gather system metrics: {}", e.getMessage());
            metrics.put("error", "Could not gather system metrics");
        }

        return metrics;
    }

    private Map<String, Object> getCacheStatus() {
        Map<String, Object> cacheStatus = new HashMap<>();

        try {
            if (cacheManager != null) {
                cacheManager.getCacheNames().forEach(cacheName -> {
                    try {
                        var cache = cacheManager.getCache(cacheName);
                        if (cache != null) {
                            cacheStatus.put(cacheName, "AVAILABLE");
                        } else {
                            cacheStatus.put(cacheName, "NOT_AVAILABLE");
                        }
                    } catch (Exception e) {
                        cacheStatus.put(cacheName, "ERROR: " + e.getMessage());
                    }
                });
            } else {
                cacheStatus.put("status", "Cache manager not available");
            }
        } catch (Exception e) {
            logger.warn("Could not check cache status: {}", e.getMessage());
            cacheStatus.put("error", "Could not check cache status");
        }

        return cacheStatus;
    }
}