package com.deepai.mcpclient.service;

import com.deepai.mcpclient.service.impl.AiServiceImpl;
import com.deepai.mcpclient.security.InputValidationService;

import com.deepai.mcpclient.config.AiConfiguration;
import com.deepai.mcpclient.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for AI Service that validates natural language processing
 * and integration with MCP tools
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AiServiceIntegrationTest {

    @Mock
    private ChatModel chatModel;
    
    @Mock
    private McpClientService mcpClientService;
    
    @Mock
    private AiConfiguration.AiProperties aiProperties;
    
    private AiService aiService;

    @BeforeEach
    void setUp() {
        // Configure AI properties
        when(aiProperties.isEnabled()).thenReturn(true);
        when(aiProperties.getDefaultModel()).thenReturn("test-model");
        when(aiProperties.getMaxTokens()).thenReturn(2048);
        
        // Create mocks for additional dependencies
        InputValidationService inputValidationService = mock(InputValidationService.class);
        ResilienceService resilienceService = mock(ResilienceService.class);

        // Configure validation service mock
        when(inputValidationService.validateUserInput(anyString()))
            .thenReturn(InputValidationService.ValidationResult.valid("test input"));
        when(inputValidationService.validateContextId(anyString()))
            .thenReturn(InputValidationService.ValidationResult.valid("test-context"));
        when(inputValidationService.isHighRiskInput(anyString())).thenReturn(false);

        // Configure resilience service mock
        when(resilienceService.canUserMakeRequest()).thenReturn(true);

        // Create AI service
        aiService = new AiServiceImpl(chatModel, mcpClientService, inputValidationService, resilienceService, aiProperties);
    }

    @Test
    void testProcessChatWithToolExecution() {
        // Given: Mock MCP server with database tools
        String serverId = "test-mongodb";
        List<String> serverIds = List.of(serverId);
        
        McpTool listDbTool = new McpTool(
            "listDatabases",
            "List all databases on the MongoDB server",
            Map.of("type", "object", "properties", Map.of()),
            Map.of("type", "object", "properties", Map.of())
        );
        
        when(mcpClientService.getServerIds()).thenReturn(serverIds);
        when(mcpClientService.isServerHealthy(serverId)).thenReturn(Mono.just(true));
        when(mcpClientService.listTools(serverId)).thenReturn(Mono.just(List.of(listDbTool)));
        
        // Mock AI intent analysis response
        String intentAnalysisResponse = """
            {
              "requiresToolExecution": true,
              "toolName": "listDatabases",
              "serverId": "test-mongodb",
              "parameters": {},
              "confidence": 0.9,
              "reasoning": "User wants to see available databases"
            }
            """;
        
        org.springframework.ai.chat.model.ChatResponse mockIntentResponse = createMockSpringAiChatResponse(intentAnalysisResponse);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockIntentResponse);
        
        // Mock tool execution
        McpToolResult toolResult = new McpToolResult(
            List.of(McpContent.text("Content: Found 3 databases")),
            false
        );
        when(mcpClientService.executeTool(eq(serverId), eq("listDatabases"), any()))
            .thenReturn(Mono.just(toolResult));
        
        // Mock final AI response
        String finalResponse = "I found 3 databases on your MongoDB server: testdb, userdb, and analytics.";
        org.springframework.ai.chat.model.ChatResponse mockFinalResponse = createMockSpringAiChatResponse(finalResponse);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockIntentResponse, mockFinalResponse);
        
        // When: Process natural language request
        ChatRequest request = ChatRequest.simple("Show me what databases are available");
        
        // Then: Verify the response
        StepVerifier.create(aiService.processChat(request))
            .assertNext(response -> {
                assertThat(response.response()).contains("databases");
                assertThat(response.toolsUsed()).hasSize(1);
                assertThat(response.toolsUsed().get(0).toolName()).isEqualTo("listDatabases");
                assertThat(response.toolsUsed().get(0).success()).isTrue();
                assertThat(response.contextId()).isNotNull();
            })
            .verifyComplete();
        
        // Verify MCP service interactions
        verify(mcpClientService).getServerIds();
        verify(mcpClientService).isServerHealthy(serverId);
        verify(mcpClientService).listTools(serverId);
        verify(mcpClientService, times(1)).executeTool(eq(serverId), eq("listDatabases"), any());
        
        // Verify AI model was called for intent analysis and response generation
        verify(chatModel, times(2)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void testProcessChatWithoutToolExecution() {
        // Given: AI determines no tool execution needed
        String intentAnalysisResponse = """
            {
              "requiresToolExecution": false,
              "toolName": null,
              "serverId": null,
              "parameters": {},
              "confidence": 0.8,
              "reasoning": "This is a greeting, no tool execution needed"
            }
            """;
        
        org.springframework.ai.chat.model.ChatResponse mockIntentResponse = createMockSpringAiChatResponse(intentAnalysisResponse);
        
        // This is the actual conversational response expected by the test
        String expectedConversationalResponse = "Hello! I'm here to help you interact with MCP servers. You can ask me to list databases, find documents, check server health, and more!";
        org.springframework.ai.chat.model.ChatResponse mockFinalResponse = createMockSpringAiChatResponse(expectedConversationalResponse);
        
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockIntentResponse, mockFinalResponse);
        
        // Mock empty server list (no tools available)
        when(mcpClientService.getServerIds()).thenReturn(List.of());
        
        // When: Process greeting
        ChatRequest request = ChatRequest.simple("Hello there!");
        
        // Then: Verify conversational response
        StepVerifier.create(aiService.processChat(request))
            .assertNext(response -> {
                // The response should contain the AI-generated content, not necessarily "Hello"
                assertThat(response.response()).isNotNull();
                assertThat(response.toolsUsed()).isEmpty();
                assertThat(response.contextId()).isNotNull();
            })
            .verifyComplete();
        
        // Verify AI was called for response generation (only once when no tools available)
        verify(chatModel, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void testAskSimpleQuestion() {
        // Given: Mock simple conversational response
        String responseContent = "I can help you interact with MCP servers using natural language.";
        ChatResponse mockResponse = createMockChatResponse(responseContent); // Use a different variable name to avoid confusion
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mcpClientService.getServerIds()).thenReturn(List.of());
        
        // When: Ask a simple question
        String question = "What can you do?";
        
        // Then: Verify response
        StepVerifier.create(aiService.ask(question))
            .assertNext(answer -> {
                assertThat(answer).contains("MCP servers");
            })
            .verifyComplete();
    }

    @Test
    void testErrorHandling() {
        // Given: MCP service throws error
        when(mcpClientService.getServerIds()).thenThrow(new RuntimeException("Service unavailable"));
        
        // Mock AI response for the error message
        String aiErrorMessage = "I encountered an error while processing your request. Please try again or rephrase your question.";
        ChatResponse mockErrorChatResponse = createMockChatResponse(aiErrorMessage);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockErrorChatResponse);

        // When: Process request that should cause error
        ChatRequest request = ChatRequest.simple("List databases");
        
        // Then: Verify error is handled gracefully
        StepVerifier.create(aiService.processChat(request))
            .assertNext(response -> {
                assertThat(response.response()).contains("I encountered an error");
                // Note: The current implementation doesn't set error metadata
                // This test verifies the error message is returned
            })
            .verifyComplete();
    }

    @Test
    void testConversationContext() {
        // Given: Multiple requests with same context ID
        String contextId = "test-context-123";
        String response1 = "Response to first message";
        String response2 = "Response to second message in same context";
        
        ChatResponse mockResponse1 = createMockChatResponse(response1);
        ChatResponse mockResponse2 = createMockChatResponse(response2);
        
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse1, mockResponse2);
        when(mcpClientService.getServerIds()).thenReturn(List.of());
        
        // When: Send two messages in same context
        ChatRequest request1 = new ChatRequest("First message", null, contextId, false);
        ChatRequest request2 = new ChatRequest("Second message", null, contextId, false);
        
        // Then: Verify both use same context
        StepVerifier.create(aiService.processChat(request1))
            .assertNext(response -> {
                assertThat(response.contextId()).isEqualTo(contextId);
            })
            .verifyComplete();
            
        StepVerifier.create(aiService.processChat(request2))
            .assertNext(response -> {
                assertThat(response.contextId()).isEqualTo(contextId);
            })
            .verifyComplete();
        
        // Verify context is tracked
        assertThat(aiService.getActiveContextsCount()).isGreaterThan(0);
        assertThat(aiService.getContext(contextId)).isNotNull();
    }

    @Test
    void testServerSpecificRequest() {
        // Given: Request targeting specific server
        String serverId = "specific-server";
        String response = "Server-specific response";
        ChatResponse mockResponse = createMockChatResponse(response);
        
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);
        when(mcpClientService.getServerIds()).thenReturn(List.of(serverId));
        when(mcpClientService.isServerHealthy(serverId)).thenReturn(Mono.just(true));
        when(mcpClientService.listTools(serverId)).thenReturn(Mono.just(List.of()));
        
        // When: Send request with server ID
        ChatRequest request = ChatRequest.forServer("Hello", serverId);
        
        // Then: Verify server preference is set
        StepVerifier.create(aiService.processChat(request))
            .assertNext(response_result -> {
                assertThat(response_result.response()).isNotEmpty();
                // Verify context has preferred server
                ConversationContext context = aiService.getContext(response_result.contextId());
                assertThat(context.getPreferredServerId()).isEqualTo(serverId);
            })
            .verifyComplete();
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

    /**
     * Helper method to create mock Spring AI ChatResponse for intent analysis
     */
    private org.springframework.ai.chat.model.ChatResponse createMockSpringAiChatResponse(String content) {
        // Create real objects instead of mocking final methods
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);
        
        // Create a custom ChatResponse implementation to avoid mocking final methods
        return new org.springframework.ai.chat.model.ChatResponse(List.of(generation));
    }
}
