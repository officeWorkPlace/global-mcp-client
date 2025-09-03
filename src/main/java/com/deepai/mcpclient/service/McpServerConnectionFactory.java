package com.deepai.mcpclient.service;

import com.deepai.mcpclient.config.McpConfigurationProperties.ServerConfig;

public interface McpServerConnectionFactory {
    McpServerConnection createConnection(String serverId, ServerConfig serverConfig);
    boolean supports(String type);
}
