package com.deepai.mcpclient.cli.commands;

import com.deepai.mcpclient.cli.shell.EnhancedShell;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * Enhanced shell command - Modern terminal experience
 */
@Component
@Command(name = "enhanced-shell", 
         aliases = {"eshell", "modern-shell"},
         description = "🚀 Start enhanced interactive shell with modern UI")
public class EnhancedShellCommand extends BaseCommand implements Runnable {

    @Autowired
    private EnhancedShell enhancedShell;

    @Override
    public void run() {
        enhancedShell.start();
    }
}
