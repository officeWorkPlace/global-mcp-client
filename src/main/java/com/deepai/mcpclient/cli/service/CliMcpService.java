package com.deepai.mcpclient.cli.service;

import com.deepai.mcpclient.model.*;
import com.deepai.mcpclient.service.McpClientService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * CLI-specific service that wraps McpClientService for direct MCP operations.
 * This provides a simplified interface for CLI commands without REST API overhead.
 */
@Service
public class CliMcpService {
    
    private final McpClientService mcpClientService;
    
    public CliMcpService(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }
    
    /**
     * Get all server IDs.
     */
    public List<String> getServerIds() {
        return mcpClientService.getServerIds();
    }
    
    /**
     * Get server info by ID.
     */
    public McpServerInfo getServerInfo(String serverId) {
        return mcpClientService.getServerInfo(serverId).block();
    }
    
    /**
     * Check if server is healthy.
     */
    public Boolean isServerHealthy(String serverId) {
        return mcpClientService.isServerHealthy(serverId).block();
    }
    
    /**
     * List all tools from a server.
     */
    public List<McpTool> listTools(String serverId) {
        return mcpClientService.listTools(serverId).block();
    }
    
    /**
     * Get all tools from all servers.
     */
    public Map<String, List<McpTool>> getAllTools() {
        return mcpClientService.getAllTools().block();
    }
    
    /**
     * Execute a tool on a specific server.
     */
    public McpToolResult executeTool(String serverId, String toolName, Map<String, Object> arguments) {
        return mcpClientService.executeTool(serverId, toolName, arguments).block();
    }
    
    /**
     * List resources from a server.
     */
    public List<McpResource> listResources(String serverId) {
        return mcpClientService.listResources(serverId).block();
    }
    
    /**
     * Get overall health status of all servers.
     */
    public Map<String, Boolean> getOverallHealth() {
        return mcpClientService.getOverallHealth().block();
    }
    
    /**
     * Initialize the MCP client service (if not already done).
     */
    public void initialize() {
        mcpClientService.initialize();
    }
}
