package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.cli.service.CliMcpService;
import com.deepai.mcpclient.model.McpServerInfo;
import com.deepai.mcpclient.model.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server management commands
 * User-friendly commands for managing database servers
 */
@Component
@Command(
    name = "server",
    description = "🖥️  Manage database servers",
    subcommands = {
        ServerCommands.ListCommand.class,
        ServerCommands.InfoCommand.class,
        ServerCommands.HealthCommand.class,
        ServerCommands.StatusCommand.class
    }
)
public class ServerCommands extends BaseCommand {

    @Autowired
    private CliMcpService cliMcpService;

    /**
     * List all available servers
     */
    @Component
    @Command(name = "list", description = "📋 Show all available database servers")
    public static class ListCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Override
        public void run() {
            try {
                printHeader("Available Database Servers");
                
                List<String> serverIds = cliMcpService.getServerIds();
                
                if (serverIds.isEmpty()) {
                    printWarning("No database servers found");
                    System.out.println();
                    printInfo("💡 Make sure your MCP servers are configured and running");
                    return;
                }
                
                // Create server list for display
                List<Map<String, Object>> servers = new ArrayList<>();
                for (String serverId : serverIds) {
                    Map<String, Object> server = new HashMap<>();
                    server.put("serverId", serverId);
                    
                    try {
                        McpServerInfo info = cliMcpService.getServerInfo(serverId);
                        server.put("name", info.name() != null ? info.name() : serverId);
                        server.put("version", info.version() != null ? info.version() : "Unknown");
                        
                        if (serverId.contains("mongo")) {
                            server.put("description", "MongoDB Database Server");
                            server.put("type", "MongoDB");
                        } else {
                            server.put("description", info.description() != null ? info.description() : "Database Server");
                            server.put("type", "Unknown");
                        }
                        
                        Boolean healthy = cliMcpService.isServerHealthy(serverId);
                        server.put("status", healthy ? "✅ Healthy" : "❌ Unhealthy");
                    } catch (Exception e) {
                        server.put("name", serverId);
                        server.put("version", "Unknown");
                        server.put("description", "Connection Error");
                        server.put("type", "Unknown");
                        server.put("status", "❌ Error");
                    }
                    
                    servers.add(server);
                }
                
                tableFormatter.printFormatted(servers, format);
                
                System.out.println();
                printSuccess("Found " + servers.size() + " database server(s)");
                printInfo("💡 Use 'mcpcli server info <server-name>' for details");
                
            } catch (Exception e) {
                printError("Failed to list servers: " + e.getMessage());
                printVerbose("Full error: " + e.toString());
            }
        }
    }

    /**
     * Show detailed information about a specific server
     */
    @Component
    @Command(name = "info", description = "📊 Show detailed information about a database server")
    public static class InfoCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(
            index = "0",
            description = "Name of the database server (e.g., mongo-mcp-server-test)"
        )
        private String serverId;

        @Override
        public void run() {
            try {
                printHeader("Server Information: " + serverId);
                
                McpServerInfo serverInfo = cliMcpService.getServerInfo(serverId);
                
                Map<String, Object> info = new HashMap<>();
                info.put("friendlyName", getFriendlyName(serverId));
                info.put("description", getServerDescription(serverId));
                info.put("serverId", serverId);
                info.put("name", serverInfo.name() != null ? serverInfo.name() : "Unknown");
                info.put("version", serverInfo.version() != null ? serverInfo.version() : "Unknown");
                info.put("author", serverInfo.vendor() != null ? serverInfo.vendor() : "Unknown");
                
                tableFormatter.printFormatted(info, format);
                
                System.out.println();
                printSuccess("Server information retrieved successfully");
                printInfo("💡 Use 'mcpcli server health " + serverId + "' to check if it's working");
                
            } catch (Exception e) {
                printError("Failed to get server info: " + e.getMessage());
                printInfo("💡 Make sure the server name is correct. Use 'mcpcli server list' to see available servers");
            }
        }

        private String getFriendlyName(String serverId) {
            if (serverId.contains("mongo")) {
                return "MongoDB Database";
            } else if (serverId.contains("postgres")) {
                return "PostgreSQL Database";
            } else if (serverId.contains("mysql")) {
                return "MySQL Database";
            }
            return "Database Server";
        }

        private String getServerDescription(String serverId) {
            if (serverId.contains("mongo")) {
                return "NoSQL document database - great for flexible data storage";
            } else if (serverId.contains("postgres")) {
                return "Powerful relational database with advanced features";
            } else if (serverId.contains("mysql")) {
                return "Popular relational database for web applications";
            }
            return "Database server for data storage and retrieval";
        }
    }

    /**
     * Check server health
     */
    @Component
    @Command(name = "health", description = "❤️  Check if a database server is healthy and responding")
    public static class HealthCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(
            index = "0",
            description = "Name of the database server to check"
        )
        private String serverId;

        @Override
        public void run() {
            try {
                printHeader("Health Check: " + serverId);
                
                printInfo("Checking server health...");
                
                Boolean isHealthy = cliMcpService.isServerHealthy(serverId);
                
                Map<String, Object> health = new HashMap<>();
                health.put("server", serverId);
                health.put("status", isHealthy ? "healthy" : "unhealthy");
                health.put("message", isHealthy ? "Server is responding correctly" : "Server is not responding");
                
                if (isHealthy) {
                    printSuccess("✅ Server is healthy and ready to use!");
                } else {
                    printWarning("⚠️  Server is unhealthy");
                }
                
                tableFormatter.printFormatted(health, format);
                
                System.out.println();
                printInfo("💡 If unhealthy, check server logs or restart the service");
                
            } catch (Exception e) {
                printError("❌ Server health check failed: " + e.getMessage());
                printInfo("💡 This usually means the server is not running or not accessible");
                printInfo("🔧 Try restarting the server or check your configuration");
            }
        }
    }

    /**
     * Show server status (alias for health with more details)
     */
    @Component
    @Command(name = "status", description = "📈 Show comprehensive server status and statistics")
    public static class StatusCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(
            index = "0",
            description = "Name of the database server"
        )
        private String serverId;

        @Override
        public void run() {
            try {
                printHeader("Server Status: " + serverId);
                
                // Get basic health
                Boolean isHealthy = cliMcpService.isServerHealthy(serverId);
                Map<String, Object> health = new HashMap<>();
                health.put("server", serverId);
                health.put("status", isHealthy ? "healthy" : "unhealthy");
                health.put("responsive", isHealthy ? "Yes" : "No");
                
                System.out.println("🏥 Health Status:");
                tableFormatter.printFormatted(health, "table");
                
                System.out.println();
                
                // Get available tools count
                try {
                    List<McpTool> tools = cliMcpService.listTools(serverId);
                    printInfo("🛠️  Available tools: " + tools.size());
                    
                    if (!tools.isEmpty() && verbose) {
                        System.out.println();
                        System.out.println("📋 Available Tools:");
                        for (McpTool tool : tools) {
                            System.out.println("  • " + tool.name() + " - " + tool.description());
                        }
                    }
                } catch (Exception e) {
                    printWarning("Could not retrieve tools information");
                }
                
                System.out.println();
                printSuccess("Status check completed");
                printInfo("💡 Use --verbose for more detailed information");
                
            } catch (Exception e) {
                printError("Failed to get server status: " + e.getMessage());
            }
        }
    }
}
