package com.jwcode.core.coordinator;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具测试配置
 */
public class ToolTestConfig {

    private String testSuiteName = "JwCode Tool Test";
    private boolean environmentCheckEnabled = true;
    private boolean strictMode = false;
    private boolean includeToolsRequiringExternalDeps = false;
    private boolean criticalErrorStopsAll = true;
    private int maxConsecutiveFailures = 3;
    private int timeoutSeconds = 30;
    private List<String> toolNames;
    private Map<String, JsonNode> testInputs = new HashMap<>();

    public ToolTestConfig() {
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getTestSuiteName() {
        return testSuiteName;
    }

    public void setTestSuiteName(String testSuiteName) {
        this.testSuiteName = testSuiteName;
    }

    public boolean isEnvironmentCheckEnabled() {
        return environmentCheckEnabled;
    }

    public void setEnvironmentCheckEnabled(boolean environmentCheckEnabled) {
        this.environmentCheckEnabled = environmentCheckEnabled;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public boolean isIncludeToolsRequiringExternalDeps() {
        return includeToolsRequiringExternalDeps;
    }

    public void setIncludeToolsRequiringExternalDeps(boolean includeToolsRequiringExternalDeps) {
        this.includeToolsRequiringExternalDeps = includeToolsRequiringExternalDeps;
    }

    public boolean isCriticalErrorStopsAll() {
        return criticalErrorStopsAll;
    }

    public void setCriticalErrorStopsAll(boolean criticalErrorStopsAll) {
        this.criticalErrorStopsAll = criticalErrorStopsAll;
    }

    public int getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    public void setMaxConsecutiveFailures(int maxConsecutiveFailures) {
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<String> getToolNames() {
        return toolNames;
    }

    public void setToolNames(List<String> toolNames) {
        this.toolNames = toolNames;
    }

    public Map<String, JsonNode> getTestInputs() {
        return testInputs;
    }

    public void setTestInputs(Map<String, JsonNode> testInputs) {
        this.testInputs = testInputs;
    }

    public JsonNode getTestInput(String toolName) {
        return testInputs.get(toolName);
    }

    public void addTestInput(String toolName, JsonNode input) {
        this.testInputs.put(toolName, input);
    }

    /**
     * 配置构建器
     */
    public static class Builder {
        private final ToolTestConfig config = new ToolTestConfig();

        public Builder testSuiteName(String name) {
            config.setTestSuiteName(name);
            return this;
        }

        public Builder environmentCheckEnabled(boolean enabled) {
            config.setEnvironmentCheckEnabled(enabled);
            return this;
        }

        public Builder strictMode(boolean strict) {
            config.setStrictMode(strict);
            return this;
        }

        public Builder includeToolsRequiringExternalDeps(boolean include) {
            config.setIncludeToolsRequiringExternalDeps(include);
            return this;
        }

        public Builder criticalErrorStopsAll(boolean stops) {
            config.setCriticalErrorStopsAll(stops);
            return this;
        }

        public Builder maxConsecutiveFailures(int max) {
            config.setMaxConsecutiveFailures(max);
            return this;
        }

        public Builder timeoutSeconds(int seconds) {
            config.setTimeoutSeconds(seconds);
            return this;
        }

        public Builder toolNames(List<String> names) {
            config.setToolNames(names);
            return this;
        }

        public Builder addTestInput(String toolName, JsonNode input) {
            config.addTestInput(toolName, input);
            return this;
        }

        public ToolTestConfig build() {
            return config;
        }
    }
}
