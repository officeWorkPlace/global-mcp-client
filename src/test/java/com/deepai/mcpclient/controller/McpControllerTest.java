package com.deepai.mcpclient.controller;

import com.deepai.mcpclient.model.McpServerInfo;
import com.deepai.mcpclient.service.McpClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = McpController.class)
class McpControllerTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @MockBean
    private McpClientService mcpClientService;
    
    @Test
    void shouldListServers() {
        when(mcpClientService.getServerIds()).thenReturn(List.of("server1", "server2"));
        
        webTestClient.get()
            .uri("/api/servers")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .json("[\"server1\",\"server2\"]");
    }
    
    @Test
    void shouldGetServerInfo() {
        McpServerInfo serverInfo = new McpServerInfo(
            "test-server", "1.0.0", "Test server", null, "test-vendor", null);
        
        when(mcpClientService.getServerInfo(anyString()))
            .thenReturn(Mono.just(serverInfo));
        
        webTestClient.get()
            .uri("/api/servers/test-server/info")
            .exchange()
            .expectStatus().isOk()
            .expectBody(McpServerInfo.class)
            .isEqualTo(serverInfo);
    }
    
    @Test
    void shouldGetServerHealth() {
        when(mcpClientService.isServerHealthy(anyString()))
            .thenReturn(Mono.just(true));
        
        webTestClient.get()
            .uri("/api/servers/test-server/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Map.class)
            .value(map -> map.get("healthy").equals(true));
    }
    
    @Test
    void shouldHandleServerNotFound() {
        when(mcpClientService.getServerInfo(anyString()))
            .thenReturn(Mono.error(new RuntimeException("Server not found")));
        
        webTestClient.get()
            .uri("/api/servers/non-existent/info")
            .exchange()
            .expectStatus().is5xxServerError();
    }
}
