package com.deepai.mcpclient.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience configuration for rate limiting and circuit breaking
 * Protects external services and prevents resource exhaustion
 */
@Configuration
public class ResilienceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceConfiguration.class);

    /**
     * Rate limiter for Gemini API calls
     * Prevents overwhelming the external service and hitting API quotas
     */
    @Bean
    public RateLimiter geminiApiRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(30)                    // 30 requests per period
            .limitRefreshPeriod(Duration.ofMinutes(1))  // 1 minute period
            .timeoutDuration(Duration.ofSeconds(5))     // 5 seconds wait for permit
            .build();

        RateLimiter rateLimiter = RateLimiter.of("gemini-api", config);

        // Add event listeners for monitoring
        rateLimiter.getEventPublisher()
            .onSuccess(event -> logger.debug("🟢 Gemini API call allowed"))
            .onFailure(event -> logger.warn("🔴 Gemini API rate limit exceeded - rejecting request"));

        logger.info("🚦 Gemini API rate limiter configured: {} requests per minute",
                   config.getLimitForPeriod());

        return rateLimiter;
    }

    /**
     * Rate limiter for general user requests
     * Protects against DoS attacks and abuse
     */
    @Bean
    public RateLimiter userRequestRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(100)                   // 100 requests per period
            .limitRefreshPeriod(Duration.ofMinutes(1))  // 1 minute period
            .timeoutDuration(Duration.ofSeconds(1))     // 1 second wait for permit
            .build();

        RateLimiter rateLimiter = RateLimiter.of("user-requests", config);

        rateLimiter.getEventPublisher()
            .onSuccess(event -> logger.debug("🟢 User request allowed"))
            .onFailure(event -> logger.warn("🔴 User rate limit exceeded - IP: {}", "TODO: extract IP"));

        logger.info("🚦 User request rate limiter configured: {} requests per minute",
                   config.getLimitForPeriod());

        return rateLimiter;
    }

    /**
     * Circuit breaker for Gemini API
     * Prevents cascading failures when external service is down
     */
    @Bean
    public CircuitBreaker geminiApiCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)              // 50% failure rate threshold
            .waitDurationInOpenState(Duration.ofSeconds(30))  // 30s wait before retry
            .slidingWindowSize(10)                 // 10 requests sliding window
            .minimumNumberOfCalls(5)               // Minimum 5 calls before evaluation
            .permittedNumberOfCallsInHalfOpenState(3)  // 3 test calls in half-open
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("gemini-api", config);

        // Add event listeners for monitoring
        circuitBreaker.getEventPublisher()
            .onStateTransition(event ->
                logger.warn("🔄 Gemini API circuit breaker state transition: {} -> {}",
                           event.getStateTransition().getFromState(),
                           event.getStateTransition().getToState()))
            .onSuccess(event -> logger.debug("🟢 Gemini API call successful"))
            .onError(event -> logger.warn("🔴 Gemini API call failed: {}", event.getThrowable().getMessage()))
            .onCallNotPermitted(event ->
                logger.error("🚫 Gemini API call blocked by circuit breaker - service may be down"));

        logger.info("🛡️ Gemini API circuit breaker configured: {}% failure threshold",
                   config.getFailureRateThreshold());

        return circuitBreaker;
    }

    /**
     * Circuit breaker for MCP services
     * Protects against individual MCP server failures
     */
    @Bean
    public CircuitBreaker mcpServiceCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(60)              // 60% failure rate (more lenient)
            .waitDurationInOpenState(Duration.ofSeconds(15))  // 15s wait before retry
            .slidingWindowSize(8)                  // 8 requests sliding window
            .minimumNumberOfCalls(3)               // Minimum 3 calls before evaluation
            .permittedNumberOfCallsInHalfOpenState(2)  // 2 test calls in half-open
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("mcp-service", config);

        circuitBreaker.getEventPublisher()
            .onStateTransition(event ->
                logger.warn("🔄 MCP service circuit breaker state transition: {} -> {}",
                           event.getStateTransition().getFromState(),
                           event.getStateTransition().getToState()))
            .onCallNotPermitted(event ->
                logger.error("🚫 MCP service call blocked by circuit breaker"));

        logger.info("🛡️ MCP service circuit breaker configured: {}% failure threshold",
                   config.getFailureRateThreshold());

        return circuitBreaker;
    }

    /**
     * Rate limiter for tool executions
     * Prevents tool execution flooding
     */
    @Bean
    public RateLimiter toolExecutionRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(50)                    // 50 tool executions per period
            .limitRefreshPeriod(Duration.ofMinutes(1))  // 1 minute period
            .timeoutDuration(Duration.ofSeconds(2))     // 2 seconds wait for permit
            .build();

        RateLimiter rateLimiter = RateLimiter.of("tool-execution", config);

        rateLimiter.getEventPublisher()
            .onSuccess(event -> logger.debug("🟢 Tool execution allowed"))
            .onFailure(event -> logger.warn("🔴 Tool execution rate limit exceeded"));

        logger.info("🚦 Tool execution rate limiter configured: {} executions per minute",
                   config.getLimitForPeriod());

        return rateLimiter;
    }
}