package com.deepai.mcpclient.monitoring;

import com.deepai.mcpclient.health.AiServiceHealthIndicator;
import com.deepai.mcpclient.health.ConnectionPoolHealthIndicator;
import com.deepai.mcpclient.health.McpServersHealthIndicator;
import com.deepai.mcpclient.health.SystemHealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Proactive health monitoring service
 * Continuously monitors system health and alerts on issues
 */
@Service
public class HealthMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(HealthMonitoringService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final McpServersHealthIndicator mcpServersHealth;
    private final AiServiceHealthIndicator aiServiceHealth;
    private final ConnectionPoolHealthIndicator connectionPoolHealth;
    private final SystemHealthIndicator systemHealth;

    private final AtomicLong healthCheckCount = new AtomicLong(0);
    private final Map<String, String> lastKnownStatus = new HashMap<>();

    @Autowired
    public HealthMonitoringService(McpServersHealthIndicator mcpServersHealth,
                                 AiServiceHealthIndicator aiServiceHealth,
                                 ConnectionPoolHealthIndicator connectionPoolHealth,
                                 SystemHealthIndicator systemHealth) {
        this.mcpServersHealth = mcpServersHealth;
        this.aiServiceHealth = aiServiceHealth;
        this.connectionPoolHealth = connectionPoolHealth;
        this.systemHealth = systemHealth;

        logger.info("🏥 Health monitoring service initialized");
    }

    /**
     * Comprehensive health check - runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void performComprehensiveHealthCheck() {
        long checkNumber = healthCheckCount.incrementAndGet();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        logger.debug("🔍 Starting comprehensive health check #{} at {}", checkNumber, timestamp);

        try {
            // Check all health indicators
            Health mcpHealth = mcpServersHealth.health();
            Health aiHealth = aiServiceHealth.health();
            Health poolHealth = connectionPoolHealth.health();
            Health sysHealth = systemHealth.health();

            // Log status changes
            checkAndLogStatusChange("MCP_SERVERS", mcpHealth.getStatus());
            checkAndLogStatusChange("AI_SERVICE", aiHealth.getStatus());
            checkAndLogStatusChange("CONNECTION_POOLS", poolHealth.getStatus());
            checkAndLogStatusChange("SYSTEM", sysHealth.getStatus());

            // Log summary
            boolean allHealthy = isAllHealthy(mcpHealth, aiHealth, poolHealth, sysHealth);

            if (allHealthy) {
                logger.info("✅ Health check #{} PASSED - All systems healthy", checkNumber);
            } else {
                logger.warn("⚠️ Health check #{} DEGRADED - Some systems unhealthy", checkNumber);
                logDetailedHealthStatus(mcpHealth, aiHealth, poolHealth, sysHealth);
            }

        } catch (Exception e) {
            logger.error("❌ Health check #{} FAILED due to exception: {}", checkNumber, e.getMessage(), e);
        }
    }

    /**
     * Quick connectivity check - runs every minute
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    public void performQuickConnectivityCheck() {
        try {
            // Quick AI service connectivity test
            Health aiHealth = aiServiceHealth.health();
            Status aiStatus = aiHealth.getStatus();

            if (!Status.UP.equals(aiStatus)) {
                logger.warn("🚨 ALERT: AI service connectivity issue detected - Status: {}", aiStatus);
            }

        } catch (Exception e) {
            logger.error("❌ Quick connectivity check failed: {}", e.getMessage());
        }
    }

    /**
     * System resource monitoring - runs every 10 minutes
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void performResourceMonitoring() {
        try {
            Health sysHealth = systemHealth.health();

            if (sysHealth.getDetails().containsKey("memory_warning")) {
                logger.warn("⚠️ MEMORY WARNING: {}", sysHealth.getDetails().get("memory_warning"));
            }

            if (sysHealth.getDetails().containsKey("resilience_warning")) {
                logger.warn("⚠️ RESILIENCE WARNING: {}", sysHealth.getDetails().get("resilience_warning"));
            }

            // Log periodic status summary
            logger.info("📊 Resource Check - Memory: {}%, Uptime: {}min",
                       sysHealth.getDetails().get("memory_usage_percent"),
                       sysHealth.getDetails().get("system") != null ?
                           ((Map<?, ?>) sysHealth.getDetails().get("system")).get("uptime_minutes") : "unknown");

        } catch (Exception e) {
            logger.error("❌ Resource monitoring failed: {}", e.getMessage());
        }
    }

    private void checkAndLogStatusChange(String component, Status currentStatus) {
        String currentStatusStr = currentStatus.getCode();
        String lastStatus = lastKnownStatus.get(component);

        if (lastStatus == null) {
            // First check
            lastKnownStatus.put(component, currentStatusStr);
            logger.info("🆕 {} initial status: {}", component, currentStatusStr);
        } else if (!lastStatus.equals(currentStatusStr)) {
            // Status changed
            lastKnownStatus.put(component, currentStatusStr);

            if (Status.UP.getCode().equals(currentStatusStr)) {
                logger.info("✅ {} RECOVERED: {} -> {}", component, lastStatus, currentStatusStr);
            } else if (Status.DOWN.getCode().equals(currentStatusStr)) {
                logger.error("🚨 {} FAILED: {} -> {}", component, lastStatus, currentStatusStr);
            } else {
                logger.warn("⚠️ {} STATUS CHANGED: {} -> {}", component, lastStatus, currentStatusStr);
            }
        }
    }

    private boolean isAllHealthy(Health... healthChecks) {
        for (Health health : healthChecks) {
            if (!Status.UP.equals(health.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private void logDetailedHealthStatus(Health mcpHealth, Health aiHealth,
                                       Health poolHealth, Health sysHealth) {
        logger.warn("📋 Detailed Health Status:");
        logger.warn("   🔗 MCP Servers: {} - {}", mcpHealth.getStatus(),
                   mcpHealth.getDetails().get("server_count"));
        logger.warn("   🤖 AI Service: {} - {}", aiHealth.getStatus(),
                   aiHealth.getDetails().get("connectivity_test"));
        logger.warn("   🌐 Connection Pools: {} - {}", poolHealth.getStatus(),
                   poolHealth.getDetails().get("overall_healthy"));
        logger.warn("   💻 System: {} - Memory: {}", sysHealth.getStatus(),
                   sysHealth.getDetails().get("memory_usage_percent"));
    }

    /**
     * Get monitoring statistics
     */
    public MonitoringStats getMonitoringStats() {
        return new MonitoringStats(
            healthCheckCount.get(),
            lastKnownStatus,
            LocalDateTime.now().format(TIMESTAMP_FORMAT)
        );
    }

    /**
     * Monitoring statistics data class
     */
    public static class MonitoringStats {
        private final long totalHealthChecks;
        private final Map<String, String> currentStatus;
        private final String lastCheckTime;

        public MonitoringStats(long totalHealthChecks, Map<String, String> currentStatus, String lastCheckTime) {
            this.totalHealthChecks = totalHealthChecks;
            this.currentStatus = new HashMap<>(currentStatus);
            this.lastCheckTime = lastCheckTime;
        }

        public long getTotalHealthChecks() { return totalHealthChecks; }
        public Map<String, String> getCurrentStatus() { return currentStatus; }
        public String getLastCheckTime() { return lastCheckTime; }
    }
}