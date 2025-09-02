package com.deepai.mcpclient;

import com.deepai.mcpclient.model.McpTool;
import com.deepai.mcpclient.model.McpToolResult;
import com.deepai.mcpclient.service.McpClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "logging.level.com.deepai=DEBUG",
    "logging.level.root=INFO"
})
public class PythonMcpEndToEndTest {

    @Autowired
    private McpClientService mcpClientService;

    @Test
    void testPythonMcpServerEndToEnd() {
        System.out.println("\n=== Python MCP Server End-to-End Integration Test ===");
        
        // Give the server some time to initialize
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Test server connection
        StepVerifier.create(mcpClientService.isServerHealthy("filesystem-python-mcp-server"))
            .expectNext(true)
            .verifyComplete();
        
        System.out.println("✓ Python MCP server is healthy and connected");

        // Test listing tools
        StepVerifier.create(mcpClientService.listTools("filesystem-python-mcp-server"))
            .assertNext(tools -> {
                assertNotNull(tools, "Tools list should not be null");
                assertFalse(tools.isEmpty(), "Tools list should not be empty");
                
                System.out.println("✓ Successfully retrieved " + tools.size() + " tools from Python MCP server");
                System.out.println("Available tools:");
                tools.forEach(tool -> 
                    System.out.println("  - " + tool.name() + ": " + tool.description())
                );
                
                // Verify some expected filesystem tools exist
                boolean hasListFiles = tools.stream().anyMatch(tool -> tool.name().equals("list_files"));
                boolean hasReadFile = tools.stream().anyMatch(tool -> tool.name().equals("read_file"));
                boolean hasCreateFile = tools.stream().anyMatch(tool -> tool.name().equals("create_file"));
                
                assertTrue(hasListFiles, "Should have list_files tool");
                assertTrue(hasReadFile, "Should have read_file tool");  
                assertTrue(hasCreateFile, "Should have create_file tool");
                
                System.out.println("✓ Verified expected filesystem tools are present");
            })
            .verifyComplete();

        // Test executing a safe tool (list_files with a safe directory)
        StepVerifier.create(mcpClientService.executeTool(
                "filesystem-python-mcp-server", 
                "list_files", 
                Map.of("path", System.getProperty("user.home"))
            ))
            .assertNext(result -> {
                assertNotNull(result, "Tool execution result should not be null");
                assertFalse(result.isError(), "Tool execution should not be an error: " + 
                    (result.isError() ? result.content() : ""));
                
                System.out.println("✓ Successfully executed list_files tool");
                System.out.println("Result: " + result.content());
            })
            .verifyComplete();

        System.out.println("✓ Python MCP Server End-to-End test completed successfully!");
    }

    @Test
    void testPythonServerInfo() {
        System.out.println("\n=== Testing Python MCP Server Info ===");
        
        StepVerifier.create(mcpClientService.getServerInfo("filesystem-python-mcp-server"))
            .assertNext(serverInfo -> {
                assertNotNull(serverInfo, "Server info should not be null");
                assertNotNull(serverInfo.name(), "Server name should not be null");
                assertNotNull(serverInfo.version(), "Server version should not be null");
                
                System.out.println("✓ Server info retrieved successfully:");
                System.out.println("  - Name: " + serverInfo.name());
                System.out.println("  - Version: " + serverInfo.version());
                System.out.println("  - Capabilities: " + serverInfo.capabilities());
            })
            .verifyComplete();
    }

    @Test
    void testOverallHealth() {
        System.out.println("\n=== Testing Overall Health Status ===");
        
        StepVerifier.create(mcpClientService.getOverallHealth())
            .assertNext(healthMap -> {
                assertNotNull(healthMap, "Health map should not be null");
                assertFalse(healthMap.isEmpty(), "Health map should not be empty");
                
                System.out.println("✓ Overall health status:");
                healthMap.forEach((serverId, healthy) -> 
                    System.out.println("  - " + serverId + ": " + (healthy ? "HEALTHY" : "UNHEALTHY"))
                );
                
                // Verify Python server is healthy
                assertTrue(healthMap.containsKey("filesystem-python-mcp-server"), 
                    "Should contain Python server health status");
                assertTrue(healthMap.get("filesystem-python-mcp-server"), 
                    "Python MCP server should be healthy");
            })
            .verifyComplete();
    }
}
