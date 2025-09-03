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
        return new ChatResponse(
            response, 
            contextId, 
            List.of(), 
            LocalDateTime.now(), 
            "llama3.1:8b", 
            Map.of()
        );
    }
    
    /**
     * Create a response with tool executions
     */
    public static ChatResponse withTools(String response, String contextId, List<ToolExecution> tools) {
        return new ChatResponse(
            response, 
            contextId, 
            tools, 
            LocalDateTime.now(), 
            "llama3.1:8b", 
            Map.of("tools_count", tools.size())
        );
    }
    
    /**
     * Create an error response
     */
    public static ChatResponse error(String errorMessage, String contextId) {
        return new ChatResponse(
            errorMessage, 
            contextId, 
            List.of(), 
            LocalDateTime.now(), 
            "llama3.1:8b", 
            Map.of("error", true)
        );
    }
}
