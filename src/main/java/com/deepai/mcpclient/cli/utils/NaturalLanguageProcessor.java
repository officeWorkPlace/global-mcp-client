package com.deepai.mcpclient.cli.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI-Powered Natural Language Command Processor for MCP Shell
 * Uses real-time AI to understand and convert natural language to MCP commands
 */
@Component
public class NaturalLanguageProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(NaturalLanguageProcessor.class);
    
    @Autowired(required = false)
    private ChatModel chatModel;
    
    private static final String DEFAULT_SERVER = "mongo-mcp-server-test";
    
    // System prompt for command interpretation
    private static final String SYSTEM_PROMPT = """
        You are an AI assistant that converts natural language to MCP (Model Context Protocol) commands.
        
        Available MCP Command Structure:
        - server list                           - List all available servers
        - server info <server-id>              - Get server information  
        - server health <server-id>            - Check server health
        - tool list <server-id>                - List tools on a server
        - tool all                             - List all tools from all servers
        - tool exec <server-id> <tool-name>    - Execute a specific tool
        - help                                 - Show help
        - clear                               - Clear screen
        - exit                                - Exit shell
        
        Default server is: mongo-mcp-server-test
        
        Common MongoDB tools available:
        - ping                  - Test connectivity
        - listDatabases        - List all databases
        - listCollections      - List collections (needs database parameter)
        - findDocuments        - Find documents in collection
        - insertDocument       - Insert a document
        - updateDocument       - Update documents
        - deleteDocument       - Delete documents
        - createIndex          - Create database index
        - dropIndex           - Drop database index
        
        RULES:
        1. Convert natural language to exact MCP command syntax
        2. If user asks about "mongo" or "test server", use "mongo-mcp-server-test"
        3. For database operations, default to "mongo-mcp-server-test" server
        4. Return ONLY the command, no explanations
        5. If request is unclear, return "HELP_NEEDED: [brief reason]"
        
        Examples:
        "Show me all servers" → server list
        "List tools on mongo server" → tool list mongo-mcp-server-test
        "Ping the test server" → tool exec mongo-mcp-server-test ping
        "What databases are available?" → tool exec mongo-mcp-server-test listDatabases
        "Get server info" → server info mongo-mcp-server-test
        "List collections" → tool exec mongo-mcp-server-test listCollections
        "Help me" → help
        "Clear screen" → clear
        "Exit" → exit
        
        Convert this natural language to MCP command:
        """;
    
    /**
     * Process natural language input using AI and return appropriate command
     */
    public CommandResult processInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new CommandResult(false, "");
        }
        
        String normalizedInput = input.trim();
        
        // Quick check for direct commands (bypass AI for performance)
        if (isDirectCommand(normalizedInput)) {
            return new CommandResult(true, normalizedInput);
        }
        
        // Use AI for natural language processing
        return processWithAI(normalizedInput);
    }
    
    /**
     * Check if input is already a direct command
     */
    private boolean isDirectCommand(String input) {
        String lower = input.toLowerCase();
        return lower.startsWith("server ") || 
               lower.startsWith("tool ") || 
               lower.startsWith("config ") ||
               lower.equals("help") || 
               lower.equals("clear") || 
               lower.equals("exit") ||
               lower.equals("quit");
    }
    
    /**
     * Process input using AI model for real-time natural language understanding
     */
    private CommandResult processWithAI(String input) {
        if (chatModel == null) {
            logger.warn("No AI model available, falling back to pattern matching");
            return fallbackProcessing(input);
        }
        
        try {
            // Create AI prompt with comprehensive instructions
            String fullPrompt = SYSTEM_PROMPT + "\nUser input: \"" + input + "\"";
            UserMessage userMessage = new UserMessage(fullPrompt);
            Prompt prompt = new Prompt(userMessage);
            
            // Get AI response
            logger.info("Processing with AI: {}", input);
            var response = chatModel.call(prompt);
            String aiCommand = response.getResult().getOutput().getText();
            if (aiCommand == null) {
                aiCommand = "";
            }
            aiCommand = aiCommand.trim();
            
            logger.info("✅ AI processed '{}' → '{}'", input, aiCommand);
            
            // Validate AI response
            if (aiCommand.startsWith("HELP_NEEDED:")) {
                String reason = aiCommand.substring("HELP_NEEDED:".length()).trim();
                return new CommandResult(false, createHelpMessage(input, reason));
            }
            
            // Return the AI-generated command
            return new CommandResult(true, aiCommand);
            
        } catch (Exception e) {
            logger.error("AI processing failed for '{}': {}", input, e.getMessage());
            logger.debug("AI processing error details", e);
            // Fallback to pattern matching
            return fallbackProcessing(input);
        }
    }
    
    /**
     * Validate if the AI response is a valid MCP command
     */
    private boolean isValidMcpCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        
        String[] parts = command.split("\\s+");
        if (parts.length == 0) {
            return false;
        }
        
        String mainCommand = parts[0].toLowerCase();
        
        // Check valid main commands
        switch (mainCommand) {
            case "server":
                return parts.length >= 2 && 
                       ("list".equals(parts[1]) || 
                        ("info".equals(parts[1]) && parts.length >= 3) ||
                        ("health".equals(parts[1]) && parts.length >= 3));
            case "tool":
                return parts.length >= 2 && 
                       ("all".equals(parts[1]) ||
                        ("list".equals(parts[1]) && parts.length >= 3) ||
                        ("exec".equals(parts[1]) && parts.length >= 4));
            case "help":
            case "clear":
            case "exit":
                return parts.length == 1;
            default:
                return false;
        }
    }
    
    /**
     * Fallback processing when AI is not available
     */
    private CommandResult fallbackProcessing(String input) {
        String lower = input.toLowerCase();
        
        // Server commands
        if (lower.contains("server") && (lower.contains("list") || lower.contains("show"))) {
            return new CommandResult(true, "server list");
        }
        
        // Tool commands - more comprehensive patterns
        if ((lower.contains("tool") && lower.contains("list")) ||
            (lower.contains("show") && lower.contains("tool")) ||
            (lower.contains("all") && lower.contains("tool"))) {
            return new CommandResult(true, "tool all");
        }
        
        if (lower.contains("tool") && lower.contains("mongo")) {
            return new CommandResult(true, "tool list " + DEFAULT_SERVER);
        }
        
        // Test/Health commands
        if (lower.contains("ping") || 
            (lower.contains("test") && (lower.contains("server") || lower.contains("connect")))) {
            return new CommandResult(true, "tool exec " + DEFAULT_SERVER + " ping");
        }
        
        // Database commands
        if ((lower.contains("database") || lower.contains("db")) && 
            (lower.contains("list") || lower.contains("show") || lower.contains("available"))) {
            return new CommandResult(true, "tool exec " + DEFAULT_SERVER + " listDatabases");
        }
        
        if (lower.contains("collection") && lower.contains("list")) {
            return new CommandResult(true, "tool exec " + DEFAULT_SERVER + " listCollections");
        }
        
        // Basic shell commands
        if (lower.contains("help")) {
            return new CommandResult(true, "help");
        }
        
        if (lower.contains("clear")) {
            return new CommandResult(true, "clear");
        }
        
        if (lower.contains("exit") || lower.contains("quit")) {
            return new CommandResult(true, "exit");
        }
        
        // Provide suggestions
        return new CommandResult(false, createHelpMessage(input, "Command not recognized"));
    }
    
    /**
     * Create helpful message for unrecognized commands
     */
    private String createHelpMessage(String input, String reason) {
        StringBuilder help = new StringBuilder();
        help.append("🤔 I couldn't understand: \"").append(input).append("\"");
        if (reason != null && !reason.isEmpty()) {
            help.append(" (").append(reason).append(")");
        }
        help.append("\n\n💡 Try these natural language examples:\n");
        help.append("• 'Show me all servers'\n");
        help.append("• 'Show all tools' or 'List all tools'\n");
        help.append("• 'List tools on mongo server'\n");
        help.append("• 'Ping the test server'\n");
        help.append("• 'What databases are available?'\n");
        help.append("• 'List collections'\n");
        help.append("• 'Help me with commands'\n");
        
        return help.toString();
    }
    
    /**
     * Result of command processing
     */
    public static class CommandResult {
        private final boolean isCommand;
        private final String result;
        
        public CommandResult(boolean isCommand, String result) {
            this.isCommand = isCommand;
            this.result = result;
        }
        
        public boolean isCommand() {
            return isCommand;
        }
        
        public String getResult() {
            return result;
        }
        
        public boolean isHelp() {
            return !isCommand;
        }
        
        public String getHelpText() {
            return isHelp() ? result : "";
        }
        
        public String getCommand() {
            return isCommand ? result : "";
        }
    }
}
