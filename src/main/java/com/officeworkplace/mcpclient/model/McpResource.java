package com.officeworkplace.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.net.URI;
import java.util.Map;

/**
 * MCP Resource definition models.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpResource(
    @JsonProperty("uri")
    @NotBlank
    String uri,
    
    @JsonProperty("name")
    String name,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("mimeType")
    String mimeType,
    
    @JsonProperty("annotations")
    Map<String, Object> annotations
) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record McpResourceContent(
    @JsonProperty("uri")
    @NotBlank
    String uri,
    
    @JsonProperty("mimeType")
    String mimeType,
    
    @JsonProperty("text")
    String text,
    
    @JsonProperty("blob")
    byte[] blob
) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record McpResourceTemplate(
    @JsonProperty("uriTemplate")
    @NotBlank
    String uriTemplate,
    
    @JsonProperty("name")
    String name,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("mimeType")
    String mimeType
) {
}
