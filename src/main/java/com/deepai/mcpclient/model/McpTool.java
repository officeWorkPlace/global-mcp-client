package com.deepai.mcpclient.model;

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
    Map<String, Object> inputSchema,
    
    @JsonProperty("outputSchema")
    Map<String, Object> outputSchema
) {
}
