package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.cli.utils.ColorOutput;
import com.deepai.mcpclient.cli.utils.TableFormatter;
import org.springframework.stereotype.Component;

/**
 * Base command class with common functionality for all CLI commands
 */
@Component
public abstract class BaseCommand {
    
    protected final ColorOutput colorOutput;
    protected final TableFormatter tableFormatter;
    
    protected boolean verbose = false;
    protected String format = "table";
    
    public BaseCommand() {
        this.colorOutput = new ColorOutput();
        this.tableFormatter = new TableFormatter();
    }
    
    /**
     * Set global options from parent command
     */
    public void setGlobalOptions(boolean verbose, String format) {
        this.verbose = verbose;
        this.format = format;
    }
    
    /**
     * Print success message with green color
     */
    protected void printSuccess(String message) {
        System.out.println(colorOutput.green("✅ " + message));
    }
    
    /**
     * Print error message with red color
     */
    protected void printError(String message) {
        System.err.println(colorOutput.red("❌ " + message));
    }
    
    /**
     * Print warning message with yellow color
     */
    protected void printWarning(String message) {
        System.out.println(colorOutput.yellow("⚠️  " + message));
    }
    
    /**
     * Print info message with blue color
     */
    protected void printInfo(String message) {
        System.out.println(colorOutput.blue("ℹ️  " + message));
    }
    
    /**
     * Print verbose message (only if verbose mode is enabled)
     */
    protected void printVerbose(String message) {
        if (verbose) {
            System.out.println(colorOutput.gray("🔍 " + message));
        }
    }
    
    /**
     * Print a nice header
     */
    protected void printHeader(String title) {
        System.out.println();
        System.out.println(colorOutput.cyan("═══ " + title + " ═══"));
        System.out.println();
    }
    
    /**
     * Print divider line
     */
    protected void printDivider() {
        System.out.println(colorOutput.gray("─────────────────────────────────────"));
    }
}
