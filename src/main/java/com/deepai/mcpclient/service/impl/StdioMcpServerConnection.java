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
    private BufferedOutputStream processStdin;
    private BufferedInputStream processStdout;
    private BufferedReader processStdoutReader;
    private Thread readerThread;
    private Thread stderrThread;
    
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
            pendingRequests.put(requestId, future);
            
            try {
                byte[] body = objectMapper.writeValueAsBytes(message);
                logger.debug("Sending stdio message to {}: {} (attempt: {})", serverId, message.method(), 2 - maxRetries + 1);
                writeNdjson(body);
                
                // Use configured timeout
                long timeoutMs = config.timeout();
                McpMessage response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                logger.debug("Received stdio response from {}: {}", serverId, response.id());
                return response;
            } catch (Exception e) {
                pendingRequests.remove(requestId);
                throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
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
            pendingRequests.put(requestId, future);
            
            try {
                byte[] body = objectMapper.writeValueAsBytes(message);
                logger.debug("Sending direct MCP message to {}: {}", serverId, message.method());
                writeNdjson(body);
                
                // Use longer timeout for handshake
                long timeoutMs = Math.max(config.timeout(), 10000);
                McpMessage response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                logger.debug("Received direct MCP response from {}: {}", serverId, response.id());
                return response;
            } catch (Exception e) {
                pendingRequests.remove(requestId);
                throw new RuntimeException("Failed to send direct message: " + e.getMessage(), e);
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
            
        if (readerThread != null) {
                readerThread.interrupt();
            }
            if (stderrThread != null) {
                stderrThread.interrupt();
            }
            
        if (processStdin != null) {
                try {
            processStdin.close();
                } catch (IOException e) {
                    logger.warn("Error closing process input: {}", e.getMessage());
                }
            }
            if (processStdout != null) {
                try {
                    processStdout.close();
                } catch (IOException e) {
                    logger.warn("Error closing process output: {}", e.getMessage());
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
            while (!Thread.currentThread().isInterrupted()) {
                McpMessage message = readFramedMessage();
                if (message == null) {
                    continue; // skip malformed/empty
                }
                try {
                    if (message.isResponse() && message.id() instanceof Number) {
                        Long requestId = ((Number) message.id()).longValue();
                        CompletableFuture<McpMessage> future = pendingRequests.remove(requestId);
                        if (future != null) {
                            future.complete(message);
                        }
                    } else if (message.isNotification()) {
                        notificationSink.tryEmitNext(message);
                    } else {
                        logger.debug("Received unexpected message type from {}", serverId);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to process message from {}: {}", serverId, e.getMessage());
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
     */
    private McpMessage readFramedMessage() throws IOException {
        // NDJSON mode: read one line and parse as a single JSON message
        String line = processStdoutReader.readLine();
        if (line == null) {
            return null; // stream closed
        }
        // Tolerate stray CR
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
            line = line.substring(0, line.length() - 1);
        }
        try {
            return objectMapper.readValue(line, McpMessage.class);
        } catch (Exception ex) {
            logger.warn("Failed to parse JSON from {}: {}", serverId, ex.getMessage());
            return null;
        }
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
