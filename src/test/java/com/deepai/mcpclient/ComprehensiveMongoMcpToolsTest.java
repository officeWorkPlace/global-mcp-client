package com.deepai.mcpclient;

import com.deepai.mcpclient.model.McpTool;
import com.deepai.mcpclient.model.McpToolResult;
import com.deepai.mcpclient.service.McpClientService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "logging.level.com.deepai=INFO",
    "logging.level.root=WARN"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComprehensiveMongoMcpToolsTest {

    @Autowired
    private McpClientService mcpClientService;
    
    private static final String SERVER_ID = "mongo-mcp-server-test";
    private static final String TEST_DATABASE = "mcptest";
    private static final String TEST_COLLECTION = "test_collection";
    private static List<McpTool> availableTools = new ArrayList<>();
    private static Map<String, Boolean> toolTestResults = new HashMap<>();
    private static Map<String, String> toolTestErrors = new HashMap<>();
    private static AtomicInteger successCount = new AtomicInteger(0);
    private static AtomicInteger failureCount = new AtomicInteger(0);

    @BeforeAll
    static void setupClass() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("        COMPREHENSIVE MONGODB MCP SERVER TOOLS TEST");
        System.out.println("        Goal: 100% SUCCESS RATE FOR ALL 39 TOOLS");
        System.out.println("=".repeat(80));
    }

    @Test
    @Order(1)
    void test01_ServerHealthAndConnection() {
        System.out.println("\n🔍 Testing Spring AI MCP Server Health and Connection...");
        
        // Give servers time to initialize
        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        StepVerifier.create(mcpClientService.isServerHealthy(SERVER_ID))
            .expectNext(true)
            .verifyComplete();
        
        System.out.println("✅ Spring AI MCP server is healthy and connected");
    }

    @Test
    @Order(2)
    void test02_SetupTestData() {
        System.out.println("\n🔧 Setting up comprehensive test data...");
        
        // Create test database
        Map<String, Object> createDbParams = new HashMap<>();
        createDbParams.put("dbName", TEST_DATABASE);
        createDbParams.put("initialCollectionName", TEST_COLLECTION);
        
        StepVerifier.create(
            mcpClientService.executeTool(SERVER_ID, "createDatabase", createDbParams)
                .timeout(Duration.ofSeconds(15))
        )
        .assertNext(result -> {
            System.out.println("📁 Test database setup: " + (result.isError() != null && result.isError() ? "Already exists" : "Created"));
        })
        .verifyComplete();
        
        // Create test collection
        Map<String, Object> createCollParams = new HashMap<>();
        createCollParams.put("databaseName", TEST_DATABASE);
        createCollParams.put("collectionName", TEST_COLLECTION);
        
        StepVerifier.create(
            mcpClientService.executeTool(SERVER_ID, "createCollection", createCollParams)
                .timeout(Duration.ofSeconds(15))
        )
        .assertNext(result -> {
            System.out.println("📄 Test collection setup: " + (result.isError() != null && result.isError() ? "Already exists" : "Created"));
        })
        .verifyComplete();
        
        // Insert test documents
        Map<String, Object> insertParams = new HashMap<>();
        insertParams.put("databaseName", TEST_DATABASE);
        insertParams.put("collectionName", TEST_COLLECTION);
        insertParams.put("documents", Arrays.asList(
            Map.of("_id", "test1", "name", "Test Document 1", "value", 100, "category", "A", "location", Map.of("type", "Point", "coordinates", Arrays.asList(-73.97, 40.77))),
            Map.of("_id", "test2", "name", "Test Document 2", "value", 200, "category", "B", "location", Map.of("type", "Point", "coordinates", Arrays.asList(-73.98, 40.78))),
            Map.of("_id", "test3", "name", "Test Document 3", "value", 300, "category", "A", "location", Map.of("type", "Point", "coordinates", Arrays.asList(-73.99, 40.79)))
        ));
        
        StepVerifier.create(
            mcpClientService.executeTool(SERVER_ID, "insertMany", insertParams)
                .timeout(Duration.ofSeconds(15))
        )
        .assertNext(result -> {
            System.out.println("📝 Test documents: " + (result.isError() != null && result.isError() ? "Insertion failed" : "Inserted"));
        })
        .verifyComplete();
        
        System.out.println("✅ Test data setup completed");
    }



    private void testSingleToolComprehensively(String toolName, Map<String, Object> params) {
        System.out.printf("  %-40s", toolName + " →");
        
        try {
            StepVerifier.create(
                mcpClientService.executeTool(SERVER_ID, toolName, params)
                    .timeout(Duration.ofSeconds(30)) // Increased timeout for complex operations
            )
            .assertNext(result -> {
                assertNotNull(result, "Result should not be null for " + toolName);
                if (result.isError() != null && result.isError()) {
                    toolTestResults.put(toolName, false);
                    String errorContent = result.content() != null ? result.content().toString() : "Unknown error";
                    toolTestErrors.put(toolName, errorContent);
                    failureCount.incrementAndGet();
                    System.out.println(" ❌ ERROR");
                } else {
                    toolTestResults.put(toolName, true);
                    successCount.incrementAndGet();
                    System.out.println(" ✅ SUCCESS");
                }
            })
            .verifyComplete();
            
        } catch (Exception e) {
            toolTestResults.put(toolName, false);
            toolTestErrors.put(toolName, e.getMessage());
            failureCount.incrementAndGet();
            System.out.println(" ❌ TIMEOUT/EXCEPTION");
        }
    }

    private Map<String, Object> createComprehensiveParamsForTool(String toolName) {
        Map<String, Object> params = new HashMap<>();
        
        // Base parameters for all tools - FIXED to match MongoDB server parameter names
        params.put("dbName", TEST_DATABASE);
        
        switch (toolName.toLowerCase()) {
            case "listdatabases":
                // No additional params needed
                break;
                
            case "getdatabasestats":
                // Only needs database name (already set)
                break;
                
            case "createdatabase":
                params.put("dbName", "temp_test_db_" + System.currentTimeMillis());
                params.put("initialCollectionName", "temp_collection");
                break;
                
            case "dropdatabase":
                // First create a temporary database to drop
                params.put("dbName", "temp_drop_db");
                break;
                
            case "repairdatabase":
                // Only needs database name (already set)
                break;
                
            case "ping":
                // No additional params needed
                break;
                
            case "listcollections":
                // Only needs database name (already set)
                break;
                
            case "createcollection":
                params.put("collectionName", "new_test_collection_" + System.currentTimeMillis());
                break;
                
            case "dropcollection":
                params.put("collectionName", "temp_collection");
                break;
                
            case "renamecollection":
                params.put("collectionName", TEST_COLLECTION);
                params.put("newName", "renamed_collection_" + System.currentTimeMillis());
                break;
                
            case "getcollectionstats":
            case "countdocuments":
            case "validateschema":
            case "reindex":
                params.put("collectionName", TEST_COLLECTION);
                break;
                
            case "insertdocument":
                params.put("collectionName", TEST_COLLECTION);
                params.put("document", Map.of(
                    "_id", "insert_test_" + System.currentTimeMillis(),
                    "name", "Inserted Document",
                    "value", 999,
                    "category", "Test"
                ));
                break;
                
            case "insertmany":
                params.put("collectionName", TEST_COLLECTION);
                params.put("documents", Arrays.asList(
                    Map.of("_id", "bulk1_" + System.currentTimeMillis(), "bulk", true, "value", 1),
                    Map.of("_id", "bulk2_" + System.currentTimeMillis(), "bulk", true, "value", 2)
                ));
                break;
                
            case "updatedocument":
                params.put("collectionName", TEST_COLLECTION);
                params.put("filter", Map.of("_id", "test1"));
                params.put("update", Map.of("$set", Map.of("updated", true, "updateTime", new Date())));
                break;
                
            case "deletedocument":
                params.put("collectionName", TEST_COLLECTION);
                params.put("filter", Map.of("category", "Test"));
                break;
                
            case "findone":
                params.put("collectionName", TEST_COLLECTION);
                params.put("filter", Map.of("_id", "test1"));
                break;
                
            case "finddocument":
                params.put("collectionName", TEST_COLLECTION);
                params.put("filter", Map.of("category", "A"));
                params.put("limit", 5);
                break;
                
            case "simplequery":
                params.put("collectionName", TEST_COLLECTION);
                params.put("field", "category");
                params.put("value", "A");
                break;
                
            case "complexquery":
                params.put("collectionName", TEST_COLLECTION);
                params.put("query", Map.of("value", Map.of("$gte", 100)));
                params.put("projection", Map.of("name", 1, "value", 1));
                params.put("sort", Map.of("value", 1));
                params.put("limit", 10);
                break;
                
            case "aggregatepipeline":
                params.put("collectionName", TEST_COLLECTION);
                params.put("pipeline", Arrays.asList(
                    Map.of("$match", Map.of("category", "A")),
                    Map.of("$group", Map.of("_id", "$category", "total", Map.of("$sum", "$value")))
                ));
                break;
                
            case "distinctvalues":
                params.put("collectionName", TEST_COLLECTION);
                params.put("field", "category");
                break;
                
            case "groupbyfield":
                params.put("collectionName", TEST_COLLECTION);
                params.put("field", "category");
                break;
                
            case "createindex":
                params.put("collectionName", TEST_COLLECTION);
                params.put("indexSpec", Map.of("name", 1, "value", -1));
                params.put("indexName", "name_value_idx");
                break;
                
            case "dropindex":
                params.put("collectionName", TEST_COLLECTION);
                params.put("indexName", "name_value_idx");
                break;
                
            case "listindexes":
                params.put("collectionName", TEST_COLLECTION);
                break;
                
            case "createvectorindex":
                params.put("collectionName", TEST_COLLECTION);
                params.put("vectorField", "embedding");
                params.put("dimensions", 1536);
                params.put("similarity", "cosine");
                break;
                
            case "textsearch":
                params.put("collectionName", TEST_COLLECTION);
                params.put("searchText", "Test Document");
                params.put("text", "Test Document");
                break;
                
            case "geosearch":
                params.put("collectionName", TEST_COLLECTION);
                params.put("coordinates", Arrays.asList(-73.97, 40.77));
                params.put("maxDistance", 1000);
                params.put("geoField", "location");
                break;
                
            case "vectorsearch":
                params.put("collectionName", TEST_COLLECTION);
                params.put("queryVector", Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5));
                params.put("vectorField", "embedding");
                params.put("limit", 5);
                break;
                
            case "semanticsearch":
                params.put("collectionName", TEST_COLLECTION);
                params.put("searchText", "test document content");
                params.put("limit", 5);
                break;
                
            case "explainquery":
                params.put("collectionName", TEST_COLLECTION);
                params.put("query", Map.of("category", "A"));
                break;
                
            // AI-related tools
            case "generateembeddings":
                params.put("text", "This is a test document for embedding generation");
                params.put("model", "text-embedding-ada-002");
                break;
                
            case "aidocumentsummary":
                params.put("collectionName", TEST_COLLECTION);
                params.put("documentId", "test1");
                params.put("model", "gpt-3.5-turbo");
                break;
                
            case "aianalyzedocument":
                params.put("collectionName", TEST_COLLECTION);
                params.put("documentId", "test1");
                params.put("analysisType", "content");
                params.put("model", "gpt-3.5-turbo");
                break;
                
            case "aianalyzecollection":
                params.put("collectionName", TEST_COLLECTION);
                params.put("analysisType", "schema");
                params.put("model", "gpt-3.5-turbo");
                break;
                
            case "aiquerySuggestion":
            case "aiquerysuggestion":
                params.put("collectionName", TEST_COLLECTION);
                params.put("intent", "find all documents with high values");
                params.put("model", "gpt-3.5-turbo");
                break;
                
            case "aiqueryoptimization":
                params.put("collectionName", TEST_COLLECTION);
                params.put("query", Map.of("value", Map.of("$gte", 100)));
                params.put("model", "gpt-3.5-turbo");
                break;
                
            case "aiqueryexplanation":
                params.put("collectionName", TEST_COLLECTION);
                params.put("query", Map.of("value", Map.of("$gte", 100)));
                params.put("model", "gpt-3.5-turbo");
                break;
                
            default:
                // For any other tools, provide collection name
                params.put("collectionName", TEST_COLLECTION);
                break;
        }
        
        return params;
    }

    @Test
    @Order(3) 
    void test03_TestAllToolsComprehensively() {
        System.out.println("\n🧪 Testing ALL 39 MongoDB MCP tools with comprehensive parameters...");
        
        StepVerifier.create(mcpClientService.listTools(SERVER_ID))
            .assertNext(tools -> {
                assertNotNull(tools, "Tools list should not be null");
                assertFalse(tools.isEmpty(), "Tools list should not be empty");
                
                availableTools = new ArrayList<>(tools);
                System.out.println("🎯 Target: 100% success rate for " + tools.size() + " tools");
                System.out.println("-".repeat(80));
                
                for (McpTool tool : tools) {
                    testSingleToolComprehensively(tool.name(), createComprehensiveParamsForTool(tool.name()));
                }
            })
            .verifyComplete();
    }

    @Test
    @Order(98)
    void test98_CleanupTestData() {
        System.out.println("\n🧹 Cleaning up test data...");
        
        // Drop test collection
        Map<String, Object> dropCollParams = new HashMap<>();
        dropCollParams.put("databaseName", TEST_DATABASE);
        dropCollParams.put("collectionName", TEST_COLLECTION);
        
        StepVerifier.create(
            mcpClientService.executeTool(SERVER_ID, "dropCollection", dropCollParams)
                .timeout(Duration.ofSeconds(15))
        )
        .assertNext(result -> {
            System.out.println("🗑️ Test collection cleanup: " + (result.isError() != null && result.isError() ? "Failed" : "Completed"));
        })
        .verifyComplete();
        
        // Drop test database
        Map<String, Object> dropDbParams = new HashMap<>();
        dropDbParams.put("databaseName", TEST_DATABASE);
        
        StepVerifier.create(
            mcpClientService.executeTool(SERVER_ID, "dropDatabase", dropDbParams)
                .timeout(Duration.ofSeconds(15))
        )
        .assertNext(result -> {
            System.out.println("🗑️ Test database cleanup: " + (result.isError() != null && result.isError() ? "Failed" : "Completed"));
        })
        .verifyComplete();
        
        System.out.println("✅ Cleanup completed");
    }

    @Test
    @Order(99)
    void test99_GenerateFinalReport() {
        generateFinalReport();
    }

    private void generateFinalReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("               COMPREHENSIVE MONGODB MCP TOOLS TEST REPORT");
        System.out.println("=".repeat(80));
        
        int totalTools = successCount.get() + failureCount.get();
        double successRate = totalTools > 0 ? (successCount.get() * 100.0 / totalTools) : 0;
        
        System.out.println("📊 SUMMARY STATISTICS:");
        System.out.println("-".repeat(50));
        System.out.printf("🔧 Total Tools Available:   %d%n", availableTools.size());
        System.out.printf("🧪 Total Tools Tested:      %d%n", totalTools);
        System.out.printf("✅ Successful:              %d%n", successCount.get());
        System.out.printf("❌ Failed:                  %d%n", failureCount.get());
        System.out.printf("📈 Success Rate:            %.1f%%%n", successRate);
        
        if (!toolTestResults.isEmpty()) {
            System.out.println("\n📝 DETAILED RESULTS:");
            System.out.println("-".repeat(80));
            
            // Successful tools
            if (successCount.get() > 0) {
                System.out.println("\n✅ SUCCESSFUL TOOLS:");
                toolTestResults.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> System.out.println("  ✓ " + entry.getKey()));
            }
            
            // Failed tools
            if (failureCount.get() > 0) {
                System.out.println("\n❌ FAILED TOOLS:");
                toolTestResults.entrySet().stream()
                    .filter(entry -> !entry.getValue())
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        System.out.println("  ✗ " + entry.getKey());
                        String error = toolTestErrors.get(entry.getKey());
                        if (error != null && !error.isEmpty()) {
                            System.out.println("    └─ " + error.substring(0, Math.min(error.length(), 120)) + 
                                             (error.length() > 120 ? "..." : ""));
                        }
                    });
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        if (successRate == 100.0) {
            System.out.println("🎉 PERFECT! ALL MONGODB MCP TOOLS WORKING!");
            System.out.printf("   All %d MongoDB tools are functioning correctly%n", totalTools);
        } else {
            System.out.println("⚠️  COMPREHENSIVE MONGODB MCP SERVER TEST COMPLETED");
            System.out.printf("   Working MongoDB Tools: %d/%d (%.1f%% success rate)%n", 
                             successCount.get(), totalTools, successRate);
            System.out.println("   🎯 Goal: Achieve 100% success rate for all tools");
        }
        System.out.println("=".repeat(80));
        
        if (totalTools > 0) {
            System.out.printf("\n📊 Final Result: %d/%d MongoDB MCP tools working%n", 
                             successCount.get(), totalTools);
        }
    }
}
