package com.deepai.mcpclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class GeminiCircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(GeminiCircuitBreaker.class);

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration RESET_TIMEOUT = Duration.ofMinutes(1);

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastFailureTime = new AtomicReference<>();
    private volatile CircuitState state = CircuitState.CLOSED;

    public enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    public <T> T execute(CircuitBreakerCall<T> call) throws Exception {
        if (state == CircuitState.OPEN) {
            if (shouldAttemptReset()) {
                state = CircuitState.HALF_OPEN;
                logger.info("Circuit breaker is now HALF_OPEN - attempting reset");
            } else {
                throw new RuntimeException("Circuit breaker is OPEN - too many recent failures");
            }
        }

        try {
            T result = call.execute();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private boolean shouldAttemptReset() {
        LocalDateTime lastFailure = lastFailureTime.get();
        return lastFailure != null &&
               Duration.between(lastFailure, LocalDateTime.now()).compareTo(RESET_TIMEOUT) > 0;
    }

    private void onSuccess() {
        failureCount.set(0);
        state = CircuitState.CLOSED;
        logger.debug("Circuit breaker reset to CLOSED state");
    }

    private void onFailure() {
        lastFailureTime.set(LocalDateTime.now());
        int failures = failureCount.incrementAndGet();

        if (failures >= FAILURE_THRESHOLD) {
            state = CircuitState.OPEN;
            logger.warn("Circuit breaker opened after {} failures", failures);
        }
    }

    public CircuitState getState() {
        return state;
    }

    @FunctionalInterface
    public interface CircuitBreakerCall<T> {
        T execute() throws Exception;
    }
}