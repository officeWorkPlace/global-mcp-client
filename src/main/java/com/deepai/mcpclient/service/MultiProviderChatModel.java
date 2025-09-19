package com.deepai.mcpclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Multi-provider ChatModel implementation that dynamically selects
 * the best available AI provider based on API key availability
 */
@Service
public class MultiProviderChatModel implements ChatModel {

    private static final Logger logger = LoggerFactory.getLogger(MultiProviderChatModel.class);

    private final DynamicAiProviderService providerService;
    private final RestTemplate restTemplate;

    @Autowired
    public MultiProviderChatModel(DynamicAiProviderService providerService) {
        this.providerService = providerService;
        this.restTemplate = new RestTemplate();
        // Set reasonable timeouts
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(5000);
            setReadTimeout(30000);
        }});
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String promptText = extractPromptText(prompt);
        logger.info("🤖 Processing request with multi-provider ChatModel: '{}'",
            promptText.length() > 100 ? promptText.substring(0, 100) + "..." : promptText);

        String currentProvider = providerService.getCurrentProvider();

        // Try current provider first
        ChatResponse response = tryProvider(currentProvider, promptText);
        if (response != null) {
            return response;
        }

        // If current provider failed, try other available providers
        List<String> availableProviders = providerService.getAvailableProviders();
        for (String provider : availableProviders) {
            if (!provider.equals(currentProvider)) {
                logger.info("🔄 Trying fallback provider: {}", provider.toUpperCase());
                response = tryProvider(provider, promptText);
                if (response != null) {
                    // Update current provider to the working one
                    providerService.selectBestProvider();
                    return response;
                }
            }
        }

        // If all providers failed, fall back to pattern matching
        logger.warn("❌ All AI providers failed, falling back to pattern matching");
        return callPatternMatching(promptText);
    }

    /**
     * Try to get a response from a specific provider
     */
    private ChatResponse tryProvider(String provider, String promptText) {
        try {
            logger.info("🎯 Trying provider: {}", provider.toUpperCase());

            switch (provider.toLowerCase()) {
                case "openai":
                    return callOpenAi(promptText);
                case "gemini":
                    return callGemini(promptText);
                case "claude":
                case "anthropic":
                    return callClaude(promptText);
                case "ollama":
                    return callOllama(promptText);
                case "pattern-matching":
                    return callPatternMatching(promptText);
                default:
                    logger.warn("⚠️ Unknown provider: {}", provider);
                    return null;
            }
        } catch (Exception e) {
            logger.error("❌ Provider {} failed: {}", provider, e.getMessage());
            return null;
        }
    }

    /**
     * Call OpenAI GPT API
     */
    private ChatResponse callOpenAi(String promptText) {
        String apiKey = providerService.getApiKeyForProvider("openai");
        if (apiKey == null) {
            logger.warn("⚠️ OpenAI API key not available");
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(Map.of(
                    "role", "user",
                    "content", promptText
                )),
                "max_tokens", 4096,
                "temperature", 0.7
            );

            long startTime = System.currentTimeMillis();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                "https://api.openai.com/v1/chat/completions", request, Map.class);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("⏱️ OpenAI API call completed in {}ms", duration);

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");

                    if (content != null && !content.trim().isEmpty()) {
                        logger.info("✅ OpenAI responded successfully with {} characters", content.length());
                        AssistantMessage assistantMessage = new AssistantMessage(content.trim());
                        Generation generation = new Generation(assistantMessage);
                        return new ChatResponse(List.of(generation));
                    }
                }
            }

            throw new RuntimeException("Invalid or empty response from OpenAI API");
        } catch (Exception e) {
            logger.error("❌ OpenAI API call failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Call Google Gemini API
     */
    private ChatResponse callGemini(String promptText) {
        String apiKey = providerService.getApiKeyForProvider("gemini");
        if (apiKey == null) {
            logger.warn("⚠️ Gemini API key not available");
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                    "parts", List.of(Map.of("text", promptText))
                )),
                "generationConfig", Map.of(
                    "temperature", 0.7,
                    "maxOutputTokens", 32000,
                    "topP", 0.8,
                    "topK", 10
                ),
                "safetySettings", List.of(
                    Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_HATE_SPEECH", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE")
                )
            );

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key=" + apiKey;

            long startTime = System.currentTimeMillis();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("⏱️ Gemini API call completed in {}ms", duration);

            if (response != null && response.containsKey("candidates")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");

                if (!candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> content = (Map<String, Object>) candidate.get("content");

                    if (content != null && content.containsKey("parts")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                        if (!parts.isEmpty()) {
                            String responseText = (String) parts.get(0).get("text");
                            if (responseText != null && !responseText.trim().isEmpty()) {
                                logger.info("✅ Gemini responded successfully with {} characters", responseText.length());
                                AssistantMessage assistantMessage = new AssistantMessage(responseText.trim());
                                Generation generation = new Generation(assistantMessage);
                                return new ChatResponse(List.of(generation));
                            }
                        }
                    }
                }
            }

            throw new RuntimeException("Invalid or empty response from Gemini API");
        } catch (Exception e) {
            logger.error("❌ Gemini API call failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Call Anthropic Claude API
     */
    private ChatResponse callClaude(String promptText) {
        String apiKey = providerService.getApiKeyForProvider("claude");
        if (apiKey == null) {
            logger.warn("⚠️ Claude API key not available");
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> requestBody = Map.of(
                "model", "claude-3-5-sonnet-20241022",
                "max_tokens", 4096,
                "temperature", 0.7,
                "messages", List.of(Map.of(
                    "role", "user",
                    "content", promptText
                ))
            );

            long startTime = System.currentTimeMillis();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                "https://api.anthropic.com/v1/messages", request, Map.class);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("⏱️ Claude API call completed in {}ms", duration);

            if (response != null && response.containsKey("content")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
                if (!content.isEmpty()) {
                    String responseText = (String) content.get(0).get("text");
                    if (responseText != null && !responseText.trim().isEmpty()) {
                        logger.info("✅ Claude responded successfully with {} characters", responseText.length());
                        AssistantMessage assistantMessage = new AssistantMessage(responseText.trim());
                        Generation generation = new Generation(assistantMessage);
                        return new ChatResponse(List.of(generation));
                    }
                }
            }

            throw new RuntimeException("Invalid or empty response from Claude API");
        } catch (Exception e) {
            logger.error("❌ Claude API call failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Call local Ollama API
     */
    private ChatResponse callOllama(String promptText) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                "model", "llama3.1:8b",
                "prompt", promptText,
                "stream", false,
                "options", Map.of(
                    "temperature", 0.7,
                    "num_ctx", 2048
                )
            );

            String ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL");
            if (ollamaBaseUrl == null) {
                ollamaBaseUrl = "http://localhost:11434";
            }

            long startTime = System.currentTimeMillis();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                ollamaBaseUrl + "/api/generate", request, Map.class);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("⏱️ Ollama API call completed in {}ms", duration);

            if (response != null && response.containsKey("response")) {
                String responseText = (String) response.get("response");
                if (responseText != null && !responseText.trim().isEmpty()) {
                    logger.info("✅ Ollama responded successfully with {} characters", responseText.length());
                    AssistantMessage assistantMessage = new AssistantMessage(responseText.trim());
                    Generation generation = new Generation(assistantMessage);
                    return new ChatResponse(List.of(generation));
                }
            }

            throw new RuntimeException("Invalid or empty response from Ollama API");
        } catch (Exception e) {
            logger.error("❌ Ollama API call failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Pattern matching fallback (same as before)
     */
    private ChatResponse callPatternMatching(String promptText) {
        logger.info("🔄 Using pattern matching fallback for: {}",
            promptText.length() > 50 ? promptText.substring(0, 50) + "..." : promptText);

        // Extract user input from the full prompt
        String userInput = promptText;

        if (promptText.contains("User: ")) {
            int startIndex = promptText.lastIndexOf("User: ") + "User: ".length();
            int endIndex = promptText.indexOf("\n", startIndex);
            if (endIndex == -1) endIndex = promptText.length();
            if (startIndex < endIndex) {
                userInput = promptText.substring(startIndex, endIndex).trim();
            }
        }

        String lower = userInput.toLowerCase();
        String response;

        // Enhanced pattern matching for natural language commands
        if (lower.contains("create") && lower.contains("collection")) {
            response = "tool exec mongo-mcp-server-test createCollection";
        } else if (lower.contains("list") && (lower.contains("database") || lower.contains("db"))) {
            response = "tool exec mongo-mcp-server-test listDatabases";
        } else if (lower.contains("collection") && lower.contains("list")) {
            response = "tool exec mongo-mcp-server-test listCollections";
        } else if (lower.contains("server") && (lower.contains("list") || lower.contains("show"))) {
            response = "server list";
        } else if (lower.contains("ping") || (lower.contains("test") && lower.contains("server"))) {
            response = "tool exec mongo-mcp-server-test ping";
        } else if (lower.contains("show") && lower.contains("tool")) {
            response = "tool all";
        } else if (lower.contains("list") && lower.contains("tool")) {
            response = "tool all";
        } else if (lower.contains("help")) {
            response = "help";
        } else if (lower.contains("clear")) {
            response = "clear";
        } else if (lower.contains("exit") || lower.contains("quit")) {
            response = "exit";
        } else if (lower.contains("hi") || lower.contains("hello")) {
            response = "help";
        } else {
            response = "help";
        }

        logger.info("✅ Pattern matching processed '{}' → '{}'", userInput, response);
        AssistantMessage assistantMessage = new AssistantMessage(response);
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }

    /**
     * Extract prompt text from Prompt object
     */
    private String extractPromptText(Prompt prompt) {
        String promptText = "";
        if (!prompt.getInstructions().isEmpty()) {
            var message = prompt.getInstructions().get(0);
            if (message instanceof UserMessage) {
                promptText = ((UserMessage) message).getText();
            } else {
                promptText = message.toString();
            }
        }
        return promptText;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
        return null;
    }
}