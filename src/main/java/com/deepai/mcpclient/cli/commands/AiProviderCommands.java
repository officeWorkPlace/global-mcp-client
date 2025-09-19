package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.service.DynamicAiProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * CLI commands for managing AI providers dynamically
 */
@Component
public class AiProviderCommands extends BaseCommand {

    private static final Logger logger = LoggerFactory.getLogger(AiProviderCommands.class);

    private final DynamicAiProviderService providerService;

    @Autowired
    public AiProviderCommands(DynamicAiProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * Show current AI provider status
     */
    public String showAiStatus() {
        try {
            Map<String, Object> status = providerService.getProviderHealthStatus();

            StringBuilder result = new StringBuilder();
            result.append("🤖 AI Provider Status\n");
            result.append("═══════════════════════════════════════\n");

            String currentProvider = (String) status.get("currentProvider");
            result.append(String.format("🎯 Current Provider: %s\n", currentProvider.toUpperCase()));
            result.append(String.format("📝 Current Model: %s\n", providerService.getCurrentModel()));
            result.append("\n");

            @SuppressWarnings("unchecked")
            List<String> availableProviders = (List<String>) status.get("availableProviders");
            result.append("✅ Available Providers:\n");
            if (availableProviders.isEmpty()) {
                result.append("   ❌ No providers available\n");
            } else {
                for (String provider : availableProviders) {
                    result.append(String.format("   • %s\n", provider.toUpperCase()));
                }
            }

            result.append("\n");
            @SuppressWarnings("unchecked")
            Map<String, Boolean> healthStatus = (Map<String, Boolean>) status.get("providerHealth");
            result.append("📊 Provider Health:\n");
            for (Map.Entry<String, Boolean> entry : healthStatus.entrySet()) {
                String icon = entry.getValue() ? "✅" : "❌";
                result.append(String.format("   %s %s\n", icon, entry.getKey().toUpperCase()));
            }

            return result.toString();

        } catch (Exception e) {
            logger.error("Failed to get AI provider status: {}", e.getMessage());
            return "❌ Failed to get AI provider status: " + e.getMessage();
        }
    }

    /**
     * Refresh AI provider detection
     */
    public String refreshProviders() {
        try {
            logger.info("🔄 Refreshing AI providers via CLI command");
            providerService.refreshProviders();

            return "✅ AI provider detection refreshed successfully!\n" +
                   "Use 'ai-status' to see the current configuration.";

        } catch (Exception e) {
            logger.error("Failed to refresh AI providers: {}", e.getMessage());
            return "❌ Failed to refresh AI providers: " + e.getMessage();
        }
    }

    /**
     * Test connectivity to a specific provider
     */
    public String testProvider(String provider) {
        try {
            logger.info("🔍 Testing connectivity to provider: {}", provider);

            boolean result = providerService.testProviderConnectivity(provider);

            if (result) {
                return String.format("✅ %s connectivity test passed!", provider.toUpperCase());
            } else {
                return String.format("❌ %s connectivity test failed. Check API key and network connectivity.", provider.toUpperCase());
            }

        } catch (Exception e) {
            logger.error("Failed to test provider {}: {}", provider, e.getMessage());
            return String.format("❌ Failed to test %s: %s", provider.toUpperCase(), e.getMessage());
        }
    }

    /**
     * Switch to next available provider
     */
    public String switchToNextProvider() {
        try {
            String previousProvider = providerService.getCurrentProvider();
            String newProvider = providerService.selectNextProvider();

            if (newProvider.equals(previousProvider)) {
                return String.format("⚠️ No other providers available. Still using %s", newProvider.toUpperCase());
            } else {
                return String.format("🔄 Switched from %s to %s", previousProvider.toUpperCase(), newProvider.toUpperCase());
            }

        } catch (Exception e) {
            logger.error("Failed to switch provider: {}", e.getMessage());
            return "❌ Failed to switch provider: " + e.getMessage();
        }
    }

    /**
     * Show detailed provider configuration
     */
    public String showProviderConfig() {
        try {
            Map<String, Object> status = providerService.getProviderHealthStatus();

            StringBuilder result = new StringBuilder();
            result.append("🔧 AI Provider Configuration\n");
            result.append("═══════════════════════════════════════\n");

            @SuppressWarnings("unchecked")
            Map<String, String> models = (Map<String, String>) status.get("providerModels");
            @SuppressWarnings("unchecked")
            Map<String, Boolean> health = (Map<String, Boolean>) status.get("providerHealth");

            for (String provider : List.of("openai", "gemini", "claude", "ollama")) {
                String healthIcon = health.getOrDefault(provider, false) ? "✅" : "❌";
                String model = models.getOrDefault(provider, "N/A");
                String keyStatus = getKeyStatus(provider);

                result.append(String.format("%s %s\n", healthIcon, provider.toUpperCase()));
                result.append(String.format("   Model: %s\n", model));
                result.append(String.format("   API Key: %s\n", keyStatus));
                result.append("\n");
            }

            return result.toString();

        } catch (Exception e) {
            logger.error("Failed to get provider configuration: {}", e.getMessage());
            return "❌ Failed to get provider configuration: " + e.getMessage();
        }
    }

    /**
     * Get API key status for a provider without exposing the actual key
     */
    private String getKeyStatus(String provider) {
        String apiKey = providerService.getApiKeyForProvider(provider);

        if (provider.equals("ollama")) {
            return "Not required (local service)";
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "❌ Not configured";
        }

        if (apiKey.contains("your-") || apiKey.contains("api-key-here") || apiKey.equals("not-set")) {
            return "❌ Placeholder value";
        }

        // Show first 4 characters and length for verification
        return String.format("✅ Configured (%s...%d chars)",
            apiKey.substring(0, Math.min(4, apiKey.length())), apiKey.length());
    }

    /**
     * Show AI provider help
     */
    public String showProviderHelp() {
        return """
                🤖 AI Provider Commands Help
                ═══════════════════════════════════════

                Available Commands:
                • ai-status       - Show current AI provider status
                • ai-refresh      - Refresh AI provider detection
                • ai-test -p <provider>  - Test specific provider connectivity
                • ai-next         - Switch to next available provider
                • ai-config       - Show detailed provider configuration
                • ai-help         - Show this help message

                Supported Providers:
                • openai    - OpenAI GPT (requires OPENAI_API_KEY)
                • gemini    - Google Gemini (requires GEMINI_API_KEY)
                • claude    - Anthropic Claude (requires ANTHROPIC_API_KEY)
                • ollama    - Local Ollama (requires running service)

                Environment Variables:
                • OPENAI_API_KEY     - OpenAI API key (starts with 'sk-')
                • GEMINI_API_KEY     - Gemini API key (starts with 'AIza')
                • ANTHROPIC_API_KEY  - Claude API key (starts with 'sk-ant-')
                • OLLAMA_BASE_URL    - Ollama service URL (default: http://localhost:11434)

                Examples:
                • ai-status               - Check current status
                • ai-test -p gemini      - Test Gemini connectivity
                • ai-refresh             - Re-detect available providers
                """;
    }
}