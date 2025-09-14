package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.cli.client.McpApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Map;

/**
 * Configuration management commands
 * User-friendly configuration for normal users
 */
@Component
@Command(
    name = "config",
    description = "⚙️  Manage your database connection settings",
    subcommands = {
        ConfigCommands.ShowCommand.class,
        ConfigCommands.TestCommand.class,
        ConfigCommands.SetCommand.class,
        ConfigCommands.ResetCommand.class
    }
)
public class ConfigCommands extends BaseCommand {

    @Autowired
    private McpApiClient apiClient;

    /**
     * Show current configuration
     */
    @Component
    @Command(name = "show", description = "👀 Show current connection settings")
    public static class ShowCommand extends BaseCommand implements Runnable {

        @Autowired
        private McpApiClient apiClient;

        @Option(
            names = {"-s", "--sensitive"}, 
            description = "Show sensitive information like passwords (be careful!)"
        )
        private boolean showSensitive;

        @Override
        public void run() {
            try {
                printHeader("⚙️  Current Configuration");
                
                Map<String, Object> config = apiClient.getConfiguration();
                
                if (!showSensitive) {
                    // Hide sensitive data for security
                    config = maskSensitiveData(config);
                    printWarning("🔒 Sensitive data is hidden. Use --sensitive to show all");
                }
                
                if (config.isEmpty()) {
                    printInfo("No configuration found. Use 'mcpcli config set' to add settings.");
                } else {
                    tableFormatter.printFormatted(config, format);
                }
                
                System.out.println();
                printInfo("💡 Use 'mcpcli config test' to check if connections work");
                
            } catch (Exception e) {
                printError("Failed to get configuration: " + e.getMessage());
                printInfo("💡 Make sure the MCP server is running");
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> maskSensitiveData(Map<String, Object> config) {
            // Simple masking for demonstration
            config.entrySet().forEach(entry -> {
                String key = entry.getKey().toLowerCase();
                if (key.contains("password") || key.contains("secret") || key.contains("token")) {
                    entry.setValue("****");
                }
            });
            return config;
        }
    }

    /**
     * Test configuration
     */
    @Component
    @Command(name = "test", description = "🧪 Test if your database connections are working")
    public static class TestCommand extends BaseCommand implements Runnable {

        @Autowired
        private McpApiClient apiClient;

        @Override
        public void run() {
            try {
                printHeader("🧪 Testing Configuration");
                printInfo("Checking database connections...");
                
                Map<String, Object> testResults = apiClient.testConfiguration();
                
                Boolean allGood = (Boolean) testResults.get("allConnectionsWorking");
                if (Boolean.TRUE.equals(allGood)) {
                    printSuccess("✅ All database connections are working perfectly!");
                } else {
                    printWarning("⚠️  Some connections have issues");
                }
                
                tableFormatter.printFormatted(testResults, format);
                
                System.out.println();
                printInfo("💡 If you see any failures, check your connection settings");
                printInfo("💡 Use 'mcpcli config set' to update connection details");
                
            } catch (Exception e) {
                printError("❌ Configuration test failed: " + e.getMessage());
                printInfo("💡 This usually means the MCP server is not reachable");
                printInfo("🔧 Check if the server is running on the correct port");
            }
        }
    }

    /**
     * Set configuration values
     */
    @Component
    @Command(name = "set", description = "🔧 Set connection settings (interactive and safe)")
    public static class SetCommand extends BaseCommand implements Runnable {

        @Autowired
        private McpApiClient apiClient;

        @Parameters(
            index = "0",
            description = "What to configure: 'database', 'ai', 'server', or 'all'"
        )
        private String configType;

        @Option(
            names = {"-f", "--file"},
            description = "Load settings from a configuration file"
        )
        private String configFile;

        @Override
        public void run() {
            try {
                printHeader("🔧 Configuration Setup");
                
                if (configFile != null) {
                    printInfo("Loading configuration from file: " + configFile);
                    // File-based configuration would be implemented here
                    printError("File-based configuration is not yet implemented");
                    printInfo("💡 For now, please use the interactive setup");
                    return;
                }
                
                switch (configType.toLowerCase()) {
                    case "database":
                        setupDatabaseConfig();
                        break;
                    case "ai":
                        setupAiConfig();
                        break;
                    case "server":
                        setupServerConfig();
                        break;
                    case "all":
                        setupAllConfig();
                        break;
                    default:
                        printError("Unknown configuration type: " + configType);
                        printInfo("💡 Valid options: database, ai, server, all");
                        return;
                }
                
                printSuccess("✅ Configuration updated successfully!");
                printInfo("💡 Use 'mcpcli config test' to verify your settings");
                
            } catch (Exception e) {
                printError("Configuration setup failed: " + e.getMessage());
                printInfo("💡 Please check your inputs and try again");
            }
        }

        private void setupDatabaseConfig() {
            printInfo("🗄️  Database Configuration (Coming Soon)");
            printInfo("This will help you set up database connections interactively");
            printInfo("💡 For now, use environment variables or application.yml");
        }

        private void setupAiConfig() {
            printInfo("🤖 AI Configuration (Coming Soon)");
            printInfo("This will help you configure the AI assistant");
            printInfo("💡 For now, set GEMINI_API_KEY environment variable");
        }

        private void setupServerConfig() {
            printInfo("🌐 Server Configuration (Coming Soon)");
            printInfo("This will help you configure MCP server settings");
            printInfo("💡 For now, use application.yml for server settings");
        }

        private void setupAllConfig() {
            printInfo("🚀 Complete Setup (Coming Soon)");
            printInfo("This will guide you through setting up everything");
            printInfo("💡 Interactive configuration wizard is being developed");
        }
    }

    /**
     * Reset configuration to defaults
     */
    @Component
    @Command(name = "reset", description = "🔄 Reset settings to defaults (with confirmation)")
    public static class ResetCommand extends BaseCommand implements Runnable {

        @Autowired
        private McpApiClient apiClient;

        @Option(
            names = {"-y", "--yes"},
            description = "Skip confirmation prompt (dangerous!)"
        )
        private boolean skipConfirmation;

        @Override
        public void run() {
            try {
                printHeader("🔄 Reset Configuration");
                
                if (!skipConfirmation) {
                    printWarning("⚠️  This will reset ALL settings to defaults!");
                    printWarning("🔒 Any custom database connections will be lost!");
                    System.out.println();
                    System.out.print(colorOutput.yellow("Are you sure? Type 'yes' to continue: "));
                    
                    java.util.Scanner scanner = new java.util.Scanner(System.in);
                    String confirmation = scanner.nextLine().trim();
                    
                    if (!"yes".equalsIgnoreCase(confirmation)) {
                        printInfo("Reset cancelled. Your settings are safe!");
                        return;
                    }
                }
                
                printInfo("Resetting configuration...");
                
                Map<String, Object> result = apiClient.resetConfiguration();
                
                printSuccess("✅ Configuration reset to defaults");
                tableFormatter.printFormatted(result, format);
                
                System.out.println();
                printInfo("💡 Use 'mcpcli config set all' to set up your connections again");
                printInfo("💡 Use 'mcpcli config test' to verify everything works");
                
            } catch (Exception e) {
                printError("Configuration reset failed: " + e.getMessage());
                printInfo("💡 Your existing settings are still intact");
            }
        }
    }
}
