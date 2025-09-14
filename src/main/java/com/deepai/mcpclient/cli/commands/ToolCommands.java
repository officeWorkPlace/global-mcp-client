package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.cli.service.CliMcpService;
import com.deepai.mcpclient.model.McpTool;
import com.deepai.mcpclient.model.McpToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool execution commands
 * User-friendly commands for running database operations
 */
@Component
@Command(
    name = "tool",
    description = "🛠️  Run database operations and tools",
    subcommands = {
        ToolCommands.ListCommand.class,
        ToolCommands.InfoCommand.class,
        ToolCommands.ExecCommand.class,
        ToolCommands.QuickCommand.class
    }
)
public class ToolCommands extends BaseCommand {

    @Autowired
    private CliMcpService cliMcpService;

    /**
     * List available tools for a specific server
     */
    @Component
    @Command(name = "list", description = "📋 Show all available database tools for a server")
    public static class ListCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(
            index = "0",
            description = "Server name (e.g., mongo-mcp-server-test)"
        )
        private String serverId;

        @Override
        public void run() {
            try {
                printHeader("Available Tools: " + serverId);
                
                List<McpTool> tools = cliMcpService.listTools(serverId);
                
                if (tools.isEmpty()) {
                    printWarning("No tools available for server: " + serverId);
                    printInfo("💡 The server may not be running or may not have any tools configured");
                    return;
                }
                
                // Convert to display format
                List<Map<String, Object>> toolList = new ArrayList<>();
                for (McpTool tool : tools) {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("name", tool.name());
                    toolMap.put("description", tool.description());
                    toolMap.put("category", getCategoryForTool(tool.name()));
                    toolList.add(toolMap);
                }
                
                tableFormatter.printFormatted(toolList, format);
                
                System.out.println();
                printSuccess("Found " + tools.size() + " available tool(s)");
                printInfo("💡 Use 'mcpcli tool info " + serverId + " <tool-name>' for details");
                printInfo("💡 Use 'mcpcli tool exec " + serverId + " <tool-name>' to run a tool");
                
            } catch (Exception e) {
                printError("Failed to list tools: " + e.getMessage());
                printInfo("💡 Make sure the server is running and accessible");
            }
        }
        
        private String getCategoryForTool(String toolName) {
            if (toolName.toLowerCase().contains("database") || toolName.toLowerCase().contains("db")) {
                return "Database";
            } else if (toolName.toLowerCase().contains("collection") || toolName.toLowerCase().contains("table")) {
                return "Collection";
            } else if (toolName.toLowerCase().contains("find") || toolName.toLowerCase().contains("query") || toolName.toLowerCase().contains("search")) {
                return "Query";
            } else if (toolName.toLowerCase().contains("insert") || toolName.toLowerCase().contains("create") || toolName.toLowerCase().contains("add")) {
                return "Create";
            } else if (toolName.toLowerCase().contains("update") || toolName.toLowerCase().contains("modify")) {
                return "Update";
            } else if (toolName.toLowerCase().contains("delete") || toolName.toLowerCase().contains("remove")) {
                return "Delete";
            }
            return "General";
        }
    }

    /**
     * List all available tools from all servers
     */
    @Component
    @Command(name = "all", description = "📋 Show all available tools from all servers")
    public static class AllCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Override
        public void run() {
            try {
                printHeader("All Available Database Tools");
                
                Map<String, List<McpTool>> allTools = cliMcpService.getAllTools();
                
                if (allTools.isEmpty()) {
                    printWarning("No tools available from any server");
                    printInfo("💡 Make sure your servers are running and configured");
                    return;
                }
                
                List<Map<String, Object>> toolList = new ArrayList<>();
                int totalTools = 0;
                
                for (Map.Entry<String, List<McpTool>> entry : allTools.entrySet()) {
                    String serverId = entry.getKey();
                    List<McpTool> tools = entry.getValue();
                    
                    for (McpTool tool : tools) {
                        Map<String, Object> toolMap = new HashMap<>();
                        toolMap.put("server", serverId);
                        toolMap.put("name", tool.name());
                        toolMap.put("description", tool.description());
                        toolList.add(toolMap);
                        totalTools++;
                    }
                }
                
                tableFormatter.printFormatted(toolList, format);
                
                System.out.println();
                printSuccess("Found " + totalTools + " total tool(s) across " + allTools.size() + " server(s)");
                printInfo("💡 Use 'mcpcli tool exec <server> <tool>' to run a specific tool");
                
            } catch (Exception e) {
                printError("Failed to list all tools: " + e.getMessage());
                printVerbose("Full error: " + e.toString());
            }
        }
    }

    /**
     * Show detailed information about a specific tool
     */
    @Component
    @Command(name = "info", description = "📊 Show detailed information about a specific tool")
    public static class InfoCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(index = "0", description = "Server name")
        private String serverId;

        @Parameters(index = "1", description = "Tool name")
        private String toolName;

        @Override
        public void run() {
            try {
                printHeader("Tool Information: " + toolName + " (" + serverId + ")");
                
                List<McpTool> tools = cliMcpService.listTools(serverId);
                McpTool tool = tools.stream()
                    .filter(t -> t.name().equals(toolName))
                    .findFirst()
                    .orElse(null);
                
                if (tool == null) {
                    printError("Tool '" + toolName + "' not found on server '" + serverId + "'");
                    printInfo("💡 Use 'mcpcli tool list " + serverId + "' to see available tools");
                    return;
                }
                
                Map<String, Object> info = new HashMap<>();
                info.put("name", tool.name());
                info.put("description", tool.description());
                info.put("server", serverId);
                
                // Add input schema info if available
                if (tool.inputSchema() != null && tool.inputSchema().containsKey("properties")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");
                    info.put("parameters", properties.size() + " parameter(s)");
                } else {
                    info.put("parameters", "No parameters required");
                }
                
                tableFormatter.printFormatted(info, format);
                
                System.out.println();
                printSuccess("Tool information retrieved successfully");
                printInfo("💡 Use 'mcpcli tool exec " + serverId + " " + toolName + "' to run this tool");
                
            } catch (Exception e) {
                printError("Failed to get tool info: " + e.getMessage());
                printInfo("💡 Make sure the server and tool names are correct");
            }
        }
    }

    /**
     * Execute a tool with parameters
     */
    @Component
    @Command(name = "exec", description = "⚡ Execute a database tool")
    public static class ExecCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(index = "0", description = "Server name")
        private String serverId;

        @Parameters(index = "1", description = "Tool name")
        private String toolName;

        @Option(names = {"-p", "--param"}, description = "Tool parameters in key=value format")
        private Map<String, String> params = new HashMap<>();

        @Override
        public void run() {
            try {
                printHeader("Executing Tool: " + toolName + " (" + serverId + ")");
                
                // Convert string params to proper types
                Map<String, Object> toolParams = new HashMap<>();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    // Try to convert to appropriate type
                    try {
                        if (value.equals("true") || value.equals("false")) {
                            toolParams.put(key, Boolean.parseBoolean(value));
                        } else if (value.matches("\\d+")) {
                            toolParams.put(key, Integer.parseInt(value));
                        } else {
                            toolParams.put(key, value);
                        }
                    } catch (Exception e) {
                        toolParams.put(key, value); // Default to string
                    }
                }
                
                printInfo("Executing tool with parameters: " + toolParams);
                
                McpToolResult result = cliMcpService.executeTool(serverId, toolName, toolParams);
                
                System.out.println();
                printSuccess("✅ Tool executed successfully!");
                
                if (result.content() != null && !result.content().isEmpty()) {
                    System.out.println();
                    System.out.println("📄 Result:");
                    result.content().forEach(content -> {
                        if (content.text() != null) {
                            System.out.println(content.text());
                        }
                    });
                }
                
                System.out.println();
                printInfo("💡 Use --verbose for more detailed output");
                
            } catch (Exception e) {
                printError("Failed to execute tool: " + e.getMessage());
                printInfo("💡 Check tool parameters with 'mcpcli tool info " + serverId + " " + toolName + "'");
                printVerbose("Full error: " + e.toString());
            }
        }
    }

    /**
     * Quick access to common database operations
     */
    @Component
    @Command(name = "quick", description = "🚀 Quick access to common database operations")
    public static class QuickCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(
            index = "0",
            description = "Operation: ping, databases, collections, status"
        )
        private String operation;

        @Option(names = {"-s", "--server"}, description = "Server name (auto-detected if not specified)")
        private String serverId;

        @Option(names = {"-d", "--database"}, description = "Database name (for collections operation)")
        private String database;

        @Override
        public void run() {
            try {
                // Auto-detect server if not specified
                if (serverId == null) {
                    List<String> servers = cliMcpService.getServerIds();
                    if (servers.isEmpty()) {
                        printError("No servers available");
                        return;
                    }
                    serverId = servers.get(0); // Use first available server
                    printInfo("Using server: " + serverId);
                }

                printHeader("Quick Operation: " + operation.toUpperCase());

                switch (operation.toLowerCase()) {
                    case "ping":
                        executePing();
                        break;
                    case "databases":
                        executeDatabases();
                        break;
                    case "collections":
                        executeCollections();
                        break;
                    case "status":
                        executeStatus();
                        break;
                    default:
                        printError("Unknown operation: " + operation);
                        printInfo("Available operations: ping, databases, collections, status");
                }

            } catch (Exception e) {
                printError("Quick operation failed: " + e.getMessage());
                printVerbose("Full error: " + e.toString());
            }
        }

        private void executePing() throws Exception {
            McpToolResult result = cliMcpService.executeTool(serverId, "ping", Map.of());
            printSuccess("✅ Ping successful - server is responding");
        }

        private void executeDatabases() throws Exception {
            McpToolResult result = cliMcpService.executeTool(serverId, "listDatabases", Map.of());
            printSuccess("✅ Database list retrieved");
            if (result.content() != null) {
                result.content().forEach(content -> {
                    if (content.text() != null) {
                        System.out.println(content.text());
                    }
                });
            }
        }

        private void executeCollections() throws Exception {
            if (database == null) {
                printError("Database name required for collections operation");
                printInfo("Use: mcpcli tool quick collections --database <name>");
                return;
            }
            
            Map<String, Object> params = Map.of("database", database);
            McpToolResult result = cliMcpService.executeTool(serverId, "listCollections", params);
            printSuccess("✅ Collections list retrieved for database: " + database);
            if (result.content() != null) {
                result.content().forEach(content -> {
                    if (content.text() != null) {
                        System.out.println(content.text());
                    }
                });
            }
        }

        private void executeStatus() throws Exception {
            Boolean isHealthy = cliMcpService.isServerHealthy(serverId);
            if (isHealthy) {
                printSuccess("✅ Server status: Healthy");
            } else {
                printWarning("⚠️ Server status: Unhealthy");
            }
        }
    }
}
