package com.jwcode.core.agent.parallel;

import java.util.Map;
import java.util.List;

/**
 * 子 Agent 执行结果
 */
public class SubAgentResult {
    
    /**
     * 关联的任务ID
     */
    private String taskId;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 结果数据
     */
    private String output;
    
    /**
     * 结构化结果数据
     */
    private Map<String, Object> data;
    
    /**
     * 错误信息（失败时）
     */
    private String error;
    
    /**
     * 执行耗时（毫秒）
     */
    private long executionTimeMs;
    
    /**
     * 使用的 token 数
     */
    private TokenUsage tokenUsage;
    
    /**
     * 执行过程中的工具调用记录
     */
    private List<ToolCallRecord> toolCalls = List.of();
    
    /**
     * 执行的 Agent ID
     */
    private String agentId;
    
    public SubAgentResult() {}
    
    public SubAgentResult(String taskId, boolean success, String output, Map<String, Object> data,
                         String error, long executionTimeMs, TokenUsage tokenUsage, 
                         List<ToolCallRecord> toolCalls, String agentId) {
        this.taskId = taskId;
        this.success = success;
        this.output = output;
        this.data = data;
        this.error = error;
        this.executionTimeMs = executionTimeMs;
        this.tokenUsage = tokenUsage;
        this.toolCalls = toolCalls != null ? toolCalls : List.of();
        this.agentId = agentId;
    }
    
    // Getters and Setters
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    
    public TokenUsage getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(TokenUsage tokenUsage) { this.tokenUsage = tokenUsage; }
    
    public List<ToolCallRecord> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls != null ? toolCalls : List.of(); }
    
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    
    /**
     * 创建成功结果
     */
    public static SubAgentResult success(String taskId, String output) {
        return new SubAgentResult(taskId, true, output, null, null, 0, null, List.of(), null);
    }
    
    /**
     * 创建成功结果（简化版）
     */
    public static SubAgentResult success(String output) {
        return new SubAgentResult("unknown", true, output, null, null, 0, null, List.of(), null);
    }
    
    /**
     * 创建失败结果
     */
    public static SubAgentResult failure(String taskId, String error) {
        return new SubAgentResult(taskId, false, null, null, error, 0, null, List.of(), null);
    }
    
    /**
     * 创建失败结果（简化版）
     */
    public static SubAgentResult failure(String error) {
        return new SubAgentResult("unknown", false, null, null, error, 0, null, List.of(), null);
    }
    
    /**
     * 合并多个结果
     */
    public static SubAgentResult merge(List<SubAgentResult> results) {
        StringBuilder combinedOutput = new StringBuilder();
        boolean allSuccess = true;
        
        for (SubAgentResult result : results) {
            if (!result.isSuccess()) {
                allSuccess = false;
            }
            if (result.getOutput() != null) {
                combinedOutput.append("## ").append(result.getTaskId()).append("\n\n");
                combinedOutput.append(result.getOutput()).append("\n\n");
            }
        }
        
        return new SubAgentResult("merged", allSuccess, combinedOutput.toString(), 
            null, null, 0, null, List.of(), null);
    }
    
    // Builder
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String taskId;
        private boolean success;
        private String output;
        private Map<String, Object> data;
        private String error;
        private long executionTimeMs;
        private TokenUsage tokenUsage;
        private List<ToolCallRecord> toolCalls = List.of();
        private String agentId;
        
        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder output(String output) { this.output = output; return this; }
        public Builder data(Map<String, Object> data) { this.data = data; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder executionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
        public Builder tokenUsage(TokenUsage tokenUsage) { this.tokenUsage = tokenUsage; return this; }
        public Builder toolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        
        public SubAgentResult build() {
            return new SubAgentResult(taskId, success, output, data, error, executionTimeMs,
                tokenUsage, toolCalls, agentId);
        }
    }
    
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        
        public TokenUsage() {}
        
        public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }
        
        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
        
        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
        
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private int promptTokens;
            private int completionTokens;
            private int totalTokens;
            
            public Builder promptTokens(int promptTokens) { this.promptTokens = promptTokens; return this; }
            public Builder completionTokens(int completionTokens) { this.completionTokens = completionTokens; return this; }
            public Builder totalTokens(int totalTokens) { this.totalTokens = totalTokens; return this; }
            
            public TokenUsage build() {
                return new TokenUsage(promptTokens, completionTokens, totalTokens);
            }
        }
        
        public static TokenUsage add(TokenUsage a, TokenUsage b) {
            if (a == null) return b;
            if (b == null) return a;
            return new TokenUsage(
                a.promptTokens + b.promptTokens,
                a.completionTokens + b.completionTokens,
                a.totalTokens + b.totalTokens
            );
        }
    }
    
    public static class ToolCallRecord {
        private String toolName;
        private String input;
        private String output;
        private long timestamp;
        private long executionTimeMs;
        
        public ToolCallRecord() {}
        
        public ToolCallRecord(String toolName, String input, String output, long timestamp, long executionTimeMs) {
            this.toolName = toolName;
            this.input = input;
            this.output = output;
            this.timestamp = timestamp;
            this.executionTimeMs = executionTimeMs;
        }
        
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String toolName;
            private String input;
            private String output;
            private long timestamp;
            private long executionTimeMs;
            
            public Builder toolName(String toolName) { this.toolName = toolName; return this; }
            public Builder input(String input) { this.input = input; return this; }
            public Builder output(String output) { this.output = output; return this; }
            public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
            public Builder executionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
            
            public ToolCallRecord build() {
                return new ToolCallRecord(toolName, input, output, timestamp, executionTimeMs);
            }
        }
    }
}
