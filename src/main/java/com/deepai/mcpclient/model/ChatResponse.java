package com.deepai.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response model for AI chat interactions
 */
public record ChatResponse(
    @JsonProperty("response")
    String response,
    
    @JsonProperty("context_id")
    String contextId,
    
    @JsonProperty("tools_used")
    List<ToolExecution> toolsUsed,
    
    @JsonProperty("timestamp")
    LocalDateTime timestamp,
    
    @JsonProperty("model")
    String model,
    
    @JsonProperty("metadata")
    Map<String, Object> metadata
) {
    
    /**
     * Information about tool execution during chat
     */
    public record ToolExecution(
        @JsonProperty("tool_name")
        String toolName,
        
        @JsonProperty("server_id")
        String serverId,
        
        @JsonProperty("parameters")
        Map<String, Object> parameters,
        
        @JsonProperty("success")
        boolean success,
        
        @JsonProperty("execution_time_ms")
        long executionTimeMs
    ) {}
    
    /**
     * Create a simple success response
     */
    public static ChatResponse success(String response, String contextId) {
        return success(response, contextId, "gemini-1.5-flash");
    }
    
    /**
     * Create a simple success response with model
     */
    public static ChatResponse success(String response, String contextId, String model) {
        return new ChatResponse(
            response, 
            contextId, 
            List.of(), 
            LocalDateTime.now(), 
            model, 
            Map.of()
        );
    }
    
    /**
     * Create a response with tool executions
     */
    public static ChatResponse withTools(String response, String contextId, List<ToolExecution> tools) {
        return withTools(response, contextId, tools, "gemini-1.5-flash");
    }
    
    /**
     * Create a response with tool executions and model
     */
    public static ChatResponse withTools(String response, String contextId, List<ToolExecution> tools, String model) {
        return new ChatResponse(
            response, 
            contextId, 
            tools, 
            LocalDateTime.now(), 
            model, 
            Map.of("tools_count", tools.size())
        );
    }
    
    /**
     * Create an error response
     */
    public static ChatResponse error(String errorMessage, String contextId) {
        return error(errorMessage, contextId, "pattern-matching");
    }
    
    /**
     * Create an error response with model
     */
    public static ChatResponse error(String errorMessage, String contextId, String model) {
        return new ChatResponse(
            errorMessage, 
            contextId, 
            List.of(), 
            LocalDateTime.now(), 
            model, 
            Map.of("error", true)
        );
    }
}
