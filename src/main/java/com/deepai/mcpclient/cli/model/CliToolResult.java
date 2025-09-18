package com.deepai.mcpclient.cli.model;

import com.deepai.mcpclient.model.ChatResponse;

import java.util.List;

/**
 * Result wrapper for CLI tool executions with AI intelligence
 */
public class CliToolResult {
    private final boolean success;
    private final String response;
    private final List<ChatResponse.ToolExecution> toolExecutions;
    private final String model;

    public CliToolResult(boolean success, String response,
                        List<ChatResponse.ToolExecution> toolExecutions, String model) {
        this.success = success;
        this.response = response;
        this.toolExecutions = toolExecutions;
        this.model = model;
    }

    public boolean isSuccess() { return success; }
    public String getResponse() { return response; }
    public List<ChatResponse.ToolExecution> getToolExecutions() { return toolExecutions; }
    public String getModel() { return model; }

    public boolean hasToolExecutions() {
        return toolExecutions != null && !toolExecutions.isEmpty();
    }

    public int getExecutionCount() {
        return toolExecutions != null ? toolExecutions.size() : 0;
    }

    @Override
    public String toString() {
        return String.format("CliToolResult{success=%s, executions=%d, model='%s'}",
                           success, getExecutionCount(), model);
    }
}