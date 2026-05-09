package com.jwcode.core.planner;

import com.jwcode.core.a2a.model.StepStatus;
import com.jwcode.core.agent.Agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划步骤
 */
public class PlanStep {
    
    private int stepNumber;
    private String action;
    private String description;
    private String agentType;
    private Agent assignedAgent;
    private String expectedOutput;
    private int priority;
    private boolean dependsOnPrevious;
    private List<String> dependencies;
    private Map<String, Object> context;
    private StepStatus status;
    private long estimatedTimeMs;
    private String actualOutput;
    private String errorMessage;
    
    public PlanStep() {
        this.dependencies = new ArrayList<>();
        this.context = new HashMap<>();
    }
    
    // Getters
    public int getStepNumber() { return stepNumber; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
    public String getAgentType() { return agentType; }
    public Agent getAssignedAgent() { return assignedAgent; }
    public String getExpectedOutput() { return expectedOutput; }
    public int getPriority() { return priority; }
    public boolean isDependsOnPrevious() { return dependsOnPrevious; }
    public List<String> getDependencies() { 
        if (dependencies == null) dependencies = new ArrayList<>();
        return dependencies; 
    }
    public Map<String, Object> getContext() { 
        if (context == null) context = new HashMap<>();
        return context; 
    }
    public StepStatus getStatus() { return status; }
    public long getEstimatedTimeMs() { return estimatedTimeMs; }
    public String getActualOutput() { return actualOutput; }
    public String getErrorMessage() { return errorMessage; }
    
    // Setters
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public void setAction(String action) { this.action = action; }
    public void setDescription(String description) { this.description = description; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public void setAssignedAgent(Agent assignedAgent) { this.assignedAgent = assignedAgent; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setDependsOnPrevious(boolean dependsOnPrevious) { this.dependsOnPrevious = dependsOnPrevious; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public void setStatus(StepStatus status) { this.status = status; }
    public void setEstimatedTimeMs(long estimatedTimeMs) { this.estimatedTimeMs = estimatedTimeMs; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    /**
     * 添加依赖
     */
    public void addDependency(String stepNumber) {
        if (dependencies == null) {
            dependencies = new ArrayList<>();
        }
        if (!dependencies.contains(stepNumber)) {
            dependencies.add(stepNumber);
        }
    }
    
    /**
     * 检查是否依赖于指定步骤
     */
    public boolean dependsOn(int stepNumber) {
        if (dependencies == null) {
            return false;
        }
        return dependencies.contains(String.valueOf(stepNumber));
    }
    
    // Builder
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int stepNumber;
        private String action;
        private String description;
        private String agentType;
        private Agent assignedAgent;
        private String expectedOutput;
        private int priority;
        private boolean dependsOnPrevious;
        private List<String> dependencies = new ArrayList<>();
        private Map<String, Object> context = new HashMap<>();
        private StepStatus status;
        private long estimatedTimeMs;
        private String actualOutput;
        private String errorMessage;
        
        public Builder stepNumber(int stepNumber) { this.stepNumber = stepNumber; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder agentType(String agentType) { this.agentType = agentType; return this; }
        public Builder assignedAgent(Agent assignedAgent) { this.assignedAgent = assignedAgent; return this; }
        public Builder expectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder dependsOnPrevious(boolean dependsOnPrevious) { this.dependsOnPrevious = dependsOnPrevious; return this; }
        public Builder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public Builder context(Map<String, Object> context) { this.context = context; return this; }
        public Builder status(StepStatus status) { this.status = status; return this; }
        public Builder estimatedTimeMs(long estimatedTimeMs) { this.estimatedTimeMs = estimatedTimeMs; return this; }
        public Builder actualOutput(String actualOutput) { this.actualOutput = actualOutput; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        
        public PlanStep build() {
            PlanStep step = new PlanStep();
            step.stepNumber = this.stepNumber;
            step.action = this.action;
            step.description = this.description;
            step.agentType = this.agentType;
            step.assignedAgent = this.assignedAgent;
            step.expectedOutput = this.expectedOutput;
            step.priority = this.priority;
            step.dependsOnPrevious = this.dependsOnPrevious;
            step.dependencies = this.dependencies;
            step.context = this.context;
            step.status = this.status;
            step.estimatedTimeMs = this.estimatedTimeMs;
            step.actualOutput = this.actualOutput;
            step.errorMessage = this.errorMessage;
            return step;
        }
    }
}
