package com.deepai.mcpclient.service.factory;

import com.deepai.mcpclient.config.McpConfigurationProperties.ServerConfig;
import com.deepai.mcpclient.service.McpServerConnection;
import com.deepai.mcpclient.service.McpServerConnectionFactory;
import com.deepai.mcpclient.service.impl.StdioMcpServerConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class StdioMcpServerConnectionFactory implements McpServerConnectionFactory {

    private final ObjectMapper objectMapper;

    public StdioMcpServerConnectionFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public McpServerConnection createConnection(String serverId, ServerConfig serverConfig) {
        return new StdioMcpServerConnection(serverId, serverConfig, objectMapper);
    }

    @Override
    public boolean supports(String type) {
        return "stdio".equalsIgnoreCase(type);
    }
}
