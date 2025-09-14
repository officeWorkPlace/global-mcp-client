package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.cli.client.McpApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

/**
 * AI assistant commands
 * Super user-friendly - lets normal users ask questions in plain English!
 */
@Component
@Command(
    name = "ai",
    description = "🤖 Ask your AI assistant anything about your databases",
    subcommands = {
        AiCommands.AskCommand.class,
        AiCommands.ChatCommand.class,
        AiCommands.HealthCommand.class,
        AiCommands.ExplainCommand.class
    }
)
public class AiCommands extends BaseCommand {

    private static final Logger logger = LoggerFactory.getLogger(AiCommands.class);

    @Autowired
    private McpApiClient apiClient;

    /**
     * Ask AI a quick question
     */
    @Component
    @Command(name = "ask", description = "💬 Ask a quick question to your AI assistant")
    public static class AskCommand extends BaseCommand implements Runnable {

        @Autowired
        private McpApiClient apiClient;

        @Parameters(
            index = "0..*",
            description = "Your question in plain English (e.g., \"Show me all databases\")"
        )
        private String[] questionWords;

        @Override
        public void run() {
            try {
                if (questionWords == null || questionWords.length == 0) {
                    printError("Please ask a question!");
                    printInfo("💡 Example: mcpcli ai ask \"Show me all databases\"");
                    printInfo("💡 Example: mcpcli ai ask \"How many users do we have?\"");
                    return;
                }

                String question = String.join(" ", questionWords);
                
                printHeader("🤖 AI Assistant");
                printInfo("Question: " + colorOutput.cyan(question));
                printInfo("Thinking...");
                
                Map<String, Object> response = apiClient.askAi(question);
                
                System.out.println();
                printSuccess("AI Response:");
                System.out.println();
                
                String aiResponse = (String) response.get("response");
                System.out.println(colorOutput.green("🤖 ") + aiResponse);
                
                System.out.println();
                printInfo("💡 Need more help? Try 'mcpcli ai chat' for a conversation");
                
            } catch (Exception e) {
                logger.error("AI assistant request failed", e);
                printError("AI assistant is not available: " + e.getMessage());
                printInfo("💡 Make sure the MCP server is running with AI enabled");
                printInfo("🔧 Check if GEMINI_API_KEY environment variable is set");
            }
        }
    }

    /**
     * Start an interactive chat with AI
     */
    @Component
    @Command(name = "chat", description = "💭 Have a conversation with your AI assistant")
    public static class ChatCommand extends BaseCommand implements Runnable {

        @Autowired
        private McpApiClient apiClient;

        @Option(
            names = {"-s", "--server"},
            description = "Focus on a specific database server"
        )
        private String serverId;

        @Override
        public void run() {
            try {
                printHeader("🤖 AI Chat Assistant");
                System.out.println("Welcome! I'm your friendly database assistant.");
                System.out.println("Ask me anything about your databases in plain English!");
                System.out.println();
                System.out.println(colorOutput.gray("💡 Examples:"));
                System.out.println(colorOutput.gray("  • \"Show me all databases\""));
                System.out.println(colorOutput.gray("  • \"How many users do we have?\""));
                System.out.println(colorOutput.gray("  • \"Create a new collection called products\""));
                System.out.println(colorOutput.gray("  • \"Find users with email containing gmail\""));
                System.out.println();
                System.out.println(colorOutput.yellow("Type 'exit' or 'quit' to end the conversation"));
                printDivider();

                String contextId = UUID.randomUUID().toString();
                
                try (Scanner scanner = new Scanner(System.in)) {
                    while (true) {
                        System.out.print(colorOutput.cyan("You: "));
                        String input = scanner.nextLine().trim();
                        
                        if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                        printSuccess("👋 Goodbye! Feel free to ask me anything anytime.");
                        break;
                    }
                    
                    if (input.isEmpty()) {
                        continue;
                    }
                    
                    try {
                        printVerbose("Sending message to AI...");
                        Map<String, Object> response = apiClient.chatWithAi(input, serverId, contextId);
                        
                        String aiResponse = (String) response.get("response");
                        System.out.println(colorOutput.green("🤖 AI: ") + aiResponse);
                        System.out.println();
                        
                    } catch (Exception e) {
                        logger.warn("AI chat request failed", e);
                        printError("Sorry, I couldn't process that: " + e.getMessage());
                        printInfo("💡 Try rephrasing your question or check the server connection");
                    }
                    }
                } // End try-with-resources for Scanner
                
            } catch (Exception e) {
                logger.error("Chat session initialization failed", e);
                printError("Chat session failed: " + e.getMessage());
                printInfo("💡 Make sure the AI service is running and accessible");
            }
        }
    }

    /**
     * Check AI assistant health
     */
    @Component
    @Command(name = "health", description = "❤️  Check if your AI assistant is working")
    public static class HealthCommand extends BaseCommand implements Runnable {

        @Autowired
        private McpApiClient apiClient;

        @Override
        public void run() {
            try {
                printHeader("🤖 AI Assistant Health Check");
                
                printInfo("Checking AI assistant...");
                Map<String, Object> health = apiClient.getAiHealth();
                
                String status = (String) health.get("status");
                if ("healthy".equalsIgnoreCase(status)) {
                    printSuccess("✅ AI assistant is ready to help you!");
                } else {
                    printWarning("⚠️  AI assistant status: " + status);
                }
                
                tableFormatter.printFormatted(health, format);
                
                System.out.println();
                printInfo("💡 Try asking: 'mcpcli ai ask \"Hello, are you working?\"'");
                
            } catch (Exception e) {
                logger.error("AI health check failed", e);
                printError("❌ AI assistant health check failed: " + e.getMessage());
                printInfo("💡 This usually means the AI service is not configured or running");
                printInfo("🔧 Check if GEMINI_API_KEY environment variable is set");
                printInfo("🔧 Make sure the MCP server is running with AI enabled");
            }
        }
    }

    /**
     * Get AI to explain database concepts
     */
    @Component
    @Command(name = "explain", description = "📚 Get AI to explain database concepts in simple terms")
    public static class ExplainCommand extends BaseCommand implements Runnable {

        @Autowired
        private McpApiClient apiClient;

        @Parameters(
            index = "0..*",
            description = "What you want explained (e.g., \"What is a collection?\", \"How does indexing work?\")"
        )
        private String[] conceptWords;

        @Override
        public void run() {
            try {
                if (conceptWords == null || conceptWords.length == 0) {
                    // Show some helpful examples
                    printHeader("🤖 AI Database Tutor");
                    System.out.println("Ask me to explain any database concept!");
                    System.out.println();
                    System.out.println(colorOutput.cyan("💡 Examples:"));
                    System.out.println("  mcpcli ai explain \"What is a database?\"");
                    System.out.println("  mcpcli ai explain \"What is MongoDB?\"");
                    System.out.println("  mcpcli ai explain \"How do I find data?\"");
                    System.out.println("  mcpcli ai explain \"What is a collection?\"");
                    System.out.println("  mcpcli ai explain \"How does indexing work?\"");
                    System.out.println();
                    printInfo("💡 I'll explain things in simple, easy-to-understand terms!");
                    return;
                }

                String concept = String.join(" ", conceptWords);
                String question = "Please explain in simple terms for a beginner: " + concept;
                
                printHeader("🤖 AI Database Tutor");
                printInfo("Explaining: " + colorOutput.cyan(concept));
                printInfo("Let me break this down for you...");
                
                Map<String, Object> response = apiClient.askAi(question);
                
                System.out.println();
                printSuccess("📚 Explanation:");
                System.out.println();
                
                String explanation = (String) response.get("response");
                System.out.println(colorOutput.green("🤖 ") + explanation);
                
                System.out.println();
                printInfo("💡 Want to try it out? Use 'mcpcli tool quick' for hands-on practice!");
                printInfo("💡 Have more questions? Use 'mcpcli ai chat' for a conversation!");
                
            } catch (Exception e) {
                logger.error("AI explanation request failed", e);
                printError("Explanation failed: " + e.getMessage());
                printInfo("💡 Make sure the AI assistant is available and working");
            }
        }
    }
}
