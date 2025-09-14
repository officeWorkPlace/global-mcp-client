package com.deepai.mcpclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI Configuration for Spring AI 1.0.1 with Gemini focus
 * Provides intelligent natural language processing for commands
 */
@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = true)
public class AiConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AiConfiguration.class);

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${spring.ai.google.gemini.api-key:}")
    private String geminiApiKey;
    
    @Value("${ai.provider:gemini}")
    private String aiProvider;
    
    @Value("${ai.model:gemini-1.5-flash}")
    private String aiModel;
    
    private final RestTemplate restTemplate;
    
    public AiConfiguration() {
        // Configure RestTemplate with timeouts
        this.restTemplate = new RestTemplate();
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            // Add timeout headers if needed
            return execution.execute(request, body);
        });
        // Set reasonable timeouts
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(5000); // 5 seconds connection timeout
            setReadTimeout(15000);   // 15 seconds read timeout
        }});
    }

    /**
     * ChatModel bean with Ollama integration and fallback to pattern matching
     */
    @Bean
    public ChatModel chatModel() {
        logger.info("Configuring ChatModel with Ollama integration and pattern matching fallback (Spring AI 1.0.1)");
        return new CustomChatModelImpl();
    }

    /**
     * AI Properties bean for injection
     */
    @Bean
    public AiProperties aiProperties() {
        return new AiProperties();
    }

    /**
     * Custom Chat Model implementation with Gemini integration
     */
    private class CustomChatModelImpl implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            logger.info("🔄 AiService.call() - Provider: {}, Model: {}", aiProvider, aiModel);
            
            // Extract prompt text from the first instruction
            String promptText = "";
            if (!prompt.getInstructions().isEmpty()) {
                var message = prompt.getInstructions().get(0);
                if (message instanceof UserMessage) {
                    promptText = ((UserMessage) message).getText();
                } else {
                    promptText = message.toString(); // Fallback
                }
            }
            
            try {
                switch (aiProvider.toLowerCase()) {
                    case "gemini":
                        return callGemini(promptText);
                    default:
                        logger.warn("⚠️ Unknown AI provider '{}', falling back to pattern matching", aiProvider);
                        return callPatternMatching(promptText);
                }
            } catch (Exception e) {
                logger.error("❌ AI provider '{}' failed: {}", aiProvider, e.getMessage());
                logger.info("🔄 Falling back to pattern matching");
                return callPatternMatching(promptText);
            }
        }
        
        private ChatResponse callGemini(String promptText) {
            try {
                logger.info("🔧 Gemini API call - API Key present: {}, Provider: {}, Model: {}", 
                    (geminiApiKey != null && !geminiApiKey.isEmpty()), aiProvider, aiModel);
                    
                if (geminiApiKey == null || geminiApiKey.isEmpty()) {
                    throw new RuntimeException("Gemini API key is not configured");
                }
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // Gemini API request structure
                Map<String, Object> content = Map.of(
                    "parts", List.of(Map.of("text", promptText))
                );
                
                Map<String, Object> requestBody = Map.of(
                    "contents", List.of(content),
                    "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 2048,
                        "topP", 0.8,
                        "topK", 10
                    )
                );
                
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + aiModel + ":generateContent?key=" + geminiApiKey;
                logger.info("🌐 Making Gemini API call to: {}", url.substring(0, url.indexOf("?key=")));
                
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
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content2 = (Map<String, Object>) candidates.get(0).get("content");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) content2.get("parts");
                        if (!parts.isEmpty()) {
                            String responseText = (String) parts.get(0).get("text");
                            if (responseText != null && !responseText.trim().isEmpty()) {
                                logger.info("✅ Gemini API responded successfully");
                                AssistantMessage assistantMessage = new AssistantMessage(responseText.trim());
                                Generation generation = new Generation(assistantMessage);
                                return new ChatResponse(List.of(generation));
                            }
                        }
                    }
                }
                throw new RuntimeException("Empty response from Gemini API");
            } catch (Exception e) {
                logger.error("❌ Gemini API call failed: {}", e.getMessage(), e);
                throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
            }
        }
        
        private ChatResponse callPatternMatching(String promptText) {
            // Extract user input from the full prompt - try multiple patterns
            String userInput = promptText;
            
            // Try to extract just the user's actual message
            if (promptText.contains("User: ")) {
                int startIndex = promptText.lastIndexOf("User: ") + "User: ".length();
                int endIndex = promptText.indexOf("\n", startIndex);
                if (endIndex == -1) endIndex = promptText.length();
                if (startIndex < endIndex) {
                    userInput = promptText.substring(startIndex, endIndex).trim();
                }
            } else if (promptText.contains("User input: \"")) {
                int startIndex = promptText.indexOf("User input: \"") + "User input: \"".length();
                int endIndex = promptText.lastIndexOf("\"");
                if (startIndex < endIndex) {
                    userInput = promptText.substring(startIndex, endIndex);
                }
            }
            
            String lower = userInput.toLowerCase();
            String response;
            
            logger.info("Using intelligent pattern matching (Spring AI 1.0.1): {}", userInput);
            
            // Enhanced pattern matching for natural language commands - ORDER MATTERS!
            // More specific patterns should come first
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
            } else if (lower.contains("all") && lower.contains("tool")) {
                response = "tool all";
            } else if (lower.contains("what") && lower.contains("tool")) {
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
            
            logger.info("✅ Pattern matching (Spring AI 1.0.1) processed '{}' → '{}'", userInput, response);
            AssistantMessage assistantMessage = new AssistantMessage(response);
            Generation generation = new Generation(assistantMessage);
            return new ChatResponse(List.of(generation));
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

    /**
     * Configuration properties for AI service (Spring AI 1.0.1 compatible)
     */
    public static class AiProperties {
        private boolean enabled = true;
        private String defaultModel = "gemini-1.5-flash";
        private String fallbackModel = "pattern-matching";
        private int contextWindow = 4096;
        private int maxTokens = 2048;

        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public String getFallbackModel() {
            return fallbackModel;
        }

        public void setFallbackModel(String fallbackModel) {
            this.fallbackModel = fallbackModel;
        }

        public int getContextWindow() {
            return contextWindow;
        }

        public void setContextWindow(int contextWindow) {
            this.contextWindow = contextWindow;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
}