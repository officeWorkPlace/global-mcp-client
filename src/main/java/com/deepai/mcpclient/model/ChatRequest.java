package com.deepai.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request model for AI chat interactions
 */
public record ChatRequest(
    @NotBlank(message = "Message cannot be empty")
    @Size(max = 4000, message = "Message too long")
    @JsonProperty("message")
    String message,
    
    @JsonProperty("server_id")
    String serverId,
    
    @JsonProperty("context_id")
    String contextId,
    
    @JsonProperty("stream")
    Boolean stream
) {
    public ChatRequest {
        // Set defaults
        if (stream == null) {
            stream = false;
        }
    }
    
    /**
     * Create a simple chat request
     */
    public static ChatRequest simple(String message) {
        return new ChatRequest(message, null, null, false);
    }
    
    /**
     * Create a chat request for a specific server
     */
    public static ChatRequest forServer(String message, String serverId) {
        return new ChatRequest(message, serverId, null, false);
    }
}
