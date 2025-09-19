package com.deepai.mcpclient.health;

import com.deepai.mcpclient.config.ConnectionPoolConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for connection pools
 * Monitors HTTP and MCP connection pool health and statistics
 */
@Component("connectionPools")
public class ConnectionPoolHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolHealthIndicator.class);

    private final ConnectionPoolConfiguration.ConnectionPoolMonitor poolMonitor;

    @Autowired
    public ConnectionPoolHealthIndicator(ConnectionPoolConfiguration.ConnectionPoolMonitor poolMonitor) {
        this.poolMonitor = poolMonitor;
    }

    @Override
    public Health health() {
        try {
            boolean poolsHealthy = poolMonitor.isPoolHealthy();

            Health.Builder healthBuilder = poolsHealthy ? Health.up() : Health.down();

            // Get HTTP pool statistics
            ConnectionPoolConfiguration.ConnectionPoolStats httpStats = poolMonitor.getHttpPoolStats();
            healthBuilder.withDetail("http_pool", createPoolDetails(httpStats));

            // Get MCP pool statistics
            ConnectionPoolConfiguration.ConnectionPoolStats mcpStats = poolMonitor.getMcpPoolStats();
            healthBuilder.withDetail("mcp_pool", createPoolDetails(mcpStats));

            healthBuilder.withDetail("overall_healthy", poolsHealthy);

            return healthBuilder.build();

        } catch (Exception e) {
            logger.error("❌ Connection pool health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Health check failed")
                .build();
        }
    }

    private Object createPoolDetails(ConnectionPoolConfiguration.ConnectionPoolStats stats) {
        return new Object() {
            public final String name = stats.getPoolName();
            public final int maxConnections = stats.getMaxConnections();
            public final int activeConnections = stats.getActiveConnections();
            public final int idleConnections = stats.getIdleConnections();
            public final long totalAcquired = stats.getTotalAcquiredConnections();
            public final String status = "HEALTHY";
        };
    }
}