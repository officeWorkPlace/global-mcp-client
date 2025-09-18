package com.deepai.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Individual step in a tool execution plan
 */
public class ToolExecutionStep {

    @JsonProperty("step")
    private int stepNumber;

    @JsonProperty("action")
    private String action;

    @JsonProperty("server")
    private String serverId;

    @JsonProperty("parameters")
    private Map<String, Object> parameters;

    @JsonProperty("reasoning")
    private String reasoning;

    private boolean critical = false;

    public ToolExecutionStep() {}

    public ToolExecutionStep(int stepNumber, String action, String serverId,
                           Map<String, Object> parameters, String reasoning) {
        this.stepNumber = stepNumber;
        this.action = action;
        this.serverId = serverId;
        this.parameters = parameters;
        this.reasoning = reasoning;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public String getId() {
        return "step_" + stepNumber;
    }
}