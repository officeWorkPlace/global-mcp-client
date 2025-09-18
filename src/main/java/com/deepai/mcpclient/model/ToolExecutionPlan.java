package com.deepai.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Tool execution plan created by AI agent
 */
public class ToolExecutionPlan {

    @JsonProperty("analysis")
    private String analysis;

    @JsonProperty("expected_outcome")
    private String expectedOutcome;

    @JsonProperty("steps")
    private List<ToolExecutionStep> steps;

    public ToolExecutionPlan() {}

    public ToolExecutionPlan(String analysis, String expectedOutcome, List<ToolExecutionStep> steps) {
        this.analysis = analysis;
        this.expectedOutcome = expectedOutcome;
        this.steps = steps;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public void setExpectedOutcome(String expectedOutcome) {
        this.expectedOutcome = expectedOutcome;
    }

    public List<ToolExecutionStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ToolExecutionStep> steps) {
        this.steps = steps;
    }
}