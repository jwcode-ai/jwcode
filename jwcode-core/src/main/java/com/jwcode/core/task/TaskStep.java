package com.jwcode.core.task;

import java.time.Instant;

/**
 * 任务步骤 — 简化版 PlanStep，专注于执行跟踪。
 *
 * <p>由 {@link TaskLifecycleManager} 从 {@link com.jwcode.core.planner.PlanStep} 转换而来，
 * 绑定到 {@link ActiveTask} 的生命周期。</p>
 */
public class TaskStep {

    private int index;
    private String description;
    private String action;
    private String stepPrompt;
    private String agentType;
    private TaskStepStatus status;
    private String result;
    private String error;
    private Instant startedAt;
    private Instant completedAt;

    public TaskStep() {
        this.status = TaskStepStatus.PENDING;
    }

    public TaskStep(int index, String description) {
        this();
        this.index = index;
        this.description = description;
    }

    public TaskStep(int index, String description, String action, String stepPrompt, String agentType) {
        this();
        this.index = index;
        this.description = description;
        this.action = action;
        this.stepPrompt = stepPrompt;
        this.agentType = agentType;
    }

    // Getters
    public int getIndex() { return index; }
    public String getDescription() { return description; }
    public String getAction() { return action; }
    public String getStepPrompt() { return stepPrompt; }
    public String getAgentType() { return agentType; }
    public TaskStepStatus getStatus() { return status; }
    public String getResult() { return result; }
    public String getError() { return error; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    // Setters
    public void setIndex(int index) { this.index = index; }
    public void setDescription(String description) { this.description = description; }
    public void setAction(String action) { this.action = action; }
    public void setStepPrompt(String stepPrompt) { this.stepPrompt = stepPrompt; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public void setStatus(TaskStepStatus status) { this.status = status; }
    public void setResult(String result) { this.result = result; }
    public void setError(String error) { this.error = error; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    /**
     * 标记步骤开始执行
     */
    public void markRunning() {
        this.status = TaskStepStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * 标记步骤成功完成
     */
    public void markCompleted(String result) {
        this.status = TaskStepStatus.COMPLETED;
        this.result = result;
        this.completedAt = Instant.now();
    }

    /**
     * 标记步骤失败
     */
    public void markFailed(String error) {
        this.status = TaskStepStatus.FAILED;
        this.error = error;
        this.completedAt = Instant.now();
    }

    /**
     * 标记步骤被阻塞
     */
    public void markBlocked() {
        this.status = TaskStepStatus.BLOCKED;
    }

    /**
     * 标记步骤被跳过
     */
    public void markSkipped() {
        this.status = TaskStepStatus.SKIPPED;
        this.completedAt = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("TaskStep[%d: %s] %s", index, status, description);
    }
}
