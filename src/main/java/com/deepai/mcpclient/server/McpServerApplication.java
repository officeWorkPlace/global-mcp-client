package com.deepai.mcpclient.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Server Application for Global MCP Client
 * 
 * Runs the application as a web server with REST API endpoints
 * Use this instead of McpCliApplication for server mode
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.deepai.mcpclient")
public class McpServerApplication {

    public static void main(String[] args) {
        // Set server profile
        System.setProperty("spring.profiles.active", "server");
        
        // Start Spring Boot application in server mode
        SpringApplication app = new SpringApplication(McpServerApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.SERVLET);
        app.run(args);
    }
}
