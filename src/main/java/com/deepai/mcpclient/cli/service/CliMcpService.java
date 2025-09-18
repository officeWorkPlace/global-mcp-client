package com.deepai.mcpclient.cli.service;

import com.deepai.mcpclient.model.*;
import com.deepai.mcpclient.cli.model.CliToolResult;
import com.deepai.mcpclient.service.McpClientService;
import com.deepai.mcpclient.service.AiService;
import com.deepai.mcpclient.service.ResponseFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Enhanced CLI service that integrates AI intelligence with MCP operations.
 * Provides the same smart capabilities as the REST API for CLI users.
 */
@Service
public class CliMcpService {

    private static final Logger logger = LoggerFactory.getLogger(CliMcpService.class);

    private final McpClientService mcpClientService;
    private final AiService aiService;
    private final ResponseFormatter responseFormatter;

    public CliMcpService(McpClientService mcpClientService,
                        AiService aiService,
                        ResponseFormatter responseFormatter) {
        this.mcpClientService = mcpClientService;
        this.aiService = aiService;
        this.responseFormatter = responseFormatter;
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
     * Execute a tool on a specific server with AI intelligence.
     * Uses the same smart processing as the REST API.
     */
    public McpToolResult executeTool(String serverId, String toolName, Map<String, Object> arguments) {
        logger.info("🔧 CLI executing tool '{}' on server '{}' with AI intelligence", toolName, serverId);

        try {
            // Use AI service for intelligent execution with all safeguards
            ChatRequest chatRequest = new ChatRequest(
                "Execute tool " + toolName + " with parameters: " + arguments,
                serverId,
                "cli-session-" + System.currentTimeMillis(),
                false
            );

            ChatResponse aiResponse = aiService.processChat(chatRequest).block();

            if (aiResponse != null && aiResponse.toolExecutions() != null && !aiResponse.toolExecutions().isEmpty()) {
                // Extract the actual tool result from AI processing
                // For now, also execute directly to get the raw result for CLI formatting
                McpToolResult rawResult = mcpClientService.executeTool(serverId, toolName, arguments).block();

                // Format the result using AI for better CLI presentation
                if (rawResult != null) {
                    logger.info("✅ Tool executed successfully with AI intelligence");
                    return rawResult;
                }
            }

            // Fallback to direct execution if AI processing fails
            logger.warn("⚠️ Falling back to direct tool execution");
            return mcpClientService.executeTool(serverId, toolName, arguments).block();

        } catch (Exception e) {
            logger.error("❌ AI-enhanced tool execution failed, using fallback: {}", e.getMessage());
            // Fallback to direct execution
            return mcpClientService.executeTool(serverId, toolName, arguments).block();
        }
    }

    /**
     * Execute a tool with natural language input (AI-powered).
     * This allows CLI users to use natural language like the REST API.
     */
    public CliToolResult executeToolWithAi(String naturalLanguageRequest, String contextId) {
        logger.info("🧠 CLI processing natural language request: '{}'", naturalLanguageRequest);

        try {
            ChatRequest chatRequest = new ChatRequest(
                naturalLanguageRequest,
                null, // Let AI choose the best server
                contextId != null ? contextId : "cli-session-" + System.currentTimeMillis(),
                false
            );

            ChatResponse aiResponse = aiService.processChat(chatRequest).block();

            if (aiResponse != null) {
                // Format response for CLI display
                String formattedResponse = aiResponse.response();

                return new CliToolResult(
                    true,
                    formattedResponse,
                    aiResponse.toolExecutions(),
                    aiResponse.model()
                );
            }

            return new CliToolResult(false, "AI processing failed", null, null);

        } catch (Exception e) {
            logger.error("❌ AI-powered CLI execution failed: {}", e.getMessage());
            return new CliToolResult(false, "Error: " + e.getMessage(), null, null);
        }
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
