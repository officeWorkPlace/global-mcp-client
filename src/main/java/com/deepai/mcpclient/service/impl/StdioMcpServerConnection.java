package com.deepai.mcpclient.service.impl;

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
    private final Map<Long, Long> requestTimestamps = new ConcurrentHashMap<>();
    private final Sinks.Many<McpMessage> notificationSink = Sinks.many().multicast().onBackpressureBuffer();
    private final ScheduledExecutorService cleanupExecutor;
    
    private Process process;
    private BufferedOutputStream processStdin;
    private BufferedInputStream processStdout;
    private BufferedReader processStdoutReader;
    private Thread readerThread;
    private Thread stderrThread;
    
    public StdioMcpServerConnection(String serverId, ServerConfig config, ObjectMapper objectMapper) {
        this.serverId = serverId;
        this.config = config;
        this.objectMapper = objectMapper;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "stdio-cleanup-" + serverId));
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
            // Ensure we do not merge stderr into stdout; logs should go to stderr
            processBuilder.redirectErrorStream(false);
            
            process = processBuilder.start();
            processStdin = new BufferedOutputStream(process.getOutputStream());
            processStdout = new BufferedInputStream(process.getInputStream());
            processStdoutReader = new BufferedReader(new InputStreamReader(processStdout, StandardCharsets.UTF_8));
            
            // Start reader thread
            readerThread = new Thread(this::readMessages, "stdio-reader-" + serverId);
            readerThread.setDaemon(true);
            readerThread.start();

            // Start stderr logger thread to capture server logs
            stderrThread = new Thread(() -> readStderr(process.getErrorStream()), "stdio-stderr-" + serverId);
            stderrThread.setDaemon(true);
            stderrThread.start();
            
            logger.info("Stdio process started for server: {}", serverId);
            return null;
        })
        .then(attemptInitialize(2, Duration.ofMillis(500)))
        .then(warmupConnection())
        .doOnSuccess(info -> logger.info("Connected and warmed up stdio server: {}", serverId))
        .doOnError(error -> logger.error("Failed to initialize stdio connection to {}: {}", serverId, error.getMessage()))
        .then();
    }

    private Mono<McpServerInfo> attemptInitialize(int attempts, Duration delay) {
        // Wait for the MCP server process to fully start up, then get real server info
        return Mono.delay(delay)
            .then(performMcpInitialization())
            .onErrorReturn(new McpServerInfo(
                serverId, 
                "unknown",
                "MCP Server via stdio",
                null,
                "Unknown",
                null
            ));
    }
    
    @Override
    public Mono<Boolean> isHealthy() {
        if (process == null || !process.isAlive()) {
            logger.debug("Health check failed for {}: process not alive", serverId);
            return Mono.just(false);
        }
        
        // Ensure initialization is complete before health check
        return ensureInitialized()
            .then(sendMessage(McpMessage.request(generateRequestId(), "tools/list", Map.of())))
            .map(response -> {
                boolean healthy = response.error() == null && response.result() != null;
                logger.debug("Health check for {}: {}", serverId, healthy);
                return healthy;
            })
            .timeout(java.time.Duration.ofSeconds(10)) // Increased timeout to allow for initialization
            .doOnError(error -> logger.debug("Health check error for {}: {}", serverId, error.getMessage()))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<McpServerInfo> getServerInfo() {
        // Use the cached server info from initialization if available
        if (serverInfo != null) {
            return Mono.just(serverInfo);
        }
        
        // Otherwise perform fresh initialization
        return performMcpInitialization();
    }
    
    private McpServerInfo serverInfo;
    private volatile boolean initializationComplete = false;
    private final Object initializationLock = new Object();
    
    /**
     * Check if initialization is complete and wait if necessary
     */
    private Mono<Void> ensureInitialized() {
        if (initializationComplete) {
            return Mono.empty();
        }
        
        return Mono.defer(() -> {
            synchronized (initializationLock) {
                if (initializationComplete) {
                    return Mono.empty();
                }
                
                logger.debug("Waiting for MCP initialization to complete for {}", serverId);
                // Perform initialization if not already done
                return performMcpInitialization()
                    .then(Mono.fromRunnable(() -> {
                        synchronized (initializationLock) {
                            initializationComplete = true;
                            logger.info("MCP initialization marked complete for {}", serverId);
                        }
                    }))
                    .onErrorResume(error -> {
                        logger.error("Failed to complete MCP initialization for {}: {}", serverId, error.getMessage());
                        return Mono.error(new RuntimeException("MCP server not properly initialized: " + error.getMessage()));
                    })
                    .then();
            }
        });
    }
    
    /**
     * Perform MCP initialization and extract real server info
     */
    private Mono<McpServerInfo> performMcpInitialization() {
        Map<String, Object> initializeParams = Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(
                "roots", Map.of("listChanged", true),
                "sampling", Map.of()
            ),
            "clientInfo", Map.of(
                "name", "global-mcp-client", 
                "version", "1.0.0"
            )
        );
        
        return sendMessageDirect(McpMessage.request(generateRequestId(), "initialize", initializeParams))
            .flatMap(response -> {
                if (response.result() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = (Map<String, Object>) response.result();
                        
                        // Extract server info from response
                        @SuppressWarnings("unchecked")
                        Map<String, Object> serverInfoMap = (Map<String, Object>) result.get("serverInfo");
                        
                        McpServerInfo info;
                        if (serverInfoMap != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> metadata = (Map<String, Object>) serverInfoMap.get("metadata");
                            info = new McpServerInfo(
                                (String) serverInfoMap.get("name"),
                                (String) serverInfoMap.get("version"),
                                (String) serverInfoMap.get("description"),
                                null, // capabilities extracted separately
                                (String) serverInfoMap.get("vendor"),
                                metadata
                            );
                        } else {
                            // Fallback for servers that don't provide serverInfo
                            info = new McpServerInfo(
                                serverId, 
                                "1.0.0",
                                "MCP Server via stdio",
                                null,
                                "Unknown",
                                null
                            );
                        }
                        
                        // Cache the server info
                        this.serverInfo = info;
                        
                        // CRITICAL: Send initialized notification as required by MCP spec
                        return sendNotificationDirect("notifications/initialized", Map.of())
                            .then(Mono.just(info))
                            .doOnSuccess(v -> logger.debug("MCP initialization completed for {}: {}", 
                                serverId, info.name()));
                        
                    } catch (Exception e) {
                        logger.warn("Failed to parse server info from {}: {}", serverId, e.getMessage());
                        McpServerInfo fallbackInfo = new McpServerInfo(serverId, "unknown", null, null, null, null);
                        this.serverInfo = fallbackInfo;
                        return Mono.just(fallbackInfo);
                    }
                }
                return Mono.error(new RuntimeException("Failed to get server info: " + 
                    (response.error() != null ? response.error().message() : "Unknown error")));
            });
    }
    
    @Override
    public Mono<List<McpTool>> listTools() {
        return ensureInitialized()
            .then(sendMessage(McpMessage.request(generateRequestId(), "tools/list", Map.of())))
            .map(response -> {
                logger.debug("Tools list response from {}: {}", serverId, response);
                if (response.result() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = objectMapper.convertValue(response.result(), Map.class);
                        logger.debug("Parsed result: {}", result);
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
                        if (tools == null) {
                            logger.warn("No 'tools' field found in response from {}", serverId);
                            return List.of();
                        }
                        logger.debug("Found {} tools from {}", tools.size(), serverId);
                        return tools.stream()
                            .map(tool -> objectMapper.convertValue(tool, McpTool.class))
                            .toList();
                    } catch (Exception e) {
                        logger.error("Failed to parse tools list from {}: {}", serverId, e.getMessage(), e);
                        throw new RuntimeException("Failed to parse tools list: " + e.getMessage(), e);
                    }
                }
                logger.error("Failed to list tools from {}: {}", serverId, response.error());
                throw new RuntimeException("Failed to list tools: " + (response.error() != null ? response.error().message() : "Unknown error"));
            });
    }
    
    @Override
    public Mono<McpToolResult> executeTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = Map.of(
            "name", toolName,
            "arguments", arguments != null ? arguments : Map.of()
        );
        
        return ensureInitialized()
            .then(sendMessage(McpMessage.request(generateRequestId(), "tools/call", params)))
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
    return sendMessage(McpMessage.request(generateRequestId(), "resources/list", Map.of()))
            .map(response -> {
                if (response.result() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = objectMapper.convertValue(response.result(), Map.class);
                        @SuppressWarnings("unchecked")
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
        return sendMessageWithRetry(message, 2);
    }
    
    private Mono<McpMessage> sendMessageWithRetry(McpMessage message, int maxRetries) {
        if (process == null || !process.isAlive()) {
            return Mono.error(new RuntimeException("Process is not running"));
        }
        
        return Mono.fromCallable(() -> {
            Long requestId = (Long) message.id();
            CompletableFuture<McpMessage> future = new CompletableFuture<>();
            
            // Enhanced request management with monitoring
            long currentTime = System.currentTimeMillis();
            pendingRequests.put(requestId, future);
            requestTimestamps.put(requestId, currentTime);
            
            // Add completion callback for cleanup
            future.whenComplete((result, throwable) -> {
                requestTimestamps.remove(requestId);
                if (throwable != null) {
                    logger.debug("Request {} completed with error for stdio server {}: {}", requestId, serverId, throwable.getMessage());
                } else {
                    logger.debug("Request {} completed successfully for stdio server: {}", requestId, serverId);
                }
            });
            
            try {
                byte[] body = objectMapper.writeValueAsBytes(message);
                logger.debug("Sending stdio message to {}: {} (attempt: {})", serverId, message.method(), 2 - maxRetries + 1);
                writeNdjson(body);
                
                // Enhanced connection lifecycle management with proper error handling
                try {
                    long timeoutMs = config.timeout();
                    McpMessage response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    logger.debug("Received stdio response from {}: {}", serverId, response.id());
                    return response;
                } catch (java.util.concurrent.TimeoutException e) {
                    logger.warn("Request {} timed out after {}ms for stdio server: {}", requestId, config.timeout(), serverId);
                    throw new RuntimeException(String.format("Request timed out after %dms for stdio server: %s", config.timeout(), serverId), e);
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    logger.error("Request {} failed for stdio server {}: {}", requestId, serverId, cause != null ? cause.getMessage() : e.getMessage());
                    throw new RuntimeException("Request execution failed for stdio server: " + serverId, cause != null ? cause : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Request {} interrupted for stdio server: {}", requestId, serverId);
                    throw new RuntimeException("Request interrupted for stdio server: " + serverId, e);
                }
            } catch (Exception e) {
                pendingRequests.remove(requestId);
                requestTimestamps.remove(requestId);
                logger.error("Exception during message sending for stdio server {}: {}", serverId, e.getMessage(), e);
                throw new RuntimeException("Failed to send message to stdio server: " + serverId + " - " + e.getMessage(), e);
            }
        })
        .onErrorResume(error -> {
            if (maxRetries > 1) {
                logger.debug("Message failed for {}, retrying... (attempts left: {}): {}", serverId, maxRetries - 1, error.getMessage());
                // Reduced retry delay for faster response
                return Mono.delay(Duration.ofMillis(100))
                          .then(sendMessageWithRetry(message, maxRetries - 1));
            }
            logger.debug("Message failed for {} after all retries: {}", serverId, error.getMessage());
            return Mono.error(error);
        });
    }
    
    /**
     * Warm up the connection by properly initializing MCP protocol
     */
    private Mono<Void> warmupConnection() {
        logger.info("Warming up MCP connection to {}", serverId);
        
        // Wait additional time for the MCP server to fully initialize
        return Mono.delay(Duration.ofSeconds(5))
            .then(performMcpHandshake())
            .retryWhen(reactor.util.retry.Retry.fixedDelay(3, Duration.ofSeconds(2))
                .doBeforeRetry(retrySignal -> 
                    logger.debug("Retrying MCP handshake for {} (attempt {})", serverId, retrySignal.totalRetries() + 1)))
            .doOnSuccess(v -> logger.info("MCP handshake completed successfully for {}", serverId))
            .onErrorResume(error -> {
                logger.warn("MCP handshake failed for {} after all retries: {}. Connection may still work for actual requests.", 
                    serverId, error.getMessage());
                return Mono.empty();
            });
    }
    
    /**
     * Perform MCP protocol handshake (used during warmup)
     */
    private Mono<Void> performMcpHandshake() {
        logger.debug("Performing MCP handshake with {}", serverId);
        
        // Use the same initialization logic and mark as complete when successful
        return performMcpInitialization()
            .doOnSuccess(info -> {
                logger.debug("MCP handshake completed for {}: {}", serverId, info.name());
                synchronized (initializationLock) {
                    initializationComplete = true;
                    logger.info("MCP initialization marked complete during warmup for {}", serverId);
                }
            })
            .then();
    }
    
    /**
     * Send message directly without retries (used for handshake)
     */
    private Mono<McpMessage> sendMessageDirect(McpMessage message) {
        if (process == null || !process.isAlive()) {
            return Mono.error(new RuntimeException("Process is not running"));
        }
        
        return Mono.fromCallable(() -> {
            Long requestId = (Long) message.id();
            CompletableFuture<McpMessage> future = new CompletableFuture<>();
            
            // Enhanced request management for direct messages
            long currentTime = System.currentTimeMillis();
            pendingRequests.put(requestId, future);
            requestTimestamps.put(requestId, currentTime);
            
            // Add completion callback for cleanup
            future.whenComplete((result, throwable) -> {
                requestTimestamps.remove(requestId);
                if (throwable != null) {
                    logger.debug("Direct request {} completed with error for stdio server {}: {}", requestId, serverId, throwable.getMessage());
                } else {
                    logger.debug("Direct request {} completed successfully for stdio server: {}", requestId, serverId);
                }
            });
            
            try {
                byte[] body = objectMapper.writeValueAsBytes(message);
                logger.debug("Sending direct MCP message to {}: {}", serverId, message.method());
                writeNdjson(body);
                
                // Enhanced error handling for direct messages
                try {
                    long timeoutMs = Math.max(config.timeout(), 10000);
                    McpMessage response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    logger.debug("Received direct MCP response from {}: {}", serverId, response.id());
                    return response;
                } catch (java.util.concurrent.TimeoutException e) {
                    long actualTimeout = Math.max(config.timeout(), 10000);
                    logger.warn("Direct request {} timed out after {}ms for stdio server: {}", requestId, actualTimeout, serverId);
                    throw new RuntimeException(String.format("Direct request timed out after %dms for stdio server: %s", actualTimeout, serverId), e);
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    logger.error("Direct request {} failed for stdio server {}: {}", requestId, serverId, cause != null ? cause.getMessage() : e.getMessage());
                    throw new RuntimeException("Direct request execution failed for stdio server: " + serverId, cause != null ? cause : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Direct request {} interrupted for stdio server: {}", requestId, serverId);
                    throw new RuntimeException("Direct request interrupted for stdio server: " + serverId, e);
                }
            } catch (Exception e) {
                pendingRequests.remove(requestId);
                requestTimestamps.remove(requestId);
                logger.error("Exception during direct message sending for stdio server {}: {}", serverId, e.getMessage(), e);
                throw new RuntimeException("Failed to send direct message to stdio server: " + serverId + " - " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Send notification directly without retries
     */
    private Mono<Void> sendNotificationDirect(String method, Object params) {
        if (process == null || !process.isAlive()) {
            return Mono.error(new RuntimeException("Process is not running"));
        }
        
        return Mono.fromRunnable(() -> {
            try {
                McpMessage notification = McpMessage.notification(method, params);
                byte[] body = objectMapper.writeValueAsBytes(notification);
                logger.debug("Sending direct MCP notification to {}: {}", serverId, method);
                writeNdjson(body);
            } catch (Exception e) {
                throw new RuntimeException("Failed to send direct notification: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Send a notification (no response expected)
     */
    private Mono<Void> sendNotification(String method, Object params) {
        if (process == null || !process.isAlive()) {
            return Mono.error(new RuntimeException("Process is not running"));
        }
        
        return Mono.fromRunnable(() -> {
            try {
                McpMessage notification = McpMessage.notification(method, params);
                byte[] body = objectMapper.writeValueAsBytes(notification);
                logger.debug("Sending stdio notification to {}: {}", serverId, method);
                
                writeNdjson(body);
            } catch (Exception e) {
                throw new RuntimeException("Failed to send notification", e);
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
            
            // Enhanced connection cleanup with proper resource management
            try {
                // Start cleanup timer monitoring
                scheduleCleanupMonitoring();
                
                // Cancel all pending requests with proper error handling
                int pendingCount = pendingRequests.size();
                if (pendingCount > 0) {
                    logger.info("Cancelling {} pending requests for stdio server: {}", pendingCount, serverId);
                    pendingRequests.values().forEach(future -> {
                        if (!future.isDone()) {
                            future.completeExceptionally(new RuntimeException(
                                "Connection closed while request was pending for stdio server: " + serverId));
                        }
                    });
                    pendingRequests.clear();
                    requestTimestamps.clear();
                }
                
                // Shutdown cleanup executor
                if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
                    logger.debug("Shutting down cleanup executor for stdio server: {}", serverId);
                    cleanupExecutor.shutdown();
                    try {
                        if (!cleanupExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                            logger.warn("Cleanup executor did not terminate gracefully for stdio server: {}", serverId);
                            cleanupExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cleanupExecutor.shutdownNow();
                    }
                }
                
                // Interrupt threads safely
                if (readerThread != null && readerThread.isAlive()) {
                    logger.debug("Interrupting reader thread for stdio server: {}", serverId);
                    readerThread.interrupt();
                    try {
                        readerThread.join(3000); // Wait up to 3 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                if (stderrThread != null && stderrThread.isAlive()) {
                    logger.debug("Interrupting stderr thread for stdio server: {}", serverId);
                    stderrThread.interrupt();
                    try {
                        stderrThread.join(3000); // Wait up to 3 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Close I/O streams with proper error handling
                if (processStdin != null) {
                    try {
                        processStdin.close();
                        logger.debug("Process stdin closed for stdio server: {}", serverId);
                    } catch (IOException e) {
                        logger.warn("Error closing process stdin for stdio server {}: {}", serverId, e.getMessage());
                    }
                }
                
                if (processStdout != null) {
                    try {
                        processStdout.close();
                        logger.debug("Process stdout closed for stdio server: {}", serverId);
                    } catch (IOException e) {
                        logger.warn("Error closing process stdout for stdio server {}: {}", serverId, e.getMessage());
                    }
                }
                
                if (processStdoutReader != null) {
                    try {
                        processStdoutReader.close();
                        logger.debug("Process stdout reader closed for stdio server: {}", serverId);
                    } catch (IOException e) {
                        logger.warn("Error closing process stdout reader for stdio server {}: {}", serverId, e.getMessage());
                    }
                }
                
                // Terminate process with enhanced error handling
                if (process != null && process.isAlive()) {
                    logger.debug("Terminating process for stdio server: {}", serverId);
                    process.destroy();
                    try {
                        if (!process.waitFor(8, TimeUnit.SECONDS)) {
                            logger.warn("Process did not terminate gracefully for stdio server {}, forcing termination", serverId);
                            process.destroyForcibly();
                            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                                logger.error("Failed to forcibly terminate process for stdio server: {}", serverId);
                            }
                        } else {
                            logger.debug("Process terminated successfully for stdio server: {}", serverId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Interrupted while waiting for process termination for stdio server: {}", serverId);
                        process.destroyForcibly();
                    }
                }
                
                // Complete notification sink
                notificationSink.tryEmitComplete();
                logger.info("Stdio connection closed successfully for server: {}", serverId);
                
            } catch (Exception e) {
                logger.error("Error during connection cleanup for stdio server {}: {}", serverId, e.getMessage(), e);
            }
        });
    }
    
    /**
     * Schedule periodic cleanup monitoring for pending requests
     */
    private void scheduleCleanupMonitoring() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.scheduleWithFixedDelay(() -> {
                try {
                    long currentTime = System.currentTimeMillis();
                    long timeoutThreshold = config.timeout() * 2; // Double the configured timeout
                    
                    requestTimestamps.entrySet().removeIf(entry -> {
                        long requestTime = entry.getValue();
                        long requestAge = currentTime - requestTime;
                        
                        if (requestAge > timeoutThreshold) {
                            Long requestId = entry.getKey();
                            CompletableFuture<McpMessage> future = pendingRequests.remove(requestId);
                            if (future != null && !future.isDone()) {
                                logger.warn("Cleaning up stale request {} (age: {}ms) for stdio server: {}", 
                                    requestId, requestAge, serverId);
                                future.completeExceptionally(new RuntimeException(
                                    "Request expired after " + requestAge + "ms for stdio server: " + serverId));
                            }
                            return true; // Remove from timestamps map
                        }
                        return false; // Keep in timestamps map
                    });
                } catch (Exception e) {
                    logger.debug("Error during cleanup monitoring for stdio server {}: {}", serverId, e.getMessage());
                }
            }, 30, 30, TimeUnit.SECONDS); // Check every 30 seconds
        }
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
            while (!Thread.currentThread().isInterrupted()) {
                McpMessage message = readFramedMessage();
                if (message == null) {
                    continue; // skip malformed/empty
                }
                try {
                    // Enhanced message processing error handling
                    if (message.isResponse() && message.id() instanceof Number) {
                        Long requestId = ((Number) message.id()).longValue();
                        CompletableFuture<McpMessage> future = pendingRequests.remove(requestId);
                        requestTimestamps.remove(requestId);
                        
                        if (future != null) {
                            if (message.error() != null) {
                                String errorMsg = message.error().message() != null ? message.error().message() : "Unknown MCP error";
                                logger.warn("Received error response for request {} from stdio server {}: {}", requestId, serverId, errorMsg);
                                future.completeExceptionally(new RuntimeException("MCP server error: " + errorMsg));
                            } else {
                                future.complete(message);
                            }
                        } else {
                            logger.warn("Received response for unknown request {} from stdio server: {}", requestId, serverId);
                        }
                    } else if (message.isNotification()) {
                        try {
                            notificationSink.tryEmitNext(message);
                        } catch (Exception e) {
                            logger.warn("Failed to emit notification from stdio server {}: {}", serverId, e.getMessage());
                        }
                    } else {
                        logger.debug("Received unexpected message type from stdio server {}: {}", serverId, message.getClass().getSimpleName());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to process message from stdio server {}: {}", serverId, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading from stdio process {}: {}", serverId, e.getMessage());
        }
    }

    /**
     * Write a JSON message with MCP framing headers to the child process stdin.
     */
    private void writeNdjson(byte[] body) throws IOException {
        // NDJSON framing: write JSON body followed by a newline
        synchronized (processStdin) {
            processStdin.write(body);
            processStdin.write('\n');
            processStdin.flush();
        }
    }

    /**
     * Read one framed JSON message from the child process stdout.
     * Returns null when stream is closed.
     * Enhanced to handle mixed JSON/non-JSON output robustly.
     */
    private McpMessage readFramedMessage() throws IOException {
        String line;
        int maxRetries = 10; // Prevent infinite loops
        int retries = 0;
        
        while (retries < maxRetries) {
            line = processStdoutReader.readLine();
            if (line == null) {
                return null; // stream closed
            }
            
            // Tolerate stray CR
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
            }
            
            line = line.trim();
            if (line.isEmpty()) {
                retries++;
                continue; // Skip empty lines
            }
            
            // Ultra-robust filtering: Only process lines that look like valid JSON
            if (!looksLikeValidJson(line)) {
                logger.debug("Filtering non-JSON line from {}: {}", serverId, 
                    line.length() > 80 ? line.substring(0, 80) + "..." : line);
                retries++;
                continue;
            }
            
            // Attempt to parse as JSON
            try {
                McpMessage message = objectMapper.readValue(line, McpMessage.class);
                logger.debug("Successfully parsed MCP message from {}", serverId);
                return message;
            } catch (Exception ex) {
                // If parsing fails, check if it might be a valid MCP message we're missing
                if (mightBeValidMcpMessage(line)) {
                    logger.warn("Potential MCP message parsing failed from {}: {} | Content: {}", 
                        serverId, ex.getMessage(), 
                        line.length() > 150 ? line.substring(0, 150) + "..." : line);
                } else {
                    logger.debug("Non-MCP JSON content from {}: {}", serverId, 
                        line.length() > 80 ? line.substring(0, 80) + "..." : line);
                }
                retries++;
                continue;
            }
        }
        
        logger.debug("Max retries exceeded reading from {}, skipping batch", serverId);
        return null;
    }
    
    /**
     * Ultra-robust check if a line looks like valid JSON that we should attempt to parse
     */
    private boolean looksLikeValidJson(String line) {
        // Must start and end with proper JSON delimiters
        if (!((line.startsWith("{") && line.endsWith("}")) || 
              (line.startsWith("[") && line.endsWith("]")))){
            return false;
        }
        
        // Quick validation: balanced brackets/braces
        if (!hasBalancedDelimiters(line)) {
            return false;
        }
        
        // Check for JSON-like structure (contains colons for key-value pairs)
        if (line.startsWith("{") && !line.contains(":")) {
            return false;
        }
        
        // Filter out obvious non-JSON patterns
        if (containsObviousNonJsonPatterns(line)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if delimiters are balanced
     */
    private boolean hasBalancedDelimiters(String line) {
        int braces = 0;
        int brackets = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (char c : line.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                switch (c) {
                    case '{': braces++; break;
                    case '}': braces--; break;
                    case '[': brackets++; break;
                    case ']': brackets--; break;
                }
            }
        }
        
        return braces == 0 && brackets == 0;
    }
    
    /**
     * Check for patterns that are obviously not JSON
     */
    private boolean containsObviousNonJsonPatterns(String line) {
        // Unquoted special characters that shouldn't be in JSON
        return containsUnquotedSpecialChars(line) ||
               line.contains("=== ") ||
               line.contains("Happy data managing!") ||
               line.contains("?") && !line.contains("\"?\"");
    }
    
    /**
     * Check if a line contains non-JSON content that should be filtered out
     * Enhanced to handle ANY type of non-JSON output from MCP servers
     */
    private boolean isNonJsonContent(String line) {
        // Must start with { or [ to be potentially valid JSON
        if (!line.startsWith("{") && !line.startsWith("[")) {
            return true;
        }
        
        // Filter out lines that contain unescaped special characters typically not in JSON
        if (containsInvalidJsonCharacters(line)) {
            return true;
        }
        
        // Additional validation: check if it's likely valid JSON structure
        if (!isLikelyValidJson(line)) {
            return true;
        }
        
        // Skip lines with log-like patterns even if they start with {
        if (containsLogPatterns(line)) {
            return true;
        }
        
        // Skip lines with timestamps in JSON-like format but are actually logs
        if (containsTimestampPatterns(line)) {
            return true;
        }
        
        // Skip application startup messages in JSON-like format
        if (containsStartupPatterns(line)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check for characters that indicate non-JSON content
     */
    private boolean containsInvalidJsonCharacters(String line) {
        // These patterns indicate output that's not proper JSON
        
        // Question marks and equals signs outside of quoted strings usually indicate configuration/log output
        if (containsUnquotedSpecialChars(line)) {
            return true;
        }
        
        // Check for obvious non-JSON patterns
        if (line.contains("=== ") || line.contains(" === ") || 
            line.contains("### ") || line.contains(" ### ")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check for unquoted special characters that shouldn't appear in JSON
     */
    private boolean containsUnquotedSpecialChars(String line) {
        // Simple heuristic: if we see ? or = that aren't inside quoted strings, it's likely not JSON
        boolean inQuotes = false;
        boolean escaped = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            
            // If we're not in quotes and see these characters, it's likely not JSON
            if (!inQuotes && (c == '?' || (c == '=' && i + 1 < line.length() && line.charAt(i + 1) != '='))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Quick heuristic check if a line is likely valid JSON
     */
    private boolean isLikelyValidJson(String line) {
        // Basic structure validation
        if (line.startsWith("{")) {
            // JSON object should have matching braces
            long openBraces = line.chars().filter(ch -> ch == '{').count();
            long closeBraces = line.chars().filter(ch -> ch == '}').count();
            if (openBraces != closeBraces) {
                return false;
            }
            
            // Should contain key-value patterns for JSON objects
            if (!line.contains(":") || (!line.contains("\"") && !line.contains("'"))) {
                return false;
            }
        }
        
        if (line.startsWith("[")) {
            // JSON array should have matching brackets
            long openBrackets = line.chars().filter(ch -> ch == '[').count();
            long closeBrackets = line.chars().filter(ch -> ch == ']').count();
            if (openBrackets != closeBrackets) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check for log patterns that might be formatted as JSON-like strings
     */
    private boolean containsLogPatterns(String line) {
        return line.contains("INFO ") || line.contains("WARN ") || line.contains("ERROR ") ||
               line.contains("DEBUG ") || line.contains("TRACE ") ||
               line.contains(" INFO:") || line.contains(" WARN:") || line.contains(" ERROR:") ||
               line.contains("\"level\":\"INFO\"") || line.contains("\"level\":\"WARN\"") ||
               line.contains("\"level\":\"ERROR\"") || line.contains("\"level\":\"DEBUG\"");
    }
    
    /**
     * Check for timestamp patterns in various formats
     */
    private boolean containsTimestampPatterns(String line) {
        return line.matches(".*\\d{2}:\\d{2}:\\d{2}.*") ||
               line.matches(".*\\d{4}-\\d{2}-\\d{2}.*") ||
               line.matches(".*\\d{4}/\\d{2}/\\d{2}.*") ||
               line.contains("timestamp\": \"") ||
               line.contains("@timestamp\": \"");
    }
    
    /**
     * Check for application startup and configuration messages
     */
    private boolean containsStartupPatterns(String line) {
        return line.contains("Spring Boot") || line.contains("Started ") ||
               line.contains("JVM running") || line.contains("profiles active") ||
               line.contains("Oracle") && (line.contains("version") || line.contains("detected") ||
                                           line.contains("created") || line.contains("features cached")) ||
               line.matches(".*^[=\\-*]+$.*") || line.contains("================") ||
               line.matches(".*[🔧🎯📊⚡💫🚀✨📈🔍💡📋🌟⭐🎪🎨🛡️🔒].*") ||
               line.startsWith("{\"?\"") || line.contains("Happy ") || 
               line.contains("Tool Registration") || line.contains("ServiceClient created") ||
               line.contains("Configuring") || line.contains("Registering");
    }
    
    /**
     * Check if a line might be a valid MCP message that we're incorrectly filtering
     */
    private boolean mightBeValidMcpMessage(String line) {
        // Check for MCP protocol fields
        return line.contains("\"jsonrpc\":") ||
               line.contains("\"method\":") ||
               line.contains("\"id\":") ||
               line.contains("\"result\":") ||
               line.contains("\"error\":") ||
               line.contains("\"params\":") ||
               // Common MCP methods
               line.contains("initialize") ||
               line.contains("tools/list") ||
               line.contains("tools/call") ||
               line.contains("resources/list") ||
               line.contains("resources/read") ||
               line.contains("notifications/");
    }

    private void readStderr(InputStream err) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                // Log at INFO to aid troubleshooting; server should write logs to stderr only
                logger.info("[{}][stderr] {}", serverId, line);
            }
        } catch (IOException ioe) {
            logger.debug("stderr reader for {} ended: {}", serverId, ioe.getMessage());
        }
    }
}
