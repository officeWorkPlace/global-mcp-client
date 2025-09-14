package com.deepai.mcpclient.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini AI Configuration
 * Custom implementation for Spring AI 1.0.1 compatibility
 */
@Configuration
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
public class GeminiConfiguration {

    @Value("${gemini.api.key:#{systemEnvironment['GEMINI_API_KEY']}}")
    private String geminiApiKey;
    
    private final String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

    @Bean
    @Primary
    public ChatModel geminiChatModel() {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new IllegalStateException("Gemini API key is required. Set 'gemini.api.key' in application.properties or 'GEMINI_API_KEY' environment variable");
        }
        return new ChatModel() {
            private final RestTemplate restTemplate = new RestTemplate();

            @Override
            public ChatResponse call(Prompt prompt) {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("x-goog-api-key", geminiApiKey);

                    String promptText = prompt.getContents();
                    
                    Map<String, Object> requestBody = Map.of(
                        "contents", List.of(
                            Map.of("parts", List.of(
                                Map.of("text", promptText)
                            ))
                        )
                    );

                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(geminiApiUrl, request, Map.class);
                    
                    if (response != null && response.containsKey("candidates")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                        if (!candidates.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                            if (!parts.isEmpty()) {
                                String responseText = (String) parts.get(0).get("text");
                                
                                // Convert to Spring AI ChatResponse format
                                var generation = new org.springframework.ai.chat.model.Generation(
                                    new org.springframework.ai.chat.messages.AssistantMessage(responseText)
                                );
                                return new ChatResponse(List.of(generation));
                            }
                        }
                    }
                    
                    // Fallback response
                    var generation = new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage("I'm here to help with your MCP operations!")
                    );
                    return new ChatResponse(List.of(generation));
                    
                } catch (Exception e) {
                    System.err.println("Gemini API error: " + e.getMessage());
                    // Fallback response
                    var generation = new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage(
                            "I'm your MCP assistant. How can I help you with database operations today?"
                        )
                    );
                    return new ChatResponse(List.of(generation));
                }
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(call(prompt));
            }
        };
    }
}
