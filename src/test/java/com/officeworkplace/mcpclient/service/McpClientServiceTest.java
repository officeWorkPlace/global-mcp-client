package com.officeworkplace.mcpclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeworkplace.mcpclient.config.McpConfigurationProperties;
import com.officeworkplace.mcpclient.model.McpServerInfo;
import com.officeworkplace.mcpclient.model.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpClientServiceTest {
    
    @Mock
    private WebClient webClient;
    
    @Mock
    private ObjectMapper objectMapper;
    
    private McpClientService mcpClientService;
    
    @BeforeEach
    void setUp() {
        McpConfigurationProperties.ClientConfig clientConfig = 
            new McpConfigurationProperties.ClientConfig(30000, 100, 10, 
                new McpConfigurationProperties.RetryConfig(3, 2.0));
        
        McpConfigurationProperties.ServerConfig serverConfig = 
            new McpConfigurationProperties.ServerConfig(
                "http", "http://localhost:8080", null, null, 30000, true, null, null);
        
        McpConfigurationProperties config = 
            new McpConfigurationProperties(clientConfig, Map.of("test-server", serverConfig));
        
        mcpClientService = new McpClientService(config, webClient, objectMapper);
    }
    
    @Test
    void shouldReturnEmptyServerList() {
        List<String> serverIds = mcpClientService.getServerIds();
        assertThat(serverIds).isEmpty();
    }
    
    @Test
    void shouldReturnErrorForNonExistentServer() {
        StepVerifier.create(mcpClientService.getServerInfo("non-existent"))
            .expectError(RuntimeException.class)
            .verify();
    }
    
    @Test
    void shouldReturnFalseForUnhealthyServer() {
        StepVerifier.create(mcpClientService.isServerHealthy("non-existent"))
            .expectNext(false)
            .verifyComplete();
    }
}
