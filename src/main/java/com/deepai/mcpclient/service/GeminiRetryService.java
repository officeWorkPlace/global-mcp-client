package com.deepai.mcpclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class GeminiRetryService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiRetryService.class);

    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_DELAY = Duration.ofMillis(1000);
    private static final double BACKOFF_MULTIPLIER = 2.0;

    public <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                T result = operation.get();
                if (attempt > 1) {
                    logger.info("✅ {} succeeded on attempt {}", operationName, attempt);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                logger.warn("⚠️ {} failed on attempt {} of {}: {}",
                    operationName, attempt, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    if (isRetriableError(e)) {
                        long delay = (long) (INITIAL_DELAY.toMillis() * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
                        logger.info("🔄 Retrying {} in {}ms...", operationName, delay);

                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        logger.error("❌ Non-retriable error for {}: {}", operationName, e.getMessage());
                        break;
                    }
                }
            }
        }

        throw new RuntimeException("Operation " + operationName + " failed after " + MAX_RETRIES + " attempts", lastException);
    }

    private boolean isRetriableError(Exception e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("rate limit") ||
               message.contains("timeout") ||
               message.contains("network") ||
               message.contains("503") ||
               message.contains("502") ||
               message.contains("429");
    }
}