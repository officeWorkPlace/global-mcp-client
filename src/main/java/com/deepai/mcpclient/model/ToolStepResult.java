package com.deepai.mcpclient.model;

/**
 * Result of a single step in the chain execution
 */
public class ToolStepResult {

    private final ToolChainStep step;
    private final McpToolResult result;
    private final boolean success;
    private String error;

    public ToolStepResult(ToolChainStep step, McpToolResult result, boolean success) {
        this.step = step;
        this.result = result;
        this.success = success;
    }

    public ToolChainStep getStep() {
        return step;
    }

    public McpToolResult getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}