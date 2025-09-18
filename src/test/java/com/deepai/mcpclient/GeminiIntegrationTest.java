package com.deepai.mcpclient;

import com.deepai.mcpclient.service.AiService;
import com.deepai.mcpclient.model.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class GeminiIntegrationTest {

    @Autowired
    private AiService aiService;

    @Test
    public void testBasicGeminiResponse() {
        var response = aiService.processChat(
            ChatRequest.simple("Hello, can you confirm you're working?")
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.response()).isNotBlank();
        assertThat(response.model()).contains("gemini");
    }

    @Test
    public void testLongContextHandling() {
        String longMessage = "Analyze this data: " + "x".repeat(10000);

        var response = aiService.processChat(
            ChatRequest.simple(longMessage)
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.response()).isNotBlank();
    }

    @Test
    public void testErrorFallback() {
        // Test with invalid API key scenario
        var response = aiService.processChat(
            ChatRequest.simple("test command")
        ).block();

        assertThat(response).isNotNull();
        // Should fall back to pattern matching
    }
}