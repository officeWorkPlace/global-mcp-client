package com.deepai.mcpclient.test;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;
import java.util.List;

/**
 * Simple OpenAI API Key Test Application
 * Tests if your OpenAI API key is working correctly
 */
@SpringBootApplication
public class OpenAIApiTest implements CommandLineRunner {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "test");
        SpringApplication.run(OpenAIApiTest.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🔑 OpenAI API Key Test");
        System.out.println("======================");
        System.out.println();

        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("❌ No OpenAI API key found!");
            System.err.println("Please set the OPENAI_API_KEY environment variable");
            System.err.println("Example: export OPENAI_API_KEY='your-api-key-here'");
            System.exit(1);
        }

        // Mask API key for display
        String maskedKey = apiKey.substring(0, Math.min(7, apiKey.length())) + 
                          "..." + 
                          apiKey.substring(Math.max(0, apiKey.length() - 4));
        System.out.println("🔍 Testing API Key: " + maskedKey);
        System.out.println();

        testOpenAIApi(apiKey);
    }

    private void testOpenAIApi(String apiKey) {
        try {
            System.out.println("🌐 Connecting to OpenAI API...");

            RestTemplate restTemplate = new RestTemplate();
            
            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // Create request body
            Map<String, Object> requestBody = Map.of(
                "model", "gpt-3.5-turbo",
                "messages", List.of(
                    Map.of(
                        "role", "user",
                        "content", "Hello! This is a test message. Please respond with just 'API test successful'"
                    )
                ),
                "max_tokens", 50,
                "temperature", 0.1
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Make API call
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.openai.com/v1/chat/completions",
                request,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("✅ API Connection Successful!");
                System.out.println();
                
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null) {
                    System.out.println("📊 Response Details:");
                    System.out.println("  Model: " + responseBody.get("model"));
                    
                    Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");
                    if (usage != null) {
                        System.out.println("  Usage: " + usage.get("total_tokens") + " tokens");
                    }
                    
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        if (message != null) {
                            System.out.println("  Response: " + message.get("content"));
                        }
                    }
                }
                
                System.out.println();
                System.out.println("🎉 Your OpenAI API key is working correctly!");
                System.out.println("🚀 Ready to use OpenAI in your Spring AI applications!");
            }

        } catch (Exception e) {
            System.err.println("❌ API Test Failed!");
            System.err.println();
            System.err.println("📋 Error Details:");
            System.err.println("  " + e.getMessage());
            
            if (e.getMessage().contains("401")) {
                System.err.println("  💡 Issue: Invalid API key");
                System.err.println("     Please check your OpenAI API key is correct");
            } else if (e.getMessage().contains("403")) {
                System.err.println("  💡 Issue: Access forbidden");
                System.err.println("     Your API key may not have the required permissions");
            } else if (e.getMessage().contains("429")) {
                System.err.println("  💡 Issue: Rate limit exceeded");
                System.err.println("     You've hit your API rate limit. Try again later");
            } else {
                System.err.println("  💡 Check your internet connection and API key");
            }
            
            System.exit(1);
        }
    }
}
