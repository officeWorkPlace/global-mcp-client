package com.deepai.mcpclient.service;

import com.deepai.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.deepai.mcpclient.model.McpMessage;
import com.deepai.mcpclient.model.McpResource;
import com.deepai.mcpclient.model.McpResourceContent;
import com.deepai.mcpclient.model.McpServerInfo;
import com.deepai.mcpclient.model.McpTool;
import com.deepai.mcpclient.model.McpToolResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface McpClientService {
    void initialize();
    void shutdown();
    List<String> getServerIds();
    Mono<McpServerConnection> getServerConnection(String serverId);
    Mono<McpServerInfo> getServerInfo(String serverId);
    Mono<Boolean> isServerHealthy(String serverId);
    Mono<List<McpTool>> listTools(String serverId);
    Mono<McpToolResult> executeTool(String serverId, String toolName, Map<String, Object> arguments);
    Mono<List<McpResource>> listResources(String serverId);
    Mono<McpResourceContent> readResource(String serverId, String uri);
    Mono<McpMessage> sendMessage(String serverId, McpMessage message);
    Flux<McpMessage> getNotifications(String serverId);
    Mono<Map<String, Boolean>> getOverallHealth();
    Mono<Map<String, List<McpTool>>> getAllTools();
    Mono<Void> addServer(String serverId, ServerConfig serverConfig);
    Mono<Void> removeServer(String serverId);
}