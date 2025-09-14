package com.deepai.mcpclient.service;

import com.deepai.mcpclient.model.McpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for IntentProcessor to verify dynamic tool discovery
 * and intent analysis against various MCP tool types
 */
@ExtendWith(MockitoExtension.class)
public class IntentProcessorTest {

    @Mock
    private McpClientService mcpClientService;
    
    @Mock
    private ChatModel chatModel;
    
    private ObjectMapper objectMapper;
    private IntentProcessor intentProcessor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        intentProcessor = new IntentProcessor(mcpClientService, chatModel, objectMapper);
    }

    @Test
    void testAnalyzeIntentWithDatabaseTools() {
        // Given: MongoDB server with database tools
        String serverId = "mongodb-server";
        List<McpTool> dbTools = List.of(
            new McpTool("listDatabases", "List all databases", Map.of(), Map.of()),
            new McpTool("listCollections", "List collections in database", 
                Map.of("properties", Map.of("database", Map.of("type", "string"))), Map.of())
        );
        
        setupMockServerWithTools(serverId, dbTools);
        
        // Mock AI response for database listing intent
        String aiResponse = """
            {
              "requiresToolExecution": true,
              "toolName": "listDatabases",
              "serverId": "mongodb-server",
              "parameters": {},
              "confidence": 0.9,
              "reasoning": "User wants to see available databases"
            }
            """;
        
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(createMockChatResponse(aiResponse));
        
        // When: Analyze database-related user input
        IntentProcessor.Intent intent = intentProcessor.analyzeIntent("Show me all databases");
        
        // Then: Verify intent analysis
        assertThat(intent.requiresToolExecution()).isTrue();
        assertThat(intent.toolName()).isEqualTo("listDatabases");
        assertThat(intent.serverId()).isEqualTo("mongodb-server");
        assertThat(intent.confidence()).isEqualTo(0.9);
        assertThat(intent.reasoning()).contains("databases");
        
        verify(mcpClientService).getServerIds();
        verify(mcpClientService).isServerHealthy(serverId);
        verify(mcpClientService).listTools(serverId);
        verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void testAnalyzeIntentWithFileSystemTools() {
        // Given: File system server with file operations
        String serverId = "filesystem-server";
        List<McpTool> fileTools = List.of(
            new McpTool("listFiles", "List files in directory", 
                Map.of("properties", Map.of("path", Map.of("type", "string"))), Map.of()),
            new McpTool("readFile", "Read file content",
                Map.of("properties", Map.of("filename", Map.of("type", "string"))), Map.of())
        );
        
        setupMockServerWithTools(serverId, fileTools);
        
        // Mock AI response for file listing intent
        String aiResponse = """
            {
              "requiresToolExecution": true,
              "toolName": "listFiles",
              "serverId": "filesystem-server",
              "parameters": {"path": "/home/user"},
              "confidence": 0.85,
              "reasoning": "User wants to list files in a directory"
            }
            """;
        
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(createMockChatResponse(aiResponse));
        
        // When: Analyze file-related user input
        IntentProcessor.Intent intent = intentProcessor.analyzeIntent("Show me files in /home/user");
        
        // Then: Verify intent analysis
        assertThat(intent.requiresToolExecution()).isTrue();
        assertThat(intent.toolName()).isEqualTo("listFiles");
        assertThat(intent.serverId()).isEqualTo("filesystem-server");
        assertThat(intent.parameters()).containsEntry("path", "/home/user");
        assertThat(intent.confidence()).isEqualTo(0.85);
        assertThat(intent.reasoning()).contains("files");
    }

    @Test
    void testAnalyzeIntentWithMultipleServers() {
        // Given: Multiple servers with different tool types
        String mongoId = "mongodb";
        String gitId = "git-server";
        
        List<McpTool> mongoTools = List.of(
            new McpTool("listDatabases", "List databases", Map.of(), Map.of())
        );
        
        List<McpTool> gitTools = List.of(
            new McpTool("getCommits", "Get recent commits", Map.of(), Map.of())
        );
        
        when(mcpClientService.getServerIds()).thenReturn(List.of(mongoId, gitId));
        when(mcpClientService.isServerHealthy(mongoId)).thenReturn(Mono.just(true));
        when(mcpClientService.isServerHealthy(gitId)).thenReturn(Mono.just(true));
        when(mcpClientService.listTools(mongoId)).thenReturn(Mono.just(mongoTools));
        when(mcpClientService.listTools(gitId)).thenReturn(Mono.just(gitTools));
        
        // Mock AI response choosing git tool
        String aiResponse = """
            {
              "requiresToolExecution": true,
              "toolName": "getCommits",
              "serverId": "git-server",
              "parameters": {},
              "confidence": 0.8,
              "reasoning": "User wants to see recent git commits"
            }
            """;
        
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(createMockChatResponse(aiResponse));
        
        // When: Analyze git-related request with multiple servers available
        IntentProcessor.Intent intent = intentProcessor.analyzeIntent("What are the latest commits?");
        
        // Then: Verify correct server and tool selected
        assertThat(intent.requiresToolExecution()).isTrue();
        assertThat(intent.toolName()).isEqualTo("getCommits");
        assertThat(intent.serverId()).isEqualTo("git-server");
        
        // Verify all servers were discovered
        verify(mcpClientService).listTools(mongoId);
        verify(mcpClientService).listTools(gitId);
    }

    @Test
    void testAnalyzeIntentWithNoToolExecution() {
        // Given: Conversational input that doesn't require tools but has available servers with tools
        String serverId = "test-server";
        List<McpTool> tools = List.of(
            new McpTool("someAvailableTool", "An available tool", Map.of(), Map.of())
        );
        setupMockServerWithTools(serverId, tools);
        
        // Mock AI response for conversational input
        String aiResponse = """
            {
              "requiresToolExecution": false,
              "toolName": null,
              "serverId": null,
              "parameters": {},
              "confidence": 0.9,
              "reasoning": "This is a greeting, no tool execution needed"
            }
            """;
        
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(createMockChatResponse(aiResponse));
        
        // When: Analyze greeting
        IntentProcessor.Intent intent = intentProcessor.analyzeIntent("Hello! How are you?");
        
        // Then: Verify no tool execution
        assertThat(intent.requiresToolExecution()).isFalse();
        assertThat(intent.toolName()).isNull();
        assertThat(intent.serverId()).isNull();
        assertThat(intent.reasoning()).contains("greeting");
    }

    @Test
    void testAnalyzeIntentWithNoServersAvailable() {
        // Given: No MCP servers available
        when(mcpClientService.getServerIds()).thenReturn(List.of());
        
        // When: Analyze intent with no servers
        IntentProcessor.Intent intent = intentProcessor.analyzeIntent("List databases");
        
        // Then: Verify appropriate response
        assertThat(intent.requiresToolExecution()).isFalse();
        assertThat(intent.reasoning()).contains("No MCP tools available");
        
        verify(mcpClientService).getServerIds();
        // Should not call other methods since no servers available
        verify(mcpClientService, never()).listTools(any());
        verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void testAnalyzeIntentWithUnhealthyServer() {
        // Given: Server that is not healthy
        String serverId = "unhealthy-server";
        when(mcpClientService.getServerIds()).thenReturn(List.of(serverId));
        when(mcpClientService.isServerHealthy(serverId)).thenReturn(Mono.just(false));
        
        // When: Analyze intent with unhealthy server
        IntentProcessor.Intent intent = intentProcessor.analyzeIntent("List databases");
        
        // Then: Verify no tools discovered from unhealthy server
        assertThat(intent.requiresToolExecution()).isFalse();
        assertThat(intent.reasoning()).contains("No MCP tools available");
        
        verify(mcpClientService).getServerIds();
        verify(mcpClientService).isServerHealthy(serverId);
        verify(mcpClientService, never()).listTools(serverId); // Should not list tools from unhealthy server
    }

    @Test
    void testFallbackIntentProcessing() {
        // Given: Server with tools, but AI analysis fails
        String serverId = "test-server";
        List<McpTool> tools = List.of(
            new McpTool("testTool", "A test tool for fallback testing", Map.of(), Map.of())
        );
        
        setupMockServerWithTools(serverId, tools);
        
        // Mock AI throwing exception
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenThrow(new RuntimeException("AI service unavailable"));
        
        // When: Analyze intent that matches tool name (fallback mode)
        IntentProcessor.Intent intent = intentProcessor.analyzeIntent("Use testTool");
        
        // Then: Verify fallback intent processing
        assertThat(intent.requiresToolExecution()).isTrue();
        assertThat(intent.toolName()).isEqualTo("testTool");
        assertThat(intent.serverId()).isEqualTo("test-server");
        assertThat(intent.confidence()).isEqualTo(0.4); // Low confidence for fallback
        assertThat(intent.reasoning()).contains("Fallback keyword matching");
    }

    @Test
    void testGetServerTools() {
        // Given: Server with tools
        String serverId = "test-server";
        List<McpTool> tools = List.of(
            new McpTool("tool1", "First tool", Map.of(), Map.of()),
            new McpTool("tool2", "Second tool", Map.of(), Map.of())
        );
        
        setupMockServerWithTools(serverId, tools);
        
        // When: Get tools for specific server
        List<McpTool> serverTools = intentProcessor.getServerTools(serverId);
        
        // Then: Verify tools returned
        assertThat(serverTools).hasSize(2);
        assertThat(serverTools).extracting(McpTool::name).containsExactly("tool1", "tool2");
    }

    @Test
    void testGetServerToolCounts() {
        // Given: Multiple servers with different tool counts
        String server1 = "server1";
        String server2 = "server2";
        
        List<McpTool> server1Tools = List.of(
            new McpTool("tool1", "Tool 1", Map.of(), Map.of()),
            new McpTool("tool2", "Tool 2", Map.of(), Map.of()),
            new McpTool("tool3", "Tool 3", Map.of(), Map.of())
        );
        
        List<McpTool> server2Tools = List.of(
            new McpTool("toolA", "Tool A", Map.of(), Map.of()),
            new McpTool("toolB", "Tool B", Map.of(), Map.of())
        );
        
        when(mcpClientService.getServerIds()).thenReturn(List.of(server1, server2));
        when(mcpClientService.isServerHealthy(server1)).thenReturn(Mono.just(true));
        when(mcpClientService.isServerHealthy(server2)).thenReturn(Mono.just(true));
        when(mcpClientService.listTools(server1)).thenReturn(Mono.just(server1Tools));
        when(mcpClientService.listTools(server2)).thenReturn(Mono.just(server2Tools));
        
        // When: Get tool counts for all servers
        Map<String, Integer> toolCounts = intentProcessor.getServerToolCounts();
        
        // Then: Verify tool counts
        assertThat(toolCounts).hasSize(2);
        assertThat(toolCounts.get(server1)).isEqualTo(3);
        assertThat(toolCounts.get(server2)).isEqualTo(2);
    }

    @Test
    void testRefreshToolCache() {
        // Given: Server with initial tools
        String serverId = "test-server";
        List<McpTool> initialTools = List.of(
            new McpTool("oldTool", "Old tool", Map.of(), Map.of())
        );
        
        setupMockServerWithTools(serverId, initialTools);
        
        // First call to populate cache
        intentProcessor.getServerTools(serverId);
        
        // Update tools for refresh
        List<McpTool> newTools = List.of(
            new McpTool("newTool", "New tool", Map.of(), Map.of())
        );
        
        when(mcpClientService.listTools(serverId)).thenReturn(Mono.just(newTools));
        
        // When: Refresh tool cache
        intentProcessor.refreshToolCache();
        List<McpTool> refreshedTools = intentProcessor.getServerTools(serverId);
        
        // Then: Verify tools were refreshed
        assertThat(refreshedTools).hasSize(1);
        assertThat(refreshedTools.get(0).name()).isEqualTo("newTool");
        
        // Verify listTools was called again after refresh
        verify(mcpClientService, times(2)).listTools(serverId);
    }

    /**
     * Helper method to set up mock server with tools
     */
    private void setupMockServerWithTools(String serverId, List<McpTool> tools) {
        when(mcpClientService.getServerIds()).thenReturn(List.of(serverId));
        when(mcpClientService.isServerHealthy(serverId)).thenReturn(Mono.just(true));
        when(mcpClientService.listTools(serverId)).thenReturn(Mono.just(tools));
    }

    /**
     * Helper method to create mock ChatResponse by creating a custom implementation
     * This avoids mocking final methods in the Spring AI API
     */
    private ChatResponse createMockChatResponse(String content) {
        // Create real objects instead of mocking final methods
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);
        
        // Create a custom ChatResponse implementation to avoid mocking final methods
        return new ChatResponse(List.of(generation));
    }
}
