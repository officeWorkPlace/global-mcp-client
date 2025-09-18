package com.deepai.mcpclient.health;

import com.deepai.mcpclient.service.McpClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health indicator for MCP servers
 * Checks connectivity and responsiveness of all configured MCP servers
 */
@Component("mcpServers")
public class McpServersHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(McpServersHealthIndicator.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);

    private final McpClientService mcpClientService;

    @Autowired
    public McpServersHealthIndicator(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }

    @Override
    public Health health() {
        try {
            List<String> serverIds = mcpClientService.getServerIds();

            if (serverIds.isEmpty()) {
                return Health.down()
                    .withDetail("status", "No MCP servers configured")
                    .withDetail("server_count", 0)
                    .build();
            }

            Map<String, Object> serverHealth = checkServerHealth(serverIds);

            boolean allHealthy = serverHealth.values().stream()
                .allMatch(status -> "UP".equals(status.toString()));

            Health.Builder healthBuilder = allHealthy ? Health.up() : Health.down();

            return healthBuilder
                .withDetail("server_count", serverIds.size())
                .withDetail("servers", serverHealth)
                .withDetail("all_healthy", allHealthy)
                .build();

        } catch (Exception e) {
            logger.error("❌ Health check failed for MCP servers: {}", e.getMessage());
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Health check failed")
                .build();
        }
    }

    private Map<String, Object> checkServerHealth(List<String> serverIds) {
        Map<String, Object> serverHealth = new HashMap<>();

        // Check each server's health in parallel with timeout
        Flux.fromIterable(serverIds)
            .flatMap(serverId ->
                mcpClientService.getServerInfo(serverId)
                    .map(info -> Map.entry(serverId, "UP"))
                    .onErrorReturn(Map.entry(serverId, "DOWN"))
                    .timeout(HEALTH_CHECK_TIMEOUT)
                    .onErrorReturn(Map.entry(serverId, "TIMEOUT"))
            )
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .subscribe(healthMap -> {
                serverHealth.putAll(healthMap);
                logger.debug("🏥 MCP servers health check completed: {}", healthMap);
            })
            .dispose();

        // Wait for results with timeout
        try {
            Thread.sleep(HEALTH_CHECK_TIMEOUT.toMillis() + 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Health check interrupted");
        }

        // If no results, mark all as unknown
        if (serverHealth.isEmpty()) {
            for (String serverId : serverIds) {
                serverHealth.put(serverId, "UNKNOWN");
            }
        }

        return serverHealth;
    }
}