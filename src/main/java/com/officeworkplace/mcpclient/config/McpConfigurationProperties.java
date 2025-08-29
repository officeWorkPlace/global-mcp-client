package com.officeworkplace.mcpclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for MCP servers.
 */
@ConfigurationProperties(prefix = "mcp")
@Validated
public record McpConfigurationProperties(
    @Valid
    @NotNull
    ClientConfig client,
    
    @Valid
    @NotNull
    Map<String, ServerConfig> servers
) {
    
    public record ClientConfig(
        @Positive
        int defaultTimeout,
        
        @Positive
        int maxConnections,
        
        @Positive
        int connectionPoolSize,
        
        @Valid
        @NotNull
        RetryConfig retry
    ) {
    }
    
    public record RetryConfig(
        @Positive
        int maxAttempts,
        
        @Positive
        double backoffMultiplier
    ) {
    }
    
    public record ServerConfig(
        @NotBlank
        String type,
        
        String url,
        
        String command,
        
        List<String> args,
        
        @Positive
        int timeout,
        
        boolean enabled,
        
        Map<String, String> headers,
        
        Map<String, String> environment
    ) {
        
        public boolean isHttpType() {
            return "http".equalsIgnoreCase(type) || "https".equalsIgnoreCase(type);
        }
        
        public boolean isStdioType() {
            return "stdio".equalsIgnoreCase(type);
        }
        
        public boolean isSseType() {
            return "sse".equalsIgnoreCase(type);
        }
    }
}
