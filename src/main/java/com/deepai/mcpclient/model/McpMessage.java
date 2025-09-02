package com.deepai.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Base MCP message structure following the JSON-RPC 2.0 specification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpMessage(
    @JsonProperty("jsonrpc")
    @NotBlank
    String jsonrpc,
    
    @JsonProperty("id")
    Object id,
    
    @JsonProperty("method")
    String method,
    
    @JsonProperty("params")
    Object params,
    
    @JsonProperty("result")
    Object result,
    
    @JsonProperty("error")
    McpError error,
    
    @JsonProperty("timestamp")
    Instant timestamp
) {
    public static McpMessage request(Object id, String method, Object params) {
        return new McpMessage(
            "2.0",
            id,
            method,
            params,
            null,
            null,
            Instant.now()
        );
    }
    
    public static McpMessage response(Object id, Object result) {
        return new McpMessage(
            "2.0",
            id,
            null,
            null,
            result,
            null,
            Instant.now()
        );
    }
    
    public static McpMessage error(Object id, McpError error) {
        return new McpMessage(
            "2.0",
            id,
            null,
            null,
            null,
            error,
            Instant.now()
        );
    }
    
    public static McpMessage notification(String method, Object params) {
        return new McpMessage(
            "2.0",
            null,  // notifications have no id
            method,
            params,
            null,
            null,
            Instant.now()
        );
    }
    
    public boolean isRequest() {
        return method != null && result == null && error == null;
    }
    
    public boolean isResponse() {
        return method == null && (result != null || error != null);
    }
    
    public boolean isNotification() {
        return method != null && id == null;
    }
}
