package com.deepai.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * MCP Error record for JSON-RPC error responses.
 */
public record McpError(
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
