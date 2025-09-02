package com.deepai.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * MCP Content record.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpContent(
    @JsonProperty("type")
    @NotBlank
    String type,
    
    @JsonProperty("text")
    String text,
    
    @JsonProperty("data")
    Object data,
    
    @JsonProperty("mimeType")
    String mimeType
) {
    public static McpContent text(String text) {
        return new McpContent("text", text, null, null);
    }
    
    public static McpContent json(Object data) {
        return new McpContent("application/json", null, data, "application/json");
    }
    
    public static McpContent error(String message) {
        return new McpContent("error", message, null, null);
    }
}
