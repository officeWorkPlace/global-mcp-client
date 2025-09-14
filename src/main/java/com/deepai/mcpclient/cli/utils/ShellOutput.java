package com.deepai.mcpclient.cli.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Beautiful console output utility for the interactive shell
 * Provides clean, human-readable output without Spring Boot logging noise
 */
public class ShellOutput {
    
    // Color codes for beautiful output
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";
    
    // Emojis for better UX
    private static final String SUCCESS_ICON = "✅";
    private static final String ERROR_ICON = "❌";
    private static final String INFO_ICON = "ℹ️";
    private static final String WARNING_ICON = "⚠️";
    private static final String ROCKET_ICON = "🚀";
    private static final String TOOL_ICON = "🛠️";
    private static final String SERVER_ICON = "📦";
    private static final String SHELL_ICON = "🐚";
    private static final String BOOK_ICON = "📚";
    
    public static void welcome() {
        println("");
        println(CYAN + BOLD + SHELL_ICON + " MCP Interactive Shell" + RESET + CYAN + " - Persistent Session" + RESET);
        println(CYAN + "══════════════════════════════" + RESET);
        println("");
        println("🤖 " + BOLD + "Smart Natural Language Interface" + RESET + " " + GREEN + "(Always On)" + RESET);
        println("Type commands in plain English! Examples:");
        println(BLUE + "  • 'Show me all servers'" + RESET + "             - List servers");
        println(BLUE + "  • 'List tools on mongo server'" + RESET + "      - Browse tools");  
        println(BLUE + "  • 'Ping the test server'" + RESET + "            - Test connectivity");
        println(BLUE + "  • 'What databases are available?'" + RESET + "   - List databases");
        println("");
        println("Traditional commands also work:");
        println(YELLOW + "  • server list, tool list <server>, help, exit" + RESET);
        println("");
        println(GREEN + "💪 Shell Status: " + BOLD + "PERSISTENT" + RESET + " - Like Warp.dev terminal");
        println("");
    }
    
    public static void prompt() {
        print(GREEN + "┌─ " + RESET + MAGENTA + ROCKET_ICON + " mcpcli" + RESET + GREEN + " (persistent session)" + RESET);
        println("");
        print(GREEN + "└─> " + RESET);
    }
    
    public static void success(String message) {
        println(GREEN + SUCCESS_ICON + " " + message + RESET);
    }
    
    public static void error(String message) {
        println(RED + ERROR_ICON + " " + message + RESET);
    }
    
    public static void info(String message) {
        println(BLUE + INFO_ICON + " " + message + RESET);
    }
    
    public static void warning(String message) {
        println(YELLOW + WARNING_ICON + " " + message + RESET);
    }
    
    public static void header(String title) {
        println("");
        println(CYAN + BOLD + title + RESET);
        println(CYAN + "═".repeat(Math.min(title.length(), 50)) + RESET);
    }
    
    public static void serverItem(String id, String name, String type, boolean healthy) {
        String status = healthy ? (GREEN + "✅ Healthy" + RESET) : (RED + "❌ Unhealthy" + RESET);
        println("  " + SERVER_ICON + " " + BOLD + id + RESET + " - " + name + " (" + status + ")");
        if (type != null && !type.isEmpty()) {
            println("    Type: " + CYAN + type + RESET);
        }
    }
    
    public static void toolItem(String name, String description) {
        println("  " + TOOL_ICON + " " + BOLD + name + RESET);
        if (description != null && !description.isEmpty()) {
            println("    " + description);
        }
    }
    
    public static void toolCategory(String category, int count) {
        println("");
        println(YELLOW + BOLD + category + " (" + count + " tools)" + RESET);
        println(YELLOW + "─".repeat(Math.min(category.length() + 10, 40)) + RESET);
    }
    
    public static void executionResult(String content) {
        if (content == null || content.trim().isEmpty()) {
            println(CYAN + "  (No output)" + RESET);
            return;
        }
        
        // Format JSON nicely if it looks like JSON
        if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
            println(CYAN + "  Result:" + RESET);
            // Simple JSON formatting
            String[] lines = content.split("\n");
            for (String line : lines) {
                println("    " + line.trim());
            }
        } else {
            println(CYAN + "  " + content + RESET);
        }
    }
    
    public static void helpSection(String title, String[] commands) {
        println("");
        println(BOOK_ICON + " " + BOLD + title + RESET);
        println("─".repeat(title.length() + 4));
        for (String command : commands) {
            println("  " + command);
        }
    }
    
    public static void goodbye() {
        println("");
        println(GREEN + SUCCESS_ICON + " Thanks for using MCP CLI! Goodbye! " + RESET + "👋");
        println("");
    }
    
    public static void clearScreen() {
        // Simplified clear screen that works better on Windows
        System.out.print("\033[2J\033[H");
        System.out.flush();
    }
    
    public static void newLine() {
        println("");
    }
    
    // Private utility methods
    private static void println(String message) {
        // Use direct output to avoid Spring Boot logging noise
        System.out.println(message);
        System.out.flush();
    }
    
    private static void print(String message) {
        System.out.print(message);
        System.out.flush();
    }
    
    // Utility method to disable Spring Boot logging during shell operations
    public static void suppressSpringLogs() {
        // Set specific loggers to ERROR level to reduce noise
        ch.qos.logback.classic.Logger rootLogger = 
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(ch.qos.logback.classic.Level.WARN);
        
        // Specifically suppress MCP connection logs during interactive use
        ch.qos.logback.classic.Logger mcpLogger = 
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.deepai.mcpclient.service");
        mcpLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
    }
    
    public static void restoreSpringLogs() {
        // Restore normal logging levels
        ch.qos.logback.classic.Logger rootLogger = 
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);
        
        ch.qos.logback.classic.Logger mcpLogger = 
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.deepai.mcpclient.service");
        mcpLogger.setLevel(ch.qos.logback.classic.Level.INFO);
    }
}
