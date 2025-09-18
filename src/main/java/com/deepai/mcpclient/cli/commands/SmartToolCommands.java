package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.cli.model.CliToolResult;
import com.deepai.mcpclient.cli.service.CliMcpService;
import com.deepai.mcpclient.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Smart AI-powered tool commands that use the same intelligence as the REST API
 */
@Component
@Command(
    name = "smart",
    description = "🧠 AI-powered tool execution with natural language",
    subcommands = {
        SmartToolCommands.AskCommand.class,
        SmartToolCommands.ExecuteCommand.class
    }
)
public class SmartToolCommands extends BaseCommand {

    @Autowired
    private CliMcpService cliMcpService;

    /**
     * Ask AI to perform operations using natural language
     */
    @Component
    @Command(name = "ask", description = "🤖 Ask AI to perform operations using natural language")
    public static class AskCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(
            index = "0..*",
            description = "Natural language request (e.g., 'show me all databases', 'find users in the admin database')"
        )
        private String[] requestWords;

        @Option(
            names = {"-c", "--context"},
            description = "Context ID for conversation continuity"
        )
        private String contextId;

        @Override
        public void run() {
            if (requestWords == null || requestWords.length == 0) {
                printError("Please provide a natural language request");
                printInfo("Example: smart ask show me all databases");
                return;
            }

            String naturalLanguageRequest = String.join(" ", requestWords);

            printInfo("🧠 Processing: \"" + naturalLanguageRequest + "\"");
            System.out.println();

            try {
                CliToolResult result = cliMcpService.executeToolWithAi(naturalLanguageRequest, contextId);

                if (result.isSuccess()) {
                    printSuccess("✅ AI Response:");
                    System.out.println();
                    System.out.println(result.getResponse());

                    if (result.hasToolExecutions()) {
                        System.out.println();
                        printInfo("🔧 Tool Executions:");
                        for (ChatResponse.ToolExecution execution : result.getToolExecutions()) {
                            System.out.printf("  • %s on %s (%s) - %dms%n",
                                execution.toolName(),
                                execution.serverId(),
                                execution.success() ? "✅ Success" : "❌ Failed",
                                execution.executionTimeMs()
                            );
                        }
                    }

                    if (result.getModel() != null) {
                        System.out.println();
                        printDebug("Model used: " + result.getModel());
                    }
                } else {
                    printError("❌ AI processing failed: " + result.getResponse());
                }

            } catch (Exception e) {
                printError("Error processing request: " + e.getMessage());
            }
        }
    }

    /**
     * Execute specific tool with AI intelligence and validation
     */
    @Component
    @Command(name = "exec", description = "🔧 Execute specific tool with AI intelligence")
    public static class ExecuteCommand extends BaseCommand implements Runnable {

        @Autowired
        private CliMcpService cliMcpService;

        @Parameters(index = "0", description = "Server ID")
        private String serverId;

        @Parameters(index = "1", description = "Tool name")
        private String toolName;

        @Option(
            names = {"-p", "--param"},
            description = "Tool parameters in key=value format (can specify multiple times)"
        )
        private String[] parameters;

        @Override
        public void run() {
            printInfo("🔧 Executing tool '" + toolName + "' on server '" + serverId + "' with AI intelligence");

            try {
                // Parse parameters
                java.util.Map<String, Object> toolParams = new java.util.HashMap<>();
                if (parameters != null) {
                    for (String param : parameters) {
                        String[] parts = param.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();

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
                    }
                }

                printInfo("Parameters: " + toolParams);
                System.out.println();

                // Execute with AI intelligence (includes validation, rate limiting, etc.)
                var result = cliMcpService.executeTool(serverId, toolName, toolParams);

                if (result != null) {
                    printSuccess("✅ Tool executed successfully with AI intelligence!");
                    System.out.println();

                    if (result.content() != null && !result.content().isEmpty()) {
                        printInfo("📄 Result:");
                        result.content().forEach(content -> {
                            if (content.text() != null) {
                                System.out.println(content.text());
                            }
                        });
                    }
                } else {
                    printError("❌ Tool execution failed");
                }

            } catch (Exception e) {
                printError("Error executing tool: " + e.getMessage());
            }
        }
    }
}