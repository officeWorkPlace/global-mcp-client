package com.officeworkplace.mcpclient.service;

import com.officeworkplace.mcpclient.config.McpConfigurationProperties;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for MCP servers.
 */
@Component
public class McpHealthIndicator implements HealthIndicator {
    
    private final McpClientService mcpClientService;
    
    public McpHealthIndicator(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }
    
    @Override
    public Health health() {
        try {
            var healthMap = mcpClientService.getOverallHealth().block();
            
            if (healthMap == null || healthMap.isEmpty()) {
                return Health.down()
                    .withDetail("message", "No MCP servers configured")
                    .build();
            }
            
            long healthyCount = healthMap.values().stream()
                .mapToLong(healthy -> healthy ? 1 : 0)
                .sum();
            
            Health.Builder builder = healthyCount == healthMap.size() ? 
                Health.up() : Health.down();
            
            return builder
                .withDetail("servers", healthMap)
                .withDetail("total", healthMap.size())
                .withDetail("healthy", healthyCount)
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
