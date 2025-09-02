package com.deepai.mcpclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepai.mcpclient.config.McpConfigurationProperties;
import com.deepai.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.deepai.mcpclient.model.*;
import com.deepai.mcpclient.service.impl.StdioMcpServerConnection;
import com.deepai.mcpclient.service.impl.SpringAiMcpServerConnection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main MCP client service that manages connections to multiple MCP servers.
 */
@Service
public class McpClientService {
    
    private static final Logger logger = LoggerFactory.getLogger(McpClientService.class);
    
    private final McpConfigurationProperties config;
    private final ObjectMapper objectMapper;
    private final Map<String, McpServerConnection> connections = new ConcurrentHashMap<>();
    
    public McpClientService(McpConfigurationProperties config, 
                           ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing MCP client with {} configured servers", config.servers().size());
        
        config.servers().forEach((serverId, serverConfig) -> {
            if (serverConfig.enabled()) {
                try {
                    initializeServer(serverId, serverConfig)
                        .subscribe(
                            success -> logger.info("Successfully initialized server: {}", serverId),
                            error -> {
                                logger.error("Failed to initialize server {}: {}", serverId, error.getMessage());
                                // Remove failed connection from map
                                connections.remove(serverId);
                            }
                        );
                } catch (Exception e) {
                    logger.error("Error during server {} initialization: {}", serverId, e.getMessage());
                }
            } else {
                logger.info("Server {} is disabled, skipping initialization", serverId);
            }
        });
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down MCP client connections");
        
        Flux.fromIterable(connections.values())
            .flatMap(McpServerConnection::close)
            .blockLast();
        
        connections.clear();
        logger.info("All MCP connections closed");
    }
    
    /**
     * Get all configured server IDs.
     */
    public List<String> getServerIds() {
        // Return both configured and successfully connected servers
        List<String> configuredServers = config.servers().entrySet().stream()
            .filter(entry -> entry.getValue().enabled())
            .map(Map.Entry::getKey)
            .toList();
        
        logger.debug("Configured servers: {}, Connected servers: {}", configuredServers, connections.keySet());
        return configuredServers;
    }
    
    /**
     * Get server connection by ID.
     */
    public Mono<McpServerConnection> getServerConnection(String serverId) {
        McpServerConnection connection = connections.get(serverId);
        if (connection == null) {
            // Check if server is configured but not yet connected
            ServerConfig serverConfig = config.servers().get(serverId);
            if (serverConfig != null && serverConfig.enabled()) {
                return Mono.error(new RuntimeException("Server " + serverId + " is configured but not yet connected. Please wait for initialization."));
            } else {
                return Mono.error(new RuntimeException("Server not found or not enabled: " + serverId));
            }
        }
        return Mono.just(connection);
    }
    
    /**
     * Get server information.
     */
    public Mono<McpServerInfo> getServerInfo(String serverId) {
        return getServerConnection(serverId)
            .flatMap(McpServerConnection::getServerInfo);
    }
    
    /**
     * Check server health.
     */
    public Mono<Boolean> isServerHealthy(String serverId) {
        return getServerConnection(serverId)
            .flatMap(McpServerConnection::isHealthy)
            .onErrorReturn(false);
    }
    
    /**
     * List available tools from a server.
     */
    public Mono<List<McpTool>> listTools(String serverId) {
        return getServerConnection(serverId)
            .flatMap(McpServerConnection::listTools);
    }
    
    /**
     * Execute a tool on a specific server.
     */
    public Mono<McpToolResult> executeTool(String serverId, String toolName, Map<String, Object> arguments) {
        return getServerConnection(serverId)
            .flatMap(connection -> connection.executeTool(toolName, arguments));
    }
    
    /**
     * List available resources from a server.
     */
    public Mono<List<McpResource>> listResources(String serverId) {
        return getServerConnection(serverId)
            .flatMap(McpServerConnection::listResources);
    }
    
    /**
     * Read resource content from a server.
     */
    public Mono<McpResourceContent> readResource(String serverId, String uri) {
        return getServerConnection(serverId)
            .flatMap(connection -> connection.readResource(uri));
    }
    
    /**
     * Send raw message to a server.
     */
    public Mono<McpMessage> sendMessage(String serverId, McpMessage message) {
        return getServerConnection(serverId)
            .flatMap(connection -> connection.sendMessage(message));
    }
    
    /**
     * Subscribe to notifications from a server.
     */
    public Flux<McpMessage> getNotifications(String serverId) {
        return getServerConnection(serverId)
            .flatMapMany(McpServerConnection::notifications);
    }
    
    /**
     * Get aggregated health status of all servers.
     */
    public Mono<Map<String, Boolean>> getOverallHealth() {
        return Flux.fromIterable(connections.keySet())
            .flatMap(serverId -> 
                isServerHealthy(serverId)
                    .map(healthy -> Map.entry(serverId, healthy))
            )
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
    
    /**
     * Get all tools from all servers.
     */
    public Mono<Map<String, List<McpTool>>> getAllTools() {
        return Flux.fromIterable(connections.keySet())
            .flatMap(serverId -> 
                listTools(serverId)
                    .map(tools -> Map.entry(serverId, tools))
                    .onErrorReturn(Map.entry(serverId, List.of()))
            )
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
    
    /**
     * Initialize a server connection with smart detection.
     */
    private Mono<Void> initializeServer(String serverId, ServerConfig serverConfig) {
        logger.debug("Creating {} connection for server: {}", serverConfig.type(), serverId);
        
        if (serverConfig.isStdioType()) {
            return createStdioConnection(serverId, serverConfig)
                .flatMap(connection -> {
                    connections.put(serverId, connection);
                    return connection.initialize()
                        .doOnError(error -> {
                            connections.remove(serverId);
                            logger.error("Failed to initialize server {}: {}", serverId, error.getMessage());
                        });
                });
        } else {
            return Mono.error(new RuntimeException("Unsupported server type: " + serverConfig.type() + ". Only stdio is supported."));
        }
    }
    
    /**
     * Smart connection creation with automatic detection.
     */
    private Mono<McpServerConnection> createStdioConnection(String serverId, ServerConfig serverConfig) {
        return Mono.fromCallable(() -> {
            // Smart detection logic
            if (isSpringAiMcpServer(serverConfig)) {
                logger.info("Detected Spring AI MCP server for {}, using SpringAiMcpServerConnection", serverId);
                return new SpringAiMcpServerConnection(serverId, serverConfig, objectMapper);
            } else {
                logger.info("Detected standard MCP server for {}, using StdioMcpServerConnection", serverId);
                return new StdioMcpServerConnection(serverId, serverConfig, objectMapper);
            }
        });
    }
    
    /**
     * Detect if this is a Spring AI MCP server based on configuration.
     */
    private boolean isSpringAiMcpServer(ServerConfig serverConfig) {
        String command = serverConfig.command();
        List<String> args = serverConfig.args();
        
        // Detection criteria for Spring AI servers:
        // 1. Command is "java"
        // 2. Arguments contain "-jar" and a jar file
        // 3. Arguments contain Spring-specific profiles like "-Dspring.profiles.active=mcp"
        if ("java".equalsIgnoreCase(command) && args != null) {
            boolean hasJar = args.contains("-jar");
            boolean hasSpringProfile = args.stream().anyMatch(arg -> 
                arg.contains("-Dspring.profiles.active=mcp") || 
                arg.contains("-Dspring.main.web-application-type=none"));
            
            if (hasJar && hasSpringProfile) {
                return true;
            }
        }
        
        // Additional detection: Check if jar filename suggests Spring AI
        if (args != null) {
            boolean hasSpringAiJar = args.stream().anyMatch(arg -> 
                arg.contains("spring-boot-ai-mongo-mcp-server") ||
                arg.contains("spring-ai-mcp") ||
                arg.contains("springai-mcp"));
            if (hasSpringAiJar) {
                return true;
            }
        }
        
        // Default to standard MCP connection for all other servers
        return false;
    }
    
    /**
     * Dynamically add a new server connection.
     */
    public Mono<Void> addServer(String serverId, ServerConfig serverConfig) {
        if (connections.containsKey(serverId)) {
            return Mono.error(new RuntimeException("Server already exists: " + serverId));
        }
        
        return initializeServer(serverId, serverConfig);
    }
    
    /**
     * Remove a server connection.
     */
    public Mono<Void> removeServer(String serverId) {
        McpServerConnection connection = connections.remove(serverId);
        if (connection == null) {
            return Mono.error(new RuntimeException("Server not found: " + serverId));
        }
        
        return connection.close();
    }
}
