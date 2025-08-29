package com.officeworkplace.mcpclient.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeworkplace.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.officeworkplace.mcpclient.model.*;
import com.officeworkplace.mcpclient.service.McpServerConnection;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP-based MCP server connection implementation.
 */
public class HttpMcpServerConnection implements McpServerConnection {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpMcpServerConnection.class);
    
    private final String serverId;
    private final ServerConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Sinks.Many<McpMessage> notificationSink = Sinks.many().multicast().onBackpressureBuffer();
    
    public HttpMcpServerConnection(String serverId, ServerConfig config, 
                                  WebClient webClient, ObjectMapper objectMapper) {
        this.serverId = serverId;
        this.config = config;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<Void> initialize() {
        logger.info("Initializing HTTP connection to server: {}", serverId);
        return getServerInfo()
            .doOnSuccess(info -> logger.info("Connected to server: {} v{}", info.name(), info.version()))
            .doOnError(error -> logger.error("Failed to initialize connection to {}: {}", serverId, error.getMessage()))
            .then();
    }
    
    @Override
    public Mono<Boolean> isHealthy() {
        return sendMessage(McpMessage.request(generateRequestId(), "ping", null))
            .map(response -> response.error() == null)
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<McpServerInfo> getServerInfo() {
        return sendMessage(McpMessage.request(generateRequestId(), "initialize", 
                Map.of("protocolVersion", "2024-11-05", "clientInfo", 
                       Map.of("name", "global-mcp-client", "version", "1.0.0"))))
            .map(response -> {
                if (response.result() != null) {
                    try {
                        return objectMapper.convertValue(response.result(), McpServerInfo.class);
                    } catch (Exception e) {
                        logger.warn("Failed to parse server info: {}", e.getMessage());
                        return new McpServerInfo(serverId, "unknown", null, null, null, null);
                    }
                }
                throw new RuntimeException("Failed to get server info: " + response.error());
            });
    }
    
    @Override
    public Mono<List<McpTool>> listTools() {
        return sendMessage(McpMessage.request(generateRequestId(), "tools/list", null))
            .map(response -> {
                if (response.result() != null) {
                    try {
                        Map<String, Object> result = objectMapper.convertValue(response.result(), Map.class);
                        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
                        return tools.stream()
                            .map(tool -> objectMapper.convertValue(tool, McpTool.class))
                            .toList();
                    } catch (Exception e) {
                        logger.warn("Failed to parse tools list: {}", e.getMessage());
                        return List.of();
                    }
                }
                logger.warn("Failed to list tools: {}", response.error());
                return List.of();
            });
    }
    
    @Override
    public Mono<McpToolResult> executeTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = Map.of(
            "name", toolName,
            "arguments", arguments != null ? arguments : Map.of()
        );
        
        return sendMessage(McpMessage.request(generateRequestId(), "tools/call", params))
            .map(response -> {
                if (response.result() != null) {
                    try {
                        return objectMapper.convertValue(response.result(), McpToolResult.class);
                    } catch (Exception e) {
                        logger.warn("Failed to parse tool result: {}", e.getMessage());
                        return new McpToolResult(List.of(McpContent.error("Failed to parse result")), true);
                    }
                }
                String errorMsg = response.error() != null ? response.error().message() : "Unknown error";
                return new McpToolResult(List.of(McpContent.error(errorMsg)), true);
            });
    }
    
    @Override
    public Mono<List<McpResource>> listResources() {
        return sendMessage(McpMessage.request(generateRequestId(), "resources/list", null))
            .map(response -> {
                if (response.result() != null) {
                    try {
                        Map<String, Object> result = objectMapper.convertValue(response.result(), Map.class);
                        List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
                        return resources.stream()
                            .map(resource -> objectMapper.convertValue(resource, McpResource.class))
                            .toList();
                    } catch (Exception e) {
                        logger.warn("Failed to parse resources list: {}", e.getMessage());
                        return List.of();
                    }
                }
                logger.warn("Failed to list resources: {}", response.error());
                return List.of();
            });
    }
    
    @Override
    public Mono<McpResourceContent> readResource(String uri) {
        return sendMessage(McpMessage.request(generateRequestId(), "resources/read", Map.of("uri", uri)))
            .map(response -> {
                if (response.result() != null) {
                    try {
                        return objectMapper.convertValue(response.result(), McpResourceContent.class);
                    } catch (Exception e) {
                        logger.warn("Failed to parse resource content: {}", e.getMessage());
                        return new McpResourceContent(uri, "text/plain", "Error reading resource", null);
                    }
                }
                String errorMsg = response.error() != null ? response.error().message() : "Unknown error";
                return new McpResourceContent(uri, "text/plain", "Error: " + errorMsg, null);
            });
    }
    
    @Override
    public Mono<McpMessage> sendMessage(McpMessage message) {
        logger.debug("Sending HTTP message to {}: {}", serverId, message.method());
        
        return webClient.post()
            .uri(config.url())
            .headers(headers -> {
                if (config.headers() != null) {
                    config.headers().forEach(headers::add);
                }
                headers.add("Content-Type", "application/json");
            })
            .bodyValue(message)
            .retrieve()
            .bodyToMono(McpMessage.class)
            .timeout(java.time.Duration.ofMillis(config.timeout()))
            .doOnError(error -> logger.error("HTTP request failed for {}: {}", serverId, error.getMessage()));
    }
    
    @Override
    public Flux<McpMessage> notifications() {
        return notificationSink.asFlux();
    }
    
    @Override
    public Mono<Void> close() {
        logger.info("Closing HTTP connection to server: {}", serverId);
        notificationSink.tryEmitComplete();
        return Mono.empty();
    }
    
    @Override
    public ServerConfig getConfig() {
        return config;
    }
    
    @Override
    public String getServerId() {
        return serverId;
    }
    
    private Long generateRequestId() {
        return requestIdCounter.getAndIncrement();
    }
}
