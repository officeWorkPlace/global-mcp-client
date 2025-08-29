package com.officeworkplace.mcpclient.model;

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

record McpError(
    @JsonProperty("code")
    @NotNull
    Integer code,
    
    @JsonProperty("message")
    @NotBlank
    String message,
    
    @JsonProperty("data")
    Object data
) {
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    
    public static McpError parseError(String message) {
        return new McpError(PARSE_ERROR, message, null);
    }
    
    public static McpError invalidRequest(String message) {
        return new McpError(INVALID_REQUEST, message, null);
    }
    
    public static McpError methodNotFound(String method) {
        return new McpError(METHOD_NOT_FOUND, "Method not found: " + method, null);
    }
    
    public static McpError invalidParams(String message) {
        return new McpError(INVALID_PARAMS, message, null);
    }
    
    public static McpError internalError(String message) {
        return new McpError(INTERNAL_ERROR, message, null);
    }
}
