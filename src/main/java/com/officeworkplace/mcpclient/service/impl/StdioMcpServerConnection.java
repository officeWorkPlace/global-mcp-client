package com.officeworkplace.mcpclient.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeworkplace.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.officeworkplace.mcpclient.model.*;
import com.officeworkplace.mcpclient.service.McpServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stdio-based MCP server connection implementation.
 */
public class StdioMcpServerConnection implements McpServerConnection {
    
    private static final Logger logger = LoggerFactory.getLogger(StdioMcpServerConnection.class);
    
    private final String serverId;
    private final ServerConfig config;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Map<Long, CompletableFuture<McpMessage>> pendingRequests = new ConcurrentHashMap<>();
    private final Sinks.Many<McpMessage> notificationSink = Sinks.many().multicast().onBackpressureBuffer();
    
    private Process process;
    private BufferedWriter processInput;
    private BufferedReader processOutput;
    private Thread readerThread;
    
    public StdioMcpServerConnection(String serverId, ServerConfig config, ObjectMapper objectMapper) {
        this.serverId = serverId;
        this.config = config;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<Void> initialize() {
        return Mono.fromCallable(() -> {
            logger.info("Starting stdio process for server: {}", serverId);
            
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(config.command());
            if (config.args() != null && !config.args().isEmpty()) {
                processBuilder.command().addAll(config.args());
            }
            
            if (config.environment() != null) {
                processBuilder.environment().putAll(config.environment());
            }
            
            process = processBuilder.start();
            processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            // Start reader thread
            readerThread = new Thread(this::readMessages, "stdio-reader-" + serverId);
            readerThread.setDaemon(true);
            readerThread.start();
            
            logger.info("Stdio process started for server: {}", serverId);
            return null;
        })
        .then(getServerInfo())
        .doOnSuccess(info -> logger.info("Connected to stdio server: {} v{}", info.name(), info.version()))
        .doOnError(error -> logger.error("Failed to initialize stdio connection to {}: {}", serverId, error.getMessage()))
        .then();
    }
    
    @Override
    public Mono<Boolean> isHealthy() {
        if (process == null || !process.isAlive()) {
            return Mono.just(false);
        }
        
        return sendMessage(McpMessage.request(generateRequestId(), "ping", null))
            .map(response -> response.error() == null)
            .timeout(java.time.Duration.ofSeconds(5))
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
        if (process == null || !process.isAlive()) {
            return Mono.error(new RuntimeException("Process is not running"));
        }
        
        return Mono.fromCallable(() -> {
            Long requestId = (Long) message.id();
            CompletableFuture<McpMessage> future = new CompletableFuture<>();
            pendingRequests.put(requestId, future);
            
            try {
                String json = objectMapper.writeValueAsString(message);
                logger.debug("Sending stdio message to {}: {}", serverId, message.method());
                
                synchronized (processInput) {
                    processInput.write(json);
                    processInput.newLine();
                    processInput.flush();
                }
                
                return future.get(config.timeout(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                pendingRequests.remove(requestId);
                throw new RuntimeException("Failed to send message", e);
            }
        });
    }
    
    @Override
    public Flux<McpMessage> notifications() {
        return notificationSink.asFlux();
    }
    
    @Override
    public Mono<Void> close() {
        return Mono.fromRunnable(() -> {
            logger.info("Closing stdio connection to server: {}", serverId);
            
            if (readerThread != null) {
                readerThread.interrupt();
            }
            
            if (processInput != null) {
                try {
                    processInput.close();
                } catch (IOException e) {
                    logger.warn("Error closing process input: {}", e.getMessage());
                }
            }
            
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            pendingRequests.values().forEach(future -> 
                future.completeExceptionally(new RuntimeException("Connection closed")));
            pendingRequests.clear();
            
            notificationSink.tryEmitComplete();
        });
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
    
    private void readMessages() {
        try {
            String line;
            while ((line = processOutput.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                try {
                    McpMessage message = objectMapper.readValue(line, McpMessage.class);
                    
                    if (message.isResponse() && message.id() instanceof Number) {
                        Long requestId = ((Number) message.id()).longValue();
                        CompletableFuture<McpMessage> future = pendingRequests.remove(requestId);
                        if (future != null) {
                            future.complete(message);
                        }
                    } else if (message.isNotification()) {
                        notificationSink.tryEmitNext(message);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse message from {}: {}", serverId, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Error reading from stdio process {}: {}", serverId, e.getMessage());
        }
    }
}
