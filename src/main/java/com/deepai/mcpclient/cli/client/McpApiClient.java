package com.deepai.mcpclient.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * REST API client for communicating with the MCP server
 * Handles all HTTP communication in a user-friendly way
 */
@Component
public class McpApiClient {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    
    public McpApiClient(@Value("${mcpcli.api.url:http://localhost:8081}") String baseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
    }
    
    /**
     * Get all available servers
     */
    public List<Map<String, Object>> getServers() {
        try {
            String url = baseUrl + "/api/servers";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            } else {
                throw new RuntimeException("Failed to get servers: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to connect to MCP server at " + baseUrl + ". Is it running?", e);
        }
    }
    
    /**
     * Get information about a specific server
     */
    public Map<String, Object> getServerInfo(String serverId) {
        try {
            String url = baseUrl + "/api/servers/" + serverId;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("Server not found: " + serverId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get server info for " + serverId, e);
        }
    }
    
    /**
     * Check server health
     */
    public Map<String, Object> getServerHealth(String serverId) {
        try {
            String url = baseUrl + "/api/servers/" + serverId + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("Health check failed for " + serverId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Server " + serverId + " is not responding", e);
        }
    }
    
    /**
     * Get tools available on a server
     */
    public List<Map<String, Object>> getServerTools(String serverId) {
        try {
            String url = baseUrl + "/api/servers/" + serverId + "/tools";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            } else {
                throw new RuntimeException("Failed to get tools for " + serverId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to retrieve tools from " + serverId, e);
        }
    }
    
    /**
     * Execute a tool on a server
     */
    public Map<String, Object> executeTool(String serverId, String toolName, Map<String, Object> parameters) {
        try {
            String url = baseUrl + "/api/servers/" + serverId + "/tools/" + toolName;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(parameters, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("Tool execution failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute tool " + toolName + " on " + serverId, e);
        }
    }
    
    /**
     * Ask AI assistant a question
     */
    public Map<String, Object> askAi(String question) {
        try {
            String url = baseUrl + "/api/ai/ask";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = Map.of("question", question);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("AI request failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("AI assistant is not available: " + e.getMessage(), e);
        }
    }
    
    /**
     * Start AI chat session
     */
    public Map<String, Object> chatWithAi(String message, String serverId, String contextId) {
        try {
            String url = baseUrl + "/api/ai/chat";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = Map.of(
                "message", message,
                "serverId", serverId != null ? serverId : "",
                "contextId", contextId != null ? contextId : ""
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("AI chat failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("AI chat is not available: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get AI service health
     */
    public Map<String, Object> getAiHealth() {
        try {
            String url = baseUrl + "/api/ai/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("AI health check failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("AI service is not available", e);
        }
    }

    /**
     * Get current configuration
     */
    public Map<String, Object> getConfiguration() {
        try {
            String url = baseUrl + "/api/config";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("Failed to get configuration");
            }
        } catch (Exception e) {
            throw new RuntimeException("Configuration service is not available", e);
        }
    }

    /**
     * Test configuration
     */
    public Map<String, Object> testConfiguration() {
        try {
            String url = baseUrl + "/api/config/test";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("Configuration test failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to test configuration", e);
        }
    }

    /**
     * Reset configuration
     */
    public Map<String, Object> resetConfiguration() {
        try {
            String url = baseUrl + "/api/config/reset";
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            } else {
                throw new RuntimeException("Configuration reset failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to reset configuration", e);
        }
    }
}
