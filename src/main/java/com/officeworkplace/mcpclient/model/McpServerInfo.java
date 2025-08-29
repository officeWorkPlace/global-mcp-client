package com.officeworkplace.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * MCP Server information and capabilities.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpServerInfo(
    @JsonProperty("name")
    @NotBlank
    String name,
    
    @JsonProperty("version")
    @NotBlank
    String version,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("capabilities")
    McpCapabilities capabilities,
    
    @JsonProperty("vendor")
    String vendor,
    
    @JsonProperty("metadata")
    Map<String, Object> metadata
) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record McpCapabilities(
    @JsonProperty("tools")
    Map<String, Object> tools,
    
    @JsonProperty("resources")
    Map<String, Object> resources,
    
    @JsonProperty("prompts")
    Map<String, Object> prompts,
    
    @JsonProperty("logging")
    Map<String, Object> logging
) {
}
