package com.deepai.mcpclient.cli.utils;

import org.springframework.stereotype.Component;

/**
 * Utility class for colored console output
 * Makes the CLI user-friendly with visual feedback
 */
@Component
public class ColorOutput {
    
    // ANSI color codes
    private static final String RESET = "\033[0m";
    private static final String RED = "\033[31m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String BLUE = "\033[34m";
    private static final String CYAN = "\033[36m";
    private static final String GRAY = "\033[37m";
    private static final String BOLD = "\033[1m";
    
    private final boolean colorsEnabled;
    
    public ColorOutput() {
        // Check if colors are supported (disable on Windows by default unless explicitly enabled)
        this.colorsEnabled = !System.getProperty("os.name", "").toLowerCase().contains("windows") 
            || "true".equals(System.getProperty("mcpcli.colors.enabled"));
    }
    
    public String red(String text) {
        return colorsEnabled ? RED + text + RESET : text;
    }
    
    public String green(String text) {
        return colorsEnabled ? GREEN + text + RESET : text;
    }
    
    public String yellow(String text) {
        return colorsEnabled ? YELLOW + text + RESET : text;
    }
    
    public String blue(String text) {
        return colorsEnabled ? BLUE + text + RESET : text;
    }
    
    public String cyan(String text) {
        return colorsEnabled ? CYAN + text + RESET : text;
    }
    
    public String gray(String text) {
        return colorsEnabled ? GRAY + text + RESET : text;
    }
    
    public String bold(String text) {
        return colorsEnabled ? BOLD + text + RESET : text;
    }
    
    /**
     * Format status with appropriate color
     */
    public String status(String status) {
        switch (status.toLowerCase()) {
            case "healthy":
            case "running":
            case "active":
            case "success":
                return green(status);
            case "unhealthy":
            case "stopped":
            case "error":
            case "failed":
                return red(status);
            case "warning":
            case "degraded":
                return yellow(status);
            default:
                return blue(status);
        }
    }
    
    /**
     * Create a progress bar
     */
    public String progressBar(int current, int total, int width) {
        if (total == 0) return "";
        
        int filled = (current * width) / total;
        int empty = width - filled;
        
        StringBuilder bar = new StringBuilder();
        bar.append("[");
        bar.append(green("█".repeat(filled)));
        bar.append("░".repeat(empty));
        bar.append("] ");
        bar.append(String.format("%d/%d", current, total));
        
        return bar.toString();
    }
}
