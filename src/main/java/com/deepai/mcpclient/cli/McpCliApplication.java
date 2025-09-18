package com.deepai.mcpclient.cli;

import com.deepai.mcpclient.cli.commands.AiCommands;
import com.deepai.mcpclient.cli.commands.ConfigCommands;
import com.deepai.mcpclient.cli.commands.EnhancedShellCommand;
import com.deepai.mcpclient.cli.commands.ServerCommands;
import com.deepai.mcpclient.cli.commands.ShellCommand;
import com.deepai.mcpclient.cli.commands.SmartToolCommands;
import com.deepai.mcpclient.cli.commands.ToolCommands;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * MCP Client CLI Application
 * 
 * User-friendly command-line interface for interacting with MCP servers.
 * Designed for both technical users and normal users who want simple database operations.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.deepai.mcpclient")
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "cli", matchIfMissing = true)
@Command(
    name = "mcpcli",
    description = "🚀 MCP Client CLI - Your friendly database companion",
    version = "1.0.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        ServerCommands.class,
        ToolCommands.class,
        SmartToolCommands.class,
        AiCommands.class,
        ConfigCommands.class,
        ShellCommand.class,
        EnhancedShellCommand.class
    }
)
public class McpCliApplication implements CommandLineRunner, Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose = false;

    @Option(names = {"-f", "--format"}, description = "Output format: table, json, yaml")
    private String format = "table";

    public static void main(String[] args) {
        // Set CLI profile for Spring Boot
        System.setProperty("spring.profiles.active", "cli");
        
        ConfigurableApplicationContext context = SpringApplication.run(McpCliApplication.class, args);
        
        // Get the command line application bean and run PicoCLI
        McpCliApplication app = context.getBean(McpCliApplication.class);
        
        // Create PicoCLI command line with Spring context
        CommandLine commandLine = new CommandLine(app, new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> cls) throws Exception {
                try {
                    return context.getBean(cls);
                } catch (Exception e) {
                    // Fallback to default constructor
                    return CommandLine.defaultFactory().create(cls);
                }
            }
        });
        
        // Execute the command
        int exitCode = commandLine.execute(args);
        
        // Close Spring context and exit
        context.close();
        System.exit(exitCode);
    }

    @Override
    public void run(String... args) throws Exception {
        // This is called by Spring Boot CommandLineRunner
        // But we handle command execution in main() using PicoCLI
        // So this method can be empty or show help if no PicoCLI args are provided
        if (args.length == 0) {
            run(); // Call PicoCLI run method
        }
    }

    @Override
    public void run() {
        // This is called by PicoCLI when no subcommand is specified
        showWelcomeMessage();
    }

    private void showWelcomeMessage() {
        System.out.println();
        System.out.println("🚀 Welcome to MCP Client CLI!");
        System.out.println();
        System.out.println("This is your friendly database companion that makes working with databases easy.");
        System.out.println("Whether you're a developer or just need to manage data, we've got you covered!");
        System.out.println();
        System.out.println("Available Commands:");
        System.out.println("  server   🖥️  Manage your database servers");
        System.out.println("  tool     🛠️  Run database operations");
        System.out.println("  ai       🤖  Ask questions in natural language");
        System.out.println("  config   ⚙️  Manage configuration settings");
        System.out.println();
        System.out.println("Quick Start:");
        System.out.println("  mcpcli server list              # See your available databases");
        System.out.println("  mcpcli ai ask \"show databases\" # Ask in plain English");
        System.out.println("  mcpcli tool quick ping          # Test database connection");
        System.out.println();
        System.out.println("For detailed help on any command, use:");
        System.out.println("  mcpcli <command> --help");
        System.out.println();
        System.out.println("Happy data managing! 🎉");
    }
}
