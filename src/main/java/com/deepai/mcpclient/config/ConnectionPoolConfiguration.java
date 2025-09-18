package com.deepai.mcpclient.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Connection pooling configuration for HTTP clients and external service connections
 * Optimizes resource usage and improves performance through connection reuse
 */
@Configuration
public class ConnectionPoolConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolConfiguration.class);

    /**
     * Configure connection pool for HTTP clients used by AI services and external APIs
     */
    @Bean
    public ConnectionProvider httpConnectionProvider() {
        ConnectionProvider provider = ConnectionProvider.builder("http-pool")
            .maxConnections(50)                    // Maximum total connections
            .maxIdleTime(Duration.ofSeconds(30))   // Close idle connections after 30s
            .maxLifeTime(Duration.ofMinutes(10))   // Maximum connection lifetime
            .pendingAcquireTimeout(Duration.ofSeconds(10)) // Wait timeout for acquiring connection
            .evictInBackground(Duration.ofSeconds(60))     // Background eviction interval
            .build();

        logger.info("🔗 HTTP connection pool configured: max=50, idle=30s, lifetime=10m");
        return provider;
    }

    /**
     * Configure HTTP client with optimized connection pooling for AI services
     */
    @Bean
    public HttpClient httpClient(ConnectionProvider connectionProvider) {
        HttpClient client = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)  // 10s connect timeout
            .option(ChannelOption.SO_KEEPALIVE, true)              // Enable TCP keep-alive
            .option(ChannelOption.TCP_NODELAY, true)               // Disable Nagle's algorithm
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)))
            .compress(true)                                        // Enable compression
            .keepAlive(true);                                      // HTTP keep-alive

        logger.info("🚀 HTTP client configured with connection pooling and timeouts");
        return client;
    }

    /**
     * Configure WebClient with connection pooling for reactive HTTP calls
     */
    @Bean
    public WebClient webClient(HttpClient httpClient) {
        WebClient client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB buffer
            })
            .build();

        logger.info("🌐 WebClient configured with connection pooling");
        return client;
    }

    /**
     * Configure connection pool for MCP server connections
     */
    @Bean
    public ConnectionProvider mcpConnectionProvider() {
        ConnectionProvider provider = ConnectionProvider.builder("mcp-pool")
            .maxConnections(20)                    // Fewer connections for MCP servers
            .maxIdleTime(Duration.ofMinutes(5))    // Longer idle time for persistent connections
            .maxLifeTime(Duration.ofMinutes(30))   // Longer lifetime for stable connections
            .pendingAcquireTimeout(Duration.ofSeconds(15)) // Longer wait for MCP connections
            .evictInBackground(Duration.ofMinutes(2))      // Less frequent eviction
            .build();

        logger.info("🔗 MCP connection pool configured: max=20, idle=5m, lifetime=30m");
        return provider;
    }

    /**
     * Connection pool health monitoring bean
     */
    @Bean
    public ConnectionPoolMonitor connectionPoolMonitor(ConnectionProvider httpConnectionProvider,
                                                      ConnectionProvider mcpConnectionProvider) {
        return new ConnectionPoolMonitor(httpConnectionProvider, mcpConnectionProvider);
    }

    /**
     * Monitor connection pool health and metrics
     */
    public static class ConnectionPoolMonitor {
        private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitor.class);

        private final ConnectionProvider httpPool;
        private final ConnectionProvider mcpPool;

        public ConnectionPoolMonitor(ConnectionProvider httpPool, ConnectionProvider mcpPool) {
            this.httpPool = httpPool;
            this.mcpPool = mcpPool;
            logger.info("📊 Connection pool monitoring initialized");
        }

        public ConnectionPoolStats getHttpPoolStats() {
            return new ConnectionPoolStats("http-pool", 50);
        }

        public ConnectionPoolStats getMcpPoolStats() {
            return new ConnectionPoolStats("mcp-pool", 20);
        }

        public boolean isPoolHealthy() {
            // Basic health check - could be enhanced with actual pool metrics
            return httpPool != null && mcpPool != null;
        }
    }

    /**
     * Connection pool statistics
     */
    public static class ConnectionPoolStats {
        private final String poolName;
        private final int maxConnections;

        public ConnectionPoolStats(String poolName, int maxConnections) {
            this.poolName = poolName;
            this.maxConnections = maxConnections;
        }

        public String getPoolName() { return poolName; }
        public int getMaxConnections() { return maxConnections; }

        // Note: Reactor Netty doesn't expose detailed connection metrics by default
        // In a production environment, you'd integrate with metrics libraries like Micrometer
        public int getActiveConnections() { return 0; } // Placeholder
        public int getIdleConnections() { return 0; }   // Placeholder
        public long getTotalAcquiredConnections() { return 0; } // Placeholder
    }
}