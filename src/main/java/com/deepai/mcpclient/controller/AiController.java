package com.deepai.mcpclient.controller;

import com.deepai.mcpclient.model.AskRequest;
import com.deepai.mcpclient.model.ChatRequest;
import com.deepai.mcpclient.model.ChatResponse;
import com.deepai.mcpclient.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * AI Controller for Global MCP Client
 * 
 * Provides AI-powered natural language interface to interact with ANY MCP server
 * Users can chat naturally instead of dealing with technical API details
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Assistant", description = "Natural language interface to MCP servers")
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = true)
public class AiController {

    private static final Logger logger = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Chat with AI assistant about MCP operations
     */
    @PostMapping("/chat")
    @Operation(
        summary = "Chat with AI Assistant", 
        description = "Have a conversation with the AI assistant to perform MCP operations using natural language"
    )
    @ApiResponse(responseCode = "200", description = "AI response with tool execution results")
    @ApiResponse(responseCode = "400", description = "Invalid request format")
    @ApiResponse(responseCode = "500", description = "AI processing error")
    public Mono<ResponseEntity<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request) {
        
        logger.info("AI chat request: '{}' (server: {}, context: {})", 
            request.message(), request.serverId(), request.contextId());
        
        return aiService.processChat(request)
                .map(response -> {
                    logger.info("AI chat response generated for context: {}", response.contextId());
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    logger.error("Error processing AI chat: {}", error.getMessage(), error);
                    ChatResponse errorResponse = ChatResponse.error(
                        "I encountered an error processing your request. Please try again.", 
                        request.contextId()
                    );
                    return Mono.just(ResponseEntity.ok(errorResponse));
                });
    }

    /**
     * Simple ask endpoint for quick questions
     */
    @PostMapping("/ask")
    @Operation(
        summary = "Ask AI Assistant", 
        description = "Ask a quick question to the AI assistant (stateless)"
    )
    @ApiResponse(responseCode = "200", description = "AI response")
    public Mono<ResponseEntity<Map<String, String>>> ask(
            @Valid @RequestBody AskRequest request) {
        
        logger.info("AI ask request: '{}'", request.question());
        
        return aiService.processChat(ChatRequest.simple(request.question()))
                .map(chatResponse -> {
                    logger.debug("AI ask response generated with model: {}", chatResponse.model());
                    return ResponseEntity.ok(Map.of(
                        "question", request.question(),
                        "response", chatResponse.response(),
                        "model", chatResponse.model(),
                        "timestamp", java.time.LocalDateTime.now().toString()
                    ));
                })
                .onErrorResume(error -> {
                    logger.error("Error processing AI ask: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.ok(Map.of(
                        "question", request.question(),
                        "response", "I encountered an error processing your question. Please try again.",
                        "error", "true",
                        "model", "error-fallback",
                        "timestamp", java.time.LocalDateTime.now().toString()
                    )));
                });
    }

    /**
     * Get conversation context information
     */
    @GetMapping("/context/{contextId}")
    @Operation(
        summary = "Get Conversation Context", 
        description = "Retrieve information about a specific conversation context"
    )
    public ResponseEntity<?> getContext(
            @Parameter(description = "Context ID to retrieve") 
            @PathVariable String contextId) {
        
        var context = aiService.getContext(contextId);
        if (context == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(Map.of(
            "contextId", context.getContextId(),
            "messageCount", context.getMessages().size(),
            "createdAt", context.getCreatedAt(),
            "lastUsed", context.getLastUsed(),
            "preferredServerId", context.getPreferredServerId()
        ));
    }

    /**
     * Get AI service statistics
     */
    @GetMapping("/stats")
    @Operation(
        summary = "Get AI Service Statistics", 
        description = "Get statistics about AI service usage and active contexts"
    )
    public ResponseEntity<Map<String, Object>> getStats() {
        
        return ResponseEntity.ok(Map.of(
            "activeContexts", aiService.getActiveContextsCount(),
            "aiEnabled", true,
            "status", "operational",
            "timestamp", java.time.LocalDateTime.now()
        ));
    }

    /**
     * Health check for AI service
     */
    @GetMapping("/health")
    @Operation(
        summary = "AI Service Health Check", 
        description = "Check if AI service is healthy and ready to process requests"
    )
    public ResponseEntity<Map<String, Object>> healthCheck() {
        
        try {
            // Simple health check - could be expanded
            return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "aiEnabled", true,
                "activeContexts", aiService.getActiveContextsCount(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        } catch (Exception e) {
            logger.error("AI health check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(503).body(Map.of(
                "status", "unhealthy",
                "error", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * Get available MCP tools summary (for AI context)
     */
    @GetMapping("/tools")
    @Operation(
        summary = "Get Available MCP Tools", 
        description = "Get summary of all available MCP tools across all servers"
    )
    public ResponseEntity<Map<String, Object>> getAvailableTools() {
        
        try {
            // This would typically call IntentProcessor to get tool information
            return ResponseEntity.ok(Map.of(
                "message", "Tool discovery is dynamic - tools are discovered when processing chat requests",
                "timestamp", java.time.LocalDateTime.now()
            ));
        } catch (Exception e) {
            logger.error("Error getting available tools: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve tool information",
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }
}
