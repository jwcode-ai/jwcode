package com.jwcode.core.agent.parallel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 子 Agent 执行结果 - Phase 2 增强版
 * 
 * 包含完整的执行结果信息，支持元数据和结果处理
 */
public class SubAgentResult {
    
    // ==================== 核心属性 ====================
    
    private String taskId;
    private boolean success;
    private String output;
    private String error;
    
    // 执行时间
    private long executionTimeMs;
    private long startTime;
    private long endTime;
    
    // Agent 信息
    private String agentId;
    private String agentName;
    
    // 结构化数据
    private Map<String, Object> data = new HashMap<>();
    
    // Token 使用
    private TokenUsage tokenUsage;
    
    // 工具调用记录
    private List<ToolCallRecord> toolCalls = new ArrayList<>();
    
    // 元数据
    private Map<String, Object> metadata = new HashMap<>();
    
    // ==================== 构造函数 ====================
    
    public SubAgentResult() {}
    
    public SubAgentResult(String taskId, boolean success, String output, String error,
                         long executionTimeMs, long startTime, long endTime,
                         String agentId, String agentName, Map<String, Object> data,
                         TokenUsage tokenUsage, List<ToolCallRecord> toolCalls,
                         Map<String, Object> metadata) {
        this.taskId = taskId;
        this.success = success;
        this.output = output;
        this.error = error;
        this.executionTimeMs = executionTimeMs;
        this.startTime = startTime;
        this.endTime = endTime;
        this.agentId = agentId;
        this.agentName = agentName;
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
        this.tokenUsage = tokenUsage;
        this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : new ArrayList<>();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    // 简化构造函数（兼容旧版本）
    public SubAgentResult(String taskId, boolean success, String output, Map<String, Object> data,
                         String error, long executionTimeMs, TokenUsage tokenUsage, 
                         List<ToolCallRecord> toolCalls, String agentId) {
        this(taskId, success, output, error, executionTimeMs, 0, 0, agentId, null,
             data, tokenUsage, toolCalls, null);
    }
    
    // ==================== Getter 和 Setter ====================
    
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public boolean isFailed() { return !success; }
    
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    
    public boolean hasError() { return error != null && !error.isEmpty(); }
    
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    
    public Date getStartDate() { 
        return startTime > 0 ? new Date(startTime) : null; 
    }
    
    public Date getEndDate() { 
        return endTime > 0 ? new Date(endTime) : null; 
    }
    
    public String getFormattedStartTime() {
        return formatTimestamp(startTime);
    }
    
    public String getFormattedEndTime() {
        return formatTimestamp(endTime);
    }
    
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "N/A";
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
    
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { 
        this.data = data != null ? new HashMap<>(data) : new HashMap<>(); 
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return data != null ? (T) data.get(key) : null;
    }
    
    public void setData(String key, Object value) {
        if (data == null) data = new HashMap<>();
        data.put(key, value);
    }
    
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }
    
    public TokenUsage getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(TokenUsage tokenUsage) { this.tokenUsage = tokenUsage; }
    
    public int getTotalTokens() {
        return tokenUsage != null ? tokenUsage.getTotalTokens() : 0;
    }
    
    public List<ToolCallRecord> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallRecord> toolCalls) { 
        this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : new ArrayList<>(); 
    }
    
    public int getToolCallCount() {
        return toolCalls != null ? toolCalls.size() : 0;
    }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { 
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>(); 
    }
    
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    public void setMetadata(String key, Object value) {
        if (metadata == null) metadata = new HashMap<>();
        metadata.put(key, value);
    }
    
    // ==================== 静态工厂方法 ====================
    
    public static SubAgentResult success(String taskId, String output) {
        return builder()
            .taskId(taskId)
            .success(true)
            .output(output)
            .endTime(System.currentTimeMillis())
            .build();
    }
    
    public static SubAgentResult success(String output) {
        return success("unknown", output);
    }
    
    public static SubAgentResult failure(String taskId, String error) {
        return builder()
            .taskId(taskId)
            .success(false)
            .error(error)
            .endTime(System.currentTimeMillis())
            .build();
    }
    
    public static SubAgentResult failure(String error) {
        return failure("unknown", error);
    }
    
    public static SubAgentResult timeout(String taskId, long timeoutMs) {
        return builder()
            .taskId(taskId)
            .success(false)
            .error("Task timeout after " + timeoutMs + "ms")
            .metadata(Map.of("timeout", timeoutMs, "reason", "TIMEOUT"))
            .endTime(System.currentTimeMillis())
            .build();
    }
    
    public static SubAgentResult cancelled(String taskId, String reason) {
        return builder()
            .taskId(taskId)
            .success(false)
            .error("Task cancelled: " + reason)
            .metadata(Map.of("reason", reason, "cancelled", true))
            .endTime(System.currentTimeMillis())
            .build();
    }
    
    /**
     * 合并多个结果
     */
    public static SubAgentResult merge(List<SubAgentResult> results) {
        if (results == null || results.isEmpty()) {
            return success("merged", "No results to merge");
        }
        
        StringBuilder combinedOutput = new StringBuilder();
        boolean allSuccess = true;
        int totalTokens = 0;
        List<ToolCallRecord> allToolCalls = new ArrayList<>();
        long totalExecutionTime = 0;
        
        for (SubAgentResult result : results) {
            if (!result.isSuccess()) {
                allSuccess = false;
            }
            if (result.getOutput() != null) {
                combinedOutput.append("## ").append(result.getTaskId()).append("\n\n");
                combinedOutput.append(result.getOutput()).append("\n\n");
            }
            totalTokens += result.getTotalTokens();
            totalExecutionTime += result.getExecutionTimeMs();
            if (result.getToolCalls() != null) {
                allToolCalls.addAll(result.getToolCalls());
            }
        }
        
        TokenUsage mergedTokenUsage = totalTokens > 0 ? 
            new TokenUsage(0, 0, totalTokens) : null;
        
        return builder()
            .taskId("merged_" + System.currentTimeMillis())
            .success(allSuccess)
            .output(combinedOutput.toString())
            .executionTimeMs(totalExecutionTime)
            .tokenUsage(mergedTokenUsage)
            .toolCalls(allToolCalls)
            .metadata(Map.of(
                "merged_count", results.size(),
                "source_task_ids", results.stream().map(SubAgentResult::getTaskId).collect(Collectors.toList())
            ))
            .build();
    }
    
    /**
     * 合并多个结果（带分隔符）
     */
    public static SubAgentResult merge(List<SubAgentResult> results, String separator) {
        if (results == null || results.isEmpty()) {
            return success("merged", "");
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SubAgentResult result = results.get(i);
            if (result.getOutput() != null) {
                sb.append(result.getOutput());
                if (i < results.size() - 1) {
                    sb.append(separator);
                }
            }
        }
        
        boolean allSuccess = results.stream().allMatch(SubAgentResult::isSuccess);
        
        return builder()
            .taskId("merged")
            .success(allSuccess)
            .output(sb.toString())
            .build();
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String taskId;
        private boolean success;
        private String output;
        private String error;
        private long executionTimeMs;
        private long startTime;
        private long endTime;
        private String agentId;
        private String agentName;
        private Map<String, Object> data = new HashMap<>();
        private TokenUsage tokenUsage;
        private List<ToolCallRecord> toolCalls = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();
        
        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder output(String output) { this.output = output; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder executionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
        public Builder startTime(long startTime) { this.startTime = startTime; return this; }
        public Builder endTime(long endTime) { this.endTime = endTime; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder agentName(String agentName) { this.agentName = agentName; return this; }
        public Builder data(Map<String, Object> data) { 
            this.data = data != null ? new HashMap<>(data) : new HashMap<>(); 
            return this; 
        }
        public Builder tokenUsage(TokenUsage tokenUsage) { this.tokenUsage = tokenUsage; return this; }
        public Builder toolCalls(List<ToolCallRecord> toolCalls) { 
            this.toolCalls = toolCalls != null ? new ArrayList<>(toolCalls) : new ArrayList<>(); 
            return this; 
        }
        public Builder metadata(Map<String, Object> metadata) { 
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>(); 
            return this; 
        }
        
        public SubAgentResult build() {
            return new SubAgentResult(taskId, success, output, error, executionTimeMs,
                startTime, endTime, agentId, agentName, data, tokenUsage, toolCalls, metadata);
        }
    }
    
    // ==================== 内部类 ====================
    
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
        
        public int getTotalTokens() { 
            return totalTokens > 0 ? totalTokens : promptTokens + completionTokens; 
        }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        
        public static TokenUsage add(TokenUsage a, TokenUsage b) {
            if (a == null) return b;
            if (b == null) return a;
            return new TokenUsage(
                a.promptTokens + b.promptTokens,
                a.completionTokens + b.completionTokens,
                a.totalTokens + b.totalTokens
            );
        }
        
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
    }
    
    public static class ToolCallRecord {
        private String toolName;
        private String input;
        private String output;
        private long timestamp;
        private long executionTimeMs;
        private boolean success;
        private String error;
        
        public ToolCallRecord() {}
        
        public ToolCallRecord(String toolName, String input, String output, 
                             long timestamp, long executionTimeMs) {
            this(toolName, input, output, timestamp, executionTimeMs, true, null);
        }
        
        public ToolCallRecord(String toolName, String input, String output, 
                             long timestamp, long executionTimeMs, 
                             boolean success, String error) {
            this.toolName = toolName;
            this.input = input;
            this.output = output;
            this.timestamp = timestamp;
            this.executionTimeMs = executionTimeMs;
            this.success = success;
            this.error = error;
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
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String toolName;
            private String input;
            private String output;
            private long timestamp = System.currentTimeMillis();
            private long executionTimeMs;
            private boolean success = true;
            private String error;
            
            public Builder toolName(String toolName) { this.toolName = toolName; return this; }
            public Builder input(String input) { this.input = input; return this; }
            public Builder output(String output) { this.output = output; return this; }
            public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
            public Builder executionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder error(String error) { this.error = error; return this; }
            
            public ToolCallRecord build() {
                return new ToolCallRecord(toolName, input, output, timestamp, executionTimeMs, success, error);
            }
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取结果的简要摘要
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Task ").append(taskId).append(": ");
        sb.append(success ? "SUCCESS" : "FAILED");
        sb.append(" (").append(executionTimeMs).append("ms)");
        if (output != null) {
            String preview = output.length() > 100 ? output.substring(0, 100) + "..." : output;
            sb.append(" - ").append(preview.replaceAll("\\s+", " "));
        }
        return sb.toString();
    }
    
    /**
     * 格式化结果为字符串
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Task Result: ").append(taskId).append(" ===\n");
        sb.append("Status: ").append(success ? "SUCCESS" : "FAILED").append("\n");
        sb.append("Time: ").append(getFormattedStartTime()).append(" - ").append(getFormattedEndTime()).append("\n");
        sb.append("Duration: ").append(executionTimeMs).append("ms\n");
        if (agentId != null) sb.append("Agent: ").append(agentId).append("\n");
        if (tokenUsage != null) {
            sb.append("Tokens: ").append(tokenUsage.getTotalTokens()).append("\n");
        }
        if (output != null) sb.append("\nOutput:\n").append(output).append("\n");
        if (error != null) sb.append("\nError:\n").append(error).append("\n");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format(
            "SubAgentResult{taskId='%s', success=%s, executionTimeMs=%d, agentId='%s'}",
            taskId, success, executionTimeMs, agentId
        );
    }
}
