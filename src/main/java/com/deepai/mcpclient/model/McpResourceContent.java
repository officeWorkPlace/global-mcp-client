package com.deepai.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * MCP Resource content record.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpResourceContent(
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
    
    public String content() {
        return text != null ? text : "Binary content";
    }
}
