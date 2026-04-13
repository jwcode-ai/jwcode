package com.jwcode.core.agent.parallel;

import com.jwcode.core.agent.Agent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * 子 Agent 任务定义 - Phase 2 增强版
 * 
 * 表示一个可由子 Agent 并行执行的任务单元
 * 实现了 Callable 和 Runnable 接口，支持多种执行方式
 */
public class SubAgentTask implements Callable<SubAgentResult>, Runnable {
    
    // ==================== 核心属性 ====================
    
    private String taskId = UUID.randomUUID().toString();
    private String name;
    private String role;
    private String taskDescription;
    private String instruction;
    private String agentType;
    
    // 执行配置
    private int priority = 5;
    private long timeout = 60000; // 毫秒
    private long timeoutMs = 60000; // 兼容旧版本
    
    // 上下文和依赖
    private Map<String, Object> context = new HashMap<>();
    private List<String> dependencies = new ArrayList<>();
    private List<String> dependsOn = new ArrayList<>(); // 别名，兼容旧版本
    
    // 状态管理
    private volatile TaskStatus status = TaskStatus.PENDING;
    private CompletableFuture<SubAgentResult> future;
    
    // 时间戳
    private long createdAt = System.currentTimeMillis();
    private volatile long startTime;
    private volatile long startedAt; // 兼容旧版本
    private volatile long endTime;
    private volatile long completedAt; // 兼容旧版本
    
    // 执行相关
    private Agent executingAgent;
    private SubAgentResult result;
    private Throwable error;
    
    // 元数据
    private Map<String, Object> metadata = new HashMap<>();
    
    // ==================== 构造函数 ====================
    
    public SubAgentTask() {}
    
    public SubAgentTask(String name, String role, String taskDescription) {
        this.name = name;
        this.role = role;
        this.taskDescription = taskDescription;
    }
    
    public SubAgentTask(String taskId, String name, String role, String taskDescription,
                       String instruction, String agentType, int priority, long timeout,
                       Map<String, Object> context, List<String> dependencies) {
        this.taskId = taskId != null ? taskId : UUID.randomUUID().toString();
        this.name = name;
        this.role = role;
        this.taskDescription = taskDescription;
        this.instruction = instruction;
        this.agentType = agentType;
        this.priority = priority;
        this.timeout = timeout;
        this.timeoutMs = timeout;
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
        this.dependsOn = this.dependencies;
    }
    
    // ==================== Getter 和 Setter ====================
    
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public String getId() { return taskId; } // 简写
    
    public String getName() { return name != null ? name : taskId; }
    public void setName(String name) { this.name = name; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getTaskDescription() { 
        return taskDescription != null ? taskDescription : instruction; 
    }
    public void setTaskDescription(String taskDescription) { 
        this.taskDescription = taskDescription; 
    }
    
    public String getInstruction() { 
        return instruction != null ? instruction : taskDescription; 
    }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = Math.max(1, Math.min(10, priority)); }
    
    public long getTimeout() { return timeout; }
    public void setTimeout(long timeout) { 
        this.timeout = timeout; 
        this.timeoutMs = timeout;
    }
    
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { 
        this.timeoutMs = timeoutMs; 
        this.timeout = timeoutMs;
    }
    
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { 
        this.context = context != null ? new HashMap<>(context) : new HashMap<>(); 
    }
    
    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { 
        this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
        this.dependsOn = this.dependencies;
    }
    
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { 
        this.dependsOn = dependsOn != null ? new ArrayList<>(dependsOn) : new ArrayList<>();
        this.dependencies = this.dependsOn;
    }
    
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    
    public CompletableFuture<SubAgentResult> getFuture() { return future; }
    public void setFuture(CompletableFuture<SubAgentResult> future) { this.future = future; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getStartTime() { return startTime > 0 ? startTime : startedAt; }
    public void setStartTime(long startTime) { 
        this.startTime = startTime; 
        this.startedAt = startTime;
    }
    
    public long getStartedAt() { return startedAt > 0 ? startedAt : startTime; }
    public void setStartedAt(long startedAt) { 
        this.startedAt = startedAt; 
        this.startTime = startedAt;
    }
    
    public long getEndTime() { return endTime > 0 ? endTime : completedAt; }
    public void setEndTime(long endTime) { 
        this.endTime = endTime; 
        this.completedAt = endTime;
    }
    
    public long getCompletedAt() { return completedAt > 0 ? completedAt : endTime; }
    public void setCompletedAt(long completedAt) { 
        this.completedAt = completedAt; 
        this.endTime = completedAt;
    }
    
    public Agent getExecutingAgent() { return executingAgent; }
    public void setExecutingAgent(Agent executingAgent) { this.executingAgent = executingAgent; }
    
    public SubAgentResult getResult() { return result; }
    public void setResult(SubAgentResult result) { this.result = result; }
    
    public Throwable getError() { return error; }
    public void setError(Throwable error) { this.error = error; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { 
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>(); 
    }
    
    public Object getMetadata(String key) { return metadata.get(key); }
    public void setMetadata(String key, Object value) { metadata.put(key, value); }
    
    // ==================== 状态管理 ====================
    
    public enum TaskStatus {
        PENDING("等待中", false),
        WAITING_DEPS("等待依赖", false),
        RUNNING("执行中", false),
        COMPLETED("已完成", true),
        FAILED("失败", true),
        CANCELLED("已取消", true),
        TIMEOUT("超时", true);
        
        private final String description;
        private final boolean terminal;
        
        TaskStatus(String description, boolean terminal) {
            this.description = description;
            this.terminal = terminal;
        }
        
        public String getDescription() { return description; }
        public boolean isTerminal() { return terminal; }
        
        @Override
        public String toString() { return description; }
    }
    
    /**
     * 标记任务开始执行
     */
    public void markStarted() {
        this.status = TaskStatus.RUNNING;
        this.startTime = System.currentTimeMillis();
        this.startedAt = this.startTime;
    }
    
    /**
     * 标记任务完成
     */
    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.endTime = System.currentTimeMillis();
        this.completedAt = this.endTime;
    }
    
    /**
     * 标记任务失败
     */
    public void markFailed() {
        this.status = TaskStatus.FAILED;
        this.endTime = System.currentTimeMillis();
        this.completedAt = this.endTime;
    }
    
    /**
     * 标记任务取消
     */
    public void markCancelled() {
        this.status = TaskStatus.CANCELLED;
        this.endTime = System.currentTimeMillis();
        this.completedAt = this.endTime;
    }
    
    /**
     * 标记任务超时
     */
    public void markTimeout() {
        this.status = TaskStatus.TIMEOUT;
        this.endTime = System.currentTimeMillis();
        this.completedAt = this.endTime;
    }
    
    /**
     * 是否已结束
     */
    public boolean isFinished() {
        return status.isTerminal();
    }
    
    /**
     * 是否成功完成
     */
    public boolean isSuccess() {
        return status == TaskStatus.COMPLETED;
    }
    
    /**
     * 是否失败
     */
    public boolean isFailed() {
        return status == TaskStatus.FAILED || status == TaskStatus.TIMEOUT;
    }
    
    /**
     * 获取执行耗时（毫秒）
     */
    public long getExecutionTimeMs() {
        long start = getStartTime();
        if (start == 0) return 0;
        long end = getEndTime();
        return (end > 0 ? end : System.currentTimeMillis()) - start;
    }
    
    /**
     * 是否有依赖
     */
    public boolean hasDependencies() {
        return dependencies != null && !dependencies.isEmpty();
    }
    
    /**
     * 添加依赖
     */
    public void addDependency(String taskId) {
        if (dependencies == null) {
            dependencies = new ArrayList<>();
        }
        if (!dependencies.contains(taskId)) {
            dependencies.add(taskId);
        }
        dependsOn = dependencies;
    }
    
    /**
     * 移除依赖
     */
    public void removeDependency(String taskId) {
        if (dependencies != null) {
            dependencies.remove(taskId);
        }
        dependsOn = dependencies;
    }
    
    // ==================== 接口实现 ====================
    
    /**
     * Callable 实现 - 返回结果
     */
    @Override
    public SubAgentResult call() throws Exception {
        markStarted();
        try {
            // 实际执行逻辑由外部执行器提供
            // 这里返回一个占位结果
            if (result != null) {
                return result;
            }
            throw new IllegalStateException("Task not properly configured for execution");
        } catch (Exception e) {
            markFailed();
            throw e;
        }
    }
    
    /**
     * Runnable 实现 - 无返回值
     */
    @Override
    public void run() {
        try {
            call();
        } catch (Exception e) {
            this.error = e;
            Thread.currentThread().interrupt();
        }
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String taskId = UUID.randomUUID().toString();
        private String name;
        private String role;
        private String taskDescription;
        private String instruction;
        private String agentType;
        private int priority = 5;
        private long timeout = 60000;
        private Map<String, Object> context = new HashMap<>();
        private List<String> dependencies = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();
        
        public Builder taskId(String taskId) { 
            this.taskId = taskId; 
            return this; 
        }
        
        public Builder id(String id) { 
            this.taskId = id; 
            return this; 
        }
        
        public Builder name(String name) { 
            this.name = name; 
            return this; 
        }
        
        public Builder role(String role) { 
            this.role = role; 
            return this; 
        }
        
        public Builder taskDescription(String taskDescription) { 
            this.taskDescription = taskDescription; 
            return this; 
        }
        
        public Builder instruction(String instruction) { 
            this.instruction = instruction; 
            return this; 
        }
        
        public Builder agentType(String agentType) { 
            this.agentType = agentType; 
            return this; 
        }
        
        public Builder priority(int priority) { 
            this.priority = priority; 
            return this; 
        }
        
        public Builder timeout(long timeout) { 
            this.timeout = timeout; 
            return this; 
        }
        
        public Builder timeoutMs(long timeoutMs) { 
            this.timeout = timeoutMs; 
            return this; 
        }
        
        public Builder context(Map<String, Object> context) { 
            this.context = context != null ? new HashMap<>(context) : new HashMap<>(); 
            return this; 
        }
        
        public Builder addContext(String key, Object value) {
            this.context.put(key, value);
            return this;
        }
        
        public Builder dependencies(List<String> dependencies) { 
            this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>(); 
            return this; 
        }
        
        public Builder dependsOn(List<String> dependsOn) { 
            this.dependencies = dependsOn != null ? new ArrayList<>(dependsOn) : new ArrayList<>(); 
            return this; 
        }
        
        public Builder addDependency(String taskId) {
            if (!this.dependencies.contains(taskId)) {
                this.dependencies.add(taskId);
            }
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) { 
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>(); 
            return this; 
        }
        
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public SubAgentTask build() {
            SubAgentTask task = new SubAgentTask();
            task.taskId = this.taskId;
            task.name = this.name;
            task.role = this.role;
            task.taskDescription = this.taskDescription;
            task.instruction = this.instruction;
            task.agentType = this.agentType;
            task.priority = this.priority;
            task.timeout = this.timeout;
            task.timeoutMs = this.timeout;
            task.context = new HashMap<>(this.context);
            task.dependencies = new ArrayList<>(this.dependencies);
            task.dependsOn = task.dependencies;
            task.metadata = new HashMap<>(this.metadata);
            return task;
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 创建快速任务
     */
    public static SubAgentTask quickTask(String name, String description) {
        return builder()
            .name(name)
            .taskDescription(description)
            .build();
    }
    
    /**
     * 创建带角色的任务
     */
    public static SubAgentTask withRole(String role, String description) {
        return builder()
            .role(role)
            .taskDescription(description)
            .build();
    }
    
    @Override
    public String toString() {
        return String.format(
            "SubAgentTask{id='%s', name='%s', role='%s', status=%s, priority=%d, timeout=%dms}",
            taskId, name, role, status, priority, timeout
        );
    }
}
