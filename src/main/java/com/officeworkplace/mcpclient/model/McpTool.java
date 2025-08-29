package com.officeworkplace.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool definition and execution models.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpTool(
    @JsonProperty("name")
    @NotBlank
    String name,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("inputSchema")
    @NotNull
    Map<String, Object> inputSchema
) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record McpToolCall(
    @JsonProperty("name")
    @NotBlank
    String name,
    
    @JsonProperty("arguments")
    Map<String, Object> arguments
) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record McpToolResult(
    @JsonProperty("content")
    List<McpContent> content,
    
    @JsonProperty("isError")
    Boolean isError
) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record McpContent(
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
