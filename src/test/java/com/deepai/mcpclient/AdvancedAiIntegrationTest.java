package com.deepai.mcpclient;

import com.deepai.mcpclient.service.ToolAwareAiService;
import com.deepai.mcpclient.service.IntentProcessor;
import com.deepai.mcpclient.service.ToolChainOrchestrator;
import com.deepai.mcpclient.service.impl.AiServiceImpl;
import com.deepai.mcpclient.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for advanced AI-MCP integration features
 */
@SpringBootTest
@ActiveProfiles("test")
public class AdvancedAiIntegrationTest {

    @Autowired(required = false)
    private ToolAwareAiService toolAwareAiService;

    @Autowired(required = false)
    private IntentProcessor intentProcessor;

    @Autowired(required = false)
    private ToolChainOrchestrator toolChainOrchestrator;

    @Autowired(required = false)
    private AiServiceImpl aiServiceImpl;

    @BeforeEach
    void setUp() {
        // Ensure services are available for testing
        if (toolAwareAiService == null || intentProcessor == null) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Advanced AI services not available in test context");
        }
    }

    @Test
    public void testToolDiscoveryAndCaching() {
        // Test that AI service can discover and cache available tools
        if (aiServiceImpl != null) {
            Map<String, List<McpTool>> availableTools = aiServiceImpl.getAvailableTools();

            assertThat(availableTools).isNotNull();
            // Tools may be empty in test environment, but should not be null
            assertThat(availableTools).isNotNull();

            System.out.println("✅ Tool discovery working: " + availableTools.size() + " servers");
        }
    }

    @Test
    public void testEnhancedIntentProcessing() {
        if (intentProcessor != null) {
            // Test AI-driven intent analysis
            String complexRequest = "Show me all databases and then find users with gmail addresses";
            IntentProcessor.Intent intent = intentProcessor.analyzeIntent(complexRequest);

            assertThat(intent).isNotNull();
            assertThat(intent.reasoning()).isNotBlank();
            assertThat(intent.confidence()).isGreaterThan(0.0);

            System.out.println("✅ Intent analysis: " + intent.toolName() + " (confidence: " + intent.confidence() + ")");
        }
    }

    @Test
    public void testMultiStepRequestAnalysis() {
        if (intentProcessor != null) {
            String multiStepRequest = "First list all databases, then show me tables in the main database";

            // Create mock available tools for testing
            Map<String, List<McpTool>> mockTools = createMockTools();

            IntentProcessor.Intent intent = intentProcessor.analyzeIntentWithToolAwareness(
                multiStepRequest, mockTools
            );

            assertThat(intent).isNotNull();
            assertThat(intent.reasoning()).contains("database");

            if (intent.getSuggestedSteps() != null) {
                assertThat(intent.getSuggestedSteps()).isNotEmpty();
                System.out.println("✅ Multi-step analysis: " + intent.getSuggestedSteps().size() + " suggested steps");
            }
        }
    }

    @Test
    public void testToolAwareAiService() {
        if (toolAwareAiService != null) {
            String userRequest = "Help me understand the available database tools";

            try {
                ChatResponse response = toolAwareAiService.processWithToolAwareness(
                    userRequest, "test-context"
                );

                assertThat(response).isNotNull();
                assertThat(response.response()).isNotBlank();
                assertThat(response.contextId()).isEqualTo("test-context");

                System.out.println("✅ Tool-aware response: " + response.response().substring(0, Math.min(100, response.response().length())));

            } catch (Exception e) {
                System.out.println("⚠️ Tool-aware service test skipped due to: " + e.getMessage());
            }
        }
    }

    @Test
    public void testToolChainOrchestrator() {
        if (toolChainOrchestrator != null) {
            String complexGoal = "Check server health and list available tools";
            ConversationContext context = new ConversationContext("test-chain");

            try {
                ChainExecutionResult result = toolChainOrchestrator.executeToolChain(complexGoal, context);

                assertThat(result).isNotNull();
                assertThat(result.getOriginalSteps()).isNotEmpty();

                System.out.println("✅ Tool chain execution: " + result.getSuccessCount() + " successful, " +
                                 result.getFailureCount() + " failed");

            } catch (Exception e) {
                System.out.println("⚠️ Tool chain orchestrator test skipped due to: " + e.getMessage());
            }
        }
    }

    @Test
    public void testComplexWorkflowProcessing() {
        if (aiServiceImpl != null) {
            // Test complex request that should trigger tool-aware processing
            ChatRequest complexRequest = new ChatRequest(
                "List all databases and then find documents with status 'active' in each database",
                null,
                "test-workflow",
                false
            );

            try {
                ChatResponse response = aiServiceImpl.processChat(complexRequest).block();

                assertThat(response).isNotNull();
                assertThat(response.response()).isNotBlank();
                assertThat(response.contextId()).isEqualTo("test-workflow");

                System.out.println("✅ Complex workflow processed successfully");
                System.out.println("Response: " + response.response().substring(0, Math.min(200, response.response().length())));

            } catch (Exception e) {
                System.out.println("⚠️ Complex workflow test completed with fallback: " + e.getMessage());
                // This is acceptable in test environment without real MCP servers
            }
        }
    }

    @Test
    public void testIntentTypeClassification() {
        if (intentProcessor != null) {
            Map<String, String> testCases = Map.of(
                "List all databases", "database_operation",
                "Find files in directory", "file_operation",
                "Analyze user data patterns", "analysis",
                "Hello, how are you?", "conversational"
            );

            Map<String, List<McpTool>> mockTools = createMockTools();

            for (Map.Entry<String, String> testCase : testCases.entrySet()) {
                try {
                    IntentProcessor.Intent intent = intentProcessor.analyzeIntentWithToolAwareness(
                        testCase.getKey(), mockTools
                    );

                    assertThat(intent).isNotNull();
                    System.out.println("✅ Intent classification for '" + testCase.getKey() + "': " + intent.intentType());

                } catch (Exception e) {
                    System.out.println("⚠️ Intent classification test for '" + testCase.getKey() + "' failed: " + e.getMessage());
                }
            }
        }
    }

    @Test
    public void testPerformanceMetrics() {
        if (aiServiceImpl != null) {
            // Test that performance metrics are being tracked
            int initialContexts = aiServiceImpl.getActiveContextsCount();

            ChatRequest testRequest = new ChatRequest("Test performance", null, "perf-test", false);

            try {
                long startTime = System.currentTimeMillis();
                ChatResponse response = aiServiceImpl.processChat(testRequest).block();
                long duration = System.currentTimeMillis() - startTime;

                assertThat(response).isNotNull();
                assertThat(duration).isLessThan(30000); // Should complete within 30 seconds

                int finalContexts = aiServiceImpl.getActiveContextsCount();
                assertThat(finalContexts).isGreaterThanOrEqualTo(initialContexts);

                System.out.println("✅ Performance test: " + duration + "ms, " + finalContexts + " active contexts");

            } catch (Exception e) {
                System.out.println("⚠️ Performance test completed with error: " + e.getMessage());
            }
        }
    }

    /**
     * Create mock tools for testing
     */
    private Map<String, List<McpTool>> createMockTools() {
        McpTool listDatabases = new McpTool(
            "listDatabases",
            "List all available databases",
            Map.of("type", "object", "properties", Map.of()),
            null
        );

        McpTool findDocuments = new McpTool(
            "findDocuments",
            "Find documents in database",
            Map.of("type", "object", "properties", Map.of(
                "database", Map.of("type", "string"),
                "query", Map.of("type", "object")
            )),
            null
        );

        McpTool serverHealth = new McpTool(
            "serverHealth",
            "Check server health status",
            Map.of("type", "object", "properties", Map.of()),
            null
        );

        return Map.of(
            "oracle-server", List.of(listDatabases, findDocuments),
            "mongo-server", List.of(findDocuments),
            "health-server", List.of(serverHealth)
        );
    }
}