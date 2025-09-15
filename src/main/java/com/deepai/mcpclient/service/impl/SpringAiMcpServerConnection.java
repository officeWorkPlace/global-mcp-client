package com.deepai.mcpclient.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepai.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.deepai.mcpclient.model.*;
import com.deepai.mcpclient.service.McpServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

/**
 * Spring AI 1.0.1 Native MCP Server Connection Implementation.
 * 
 * This implementation is compatible with Spring AI's native MCP server protocol,
 * which follows the standard MCP specification.
 */
public class SpringAiMcpServerConnection implements McpServerConnection {
    
    private static final Logger logger = LoggerFactory.getLogger(SpringAiMcpServerConnection.class);
    
    private final String serverId;
    private final ServerConfig config;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final Sinks.Many<McpMessage> notificationSink = Sinks.many().multicast().onBackpressureBuffer();
    private final ScheduledExecutorService timeoutExecutor;
    
    private Process process;
    private BufferedWriter processStdin;
    private BufferedReader processStdout;
    private Thread readerThread;
    private Thread stderrThread;
    private volatile boolean initialized = false;
    
    public SpringAiMcpServerConnection(String serverId, ServerConfig config, ObjectMapper objectMapper) {
        this.serverId = serverId;
        this.config = config;
        this.objectMapper = objectMapper;
        this.timeoutExecutor = Executors.newScheduledThreadPool(2, 
            r -> new Thread(r, "spring-ai-mcp-timeout-" + serverId));
    }
    
    @Override
    public Mono<Void> initialize() {
        return Mono.fromCallable(() -> {
            logger.info("Starting Spring AI native MCP server process: {}", serverId);
            
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(config.command());
            if (config.args() != null && !config.args().isEmpty()) {
                processBuilder.command().addAll(config.args());
            }
            
            if (config.environment() != null) {
                processBuilder.environment().putAll(config.environment());
            }
            
            processBuilder.redirectErrorStream(false);
            
            process = processBuilder.start();
            processStdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            processStdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            
            // Start message reader thread
            readerThread = new Thread(this::readMessages, "spring-ai-mcp-reader-" + serverId);
            readerThread.setDaemon(true);
            readerThread.start();
            
            // Start stderr logger thread
            stderrThread = new Thread(() -> readStderr(process.getErrorStream()), "spring-ai-mcp-stderr-" + serverId);
            stderrThread.setDaemon(true);
            stderrThread.start();
            
            logger.info("Spring AI MCP server process started: {}", serverId);
            return null;
        })
        .then(waitForServerReady())
        .then(performInitialization())
        .doOnSuccess(v -> logger.info("Spring AI MCP server initialized successfully: {}", serverId))
        .doOnError(error -> logger.error("Failed to initialize Spring AI MCP server {}: {}", serverId, error.getMessage()));
    }
    
    /**
     * Wait for Spring AI MCP server to be ready
     */
    private Mono<Void> waitForServerReady() {
        return Mono.delay(Duration.ofSeconds(8)) // Spring AI needs more time to initialize
            .doOnNext(v -> logger.info("Waiting for Spring AI MCP server to be ready: {}", serverId))
            .then();
    }
    
    /**
     * Perform Spring AI MCP protocol initialization
     */
    private Mono<Void> performInitialization() {
        logger.info("Performing Spring AI MCP protocol initialization: {}", serverId);
        
        Map<String, Object> initParams = Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(
                "tools", Map.of("listChanged", true),
                "resources", Map.of("listChanged", true)
            ),
            "clientInfo", Map.of(
                "name", "global-mcp-client",
                "version", "1.0.0"
            )
        );
        
        return sendRequest("initialize", initParams)
            .flatMap(response -> {
                if (response.has("error")) {
                    String errorMsg = response.get("error").get("message").asText();
                    return Mono.error(new RuntimeException("MCP initialization failed: " + errorMsg));
                }
                
                logger.info("Spring AI MCP initialization successful: {}", serverId);
                initialized = true;
                
                // Send initialized notification (Spring AI expects this)
                return sendNotification("notifications/initialized", Map.of())
                    .doOnSuccess(v -> logger.info("Sent initialized notification to Spring AI MCP server: {}", serverId));
            })
            .then();
    }
    
    @Override
    public Mono<Boolean> isHealthy() {
        if (process == null || !process.isAlive()) {
            return Mono.just(false);
        }
        
        if (!initialized) {
            return Mono.just(false);
        }
        
        // Use tools/list for health check
        return sendRequest("tools/list", Map.of())
            .map(response -> !response.has("error"))
            .timeout(Duration.ofSeconds(5))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<McpServerInfo> getServerInfo() {
        // For Spring AI, server info is obtained during initialization
        return Mono.just(new McpServerInfo(
            "mongo-mcp-server",
            "1.0.0",
            "MongoDB MCP Server with Spring AI 1.0.1",
            null,
            "DeepAI",
            null
        ));
    }
    
    @Override
    public Mono<List<McpTool>> listTools() {
        return sendRequest("tools/list", Map.of())
            .map(response -> {
                if (response.has("error")) {
                    throw new RuntimeException("Failed to list tools: " + response.get("error").get("message").asText());
                }
                
                JsonNode result = response.get("result");
                if (result == null || !result.has("tools")) {
                    logger.warn("No tools field in response from Spring AI MCP server: {}", serverId);
                    return List.<McpTool>of();
                }
                
                JsonNode toolsArray = result.get("tools");
                List<McpTool> tools = new java.util.ArrayList<>();
                
                for (JsonNode toolNode : toolsArray) {
                    try {
                        McpTool tool = objectMapper.convertValue(toolNode, McpTool.class);
                        tools.add(tool);
                    } catch (Exception e) {
                        logger.warn("Failed to parse tool from Spring AI MCP server: {}", e.getMessage());
                    }
                }
                
                logger.info("Retrieved {} tools from Spring AI MCP server: {}", tools.size(), serverId);
                return tools;
            });
    }
    
    @Override
    public Mono<McpToolResult> executeTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = Map.of(
            "name", toolName,
            "arguments", arguments != null ? arguments : Map.of()
        );
        
        return sendRequest("tools/call", params)
            .map(response -> {
                if (response.has("error")) {
                    String errorMsg = response.get("error").get("message").asText();
                    return new McpToolResult(List.of(McpContent.error(errorMsg)), true);
                }
                
                JsonNode result = response.get("result");
                if (result == null) {
                    return new McpToolResult(List.of(McpContent.error("No result from Spring AI MCP server")), true);
                }
                
                try {
                    return objectMapper.convertValue(result, McpToolResult.class);
                } catch (Exception e) {
                    logger.warn("Failed to parse tool result from Spring AI MCP server: {}", e.getMessage());
                    return new McpToolResult(List.of(McpContent.error("Failed to parse result")), true);
                }
            });
    }
    
    @Override
    public Mono<List<McpResource>> listResources() {
        return sendRequest("resources/list", Map.of())
            .map(response -> {
                if (response.has("error")) {
                    return List.<McpResource>of();
                }
                
                JsonNode result = response.get("result");
                if (result == null || !result.has("resources")) {
                    return List.<McpResource>of();
                }
                
                JsonNode resourcesArray = result.get("resources");
                List<McpResource> resources = new java.util.ArrayList<>();
                
                for (JsonNode resourceNode : resourcesArray) {
                    try {
                        McpResource resource = objectMapper.convertValue(resourceNode, McpResource.class);
                        resources.add(resource);
                    } catch (Exception e) {
                        logger.warn("Failed to parse resource from Spring AI MCP server: {}", e.getMessage());
                    }
                }
                
                return resources;
            });
    }
    
    @Override
    public Mono<McpResourceContent> readResource(String uri) {
        return sendRequest("resources/read", Map.of("uri", uri))
            .map(response -> {
                if (response.has("error")) {
                    String errorMsg = response.get("error").get("message").asText();
                    return new McpResourceContent(uri, "text/plain", "Error: " + errorMsg, null);
                }
                
                JsonNode result = response.get("result");
                if (result == null) {
                    return new McpResourceContent(uri, "text/plain", "No content", null);
                }
                
                try {
                    return objectMapper.convertValue(result, McpResourceContent.class);
                } catch (Exception e) {
                    return new McpResourceContent(uri, "text/plain", "Error parsing content", null);
                }
            });
    }
    
    @Override
    public Mono<McpMessage> sendMessage(McpMessage message) {
        // Convert McpMessage to direct JSON request for Spring AI
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", message.id());
        request.put("method", message.method());
        if (message.params() != null) {
            request.put("params", message.params());
        }
        
        return sendJsonRequest(request)
            .map(response -> {
                // Convert JSON response back to McpMessage
                Object id = response.has("id") ? response.get("id").asLong() : null;
                
                if (response.has("error")) {
                    McpError error = objectMapper.convertValue(response.get("error"), McpError.class);
                    return McpMessage.error(id, error);
                } else {
                    Object result = response.has("result") ? objectMapper.convertValue(response.get("result"), Object.class) : null;
                    return McpMessage.response(id, result);
                }
            });
    }
    
    /**
     * Send a JSON-RPC request to Spring AI MCP server
     */
    private Mono<JsonNode> sendRequest(String method, Object params) {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", generateRequestId());
        request.put("method", method);
        if (params != null) {
            request.put("params", params);
        }
        
        return sendJsonRequest(request);
    }
    
    /**
     * Send a JSON-RPC notification to Spring AI MCP server
     */
    private Mono<Void> sendNotification(String method, Object params) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null) {
            notification.put("params", params);
        }
        
        return Mono.fromRunnable(() -> {
            try {
                String jsonStr = objectMapper.writeValueAsString(notification);
                synchronized (processStdin) {
                    processStdin.write(jsonStr);
                    processStdin.write("\n");
                    processStdin.flush();
                }
                logger.debug("Sent notification to Spring AI MCP server: {}", method);
            } catch (Exception e) {
                throw new RuntimeException("Failed to send notification", e);
            }
        });
    }
    
    /**
     * Send raw JSON request and wait for response
     */
    private Mono<JsonNode> sendJsonRequest(Map<String, Object> request) {
        return Mono.fromCallable(() -> {
            if (process == null || !process.isAlive()) {
                throw new RuntimeException("Spring AI MCP server process is not running");
            }
            
            Long requestId = ((Number) request.get("id")).longValue();
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            
            // Enhanced request monitoring and timeout handling
            long timeoutMs = Math.max(config.timeout(), 15000);
            ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(() -> {
                CompletableFuture<JsonNode> expiredFuture = pendingRequests.remove(requestId);
                if (expiredFuture != null && !expiredFuture.isDone()) {
                    logger.warn("Request {} timed out after {}ms for Spring AI MCP server: {}", requestId, timeoutMs, serverId);
                    expiredFuture.completeExceptionally(new RuntimeException(
                        String.format("Request timed out after %dms for Spring AI MCP server: %s", timeoutMs, serverId)));
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);
            
            // Store future with timeout cancellation
            future.whenComplete((result, throwable) -> {
                timeoutTask.cancel(false);
                if (throwable != null) {
                    logger.debug("Request {} completed with error for Spring AI MCP server {}: {}", requestId, serverId, throwable.getMessage());
                } else {
                    logger.debug("Request {} completed successfully for Spring AI MCP server: {}", requestId, serverId);
                }
            });
            
            pendingRequests.put(requestId, future);
            
            try {
                String jsonStr = objectMapper.writeValueAsString(request);
                logger.debug("Sending request to Spring AI MCP server: {}", jsonStr);
                
                synchronized (processStdin) {
                    processStdin.write(jsonStr);
                    processStdin.write("\n");
                    processStdin.flush();
                }
                
                // Enhanced error handling for failed requests
                try {
                    JsonNode response = future.get();
                    logger.debug("Received response from Spring AI MCP server: {}", response);
                    return response;
                } catch (Exception e) {
                    logger.error("Failed to get response for request {} from Spring AI MCP server {}: {}", requestId, serverId, e.getMessage());
                    throw new RuntimeException("Failed to send request to Spring AI MCP server: " + e.getMessage(), e);
                }
                
            } catch (Exception e) {
                pendingRequests.remove(requestId);
                logger.error("Exception during request processing for Spring AI MCP server {}: {}", serverId, e.getMessage(), e);
                throw new RuntimeException("Failed to send request to Spring AI MCP server: " + e.getMessage(), e);
            }
        });
    }
    
    private void readMessages() {
        try {
            String line;
            while ((line = processStdout.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                try {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    
                    JsonNode message = objectMapper.readTree(line);
                    logger.debug("Received message from Spring AI MCP server: {}", message);
                    
                    if (message.has("id")) {
                        // Enhanced response message handling
                        Long requestId = message.get("id").asLong();
                        CompletableFuture<JsonNode> future = pendingRequests.remove(requestId);
                        if (future != null) {
                            if (message.has("error")) {
                                JsonNode error = message.get("error");
                                String errorMsg = error.has("message") ? error.get("message").asText() : "Unknown error";
                                logger.warn("Received error response for request {} from Spring AI MCP server {}: {}", requestId, serverId, errorMsg);
                                future.completeExceptionally(new RuntimeException("MCP server error: " + errorMsg));
                            } else {
                                future.complete(message);
                            }
                        } else {
                            logger.warn("Received response for unknown request {} from Spring AI MCP server: {}", requestId, serverId);
                        }
                    } else if (message.has("method")) {
                        // Notification message - convert to McpMessage
                        String method = message.get("method").asText();
                        Object params = message.has("params") ? 
                            objectMapper.convertValue(message.get("params"), Object.class) : null;
                        
                        McpMessage mcpNotification = McpMessage.notification(method, params);
                        notificationSink.tryEmitNext(mcpNotification);
                    }
                    
                } catch (Exception e) {
                    logger.warn("Failed to process message from Spring AI MCP server: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Error reading from Spring AI MCP server process: {}", e.getMessage());
        }
    }
    
    private void readStderr(InputStream stderr) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                logger.info("[{}][stderr] {}", serverId, line);
            }
        } catch (IOException e) {
            logger.debug("Stderr reader for {} ended: {}", serverId, e.getMessage());
        }
    }
    
    @Override
    public Flux<McpMessage> notifications() {
        return notificationSink.asFlux();
    }
    
    @Override
    public Mono<Void> close() {
        return Mono.fromRunnable(() -> {
            logger.info("Closing Spring AI MCP server connection: {}", serverId);
            
            // Enhanced connection cleanup and resource management
            try {
                // Cancel all pending requests with proper error handling
                int pendingCount = pendingRequests.size();
                if (pendingCount > 0) {
                    logger.info("Cancelling {} pending requests for Spring AI MCP server: {}", pendingCount, serverId);
                    pendingRequests.values().forEach(future -> {
                        if (!future.isDone()) {
                            future.completeExceptionally(new RuntimeException(
                                "Connection closed while request was pending for server: " + serverId));
                        }
                    });
                    pendingRequests.clear();
                }
                
                // Shutdown timeout executor
                if (timeoutExecutor != null && !timeoutExecutor.isShutdown()) {
                    logger.debug("Shutting down timeout executor for Spring AI MCP server: {}", serverId);
                    timeoutExecutor.shutdown();
                    try {
                        if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                            logger.warn("Timeout executor did not terminate gracefully for server: {}", serverId);
                            timeoutExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        timeoutExecutor.shutdownNow();
                    }
                }
                
                // Interrupt threads safely
                if (readerThread != null && readerThread.isAlive()) {
                    logger.debug("Interrupting reader thread for Spring AI MCP server: {}", serverId);
                    readerThread.interrupt();
                    try {
                        readerThread.join(2000); // Wait up to 2 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                if (stderrThread != null && stderrThread.isAlive()) {
                    logger.debug("Interrupting stderr thread for Spring AI MCP server: {}", serverId);
                    stderrThread.interrupt();
                    try {
                        stderrThread.join(2000); // Wait up to 2 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Close I/O streams with proper error handling
                if (processStdin != null) {
                    try {
                        processStdin.close();
                        logger.debug("Process stdin closed for Spring AI MCP server: {}", serverId);
                    } catch (IOException e) {
                        logger.warn("Error closing process stdin for server {}: {}", serverId, e.getMessage());
                    }
                }
                
                if (processStdout != null) {
                    try {
                        processStdout.close();
                        logger.debug("Process stdout closed for Spring AI MCP server: {}", serverId);
                    } catch (IOException e) {
                        logger.warn("Error closing process stdout for server {}: {}", serverId, e.getMessage());
                    }
                }
                
                // Terminate process with enhanced error handling
                if (process != null && process.isAlive()) {
                    logger.debug("Terminating process for Spring AI MCP server: {}", serverId);
                    process.destroy();
                    try {
                        if (!process.waitFor(10, TimeUnit.SECONDS)) {
                            logger.warn("Process did not terminate gracefully for server {}, forcing termination", serverId);
                            process.destroyForcibly();
                            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                                logger.error("Failed to forcibly terminate process for server: {}", serverId);
                            }
                        } else {
                            logger.debug("Process terminated successfully for Spring AI MCP server: {}", serverId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Interrupted while waiting for process termination for server: {}", serverId);
                        process.destroyForcibly();
                    }
                }
                
                // Complete notification sink
                notificationSink.tryEmitComplete();
                logger.info("Spring AI MCP server connection closed successfully: {}", serverId);
                
            } catch (Exception e) {
                logger.error("Error during connection cleanup for Spring AI MCP server {}: {}", serverId, e.getMessage(), e);
            }
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
}
