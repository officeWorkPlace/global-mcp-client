package com.deepai.mcpclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepai.mcpclient.config.McpConfigurationProperties;
import com.deepai.mcpclient.model.McpServerInfo;
import com.deepai.mcpclient.model.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private ObjectMapper objectMapper;
    
    private McpClientService mcpClientService;
    
    @BeforeEach
    void setUp() {
        McpConfigurationProperties.ClientConfig clientConfig = 
            new McpConfigurationProperties.ClientConfig(30000, 
                new McpConfigurationProperties.RetryConfig(3, 2.0));
        
        McpConfigurationProperties.ServerConfig serverConfig = 
            new McpConfigurationProperties.ServerConfig(
                "stdio", "java", List.of("-jar", "test.jar"), null, null, 30000, true, Map.of());
        
        McpConfigurationProperties config = 
            new McpConfigurationProperties(clientConfig, Map.of("test-server", serverConfig));
        
        mcpClientService = new McpClientService(config, objectMapper);
    }
    
    @Test
    void shouldReturnConfiguredServers() {
        List<String> serverIds = mcpClientService.getServerIds();
        assertThat(serverIds).containsExactly("test-server");
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
