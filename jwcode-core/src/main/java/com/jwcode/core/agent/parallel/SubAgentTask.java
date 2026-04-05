package com.jwcode.core.agent.parallel;

import com.jwcode.core.agent.Agent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * 子 Agent 任务定义
 * 
 * 表示一个可由子 Agent 并行执行的任务单元
 */
public class SubAgentTask {
    
    private String taskId = UUID.randomUUID().toString();
    
    /**
     * 任务描述/指令
     */
    private String instruction;
    
    /**
     * 指定使用的 Agent 类型
     */
    private String agentType;
    
    /**
     * 任务优先级 (1-10, 10 最高)
     */
    private int priority = 5;
    
    /**
     * 任务超时时间（毫秒）
     */
    private long timeoutMs = 60000;
    
    /**
     * 任务上下文数据
     */
    private Map<String, Object> context = Map.of();
    
    /**
     * 依赖的其他任务ID（必须等依赖任务完成后才能执行）
     */
    private List<String> dependencies = List.of();
    
    /**
     * 任务状态
     */
    private volatile TaskStatus status = TaskStatus.PENDING;
    
    /**
     * 执行结果（异步）
     */
    private CompletableFuture<SubAgentResult> future;
    
    /**
     * 任务创建时间
     */
    private long createdAt = System.currentTimeMillis();
    
    /**
     * 实际开始执行时间
     */
    private volatile long startedAt;
    
    /**
     * 完成时间
     */
    private volatile long completedAt;
    
    /**
     * 执行的 Agent 实例（执行时填充）
     */
    private Agent executingAgent;
    
    public SubAgentTask() {}
    
    public SubAgentTask(String taskId, String instruction, String agentType, int priority,
                       long timeoutMs, Map<String, Object> context, List<String> dependencies,
                       TaskStatus status, CompletableFuture<SubAgentResult> future, long createdAt,
                       long startedAt, long completedAt, Agent executingAgent) {
        this.taskId = taskId;
        this.instruction = instruction;
        this.agentType = agentType;
        this.priority = priority;
        this.timeoutMs = timeoutMs;
        this.context = context;
        this.dependencies = dependencies;
        this.status = status;
        this.future = future;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.executingAgent = executingAgent;
    }
    
    // Getters
    public String getTaskId() { return taskId; }
    public String getInstruction() { return instruction; }
    public String getAgentType() { return agentType; }
    public int getPriority() { return priority; }
    public long getTimeoutMs() { return timeoutMs; }
    public Map<String, Object> getContext() { return context; }
    public List<String> getDependencies() { return dependencies; }
    public TaskStatus getStatus() { return status; }
    public CompletableFuture<SubAgentResult> getFuture() { return future; }
    public long getCreatedAt() { return createdAt; }
    public long getStartedAt() { return startedAt; }
    public long getCompletedAt() { return completedAt; }
    public Agent getExecutingAgent() { return executingAgent; }
    
    // Setters
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setFuture(CompletableFuture<SubAgentResult> future) { this.future = future; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    public void setExecutingAgent(Agent executingAgent) { this.executingAgent = executingAgent; }
    
    public enum TaskStatus {
        PENDING,        // 等待中
        WAITING_DEPS,   // 等待依赖
        RUNNING,        // 执行中
        COMPLETED,      // 已完成
        FAILED,         // 失败
        CANCELLED,      // 已取消
        TIMEOUT         // 超时
    }
    
    /**
     * 标记任务开始执行
     */
    public void markStarted() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = System.currentTimeMillis();
    }
    
    /**
     * 标记任务完成
     */
    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = System.currentTimeMillis();
    }
    
    /**
     * 标记任务失败
     */
    public void markFailed() {
        this.status = TaskStatus.FAILED;
        this.completedAt = System.currentTimeMillis();
    }
    
    /**
     * 获取执行耗时（毫秒）
     */
    public long getExecutionTimeMs() {
        if (startedAt == 0) return 0;
        long endTime = completedAt > 0 ? completedAt : System.currentTimeMillis();
        return endTime - startedAt;
    }
    
    /**
     * 是否已结束（成功、失败、取消、超时）
     */
    public boolean isFinished() {
        return status == TaskStatus.COMPLETED 
            || status == TaskStatus.FAILED 
            || status == TaskStatus.CANCELLED
            || status == TaskStatus.TIMEOUT;
    }
    
    // Builder
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String taskId = UUID.randomUUID().toString();
        private String instruction;
        private String agentType;
        private int priority = 5;
        private long timeoutMs = 60000;
        private Map<String, Object> context = Map.of();
        private List<String> dependencies = List.of();
        private TaskStatus status = TaskStatus.PENDING;
        private CompletableFuture<SubAgentResult> future;
        private long createdAt = System.currentTimeMillis();
        private long startedAt;
        private long completedAt;
        private Agent executingAgent;
        
        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder instruction(String instruction) { this.instruction = instruction; return this; }
        public Builder agentType(String agentType) { this.agentType = agentType; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder timeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; return this; }
        public Builder context(Map<String, Object> context) { this.context = context; return this; }
        public Builder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public Builder status(TaskStatus status) { this.status = status; return this; }
        public Builder future(CompletableFuture<SubAgentResult> future) { this.future = future; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
        public Builder startedAt(long startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(long completedAt) { this.completedAt = completedAt; return this; }
        public Builder executingAgent(Agent executingAgent) { this.executingAgent = executingAgent; return this; }
        
        public SubAgentTask build() {
            return new SubAgentTask(taskId, instruction, agentType, priority, timeoutMs,
                context, dependencies, status, future, createdAt, startedAt, completedAt, executingAgent);
        }
    }
}
