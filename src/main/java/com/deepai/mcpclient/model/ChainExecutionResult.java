package com.deepai.mcpclient.model;

import java.util.List;
import java.util.Map;

/**
 * Result of executing a complete tool chain
 */
public class ChainExecutionResult {

    private final List<ToolChainStep> originalSteps;
    private final Map<String, ToolStepResult> completedSteps;

    public ChainExecutionResult(List<ToolChainStep> originalSteps, Map<String, ToolStepResult> completedSteps) {
        this.originalSteps = originalSteps;
        this.completedSteps = completedSteps;
    }

    public List<ToolChainStep> getOriginalSteps() {
        return originalSteps;
    }

    public Map<String, ToolStepResult> getCompletedSteps() {
        return completedSteps;
    }

    public boolean isSuccessful() {
        return completedSteps.values().stream().allMatch(ToolStepResult::isSuccess);
    }

    public int getSuccessCount() {
        return (int) completedSteps.values().stream().filter(ToolStepResult::isSuccess).count();
    }

    public int getFailureCount() {
        return (int) completedSteps.values().stream().filter(result -> !result.isSuccess()).count();
    }

    public List<ToolStepResult> getFailedSteps() {
        return completedSteps.values().stream()
            .filter(result -> !result.isSuccess())
            .toList();
    }
}