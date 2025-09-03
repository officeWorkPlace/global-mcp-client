package com.deepai.mcpclient.service.impl;

import com.deepai.mcpclient.service.McpServerConnectionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepai.mcpclient.config.McpConfigurationProperties;
import com.deepai.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.deepai.mcpclient.model.*;
import com.deepai.mcpclient.service.McpClientService;
import com.deepai.mcpclient.service.McpServerConnection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main MCP client service that manages connections to multiple MCP servers.
 */
@Service
public class McpClientServiceImpl implements McpClientService {
    
    private static final Logger logger = LoggerFactory.getLogger(McpClientServiceImpl.class);
    
    private final McpConfigurationProperties config;
    private final List<McpServerConnectionFactory> connectionFactories;
    private final Map<String, McpServerConnection> connections = new ConcurrentHashMap<>();
    
    public McpClientServiceImpl(McpConfigurationProperties config, 
                           List<McpServerConnectionFactory> connectionFactories) {
        this.config = config;
        this.connectionFactories = connectionFactories;
    }
    
    @Override
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
    
    @Override
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down MCP client connections");
        
        try {
            Flux.fromIterable(connections.values())
                .flatMap(McpServerConnection::close)
                .blockLast(Duration.ofSeconds(10)); // Block with a timeout
        } catch (Exception e) {
            logger.error("Error during MCP connection shutdown: {}", e.getMessage());
        } finally {
            connections.clear();
            logger.info("All MCP connections closed");
        }
    }
    
    /**
     * Get all configured server IDs.
     */
    @Override
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
    @Override
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
    @Override
    public Mono<McpServerInfo> getServerInfo(String serverId) {
        return getServerConnection(serverId)
            .flatMap(McpServerConnection::getServerInfo);
    }
    
    /**
     * Check server health.
     */
    @Override
    public Mono<Boolean> isServerHealthy(String serverId) {
        return getServerConnection(serverId)
            .flatMap(McpServerConnection::isHealthy)
            .onErrorReturn(false);
    }
    
    /**
     * List available tools from a server.
     */
    @Override
    public Mono<List<McpTool>> listTools(String serverId) {
        return getServerConnection(serverId)
            .flatMap(McpServerConnection::listTools);
    }
    
    /**
     * Execute a tool on a specific server.
     */
    @Override
    public Mono<McpToolResult> executeTool(String serverId, String toolName, Map<String, Object> arguments) {
        return getServerConnection(serverId)
            .flatMap(connection -> connection.executeTool(toolName, arguments));
    }
    
    /**
     * List available resources from a server.
     */
    @Override
    public Mono<List<McpResource>> listResources(String serverId) {
        return getServerConnection(serverId)
            .flatMap(McpServerConnection::listResources);
    }
    
    /**
     * Read resource content from a server.
     */
    @Override
    public Mono<McpResourceContent> readResource(String serverId, String uri) {
        return getServerConnection(serverId)
            .flatMap(connection -> connection.readResource(uri));
    }
    
    /**
     * Send raw message to a server.
     */
    @Override
    public Mono<McpMessage> sendMessage(String serverId, McpMessage message) {
        return getServerConnection(serverId)
            .flatMap(connection -> connection.sendMessage(message));
    }
    
    /**
     * Subscribe to notifications from a server.
     */
    @Override
    public Flux<McpMessage> getNotifications(String serverId) {
        return getServerConnection(serverId)
            .flatMapMany(McpServerConnection::notifications);
    }
    
    /**
     * Get aggregated health status of all servers.
     */
    @Override
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
    @Override
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
     * Initialize a server connection using factories.
     */
    private Mono<Void> initializeServer(String serverId, ServerConfig serverConfig) {
        logger.debug("Creating {} connection for server: {}", serverConfig.type(), serverId);
        
        McpServerConnectionFactory factory = connectionFactories.stream()
                .filter(f -> f.supports(serverConfig.type()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unsupported server type: " + serverConfig.type()));

        return Mono.fromCallable(() -> factory.createConnection(serverId, serverConfig))
                .flatMap(connection -> {
                    connections.put(serverId, connection);
                    return connection.initialize()
                        .doOnError(error -> {
                            connections.remove(serverId);
                            logger.error("Failed to initialize server {}: {}", serverId, error.getMessage());
                        });
                });
    }
    
    /**
     * Dynamically add a new server connection.
     */
    @Override
    public Mono<Void> addServer(String serverId, ServerConfig serverConfig) {
        if (connections.containsKey(serverId)) {
            return Mono.error(new RuntimeException("Server already exists: " + serverId));
        }
        
        return initializeServer(serverId, serverConfig);
    }
    
    /**
     * Remove a server connection.
     */
    @Override
    public Mono<Void> removeServer(String serverId) {
        McpServerConnection connection = connections.remove(serverId);
        if (connection == null) {
            return Mono.error(new RuntimeException("Server not found: " + serverId));
        }
        
        return connection.close();
    }
}
