package com.deepai.mcpclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for MCP servers.
 */
@ConfigurationProperties(prefix = "mcp")
@Validated
public record McpConfigurationProperties(@Valid @NotNull ClientConfig client,

		@Valid @NotNull Map<String, ServerConfig> servers) {

	public record ClientConfig(@Positive int defaultTimeout,

			@Valid @NotNull RetryConfig retry) {
	}

	public record RetryConfig(@Positive int maxAttempts,

			@Positive double backoffMultiplier) {
	}

	public record ServerConfig(@NotBlank String type,

			// For stdio connections
			String command,

			List<String> args,

			// For HTTP connections
			String url,

			Map<String, String> headers,

			@Positive int timeout,

			boolean enabled,

			Map<String, String> environment) {

		
	}
}
