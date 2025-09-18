package com.deepai.mcpclient.model;

/**
 * Result of a tool execution step including both the step info and result
 */
public class ToolResult {

    private final ToolExecutionStep step;
    private final McpToolResult mcpResult;
    private final Exception error;
    private final boolean success;

    public ToolResult(ToolExecutionStep step, McpToolResult mcpResult) {
        this.step = step;
        this.mcpResult = mcpResult;
        this.error = null;
        this.success = mcpResult != null && (mcpResult.isError() == null || !mcpResult.isError());
    }

    public ToolResult(ToolExecutionStep step, Exception error) {
        this.step = step;
        this.mcpResult = null;
        this.error = error;
        this.success = false;
    }

    public ToolExecutionStep getStep() {
        return step;
    }

    public McpToolResult getMcpResult() {
        return mcpResult;
    }

    public Exception getError() {
        return error;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        if (error != null) {
            return error.getMessage();
        }
        if (mcpResult != null && mcpResult.isError() != null && mcpResult.isError()) {
            return "Tool execution failed";
        }
        return "Unknown error";
    }

    public String getFormattedOutput() {
        if (mcpResult != null && mcpResult.content() != null && !mcpResult.content().isEmpty()) {
            return formatMcpContent(mcpResult.content());
        }
        return "No output";
    }

    private String formatMcpContent(java.util.List<McpContent> content) {
        if (content == null || content.isEmpty()) {
            return "No content";
        }

        StringBuilder formatted = new StringBuilder();
        for (McpContent item : content) {
            if (item.text() != null) {
                formatted.append(item.text()).append("\n");
            }
        }

        return formatted.toString().trim();
    }
}