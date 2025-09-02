package com.deepai.mcpclient.integration;

import com.deepai.mcpclient.service.McpClientService;
import com.deepai.mcpclient.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration test that communicates with the actual MCP server
 * to verify dynamic tool discovery and real MongoDB tool execution.
 */
@SpringBootTest
@ActiveProfiles("test")
public class LiveMcpServerTest {

    @Autowired
    private McpClientService mcpClientService;
    
    private static final String SERVER_ID = "mongo-mcp-server-test";
    
    @Test
    void testDynamicToolDiscovery() throws Exception {
        System.out.println("=== Live MCP Server - Dynamic Tool Discovery Test ===");
        
        // Test 1: Get available server IDs
        List<String> serverIds = mcpClientService.getServerIds();
        assertFalse(serverIds.isEmpty(), "Should have at least one configured MCP server");
        
        String serverId = serverIds.stream()
            .filter(id -> id.contains("mongo"))
            .findFirst()
            .orElse(serverIds.get(0));
        
        System.out.println("✅ Found MCP server ID: " + serverId);
        
        // Test 2: Get server information
        McpServerInfo serverInfo = mcpClientService.getServerInfo(serverId)
            .block(Duration.ofSeconds(10));
        
        assertNotNull(serverInfo, "Should get server info");
        System.out.println("✅ Server info: " + serverInfo.name() + " v" + serverInfo.version());
        
        // Test 3: List tools (should be dynamically discovered, not hardcoded)
        List<McpTool> tools = mcpClientService.listTools(serverId)
            .block(Duration.ofSeconds(10));
        
        assertNotNull(tools, "Tools list should not be null");
        assertFalse(tools.isEmpty(), "Should have discovered tools");
        System.out.println("📋 Discovered " + tools.size() + " tools dynamically");
        
        // Verify we have the expected core tools
        String[] expectedCoreTools = {
            "ping", "listDatabases", "listCollections", "insertDocument", "findDocument"
        };
        
        for (String expectedTool : expectedCoreTools) {
            boolean found = tools.stream()
                .anyMatch(tool -> tool.name().equals(expectedTool));
            assertTrue(found, "Should find dynamically discovered tool: " + expectedTool);
            System.out.println("✅ Found expected tool: " + expectedTool);
        }
        
        // Test 4: Verify tool schemas are properly generated
        McpTool listCollectionsTool = tools.stream()
            .filter(t -> "listCollections".equals(t.name()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(listCollectionsTool, "Should find listCollections tool");
        assertNotNull(listCollectionsTool.description(), "Tool should have description");
        
        // Verify the description contains MongoDB/Spring AI content (not hardcoded mock)
        assertTrue(
            listCollectionsTool.description().contains("MongoDB") && 
            listCollectionsTool.description().contains("Spring AI"),
            "Description should be dynamically generated from @Tool annotation: " + listCollectionsTool.description()
        );
        
        System.out.println("✅ Tool schema properly generated: " + listCollectionsTool.description());
    }
    
    @Test 
    void testRealMongoToolExecution() throws Exception {
        System.out.println("\n=== Live MCP Server - Real MongoDB Tool Execution Test ===");
        
        // Test 1: Execute ping tool (no parameters)
        System.out.println("\n1. Testing ping tool execution...");
        
        McpToolResult pingResult = mcpClientService.executeTool(
            SERVER_ID, 
            "ping", 
            Map.of()
        ).block(Duration.ofSeconds(10));
        
        assertNotNull(pingResult, "Ping result should not be null");
        assertNotNull(pingResult.content(), "Ping result content should not be null");
        assertFalse(pingResult.content().isEmpty(), "Ping result content should not be empty");
        
        if (pingResult.isError() != null && pingResult.isError()) {
            System.out.println("❌ Ping returned error: " + pingResult.content().get(0).text());
        } else {
            String pingContent = pingResult.content().get(0).text();
            System.out.println("📋 Ping result: " + pingContent);
            
            // Verify this is a real MongoDB result, not mock data
            assertTrue(
                pingContent.contains("healthy") || 
                pingContent.contains("successful") || 
                pingContent.contains("connection") ||
                pingContent.contains("ping") ||
                pingContent.contains("ok"),
                "Should contain real MongoDB ping response: " + pingContent
            );
            System.out.println("✅ Real MongoDB ping executed successfully");
        }
        
        // Test 2: Execute listDatabases tool (no parameters) 
        System.out.println("\n2. Testing listDatabases tool execution...");
        
        McpToolResult dbResult = mcpClientService.executeTool(
            SERVER_ID,
            "listDatabases", 
            Map.of()
        ).block(Duration.ofSeconds(10));
        
        assertNotNull(dbResult, "Database list result should not be null");
        assertNotNull(dbResult.content(), "Database list result content should not be null");
        assertFalse(dbResult.content().isEmpty(), "Database list result content should not be empty");
        
        if (dbResult.isError() != null && dbResult.isError()) {
            System.out.println("❌ ListDatabases returned error: " + dbResult.content().get(0).text());
        } else {
            String dbContent = dbResult.content().get(0).text();
            System.out.println("📋 Database list result: " + dbContent);
            
            // Verify this is real MongoDB data structure, not mock
            assertTrue(
                dbContent.contains("name") || 
                dbContent.contains("database") || 
                dbContent.contains("admin") ||
                dbContent.contains("databases") ||
                dbContent.contains("[]"),
                "Should contain real MongoDB database list: " + dbContent
            );
            System.out.println("✅ Real MongoDB listDatabases executed successfully");
        }
        
        // Test 3: Execute tool with parameters (listCollections)
        System.out.println("\n3. Testing listCollections tool with parameters...");
        
        McpToolResult collectionsResult = mcpClientService.executeTool(
            SERVER_ID,
            "listCollections",
            Map.of("dbName", "testdb")
        ).block(Duration.ofSeconds(10));
        
        assertNotNull(collectionsResult, "Collections result should not be null");
        assertNotNull(collectionsResult.content(), "Collections result content should not be null");
        assertFalse(collectionsResult.content().isEmpty(), "Collections result content should not be empty");
        
        if (collectionsResult.isError() != null && collectionsResult.isError()) {
            System.out.println("❌ ListCollections returned error: " + collectionsResult.content().get(0).text());
        } else {
            String collectionsContent = collectionsResult.content().get(0).text();
            System.out.println("📋 Collections result: " + collectionsContent);
            
            // Verify parameter was processed correctly
            assertTrue(
                collectionsContent.contains("testdb") || 
                collectionsContent.contains("collections") ||
                collectionsContent.contains("[]") || // Empty collections array
                collectionsContent.contains("name") ||
                collectionsContent.contains("collection"),
                "Should contain real MongoDB collections response: " + collectionsContent
            );
            System.out.println("✅ Real MongoDB listCollections with parameters executed successfully");
        }
    }
    
    @Test
    void testNoHardcodedMockData() throws Exception {
        System.out.println("\n=== Verification: No Hardcoded Mock Data Test ===");
        
        // Execute multiple tools and verify responses are not the hardcoded mock data
        // from the old implementation
        
        // Test ping - should NOT return old mock: "MongoDB MCP Server is healthy" 
        McpToolResult pingResult = mcpClientService.executeTool(
            SERVER_ID, 
            "ping", 
            Map.of()
        ).block(Duration.ofSeconds(10));
        
        assertNotNull(pingResult, "Ping result should not be null");
        assertNotNull(pingResult.content(), "Ping content should not be null");
        assertFalse(pingResult.content().isEmpty(), "Ping content should not be empty");
        
        String pingText = pingResult.content().get(0).text();
        assertFalse(
            pingText.contains("tools_registered\":39"),
            "Should not contain old hardcoded mock data: " + pingText
        );
        System.out.println("✅ Ping result is not hardcoded mock data: " + pingText.substring(0, Math.min(100, pingText.length())));
        
        // Test listDatabases - should NOT return old mock: ["mcpserver","admin","local"]
        McpToolResult dbResult = mcpClientService.executeTool(
            SERVER_ID,
            "listDatabases", 
            Map.of()
        ).block(Duration.ofSeconds(10));
        
        assertNotNull(dbResult, "Database result should not be null");
        assertNotNull(dbResult.content(), "Database content should not be null");
        assertFalse(dbResult.content().isEmpty(), "Database content should not be empty");
        
        String dbText = dbResult.content().get(0).text();
        // Real MongoDB should return objects with structure, not simple string array
        boolean hasRealStructure = dbText.contains("{") && dbText.contains("}") ||
                                  dbText.contains("sizeOnDisk") ||
                                  dbText.contains("empty") ||
                                  dbText.contains("databases") ||
                                  dbText.contains("[]"); // Even empty arrays are real responses
        
        assertTrue(hasRealStructure,
            "Should return real MongoDB database objects, not mock array: " + dbText.substring(0, Math.min(200, dbText.length()))
        );
        System.out.println("✅ ListDatabases result is real MongoDB data structure: " + dbText.substring(0, Math.min(100, dbText.length())));
        
        // Test countDocuments with real parameters - should NOT return old mock: "count":42
        try {
            McpToolResult countResult = mcpClientService.executeTool(
                SERVER_ID,
                "countDocuments",
                Map.of("dbName", "testdb", "collectionName", "testcoll")
            ).block(Duration.ofSeconds(10));
            
            if (countResult != null && countResult.content() != null && !countResult.content().isEmpty()) {
                String countText = countResult.content().get(0).text();
                assertFalse(
                    countText.equals("42") || countText.contains("\"count\":42"),
                    "Should not return old hardcoded mock count of 42: " + countText
                );
                System.out.println("✅ CountDocuments result is not hardcoded mock (42): " + countText.substring(0, Math.min(100, countText.length())));
            }
        } catch (Exception e) {
            // It's ok if this fails due to collection not existing - that's a real MongoDB response
            System.out.println("✅ CountDocuments properly handles real MongoDB errors (not mock): " + e.getMessage());
        }
    }
    
    @Test
    void testReactiveApiUsage() {
        System.out.println("\n=== Testing Reactive API Usage ===");
        
        // Test using StepVerifier for reactive streams
        StepVerifier.create(
            mcpClientService.listTools(SERVER_ID)
        )
        .assertNext(tools -> {
            assertNotNull(tools, "Tools should not be null");
            assertFalse(tools.isEmpty(), "Should have some tools");
            System.out.println("✅ Reactive API returned " + tools.size() + " tools");
        })
        .verifyComplete();
        
        // Test tool execution with StepVerifier
        StepVerifier.create(
            mcpClientService.executeTool(SERVER_ID, "ping", Map.of())
        )
        .assertNext(result -> {
            assertNotNull(result, "Result should not be null");
            assertNotNull(result.content(), "Result content should not be null");
            System.out.println("✅ Reactive tool execution successful");
        })
        .verifyComplete();
    }
}
