package com.deepai.mcpclient.model;

import java.util.List;
import java.util.Map;

/**
 * Individual step in a tool execution chain
 */
public class ToolChainStep {

    private int stepNumber;
    private String action;
    private String serverId;
    private Map<String, Object> parameters;
    private String reasoning;
    private boolean critical = true;
    private List<String> dependsOn;

    public ToolChainStep() {}

    public ToolChainStep(int stepNumber, String action, String serverId,
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

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public String getId() {
        return "step_" + stepNumber;
    }

    @Override
    public String toString() {
        return String.format("ToolChainStep{step=%d, action='%s', server='%s', critical=%s}",
            stepNumber, action, serverId, critical);
    }
}