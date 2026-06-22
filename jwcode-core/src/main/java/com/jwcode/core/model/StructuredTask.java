package com.jwcode.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StructuredTask — 结构化任务模型，支持并发/串行执行模式。
 *
 * <p>相比 PlanTask，StructuredTask 增加了：
 * <ul>
 *   <li>executionMode — 执行模式（SEQUENTIAL/CONCURRENT）</li>
 *   <li>phase — 所属阶段（planning/exploration/implementation/testing/review）</li>
 *   <li>stepNumber — 步骤编号</li>
 *   <li>parallelGroup — 并发组ID（同一组的任务可并行执行）</li>
 *   <li>estimatedDuration — 预估耗时</li>
 * </ul>
 * </p>
 */
public class StructuredTask {

    /** 任务唯一标识 */
    private String id;

    /** 任务标题 */
    private String title;

    /** 任务描述 */
    private String description;

    /** 任务状态 */
    private String status; // pending / running / completed / failed / skipped

    /** 分配的 Agent 类型 */
    private String agentType;

    /** 依赖的任务ID列表 */
    private List<String> dependencies;

    /** 子任务列表（树形结构） */
    private List<StructuredTask> children;

    /** 执行模式 */
    private ExecutionMode executionMode;

    /** 所属阶段 */
    private TaskPhase phase;

    /** 步骤编号（从1开始） */
    private int stepNumber;

    /** 并发组ID（同一组的任务可并行执行） */
    private String parallelGroup;

    /** 预估耗时（毫秒） */
    private long estimatedDuration;

    /** 执行结果 */
    private String result;

    /** 错误信息 */
    private String error;

    /** 开始时间 */
    private Long startedAt;

    /** 完成时间 */
    private Long completedAt;

    /** 进度百分比（0-100） */
    private Integer progress;

    /** 日志列表 */
    private List<String> logs;

    /**
     * 任务上下文 - 注入到下游执行时的上下文信息。
     * 包含：文件路径、依赖模块、约束条件、关键代码片段等。
     */
    private Map<String, String> context;

    /** 关联的 SprintContract ID（GAN 迭代循环用） */
    private String contractId;

    /** 当前迭代轮数（GAN 迭代循环用） */
    private int iterationRound;

    // ==================== 枚举 ====================

    public enum ExecutionMode {
        /** 串行执行 — 按顺序一个接一个 */
        SEQUENTIAL,
        /** 并发执行 — 同一组内所有任务同时执行 */
        CONCURRENT
    }

    public enum TaskPhase {
        /** 探索调研阶段 */
        EXPLORATION,
        /** 架构设计阶段 */
        DESIGN,
        /** 代码实现阶段 */
        IMPLEMENTATION,
        /** 测试验证阶段 */
        TESTING,
        /** 代码审查阶段 */
        REVIEW,
        /** 文档阶段 */
        DOCUMENTATION,
        /** 通用阶段 */
        GENERAL
    }

    // ==================== 构造器 ====================

    public StructuredTask() {
        this.dependencies = new ArrayList<>();
        this.children = new ArrayList<>();
        this.logs = new ArrayList<>();
        this.executionMode = ExecutionMode.SEQUENTIAL;
        this.phase = TaskPhase.GENERAL;
        this.status = "pending";
        this.progress = 0;
        this.context = new HashMap<>();
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String title;
        private String description;
        private String status = "pending";
        private String agentType = "orchestrator";
        private List<String> dependencies = new ArrayList<>();
        private List<StructuredTask> children = new ArrayList<>();
        private ExecutionMode executionMode = ExecutionMode.SEQUENTIAL;
        private TaskPhase phase = TaskPhase.GENERAL;
        private int stepNumber;
        private String parallelGroup;
        private long estimatedDuration;
        private String result;
        private String error;
        private Long startedAt;
        private Long completedAt;
        private Integer progress = 0;
        private List<String> logs = new ArrayList<>();
        private Map<String, String> context = new HashMap<>();
        private String contractId;
        private int iterationRound;

        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder agentType(String agentType) { this.agentType = agentType; return this; }
        public Builder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public Builder addDependency(String dep) { this.dependencies.add(dep); return this; }
        public Builder children(List<StructuredTask> children) { this.children = children; return this; }
        public Builder addChild(StructuredTask child) { this.children.add(child); return this; }
        public Builder executionMode(ExecutionMode mode) { this.executionMode = mode; return this; }
        public Builder phase(TaskPhase phase) { this.phase = phase; return this; }
        public Builder stepNumber(int stepNumber) { this.stepNumber = stepNumber; return this; }
        public Builder parallelGroup(String group) { this.parallelGroup = group; return this; }
        public Builder estimatedDuration(long ms) { this.estimatedDuration = ms; return this; }
        public Builder result(String result) { this.result = result; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder startedAt(Long startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Long completedAt) { this.completedAt = completedAt; return this; }
        public Builder progress(Integer progress) { this.progress = progress; return this; }
        public Builder logs(List<String> logs) { this.logs = logs; return this; }
        public Builder context(Map<String, String> context) { this.context = context; return this; }
        public Builder addContext(String key, String value) { this.context.put(key, value); return this; }
        public Builder contractId(String contractId) { this.contractId = contractId; return this; }
        public Builder iterationRound(int iterationRound) { this.iterationRound = iterationRound; return this; }

        public StructuredTask build() {
            StructuredTask task = new StructuredTask();
            task.id = this.id;
            task.title = this.title;
            task.description = this.description;
            task.status = this.status;
            task.agentType = this.agentType;
            task.dependencies = this.dependencies;
            task.children = this.children;
            task.executionMode = this.executionMode;
            task.phase = this.phase;
            task.stepNumber = this.stepNumber;
            task.parallelGroup = this.parallelGroup;
            task.estimatedDuration = this.estimatedDuration;
            task.result = this.result;
            task.error = this.error;
            task.startedAt = this.startedAt;
            task.completedAt = this.completedAt;
            task.progress = this.progress;
            task.logs = this.logs;
            task.context = this.context != null ? new HashMap<>(this.context) : new HashMap<>();
            task.contractId = this.contractId;
            task.iterationRound = this.iterationRound;
            return task;
        }
    }

    // ==================== 转换为 PlanTask ====================

    /**
     * 将 StructuredTask 转换为 PlanTask（用于向前兼容广播）
     */
    public PlanTask toPlanTask() {
        PlanTask pt = new PlanTask();
        pt.setId(this.id);
        pt.setTitle(this.title);
        pt.setDescription(this.description);
        pt.setStatus(this.status);
        pt.setAgentType(this.agentType);
        pt.setDependencies(this.dependencies);
        // 递归转换子任务
        if (this.children != null && !this.children.isEmpty()) {
            List<PlanTask> childPlanTasks = new ArrayList<>();
            for (StructuredTask child : this.children) {
                childPlanTasks.add(child.toPlanTask());
            }
            pt.setChildren(childPlanTasks);
        }
        pt.setResult(this.result);
        pt.setError(this.error);
        pt.setStartedAt(this.startedAt);
        pt.setCompletedAt(this.completedAt);
        pt.setProgress(this.progress);
        pt.setLogs(this.logs);
        pt.setContext(this.context != null ? new HashMap<>(this.context) : null);
        return pt;
    }

    // ==================== Getters & Setters ====================

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }

    public int getIterationRound() { return iterationRound; }
    public void setIterationRound(int iterationRound) { this.iterationRound = iterationRound; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public List<StructuredTask> getChildren() { return children; }
    public void setChildren(List<StructuredTask> children) { this.children = children; }

    public ExecutionMode getExecutionMode() { return executionMode; }
    public void setExecutionMode(ExecutionMode executionMode) { this.executionMode = executionMode; }

    public TaskPhase getPhase() { return phase; }
    public void setPhase(TaskPhase phase) { this.phase = phase; }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

    public String getParallelGroup() { return parallelGroup; }
    public void setParallelGroup(String parallelGroup) { this.parallelGroup = parallelGroup; }

    public long getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(long estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }

    public Map<String, String> getContext() { return context; }
    public void setContext(Map<String, String> context) { this.context = context; }

    /**
     * 追加执行结果到 result 字段。
     */
    public void appendResult(String resultLine) {
        if (this.result == null || this.result.isEmpty()) {
            this.result = resultLine;
        } else {
            this.result = this.result + "\n" + resultLine;
        }
    }
}
