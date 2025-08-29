package com.officeworkplace.mcpclient.controller;

import com.officeworkplace.mcpclient.model.*;
import com.officeworkplace.mcpclient.service.McpClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for MCP client operations.
 */
@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP Client", description = "Model Context Protocol client operations")
public class McpController {
    
    private final McpClientService mcpClientService;
    
    public McpController(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }
    
    @GetMapping("/servers")
    @Operation(summary = "List all configured MCP servers")
    public Mono<List<String>> listServers() {
        return Mono.just(mcpClientService.getServerIds());
    }
    
    @GetMapping("/servers/{serverId}/info")
    @Operation(summary = "Get server information")
    public Mono<McpServerInfo> getServerInfo(
            @Parameter(description = "Server ID") @PathVariable String serverId) {
        return mcpClientService.getServerInfo(serverId);
    }
    
    @GetMapping("/servers/{serverId}/health")
    @Operation(summary = "Check server health")
    public Mono<Map<String, Boolean>> getServerHealth(
            @Parameter(description = "Server ID") @PathVariable String serverId) {
        return mcpClientService.isServerHealthy(serverId)
            .map(healthy -> Map.of("healthy", healthy));
    }
    
    @GetMapping("/health")
    @Operation(summary = "Get health status of all servers")
    public Mono<Map<String, Boolean>> getOverallHealth() {
        return mcpClientService.getOverallHealth();
    }
    
    @GetMapping("/servers/{serverId}/tools")
    @Operation(summary = "List available tools from a server")
    public Mono<List<McpTool>> listTools(
            @Parameter(description = "Server ID") @PathVariable String serverId) {
        return mcpClientService.listTools(serverId);
    }
    
    @GetMapping("/tools")
    @Operation(summary = "Get all tools from all servers")
    public Mono<Map<String, List<McpTool>>> getAllTools() {
        return mcpClientService.getAllTools();
    }
    
    @PostMapping("/servers/{serverId}/tools/{toolName}")
    @Operation(summary = "Execute a tool")
    public Mono<McpToolResult> executeTool(
            @Parameter(description = "Server ID") @PathVariable String serverId,
            @Parameter(description = "Tool name") @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> arguments) {
        return mcpClientService.executeTool(serverId, toolName, arguments);
    }
    
    @GetMapping("/servers/{serverId}/resources")
    @Operation(summary = "List available resources from a server")
    public Mono<List<McpResource>> listResources(
            @Parameter(description = "Server ID") @PathVariable String serverId) {
        return mcpClientService.listResources(serverId);
    }
    
    @GetMapping("/servers/{serverId}/resources/read")
    @Operation(summary = "Read resource content")
    public Mono<McpResourceContent> readResource(
            @Parameter(description = "Server ID") @PathVariable String serverId,
            @Parameter(description = "Resource URI") @RequestParam String uri) {
        return mcpClientService.readResource(serverId, uri);
    }
    
    @PostMapping("/servers/{serverId}/messages")
    @Operation(summary = "Send raw message to server")
    public Mono<McpMessage> sendMessage(
            @Parameter(description = "Server ID") @PathVariable String serverId,
            @Valid @RequestBody McpMessage message) {
        return mcpClientService.sendMessage(serverId, message);
    }
    
    @GetMapping(value = "/servers/{serverId}/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to server notifications (Server-Sent Events)")
    public Flux<McpMessage> getNotifications(
            @Parameter(description = "Server ID") @PathVariable String serverId) {
        return mcpClientService.getNotifications(serverId);
    }
}
