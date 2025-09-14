package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.cli.service.CliMcpService;
import com.deepai.mcpclient.cli.utils.ShellOutput;
import com.deepai.mcpclient.cli.utils.NaturalLanguageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Interactive shell command for continuous CLI interaction
 */
@Component
@Command(name = "shell", description = "🐚 Start interactive shell mode")
public class ShellCommand extends BaseCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ShellCommand.class);

    @Autowired
    private CliMcpService cliMcpService;

    @Autowired
    private NaturalLanguageProcessor naturalLanguageProcessor;
    
    @Autowired
    private com.deepai.mcpclient.service.ResponseFormatter responseFormatter;

    @Option(names = {"-q", "--quiet"}, description = "Suppress welcome message")
    private boolean quiet = false;

    @Override
    public void run() {
        // Suppress Spring Boot logging noise for cleaner shell experience
        ShellOutput.suppressSpringLogs();
        
        if (!quiet) {
            ShellOutput.welcome();
        }

        // Enhanced input handling for persistent shell like Warp.dev
        BufferedReader reader = null;
        try {
            // Use BufferedReader for better input handling
            reader = new BufferedReader(new InputStreamReader(System.in));
            
            ShellOutput.info("🚀 MCP Shell ready! Type commands or natural language.");
            ShellOutput.newLine();
            
            while (true) {
                try {
                    ShellOutput.prompt();
                    
                    // Simple blocking input read
                    String input = reader.readLine();
                    
                    // Handle EOF (Ctrl+D or closed input stream)
                    if (input == null) {
                        ShellOutput.info("Input stream closed. Exiting shell.");
                        break;
                    }
                    
                    input = input.trim();
                    
                    if (input.isEmpty()) {
                        continue;
                    }
                    
                    // Handle exit commands
                    if (input.equals("exit") || input.equals("quit") || input.equals("q")) {
                        ShellOutput.goodbye();
                        break;
                    }
                    
                    // Handle help commands
                    if (input.equals("help") || input.equals("h")) {
                        showHelp();
                        continue;
                    }
                    
                    // Handle clear commands
                    if (input.equals("clear") || input.equals("cls")) {
                        // Simple clear without affecting input stream
                        ShellOutput.newLine();
                        ShellOutput.newLine();
                        if (!quiet) {
                            ShellOutput.welcome();
                        }
                        continue;
                    }
                    
                    // Execute the command with error recovery
                    executeCommandSafely(input);
                    
                } catch (Exception e) {
                    ShellOutput.error("Shell error: " + e.getMessage());
                    ShellOutput.info("Shell recovering... Continue typing commands.");
                    if (verbose) {
                        logger.debug("Shell error details", e);
                    }
                    // Don't break the loop - recover and continue
                }
            }
        } catch (Exception e) {
            ShellOutput.error("Fatal shell error: " + e.getMessage());
            logger.error("Fatal shell error", e);
        } finally {
            // Clean shutdown
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.debug("Error closing reader", e);
                }
            }
            // Restore normal logging when exiting
            ShellOutput.restoreSpringLogs();
        }
    }
    
    /**
     * Execute command with enhanced error handling and recovery
     */
    private void executeCommandSafely(String input) {
        try {
            executeCommand(input);
        } catch (Exception e) {
            ShellOutput.error("Command failed: " + e.getMessage());
            ShellOutput.info("💡 Try: 'help' for available commands, or use natural language like 'list servers'");
            if (verbose) {
                logger.debug("Command execution details", e);
            }
            // Don't propagate exception - shell should continue
        }
    }
    
    private void showHelp() {
        ShellOutput.header("📚 Available Commands");
        
        ShellOutput.helpSection("Server Management", new String[]{
            "server list                     - List all configured servers",
            "server info <server-id>         - Show detailed server information", 
            "server health <server-id>       - Check server health status",
            "server status <server-id>       - Show server status with tool count"
        });
        
        ShellOutput.helpSection("Tool Operations", new String[]{
            "tool list <server-id>           - List all available tools",
            "tool all                        - Show tools from all servers",
            "tool info <server-id> <tool>    - Show detailed tool information",
            "tool exec <server-id> <tool>    - Execute a tool",
            "tool quick ping                 - Quick connectivity test",
            "tool quick databases            - Quick database listing"
        });
        
        ShellOutput.helpSection("Examples", new String[]{
            "server list",
            "tool list mongo-mcp-server-test",
            "tool exec mongo-mcp-server-test listDatabases",
            "tool quick ping"
        });
        
        ShellOutput.helpSection("Shell Commands", new String[]{
            "help     - Show this help message",
            "clear    - Clear the screen", 
            "exit     - Exit the shell"
        });
        
        ShellOutput.newLine();
    }
    
    private void executeCommand(String input) {
        try {
            // Process natural language input using AI
            NaturalLanguageProcessor.CommandResult result = naturalLanguageProcessor.processInput(input);
            
            if (result.isHelp()) {
                // Show help/suggestions
                ShellOutput.info(result.getHelpText());
                return;
            }
            
            if (!result.isCommand()) {
                ShellOutput.error("Could not understand the command: " + input);
                ShellOutput.info("Type 'help' for available commands or try natural language like 'list tools'");
                return;
            }
            
            // Execute the processed command
            String processedCommand = result.getCommand();
            ShellOutput.info("🤖 Understanding: " + processedCommand);
            
            // Parse the processed command and execute it
            String[] parts = processedCommand.split("\\s+");
            
            if (parts.length == 0) {
                return;
            }
            
            String mainCommand = parts[0];
            
            switch (mainCommand) {
                case "server":
                    handleServerCommand(parts);
                    break;
                case "tool":
                    handleToolCommand(parts);
                    break;
                case "config":
                    handleConfigCommand(parts);
                    break;
                case "help":
                    showHelp();
                    break;
                case "clear":
                    ShellOutput.clearScreen();
                    break;
                case "exit":
                case "quit":
                    ShellOutput.info("👋 Goodbye!");
                    return;
                default:
                    ShellOutput.error("Unknown command: " + mainCommand);
                    ShellOutput.info("Available: server, tool, config, help, clear, exit");
            }
            
        } catch (Exception e) {
            ShellOutput.error("Command execution failed: " + e.getMessage());
            if (verbose) {
                ShellOutput.info("Details: " + e.toString());
            }
        }
    }
    
    private void handleServerCommand(String[] parts) {
        if (parts.length < 2) {
            ShellOutput.error("Server command requires a subcommand (list, info, health, status)");
            return;
        }
        
        String subCommand = parts[1];
        
        switch (subCommand) {
            case "list":
                executeServerList();
                break;
            case "info":
                if (parts.length < 3) {
                    ShellOutput.error("Server info requires server ID");
                    return;
                }
                executeServerInfo(parts[2]);
                break;
            case "health":
                if (parts.length < 3) {
                    ShellOutput.error("Server health requires server ID");
                    return;
                }
                executeServerHealth(parts[2]);
                break;
            case "status":
                if (parts.length < 3) {
                    ShellOutput.error("Server status requires server ID");
                    return;
                }
                executeServerStatus(parts[2]);
                break;
            default:
                ShellOutput.error("Unknown server command: " + subCommand);
                ShellOutput.info("Available: list, info, health, status");
        }
    }
    
    private void handleToolCommand(String[] parts) {
        if (parts.length < 2) {
            ShellOutput.error("Tool command requires a subcommand (list, info, exec, quick, all)");
            return;
        }
        
        String subCommand = parts[1];
        
        switch (subCommand) {
            case "list":
                if (parts.length < 3) {
                    ShellOutput.error("Tool list requires server ID");
                    return;
                }
                executeToolList(parts[2]);
                break;
            case "all":
                executeToolAll();
                break;
            case "exec":
                if (parts.length < 4) {
                    ShellOutput.error("Tool exec requires server ID and tool name");
                    return;
                }
                executeToolExec(parts[2], parts[3]);
                break;
            case "quick":
                if (parts.length < 3) {
                    ShellOutput.error("Tool quick requires operation (ping, databases, status)");
                    return;
                }
                executeToolQuick(parts[2]);
                break;
            default:
                ShellOutput.error("Unknown tool command: " + subCommand);
                ShellOutput.info("Available: list, all, exec, quick");
        }
    }
    
    private void handleConfigCommand(String[] parts) {
        if (parts.length < 2) {
            ShellOutput.error("Config command requires a subcommand (show)");
            return;
        }
        
        if ("show".equals(parts[1])) {
            executeConfigShow();
        } else {
            ShellOutput.error("Unknown config command: " + parts[1]);
            ShellOutput.info("Available: show");
        }
    }
    
    // Simplified command implementations
    private void executeServerList() {
        try {
            var serverIds = cliMcpService.getServerIds();
            if (serverIds.isEmpty()) {
                ShellOutput.warning("No servers configured");
                return;
            }
            
            ShellOutput.success("Found " + serverIds.size() + " server(s):");
            for (String serverId : serverIds) {
                try {
                    var info = cliMcpService.getServerInfo(serverId);
                    var healthy = cliMcpService.isServerHealthy(serverId);
                    ShellOutput.serverItem(serverId, info.name(), "MongoDB", healthy);
                } catch (Exception e) {
                    ShellOutput.serverItem(serverId, "Error: " + e.getMessage(), "", false);
                }
            }
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Failed to list servers: " + e.getMessage());
        }
    }
    
    private void executeServerInfo(String serverId) {
        try {
            var info = cliMcpService.getServerInfo(serverId);
            ShellOutput.header("Server Information: " + serverId);
            ShellOutput.info("Name: " + info.name());
            ShellOutput.info("Version: " + info.version());
            ShellOutput.info("Description: " + info.description());
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Failed to get server info: " + e.getMessage());
        }
    }
    
    private void executeServerHealth(String serverId) {
        try {
            var healthy = cliMcpService.isServerHealthy(serverId);
            if (healthy) {
                ShellOutput.success("Server " + serverId + " is healthy");
            } else {
                ShellOutput.warning("Server " + serverId + " is unhealthy");
            }
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Health check failed: " + e.getMessage());
        }
    }
    
    private void executeServerStatus(String serverId) {
        try {
            var healthy = cliMcpService.isServerHealthy(serverId);
            var tools = cliMcpService.listTools(serverId);
            
            ShellOutput.header("Server Status: " + serverId);
            if (healthy) {
                ShellOutput.success("Health: Healthy");
            } else {
                ShellOutput.warning("Health: Unhealthy");
            }
            ShellOutput.info("Tools: " + tools.size() + " available");
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Status check failed: " + e.getMessage());
        }
    }
    
    private void executeToolList(String serverId) {
        try {
            var tools = cliMcpService.listTools(serverId);
            ShellOutput.header("Tools for " + serverId + " (" + tools.size() + " total)");
            for (var tool : tools) {
                ShellOutput.toolItem(tool.name(), tool.description());
            }
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Failed to list tools: " + e.getMessage());
        }
    }
    
    private void executeToolAll() {
        try {
            var allTools = cliMcpService.getAllTools();
            ShellOutput.header("All Available Tools");
            for (var entry : allTools.entrySet()) {
                String serverId = entry.getKey();
                var tools = entry.getValue();
                ShellOutput.toolCategory(serverId, tools.size());
                for (var tool : tools) {
                    ShellOutput.toolItem(tool.name(), tool.description());
                }
            }
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Failed to get all tools: " + e.getMessage());
        }
    }
    
    private void executeToolExec(String serverId, String toolName) {
        try {
            ShellOutput.info("═══ Executing Tool: " + toolName + " (" + serverId + ") ═══");
            ShellOutput.newLine();
            ShellOutput.info("🔧 Executing tool with parameters: {}");
            
            var result = cliMcpService.executeTool(serverId, toolName, java.util.Map.of());
            
            ShellOutput.success("🎉 Tool executed successfully!");
            ShellOutput.newLine();
            ShellOutput.info("📊 Result:");
            
            // Use ResponseFormatter for AI-enhanced human-readable output
            if (result.content() != null && !result.content().isEmpty()) {
                String formattedResult = responseFormatter.formatToolResult(toolName, result);
                ShellOutput.executionResult(formattedResult);
            } else {
                ShellOutput.info("No content returned from tool execution.");
            }
            
            ShellOutput.info("💡 Use --verbose for more detailed output");
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Tool execution failed: " + e.getMessage());
        }
    }
    
    private void executeToolQuick(String operation) {
        try {
            var serverIds = cliMcpService.getServerIds();
            if (serverIds.isEmpty()) {
                ShellOutput.error("No servers available");
                return;
            }
            
            String serverId = serverIds.get(0);
            ShellOutput.info("Using server: " + serverId);
            
            switch (operation) {
                case "ping":
                    cliMcpService.executeTool(serverId, "ping", java.util.Map.of());
                    ShellOutput.success("Ping successful! Server is responding.");
                    break;
                case "databases":
                    var dbResult = cliMcpService.executeTool(serverId, "listDatabases", java.util.Map.of());
                    ShellOutput.success("Database list retrieved:");
                    if (dbResult.content() != null) {
                        for (var content : dbResult.content()) {
                            if (content.text() != null) {
                                ShellOutput.executionResult(content.text());
                            }
                        }
                    }
                    break;
                case "status":
                    var healthy = cliMcpService.isServerHealthy(serverId);
                    if (healthy) {
                        ShellOutput.success("Server status: Healthy");
                    } else {
                        ShellOutput.warning("Server status: Unhealthy");
                    }
                    break;
                default:
                    ShellOutput.error("Unknown quick operation: " + operation);
                    ShellOutput.info("Available: ping, databases, status");
            }
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Quick operation failed: " + e.getMessage());
        }
    }
    
    private void executeConfigShow() {
        try {
            var serverIds = cliMcpService.getServerIds();
            ShellOutput.header("Configuration");
            ShellOutput.info("Servers: " + serverIds.size());
            for (String serverId : serverIds) {
                ShellOutput.info("  - " + serverId);
            }
            ShellOutput.newLine();
        } catch (Exception e) {
            ShellOutput.error("Failed to show config: " + e.getMessage());
        }
    }
}
