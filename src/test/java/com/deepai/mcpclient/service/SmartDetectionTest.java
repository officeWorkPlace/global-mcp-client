package com.deepai.mcpclient.service;

import com.deepai.mcpclient.config.McpConfigurationProperties.ServerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify smart detection logic for different MCP server types
 */
@SpringBootTest
public class SmartDetectionTest {

    @Test
    void testSpringAiServerDetection() {
        System.out.println("\n🔵 Testing Spring AI Server Detection");
        
        // Spring AI server with full profile
        ServerConfig springAiServer = new ServerConfig(
            "stdio",
            "java",
            List.of(
                "-Dspring.profiles.active=mcp",
                "-Dspring.main.web-application-type=none",
                "-jar",
                "spring-boot-ai-mongo-mcp-server.jar"
            ),
            null, null, 8000, true,
            Map.of("SPRING_DATA_MONGODB_URI", "mongodb://localhost:27017")
        );
        
        assertTrue(isSpringAiMcpServer(springAiServer), "Should detect Spring AI server with profiles");
        System.out.println("✅ Spring AI server detected correctly");
        
        // Spring AI server with jar name detection
        ServerConfig springAiByJarName = new ServerConfig(
            "stdio",
            "java",
            List.of("-jar", "my-spring-ai-mcp-server.jar"),
            null, null, 8000, true, null
        );
        
        assertTrue(isSpringAiMcpServer(springAiByJarName), "Should detect Spring AI server by jar name");
        System.out.println("✅ Spring AI server detected by jar name");
    }
    
    @Test
    void testStandardMcpServerDetection() {
        System.out.println("\n🟢 Testing Standard MCP Server Detection");
        
        // Python MCP server
        ServerConfig pythonServer = new ServerConfig(
            "stdio",
            "python",
            List.of("-m", "mcp_server_filesystem", "--path", "/data"),
            null, null, 5000, true,
            Map.of("PYTHONPATH", "/opt/mcp")
        );
        
        assertFalse(isSpringAiMcpServer(pythonServer), "Should NOT detect Python server as Spring AI");
        System.out.println("✅ Python server correctly identified as standard MCP");
        
        // Node.js MCP server
        ServerConfig nodeServer = new ServerConfig(
            "stdio",
            "node",
            List.of("git-mcp-server.js", "--repo", "/path/to/repo"),
            null, null, 5000, true,
            Map.of("NODE_ENV", "production")
        );
        
        assertFalse(isSpringAiMcpServer(nodeServer), "Should NOT detect Node.js server as Spring AI");
        System.out.println("✅ Node.js server correctly identified as standard MCP");
        
        // Rust MCP server
        ServerConfig rustServer = new ServerConfig(
            "stdio",
            "./target/release/db-mcp-server",
            List.of("--config", "db.toml"),
            null, null, 8000, true, null
        );
        
        assertFalse(isSpringAiMcpServer(rustServer), "Should NOT detect Rust server as Spring AI");
        System.out.println("✅ Rust server correctly identified as standard MCP");
        
        // Go MCP server
        ServerConfig goServer = new ServerConfig(
            "stdio",
            "./go-mcp-server",
            List.of("-port", "3000", "-env", "prod"),
            null, null, 5000, true, null
        );
        
        assertFalse(isSpringAiMcpServer(goServer), "Should NOT detect Go server as Spring AI");
        System.out.println("✅ Go server correctly identified as standard MCP");
    }
    
    @Test
    void testEdgeCases() {
        System.out.println("\n⚪ Testing Edge Cases");
        
        // Java server without Spring profiles (should be standard MCP)
        ServerConfig javaWithoutSpring = new ServerConfig(
            "stdio",
            "java",
            List.of("-jar", "some-other-server.jar"),
            null, null, 8000, true, null
        );
        
        assertFalse(isSpringAiMcpServer(javaWithoutSpring), "Java without Spring profiles should be standard MCP");
        System.out.println("✅ Java without Spring profiles correctly identified as standard MCP");
        
        // Server with null args
        ServerConfig serverWithNullArgs = new ServerConfig(
            "stdio",
            "python",
            null,
            null, null, 5000, true, null
        );
        
        assertFalse(isSpringAiMcpServer(serverWithNullArgs), "Server with null args should be standard MCP");
        System.out.println("✅ Server with null args correctly handled");
        
        // Server with empty args
        ServerConfig serverWithEmptyArgs = new ServerConfig(
            "stdio",
            "node",
            List.of(),
            null, null, 5000, true, null
        );
        
        assertFalse(isSpringAiMcpServer(serverWithEmptyArgs), "Server with empty args should be standard MCP");
        System.out.println("✅ Server with empty args correctly handled");
    }
    
    /**
     * Helper method that replicates the detection logic from McpClientService
     */
    private boolean isSpringAiMcpServer(ServerConfig serverConfig) {
        String command = serverConfig.command();
        List<String> args = serverConfig.args();
        
        // Detection criteria for Spring AI servers:
        // 1. Command is "java"
        // 2. Arguments contain "-jar" and a jar file
        // 3. Arguments contain Spring-specific profiles like "-Dspring.profiles.active=mcp"
        if ("java".equalsIgnoreCase(command) && args != null) {
            boolean hasJar = args.contains("-jar");
            boolean hasSpringProfile = args.stream().anyMatch(arg -> 
                arg.contains("-Dspring.profiles.active=mcp") || 
                arg.contains("-Dspring.main.web-application-type=none"));
            
            if (hasJar && hasSpringProfile) {
                return true;
            }
        }
        
        // Additional detection: Check if jar filename suggests Spring AI
        if (args != null) {
            boolean hasSpringAiJar = args.stream().anyMatch(arg -> 
                arg.contains("spring-boot-ai-mongo-mcp-server") ||
                arg.contains("spring-ai-mcp") ||
                arg.contains("springai-mcp"));
            if (hasSpringAiJar) {
                return true;
            }
        }
        
        // Default to standard MCP connection for all other servers
        return false;
    }
}
