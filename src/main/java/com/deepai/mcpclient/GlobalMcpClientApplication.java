package com.deepai.mcpclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Global MCP Client.
 */
@SpringBootApplication
@EnableScheduling
public class GlobalMcpClientApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(GlobalMcpClientApplication.class, args);
    }
}
