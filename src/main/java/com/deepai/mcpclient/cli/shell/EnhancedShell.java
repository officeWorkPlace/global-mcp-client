package com.deepai.mcpclient.cli.shell;

import com.deepai.mcpclient.cli.service.CliMcpService;
import com.deepai.mcpclient.cli.utils.ShellOutput;
import com.deepai.mcpclient.cli.utils.NaturalLanguageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Interactive Shell - Modern terminal experience like Warp.dev
 * Features:
 * - Beautiful prompt with context information
 * - Command history
 * - Enhanced auto-completion suggestions
 * - Session management
 * - Real-time feedback
 */
@Component
public class EnhancedShell {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedShell.class);
    
    @Autowired
    private CliMcpService cliMcpService;

    @Autowired
    private com.deepai.mcpclient.service.AiService aiService;

    @Autowired
    private com.deepai.mcpclient.service.ResponseFormatter responseFormatter;
    
    // Shell state
    private List<String> commandHistory = new ArrayList<>();
    private String currentContext = "global";
    private String sessionId;
    private LocalDateTime sessionStart;
    
    // Color codes
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String WHITE = "\u001B[37m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String BRIGHT_BLUE = "\u001B[94m";
    private static final String BRIGHT_CYAN = "\u001B[96m";
    private static final String BRIGHT_YELLOW = "\u001B[93m";
    private static final String BRIGHT_MAGENTA = "\u001B[95m";
    
    public void start() {
        initializeSession();
        displayWelcome();
        runInteractiveLoop();
    }
    
    private void initializeSession() {
        sessionId = "mcp-" + System.currentTimeMillis();
        sessionStart = LocalDateTime.now();
        ShellOutput.suppressSpringLogs();
        
        // Clear screen for clean start
        clearScreen();
    }
    
    private void displayWelcome() {
        println("");
        println(BRIGHT_CYAN + "┌─────────────────────────────────────────────────────────────┐" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + BOLD + WHITE + "           🚀 MCP Interactive Shell v2.0                    " + RESET + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + "                                                           " + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + BRIGHT_GREEN + "  🤖 AI-Powered Terminal Experience" + RESET + "                   " + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + "  • Natural language conversation" + DIM + "                   " + RESET + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + "  • Smart tool selection" + DIM + "                           " + RESET + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + "  • Context-aware responses" + DIM + "                        " + RESET + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + "  • Professional AI formatting" + DIM + "                     " + RESET + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "└─────────────────────────────────────────────────────────────┘" + RESET);
        println("");

        println(BRIGHT_BLUE + "🤖 " + BOLD + "AI Assistant Ready!" + RESET);
        println(DIM + "   Just type what you want to do in natural language:" + RESET);
        println("");
        println("   " + BRIGHT_GREEN + "💬 " + WHITE + "\"Show me all databases\"" + RESET);
        println("   " + BRIGHT_GREEN + "💬 " + WHITE + "\"Find users in the admin database\"" + RESET);
        println("   " + BRIGHT_GREEN + "💬 " + WHITE + "\"Which servers are healthy?\"" + RESET);
        println("   " + BRIGHT_GREEN + "💬 " + WHITE + "\"Help me manage my data\"" + RESET);
        println("");
        
        println(DIM + "Session: " + sessionId + " | Started: " + sessionStart.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + RESET);
        println("");
    }
    
    private void runInteractiveLoop() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(System.in));
            
            while (true) {
                try {
                    displayPrompt();
                    
                    String input = reader.readLine();
                    
                    if (input == null) {
                        println("");
                        println(DIM + "EOF detected. Exiting..." + RESET);
                        break;
                    }
                    
                    input = input.trim();
                    
                    if (input.isEmpty()) {
                        continue;
                    }
                    
                    // Add to history
                    commandHistory.add(input);
                    
                    // Handle special commands
                    if (handleSpecialCommands(input)) {
                        continue;
                    }
                    
                    // Execute command
                    executeCommand(input);
                    
                } catch (Exception e) {
                    displayError("Command execution failed: " + e.getMessage());
                    if (logger.isDebugEnabled()) {
                        logger.debug("Command execution error", e);
                    }
                }
            }
        } catch (Exception e) {
            displayError("Fatal shell error: " + e.getMessage());
            logger.error("Fatal shell error", e);
        } finally {
            cleanup(reader);
        }
    }
    
    private void displayPrompt() {
        // Get current time
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        // Get server count
        String serverInfo = getServerInfo();

        println("");
        print(DIM + "┌─ " + RESET);
        print(BRIGHT_BLUE + "🤖 AI Assistant" + RESET);
        print(DIM + " [" + time + "]" + RESET);
        print(DIM + " " + serverInfo + RESET);
        println("");

        print(DIM + "└─" + RESET);
        print(BRIGHT_CYAN + "💬 " + RESET);
        print("Ask me anything: ");
    }

    /**
     * Helper method for printing with newline
     */
    private void println(String text) {
        System.out.println(text);
    }

    /**
     * Helper method for printing without newline
     */
    private void print(String text) {
        System.out.print(text);
    }
    
    private String getServerInfo() {
        try {
            // This would get actual server count
            return "(" + currentContext + ")";
        } catch (Exception e) {
            return "(offline)";
        }
    }
    
    private boolean handleSpecialCommands(String input) {
        String cmd = input.toLowerCase();
        
        switch (cmd) {
            case "exit":
            case "quit":
            case "q":
                displayGoodbye();
                System.exit(0);
                return true;
                
            case "clear":
            case "cls":
                clearScreen();
                displayWelcome();
                return true;
                
            case "help":
            case "h":
                displayHelp();
                return true;
                
            case "history":
                displayHistory();
                return true;
                
            case "status":
                displayStatus();
                return true;
                
            default:
                return false;
        }
    }
    
    private void executeCommand(String input) {
        println("");
        showTypingIndicator();

        try {
            long startTime = System.currentTimeMillis();

            // Use the professional AI service for intelligent processing
            var result = cliMcpService.executeToolWithAi(input, sessionId);

            clearTypingIndicator();

            if (result.isSuccess()) {
                // Display professional AI response
                displayAiResponse(result.getResponse());

                // Show tool executions if any
                if (result.hasToolExecutions()) {
                    println("");
                    displayToolExecutions(result.getToolExecutions());
                }

                // Show model used (professional touch)
                if (result.getModel() != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    println("");
                    println(DIM + "✨ " + result.getModel() + " • " + duration + "ms" + RESET);
                }

            } else {
                displayError("I encountered an issue: " + result.getResponse());
                println(DIM + "💡 Try rephrasing your request or check if the servers are running" + RESET);
            }

        } catch (Exception e) {
            clearTypingIndicator();

            // Check if it's an AI service unavailable error
            if (e.getMessage() != null && e.getMessage().contains("AI") ||
                e.getMessage().contains("rate limit") ||
                e.getMessage().contains("circuit")) {

                displayAiUnavailableMessage();

            } else {
                displayError("Something went wrong: " + e.getMessage());
                logger.debug("AI command execution error", e);

                // Graceful fallback with helpful message
                println("");
                println(DIM + "💡 You can try:" + RESET);
                println(DIM + "  • 'list servers' - see available servers" + RESET);
                println(DIM + "  • 'show databases' - see all databases" + RESET);
                println(DIM + "  • 'help' - get more assistance" + RESET);
            }
        }
    }

    private void displayAiUnavailableMessage() {
        println("");
        println(YELLOW + "⚠️ " + BOLD + "AI Assistant Temporarily Unavailable" + RESET);
        println(YELLOW + "────────────────────────────────────────" + RESET);
        println("  " + DIM + "The AI service might be busy or rate limited." + RESET);
        println("  " + DIM + "Please wait a moment and try again." + RESET);
        println("");
        println(DIM + "💡 Alternative options:" + RESET);
        println(DIM + "  • Use direct commands: 'mcpcli tools list <server>'" + RESET);
        println(DIM + "  • Check system status: 'mcpcli servers health'" + RESET);
        println(DIM + "  • Try again in a few moments" + RESET);
    }

    private void showTypingIndicator() {
        print(DIM + "🤖 Thinking");
        for (int i = 0; i < 3; i++) {
            try {
                Thread.sleep(200);
                print(".");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        println(RESET);
    }

    private void clearTypingIndicator() {
        // Move cursor up and clear the typing indicator line
        print("\033[1A\033[2K\r");
    }

    private void displayAiResponse(String response) {
        println("");
        println(BRIGHT_BLUE + "🤖 " + BOLD + "Assistant" + RESET);
        println(BRIGHT_BLUE + "──────────" + RESET);

        // Format the response with proper word wrapping and styling
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                println("");
                continue;
            }

            // Detect and highlight important information
            if (line.contains("✅") || line.contains("success")) {
                println(GREEN + "  " + line + RESET);
            } else if (line.contains("❌") || line.contains("error") || line.contains("failed")) {
                println(RED + "  " + line + RESET);
            } else if (line.contains("⚠️") || line.contains("warning")) {
                println(YELLOW + "  " + line + RESET);
            } else if (line.startsWith("•") || line.startsWith("-") || line.startsWith("*")) {
                println(CYAN + "  " + line + RESET);
            } else {
                println("  " + line);
            }
        }
    }

    private void displayToolExecutions(java.util.List<com.deepai.mcpclient.model.ChatResponse.ToolExecution> executions) {
        println(DIM + "🔧 " + BOLD + "Tool Executions" + RESET);
        println(DIM + "───────────────" + RESET);

        for (var execution : executions) {
            String statusIcon = execution.success() ? "✅" : "❌";
            String statusColor = execution.success() ? GREEN : RED;

            println(String.format("  %s %s%s%s on %s%s%s (%s%dms%s)",
                statusIcon,
                CYAN + BOLD, execution.toolName(), RESET,
                YELLOW, execution.serverId(), RESET,
                DIM, execution.executionTimeMs(), RESET
            ));
        }
    }
    
    private void executeProcessedCommand(String processedCommand) {
        try {
            String[] parts = processedCommand.split("\\s+");
            
            if (parts.length == 0) {
                displayError("Empty command");
                return;
            }
            
            String mainCommand = parts[0].toLowerCase();
            
            switch (mainCommand) {
                case "server":
                    handleServerCommand(parts);
                    break;
                case "tool":
                    handleToolCommand(parts);
                    break;
                case "list":
                    if (parts.length > 1 && parts[1].toLowerCase().contains("database")) {
                        executeListDatabases();
                    } else {
                        displayError("Unknown list command: " + processedCommand);
                    }
                    break;
                case "show":
                    handleShowCommand(parts);
                    break;
                default:
                    displayError("Unknown command: " + mainCommand);
                    println(DIM + "💡 Available commands: server, tool, list, show" + RESET);
            }
            
        } catch (Exception e) {
            displayError("Command parsing failed: " + e.getMessage());
        }
    }
    
    private void handleServerCommand(String[] parts) {
        if (parts.length < 2) {
            displayError("Server command requires subcommand (list, info, health, status)");
            return;
        }
        
        String subCommand = parts[1].toLowerCase();
        
        switch (subCommand) {
            case "list":
                executeServerList();
                break;
            case "info":
                if (parts.length < 3) {
                    displayError("Server info requires server ID");
                    return;
                }
                executeServerInfo(parts[2]);
                break;
            default:
                displayError("Unknown server command: " + subCommand);
        }
    }
    
    private void handleToolCommand(String[] parts) {
        if (parts.length < 2) {
            displayError("Tool command requires subcommand");
            return;
        }
        
        String subCommand = parts[1].toLowerCase();
        
        switch (subCommand) {
            case "list":
                if (parts.length < 3) {
                    displayError("Tool list requires server ID");
                    return;
                }
                executeToolList(parts[2]);
                break;
            case "exec":
                if (parts.length < 4) {
                    displayError("Tool exec requires server ID and tool name");
                    return;
                }
                executeToolExec(parts[2], parts[3]);
                break;
            default:
                displayError("Unknown tool command: " + subCommand);
        }
    }
    
    private void handleShowCommand(String[] parts) {
        if (parts.length < 2) {
            displayError("Show command requires target (databases, collections, etc.)");
            return;
        }
        
        String target = parts[1].toLowerCase();
        
        if (target.contains("database")) {
            executeListDatabases();
        } else if (target.contains("collection") || target.contains("table")) {
            if (parts.length >= 4 && parts[2].equals("in")) {
                String dbName = parts[3];
                executeListCollections(dbName);
            } else {
                displayError("Show collections requires database name: 'show collections in <database>'");
            }
        } else {
            displayError("Unknown show target: " + target);
        }
    }
    
    // Real MCP service integrations
    private void executeServerList() {
        try {
            var serverIds = cliMcpService.getServerIds();
            
            println("");
            println(BRIGHT_CYAN + "📡 " + BOLD + "MCP Servers" + RESET);
            println(BRIGHT_CYAN + "─────────────" + RESET);
            
            for (String serverId : serverIds) {
                try {
                    Boolean isHealthy = cliMcpService.isServerHealthy(serverId);
                    var serverInfo = cliMcpService.getServerInfo(serverId);
                    
                    String status = (isHealthy != null && isHealthy) ? 
                        (BRIGHT_GREEN + "●" + RESET + " Healthy") : 
                        (RED + "●" + RESET + " Unhealthy");
                        
                    println("  " + status + " " + BOLD + serverId + RESET);
                    if (serverInfo != null) {
                        println("    " + DIM + serverInfo.name() + RESET);
                        if (serverInfo.description() != null) {
                            println("    " + DIM + serverInfo.description() + RESET);
                        }
                    }
                } catch (Exception e) {
                    println("  " + RED + "●" + RESET + " " + BOLD + serverId + RESET + 
                           DIM + " - Error getting info" + RESET);
                }
            }
            
        } catch (Exception e) {
            displayError("Failed to list servers: " + e.getMessage());
        }
    }
    
    private void executeServerInfo(String serverId) {
        try {
            var serverInfo = cliMcpService.getServerInfo(serverId);
            Boolean isHealthy = cliMcpService.isServerHealthy(serverId);
            
            println("");
            println(BRIGHT_BLUE + "📊 " + BOLD + "Server Information: " + serverId + RESET);
            println(BRIGHT_BLUE + "────────────────────────────" + RESET);
            
            if (serverInfo != null) {
                println("  " + BRIGHT_CYAN + "Name:" + RESET + " " + serverInfo.name());
                println("  " + BRIGHT_CYAN + "Version:" + RESET + " " + serverInfo.version());
                if (serverInfo.description() != null) {
                    println("  " + BRIGHT_CYAN + "Description:" + RESET + " " + serverInfo.description());
                }
                println("  " + BRIGHT_CYAN + "Status:" + RESET + " " + 
                       ((isHealthy != null && isHealthy) ? BRIGHT_GREEN + "Healthy" : RED + "Unhealthy") + RESET);
            } else {
                displayError("Server info not available");
            }
            
        } catch (Exception e) {
            displayError("Failed to get server info: " + e.getMessage());
        }
    }
    
    private void executeToolList(String serverId) {
        try {
            var tools = cliMcpService.listTools(serverId);
            
            println("");
            println(BRIGHT_YELLOW + "🛠️  " + BOLD + "Tools for " + serverId + RESET);
            println(BRIGHT_YELLOW + "─".repeat(Math.min(15 + serverId.length(), 50)) + RESET);
            
            if (tools != null && !tools.isEmpty()) {
                for (var tool : tools) {
                    println("  • " + WHITE + tool.name() + RESET);
                    if (tool.description() != null) {
                        println("    " + DIM + tool.description() + RESET);
                    }
                }
            } else {
                println(DIM + "No tools available" + RESET);
            }
            
        } catch (Exception e) {
            displayError("Failed to list tools: " + e.getMessage());
        }
    }
    
    private void executeToolExec(String serverId, String toolName) {
        try {
            println("");
            println(BRIGHT_MAGENTA + "⚡ " + BOLD + "Executing: " + toolName + RESET);
            println(BRIGHT_MAGENTA + "─".repeat(Math.min(12 + toolName.length(), 40)) + RESET);
            
            var result = cliMcpService.executeTool(serverId, toolName, java.util.Map.of());
            
            // Use the ResponseFormatter for beautiful output
            String formattedResult = responseFormatter.formatToolResult(toolName, result);
            println(formattedResult);
            
        } catch (Exception e) {
            displayError("Failed to execute tool: " + e.getMessage());
        }
    }
    
    private void executeListDatabases() {
        try {
            // Use the default server for database operations
            String defaultServer = "mongo-mcp-server-test"; // You might want to make this configurable
            var result = cliMcpService.executeTool(defaultServer, "listDatabases", java.util.Map.of());
            
            println("");
            println(BRIGHT_BLUE + "🗄️  " + BOLD + "Available Databases" + RESET);
            println(BRIGHT_BLUE + "─────────────────────" + RESET);
            
            // Use the ResponseFormatter for beautiful output
            String formattedResult = responseFormatter.formatToolResult("listDatabases", result);
            println(formattedResult);
            
        } catch (Exception e) {
            displayError("Failed to list databases: " + e.getMessage());
        }
    }
    
    private void executeListCollections(String databaseName) {
        try {
            String defaultServer = "mongo-mcp-server-test";
            var params = java.util.Map.<String, Object>of("database", databaseName);
            var result = cliMcpService.executeTool(defaultServer, "listCollections", params);
            
            println("");
            println(BRIGHT_GREEN + "📚 " + BOLD + "Collections in " + databaseName + RESET);
            println(BRIGHT_GREEN + "─".repeat(Math.min(15 + databaseName.length(), 50)) + RESET);
            
            // Use the ResponseFormatter for beautiful output
            String formattedResult = responseFormatter.formatToolResult("listCollections", result);
            println(formattedResult);
            
        } catch (Exception e) {
            displayError("Failed to list collections: " + e.getMessage());
        }
    }
    private void displayError(String message) {
        println("");
        println(RED + "❌ " + BOLD + "Error" + RESET);
        println(RED + "─────" + RESET);
        println("  " + message);
    }
    
    private void displayGoodbye() {
        println("");
        println(BRIGHT_CYAN + "┌─────────────────────────────────────────┐" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + BOLD + WHITE + "  👋 Thanks for using MCP Shell!       " + RESET + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + "                                       " + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + DIM + "  Session: " + sessionId.substring(4, 10) + "                 " + RESET + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "│" + RESET + DIM + "  Commands: " + commandHistory.size() + "                       " + RESET + BRIGHT_CYAN + "│" + RESET);
        println(BRIGHT_CYAN + "└─────────────────────────────────────────┘" + RESET);
        println("");
    }
    
    private void clearScreen() {
        // ANSI escape sequence to clear screen
        print("\033[2J\033[H");
    }
    
    private void cleanup(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                logger.debug("Error closing reader", e);
            }
        }
        ShellOutput.restoreSpringLogs();
    }
    
    private void displayHelp() {
        println("");
        println(BRIGHT_CYAN + "📚 " + BOLD + "MCP Shell Commands" + RESET);
        println(BRIGHT_CYAN + "════════════════════" + RESET);
        println("");
        
        println(BRIGHT_GREEN + "🚀 " + BOLD + "Quick Commands:" + RESET);
        println("  " + WHITE + "servers" + RESET + DIM + "          - List all MCP servers" + RESET);
        println("  " + WHITE + "databases" + RESET + DIM + "        - Show available databases" + RESET);
        println("  " + WHITE + "tools" + RESET + DIM + "           - Display available tools" + RESET);
        println("  " + WHITE + "status" + RESET + DIM + "          - Show shell status" + RESET);
        println("  " + WHITE + "history" + RESET + DIM + "         - View command history" + RESET);
        println("  " + WHITE + "clear" + RESET + DIM + "           - Clear screen" + RESET);
        println("  " + WHITE + "exit" + RESET + DIM + "            - Exit shell" + RESET);
        println("");
        
        println(BRIGHT_YELLOW + "💬 " + BOLD + "Natural Language Examples:" + RESET);
        println("  " + BRIGHT_BLUE + "\"" + WHITE + "Show me all servers" + BRIGHT_BLUE + "\"" + RESET);
        println("  " + BRIGHT_BLUE + "\"" + WHITE + "List databases in mongo server" + BRIGHT_BLUE + "\"" + RESET);
        println("  " + BRIGHT_BLUE + "\"" + WHITE + "Create a new collection called users" + BRIGHT_BLUE + "\"" + RESET);
        println("  " + BRIGHT_BLUE + "\"" + WHITE + "Find all documents in the admin database" + BRIGHT_BLUE + "\"" + RESET);
    }
    
    private void displayHistory() {
        println("");
        println(BRIGHT_MAGENTA + "📜 " + BOLD + "Command History" + RESET);
        println(BRIGHT_MAGENTA + "─────────────────" + RESET);
        
        if (commandHistory.isEmpty()) {
            println(DIM + "No commands in history yet" + RESET);
            return;
        }
        
        for (int i = Math.max(0, commandHistory.size() - 10); i < commandHistory.size(); i++) {
            String cmd = commandHistory.get(i);
            println("  " + DIM + (i + 1) + "." + RESET + " " + WHITE + cmd + RESET);
        }
    }
    
    private void displayStatus() {
        println("");
        println(BRIGHT_GREEN + "⚡ " + BOLD + "Shell Status" + RESET);
        println(BRIGHT_GREEN + "──────────────" + RESET);
        println("");
        println("  " + BRIGHT_CYAN + "Session ID:" + RESET + " " + sessionId);
        println("  " + BRIGHT_CYAN + "Started:" + RESET + " " + sessionStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        println("  " + BRIGHT_CYAN + "Commands executed:" + RESET + " " + commandHistory.size());
        println("  " + BRIGHT_CYAN + "Current context:" + RESET + " " + currentContext);
        println("  " + BRIGHT_CYAN + "AI Response Formatter:" + RESET + " " + BRIGHT_GREEN + "Active" + RESET);
    }
}
