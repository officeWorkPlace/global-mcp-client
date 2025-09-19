package com.deepai.mcpclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service to dynamically detect available AI providers based on API keys
 * and select the best available provider for AI operations
 */
@Service
public class DynamicAiProviderService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicAiProviderService.class);

    @Value("${OPENAI_API_KEY:#{null}}")
    private String openAiApiKey;

    @Value("${GEMINI_API_KEY:#{null}}")
    private String geminiApiKey;

    @Value("${ANTHROPIC_API_KEY:#{null}}")
    private String anthropicApiKey;

    @Value("${OLLAMA_BASE_URL:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ai.fallback-providers:gemini,openai,claude,ollama}")
    private List<String> fallbackOrder;

    private final RestTemplate restTemplate;
    private final AtomicReference<String> currentProvider = new AtomicReference<>();
    private final Map<String, Boolean> providerHealthStatus = new HashMap<>();
    private final Map<String, String> providerModels = new HashMap<>();

    public DynamicAiProviderService() {
        this.restTemplate = new RestTemplate();
        // Set reasonable timeouts
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(5000);
            setReadTimeout(15000);
        }});
    }

    @PostConstruct
    public void initialize() {
        logger.info("🔍 Initializing Dynamic AI Provider Service...");
        detectAvailableProviders();
        selectBestProvider();
    }

    /**
     * Detect which AI providers have valid API keys configured
     */
    public void detectAvailableProviders() {
        logger.info("🔍 Detecting available AI providers...");

        Map<String, Boolean> availability = new HashMap<>();

        // Check OpenAI
        availability.put("openai", isValidApiKey(openAiApiKey, "sk-"));

        // Check Gemini
        availability.put("gemini", isValidApiKey(geminiApiKey, "AIza"));

        // Check Claude/Anthropic
        availability.put("claude", isValidApiKey(anthropicApiKey, "sk-ant-"));

        // Check Ollama (local availability)
        availability.put("ollama", isOllamaAvailable());

        // Update provider status
        for (Map.Entry<String, Boolean> entry : availability.entrySet()) {
            String provider = entry.getKey();
            boolean available = entry.getValue();
            providerHealthStatus.put(provider, available);

            if (available) {
                logger.info("✅ {} API key/service is available", provider.toUpperCase());
                // Set default models for each provider
                setDefaultModelForProvider(provider);
            } else {
                logger.info("❌ {} API key/service is not available", provider.toUpperCase());
            }
        }

        logProviderStatus();
    }

    /**
     * Validate if an API key is present and has the correct format
     */
    private boolean isValidApiKey(String apiKey, String expectedPrefix) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }

        // Check for placeholder values
        if (apiKey.contains("your-") || apiKey.contains("api-key-here") || apiKey.equals("not-set")) {
            return false;
        }

        // Check expected prefix (optional - some keys might not follow this pattern)
        if (expectedPrefix != null && !apiKey.startsWith(expectedPrefix)) {
            logger.warn("⚠️ API key for service might be invalid - expected prefix: {}", expectedPrefix);
            // Don't return false here as some valid keys might not follow the pattern
        }

        return apiKey.length() > 10; // Minimum reasonable key length
    }

    /**
     * Check if Ollama is available locally
     */
    private boolean isOllamaAvailable() {
        try {
            String healthUrl = ollamaBaseUrl + "/api/tags";
            restTemplate.getForObject(healthUrl, String.class);
            logger.info("✅ Ollama service is available at {}", ollamaBaseUrl);
            return true;
        } catch (Exception e) {
            logger.debug("❌ Ollama service not available at {}: {}", ollamaBaseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Set default model for each provider
     */
    private void setDefaultModelForProvider(String provider) {
        switch (provider.toLowerCase()) {
            case "openai":
                providerModels.put(provider, "gpt-4o-mini");
                break;
            case "gemini":
                providerModels.put(provider, "gemini-1.5-pro");
                break;
            case "claude":
                providerModels.put(provider, "claude-3-5-sonnet-20241022");
                break;
            case "ollama":
                providerModels.put(provider, "llama3.1:8b");
                break;
            default:
                providerModels.put(provider, "default");
        }
    }

    /**
     * Select the best available provider based on fallback order and availability
     */
    public String selectBestProvider() {
        for (String provider : fallbackOrder) {
            String normalizedProvider = provider.trim().toLowerCase();
            if (providerHealthStatus.getOrDefault(normalizedProvider, false)) {
                currentProvider.set(normalizedProvider);
                logger.info("🎯 Selected AI provider: {}", normalizedProvider.toUpperCase());
                return normalizedProvider;
            }
        }

        // If no configured provider is available, try to find any available provider
        for (Map.Entry<String, Boolean> entry : providerHealthStatus.entrySet()) {
            if (entry.getValue()) {
                String provider = entry.getKey();
                currentProvider.set(provider);
                logger.info("🎯 Using available AI provider: {} (not in configured fallback order)", provider.toUpperCase());
                return provider;
            }
        }

        logger.error("❌ No AI providers are available!");
        currentProvider.set("pattern-matching");
        return "pattern-matching";
    }

    /**
     * Get the currently selected provider
     */
    public String getCurrentProvider() {
        return currentProvider.get();
    }

    /**
     * Get the model for the current provider
     */
    public String getCurrentModel() {
        String provider = getCurrentProvider();
        return providerModels.getOrDefault(provider, "default");
    }

    /**
     * Get API key for a specific provider
     */
    public String getApiKeyForProvider(String provider) {
        switch (provider.toLowerCase()) {
            case "openai":
                return openAiApiKey;
            case "gemini":
                return geminiApiKey;
            case "claude":
            case "anthropic":
                return anthropicApiKey;
            case "ollama":
                return null; // Ollama doesn't use API keys
            default:
                return null;
        }
    }

    /**
     * Check if a specific provider is available
     */
    public boolean isProviderAvailable(String provider) {
        return providerHealthStatus.getOrDefault(provider.toLowerCase(), false);
    }

    /**
     * Get all available providers
     */
    public List<String> getAvailableProviders() {
        return providerHealthStatus.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * Force refresh of provider detection
     */
    public void refreshProviders() {
        logger.info("🔄 Refreshing AI provider detection...");
        detectAvailableProviders();
        selectBestProvider();
    }

    /**
     * Try to use the next available provider (fallback mechanism)
     */
    public String selectNextProvider() {
        String current = getCurrentProvider();
        logger.info("🔄 Current provider {} failed, selecting next available provider...", current);

        // Mark current provider as temporarily unavailable
        providerHealthStatus.put(current, false);

        // Select next best provider
        String nextProvider = selectBestProvider();

        if (!nextProvider.equals(current)) {
            logger.info("🎯 Switched to provider: {}", nextProvider.toUpperCase());
        }

        return nextProvider;
    }

    /**
     * Get health status of all providers
     */
    public Map<String, Object> getProviderHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentProvider", getCurrentProvider());
        status.put("availableProviders", getAvailableProviders());
        status.put("providerHealth", new HashMap<>(providerHealthStatus));
        status.put("providerModels", new HashMap<>(providerModels));
        return status;
    }

    /**
     * Test connectivity to a specific provider
     */
    public boolean testProviderConnectivity(String provider) {
        logger.info("🔍 Testing connectivity to provider: {}", provider);

        try {
            switch (provider.toLowerCase()) {
                case "openai":
                    return testOpenAiConnectivity();
                case "gemini":
                    return testGeminiConnectivity();
                case "claude":
                case "anthropic":
                    return testClaudeConnectivity();
                case "ollama":
                    return isOllamaAvailable();
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.warn("❌ Connectivity test failed for {}: {}", provider, e.getMessage());
            return false;
        }
    }

    private boolean testOpenAiConnectivity() {
        if (!isValidApiKey(openAiApiKey, "sk-")) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            Map<String, Object> requestBody = Map.of(
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "test")),
                "max_tokens", 1
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.postForObject("https://api.openai.com/v1/chat/completions", request, Map.class);
            return true;
        } catch (Exception e) {
            logger.debug("OpenAI connectivity test failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean testGeminiConnectivity() {
        if (!isValidApiKey(geminiApiKey, "AIza")) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", "test")))),
                "generationConfig", Map.of("maxOutputTokens", 1)
            );

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.postForObject(url, request, Map.class);
            return true;
        } catch (Exception e) {
            logger.debug("Gemini connectivity test failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean testClaudeConnectivity() {
        if (!isValidApiKey(anthropicApiKey, "sk-ant-")) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", anthropicApiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> requestBody = Map.of(
                "model", "claude-3-haiku-20240307",
                "max_tokens", 1,
                "messages", List.of(Map.of("role", "user", "content", "test"))
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            restTemplate.postForObject("https://api.anthropic.com/v1/messages", request, Map.class);
            return true;
        } catch (Exception e) {
            logger.debug("Claude connectivity test failed: {}", e.getMessage());
            return false;
        }
    }

    private void logProviderStatus() {
        logger.info("📊 AI Provider Status Summary:");
        List<String> available = getAvailableProviders();
        if (available.isEmpty()) {
            logger.warn("   ❌ No AI providers are available");
        } else {
            logger.info("   ✅ Available providers: {}", String.join(", ", available));
            logger.info("   🎯 Current provider: {}", getCurrentProvider());
            logger.info("   📝 Current model: {}", getCurrentModel());
        }
    }
}