package com.deepai.mcpclient.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Service that applies resilience patterns (rate limiting, circuit breaking)
 * to protect external services and prevent resource exhaustion
 */
@Service
public class ResilienceService {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceService.class);

    private final RateLimiter geminiApiRateLimiter;
    private final RateLimiter userRequestRateLimiter;
    private final RateLimiter toolExecutionRateLimiter;
    private final CircuitBreaker geminiApiCircuitBreaker;
    private final CircuitBreaker mcpServiceCircuitBreaker;

    @Autowired
    public ResilienceService(
            @Qualifier("geminiApiRateLimiter") RateLimiter geminiApiRateLimiter,
            @Qualifier("userRequestRateLimiter") RateLimiter userRequestRateLimiter,
            @Qualifier("toolExecutionRateLimiter") RateLimiter toolExecutionRateLimiter,
            @Qualifier("geminiApiCircuitBreaker") CircuitBreaker geminiApiCircuitBreaker,
            @Qualifier("mcpServiceCircuitBreaker") CircuitBreaker mcpServiceCircuitBreaker) {
        this.geminiApiRateLimiter = geminiApiRateLimiter;
        this.userRequestRateLimiter = userRequestRateLimiter;
        this.toolExecutionRateLimiter = toolExecutionRateLimiter;
        this.geminiApiCircuitBreaker = geminiApiCircuitBreaker;
        this.mcpServiceCircuitBreaker = mcpServiceCircuitBreaker;
    }

    /**
     * Execute Gemini API call with rate limiting and circuit breaking
     */
    public <T> Mono<T> executeGeminiApiCall(Supplier<T> operation, String operationName) {
        return Mono.fromCallable(() -> {
            // Apply rate limiting
            if (!geminiApiRateLimiter.acquirePermission()) {
                logger.warn("🚫 Gemini API rate limit exceeded for operation: {}", operationName);
                throw new RuntimeException("Gemini API rate limit exceeded. Please try again later.");
            }

            // Apply circuit breaker
            return geminiApiCircuitBreaker.executeSupplier(operation);
        })
        .doOnError(RuntimeException.class, e ->
            logger.warn("🔴 Gemini API request rejected: {}", e.getMessage()))
        .doOnError(Exception.class, e ->
            logger.error("❌ Gemini API operation '{}' failed: {}", operationName, e.getMessage()))
        .timeout(Duration.ofSeconds(30)); // Global timeout for Gemini calls
    }

    /**
     * Execute user request with rate limiting
     */
    public <T> Mono<T> executeUserRequest(Supplier<T> operation, String userId) {
        return Mono.fromCallable(() -> {
            // Apply user rate limiting
            if (!userRequestRateLimiter.acquirePermission()) {
                logger.warn("🚫 User rate limit exceeded for user: {}", userId);
                throw new RuntimeException("Rate limit exceeded. Please wait before making another request.");
            }

            return operation.get();
        })
        .doOnError(RuntimeException.class, e ->
            logger.warn("🔴 User request rejected for {}: {}", userId, e.getMessage()));
    }

    /**
     * Execute MCP tool operation with protection
     */
    public <T> Mono<T> executeMcpOperation(Supplier<T> operation, String serverId, String toolName) {
        return Mono.fromCallable(() -> {
            // Apply tool execution rate limiting
            if (!toolExecutionRateLimiter.acquirePermission()) {
                logger.warn("🚫 Tool execution rate limit exceeded for {}.{}", serverId, toolName);
                throw new RuntimeException("Tool execution rate limit exceeded. Please try again later.");
            }

            // Apply MCP service circuit breaker
            return mcpServiceCircuitBreaker.executeSupplier(operation);
        })
        .doOnError(RuntimeException.class, e ->
            logger.warn("🔴 Tool execution rejected for {}.{}: {}", serverId, toolName, e.getMessage()))
        .doOnError(Exception.class, e ->
            logger.error("❌ MCP operation {}.{} failed: {}", serverId, toolName, e.getMessage()))
        .timeout(Duration.ofSeconds(15)); // Timeout for tool operations
    }

    /**
     * Check if user can make a request (preview without consuming permit)
     */
    public boolean canUserMakeRequest() {
        return userRequestRateLimiter.getMetrics().getAvailablePermissions() > 0;
    }

    /**
     * Check if Gemini API can accept requests
     */
    public boolean canCallGeminiApi() {
        return geminiApiRateLimiter.getMetrics().getAvailablePermissions() > 0 &&
               !geminiApiCircuitBreaker.getState().equals(CircuitBreaker.State.OPEN);
    }

    /**
     * Check if tool execution is available
     */
    public boolean canExecuteTools() {
        return toolExecutionRateLimiter.getMetrics().getAvailablePermissions() > 0 &&
               !mcpServiceCircuitBreaker.getState().equals(CircuitBreaker.State.OPEN);
    }

    /**
     * Get resilience status for monitoring
     */
    public ResilienceStatus getResilienceStatus() {
        return new ResilienceStatus(
            geminiApiRateLimiter.getMetrics().getAvailablePermissions(),
            userRequestRateLimiter.getMetrics().getAvailablePermissions(),
            toolExecutionRateLimiter.getMetrics().getAvailablePermissions(),
            geminiApiCircuitBreaker.getState(),
            mcpServiceCircuitBreaker.getState()
        );
    }

    /**
     * Status information for resilience components
     */
    public static class ResilienceStatus {
        private final int geminiApiPermits;
        private final int userRequestPermits;
        private final int toolExecutionPermits;
        private final CircuitBreaker.State geminiApiState;
        private final CircuitBreaker.State mcpServiceState;

        public ResilienceStatus(int geminiApiPermits, int userRequestPermits, int toolExecutionPermits,
                               CircuitBreaker.State geminiApiState, CircuitBreaker.State mcpServiceState) {
            this.geminiApiPermits = geminiApiPermits;
            this.userRequestPermits = userRequestPermits;
            this.toolExecutionPermits = toolExecutionPermits;
            this.geminiApiState = geminiApiState;
            this.mcpServiceState = mcpServiceState;
        }

        public int getGeminiApiPermits() { return geminiApiPermits; }
        public int getUserRequestPermits() { return userRequestPermits; }
        public int getToolExecutionPermits() { return toolExecutionPermits; }
        public CircuitBreaker.State getGeminiApiState() { return geminiApiState; }
        public CircuitBreaker.State getMcpServiceState() { return mcpServiceState; }

        public boolean isHealthy() {
            return geminiApiState != CircuitBreaker.State.OPEN &&
                   mcpServiceState != CircuitBreaker.State.OPEN &&
                   userRequestPermits > 0;
        }
    }
}