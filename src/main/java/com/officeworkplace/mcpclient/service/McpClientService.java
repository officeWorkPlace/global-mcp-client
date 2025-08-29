package com.officeworkplace.mcpclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeworkplace.mcpclient.config.McpConfigurationProperties;
import com.officeworkplace.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.officeworkplace.mcpclient.model.*;
import com.officeworkplace.mcpclient.service.impl.HttpMcpServerConnection;
import com.officeworkplace.mcpclient.service.impl.StdioMcpServerConnection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Map<String, McpServerConnection> connections = new ConcurrentHashMap<>();
    
    public McpClientService(McpConfigurationProperties config, 
                           WebClient webClient, 
                           ObjectMapper objectMapper) {
        this.config = config;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing MCP client with {} configured servers", config.servers().size());
        
        config.servers().forEach((serverId, serverConfig) -> {
            if (serverConfig.enabled()) {
                initializeServer(serverId, serverConfig)
                    .subscribe(
                        success -> logger.info("Successfully initialized server: {}", serverId),
                        error -> logger.error("Failed to initialize server {}: {}", serverId, error.getMessage())
                    );
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
        return List.copyOf(connections.keySet());
    }
    
    /**
     * Get server connection by ID.
     */
    public Mono<McpServerConnection> getServerConnection(String serverId) {
        McpServerConnection connection = connections.get(serverId);
        if (connection == null) {
            return Mono.error(new RuntimeException("Server not found: " + serverId));
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
     * Initialize a server connection.
     */
    private Mono<Void> initializeServer(String serverId, ServerConfig serverConfig) {
        logger.debug("Creating connection for server: {} (type: {})", serverId, serverConfig.type());
        
        McpServerConnection connection;
        
        if (serverConfig.isHttpType()) {
            connection = new HttpMcpServerConnection(serverId, serverConfig, webClient, objectMapper);
        } else if (serverConfig.isStdioType()) {
            connection = new StdioMcpServerConnection(serverId, serverConfig, objectMapper);
        } else {
            return Mono.error(new RuntimeException("Unsupported server type: " + serverConfig.type()));
        }
        
        connections.put(serverId, connection);
        
        return connection.initialize()
            .doOnError(error -> {
                connections.remove(serverId);
                logger.error("Failed to initialize server {}: {}", serverId, error.getMessage());
            });
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
