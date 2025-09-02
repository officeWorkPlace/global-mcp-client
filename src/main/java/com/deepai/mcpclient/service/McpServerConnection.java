package com.deepai.mcpclient.service;

import com.deepai.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.deepai.mcpclient.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Interface for MCP server connections.
 */
public interface McpServerConnection {
    
    /**
     * Initialize the connection to the MCP server.
     */
    Mono<Void> initialize();
    
    /**
     * Check if the connection is healthy.
     */
    Mono<Boolean> isHealthy();
    
    /**
     * Get server information and capabilities.
     */
    Mono<McpServerInfo> getServerInfo();
    
    /**
     * List available tools from the server.
     */
    Mono<List<McpTool>> listTools();
    
    /**
     * Execute a tool with given arguments.
     */
    Mono<McpToolResult> executeTool(String toolName, Map<String, Object> arguments);
    
    /**
     * List available resources from the server.
     */
    Mono<List<McpResource>> listResources();
    
    /**
     * Read resource content.
     */
    Mono<McpResourceContent> readResource(String uri);
    
    /**
     * Send a raw message to the server.
     */
    Mono<McpMessage> sendMessage(McpMessage message);
    
    /**
     * Subscribe to server notifications.
     */
    Flux<McpMessage> notifications();
    
    /**
     * Close the connection.
     */
    Mono<Void> close();
    
    /**
     * Get the server configuration.
     */
    ServerConfig getConfig();
    
    /**
     * Get the server ID.
     */
    String getServerId();
}
