package com.deepai.mcpclient.health;

import com.deepai.mcpclient.service.AiService;
import com.deepai.mcpclient.service.ResilienceService;
import com.deepai.mcpclient.service.DynamicAiProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Health indicator for AI services
 * Checks Gemini API connectivity and circuit breaker status
 */
@Component("aiService")
public class AiServiceHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(AiServiceHealthIndicator.class);

    private final AiService aiService;
    private final ResilienceService resilienceService;
    private final DynamicAiProviderService providerService;

    @Autowired
    public AiServiceHealthIndicator(AiService aiService, ResilienceService resilienceService, DynamicAiProviderService providerService) {
        this.aiService = aiService;
        this.resilienceService = resilienceService;
        this.providerService = providerService;
    }

    @Override
    public Health health() {
        try {
            // Check resilience status first
            ResilienceService.ResilienceStatus resilienceStatus = resilienceService.getResilienceStatus();

            Health.Builder healthBuilder = Health.up();
            boolean isHealthy = true;

            // Check circuit breaker states
            if (!resilienceService.canCallGeminiApi()) {
                healthBuilder = Health.down();
                isHealthy = false;
                healthBuilder.withDetail("gemini_api", "CIRCUIT_OPEN_OR_NO_PERMITS");
            } else {
                healthBuilder.withDetail("gemini_api", "AVAILABLE");
            }

            // Check rate limiter permits
            healthBuilder.withDetail("gemini_permits", resilienceStatus.getGeminiApiPermits())
                        .withDetail("user_permits", resilienceStatus.getUserRequestPermits())
                        .withDetail("tool_permits", resilienceStatus.getToolExecutionPermits());

            // Check circuit breaker states
            healthBuilder.withDetail("gemini_circuit_state", resilienceStatus.getGeminiApiState().toString())
                        .withDetail("mcp_circuit_state", resilienceStatus.getMcpServiceState().toString());

            // Test AI connectivity with simple health check
            try {
                String healthCheckResponse = aiService.ask("Say 'OK' if you can respond")
                    .timeout(Duration.ofSeconds(10))
                    .onErrorReturn("ERROR")
                    .block();

                if (healthCheckResponse != null && !healthCheckResponse.equals("ERROR")) {
                    healthBuilder.withDetail("connectivity_test", "PASSED");
                    healthBuilder.withDetail("last_response_length", healthCheckResponse.length());
                } else {
                    healthBuilder.withDetail("connectivity_test", "FAILED");
                    isHealthy = false;
                    if (!isHealthy) healthBuilder = Health.down();
                }
            } catch (Exception e) {
                logger.warn("🚨 AI connectivity test failed: {}", e.getMessage());
                healthBuilder.withDetail("connectivity_test", "FAILED");
                healthBuilder.withDetail("connectivity_error", e.getMessage());
                isHealthy = false;
                if (!isHealthy) healthBuilder = Health.down();
            }

            // Overall resilience health
            healthBuilder.withDetail("resilience_healthy", resilienceStatus.isHealthy());

            // Add dynamic provider information
            try {
                String currentProvider = providerService.getCurrentProvider();
                List<String> availableProviders = providerService.getAvailableProviders();

                healthBuilder.withDetail("current_provider", currentProvider)
                           .withDetail("available_providers", String.join(", ", availableProviders))
                           .withDetail("total_providers_available", availableProviders.size());

                // Test current provider specifically
                if (!"pattern-matching".equals(currentProvider)) {
                    boolean providerHealthy = providerService.testProviderConnectivity(currentProvider);
                    healthBuilder.withDetail("current_provider_healthy", providerHealthy);

                    if (!providerHealthy && isHealthy) {
                        healthBuilder = Health.down();
                        isHealthy = false;
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to get dynamic provider health: {}", e.getMessage());
                healthBuilder.withDetail("provider_health_check", "FAILED: " + e.getMessage());
            }

            return healthBuilder.build();

        } catch (Exception e) {
            logger.error("❌ AI service health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Health check failed")
                .build();
        }
    }
}