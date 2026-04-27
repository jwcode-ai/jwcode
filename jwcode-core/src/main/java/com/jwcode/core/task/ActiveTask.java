package com.jwcode.core.task;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 当前激活任务 — 绑定到 Session 的任务实例。
 *
 * <p>用户每次发起新请求时，{@link com.jwcode.core.task.TaskLifecycleManager}
 * 会创建或替换 Session 中的 ActiveTask。任务清单随对话推进而动态更新。</p>
 */
public class ActiveTask {

    private String taskId;
    private String description;
    private TaskStatus status;
    private List<TaskStep> steps;
    private int currentStepIndex;
    private String waitingFor;
    private Instant createdAt;
    private Instant updatedAt;

    public ActiveTask() {
        this.taskId = UUID.randomUUID().toString().substring(0, 8);
        this.status = TaskStatus.PENDING;
        this.steps = new ArrayList<>();
        this.currentStepIndex = -1;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ActiveTask(String description) {
        this();
        this.description = description;
    }

    // Getters
    public String getTaskId() { return taskId; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public List<TaskStep> getSteps() { return Collections.unmodifiableList(steps); }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public String getWaitingFor() { return waitingFor; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(TaskStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }
    public void setWaitingFor(String waitingFor) { this.waitingFor = waitingFor; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 设置步骤列表（规划完成后调用）
     */
    public void setSteps(List<TaskStep> steps) {
        this.steps = new ArrayList<>(steps);
        this.updatedAt = Instant.now();
    }

    /**
     * 获取当前步骤
     */
    @JsonIgnore
    public TaskStep getCurrentStep() {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentStepIndex);
    }

    /**
     * 推进到下一步
     */
    public TaskStep advanceToNextStep() {
        currentStepIndex++;
        if (currentStepIndex < steps.size()) {
            TaskStep next = steps.get(currentStepIndex);
            next.markRunning();
            return next;
        }
        return null;
    }

    /**
     * 获取已完成步骤数
     */
    public int getCompletedCount() {
        return (int) steps.stream().filter(s -> s.getStatus() == TaskStepStatus.COMPLETED).count();
    }

    /**
     * 获取失败步骤数
     */
    public int getFailedCount() {
        return (int) steps.stream().filter(s -> s.getStatus() == TaskStepStatus.FAILED).count();
    }

    /**
     * 检查是否全部完成
     */
    public boolean isAllCompleted() {
        return !steps.isEmpty() && steps.stream().allMatch(s ->
            s.getStatus() == TaskStepStatus.COMPLETED || s.getStatus() == TaskStepStatus.SKIPPED);
    }

    /**
     * 生成 Markdown 格式的任务清单摘要
     */
    public String toTaskListSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("【当前任务清单】").append("\n");
        sb.append("任务：").append(description != null ? description : "未命名").append("\n");
        sb.append("状态：").append(status).append(" | ");
        sb.append("进度：").append(getCompletedCount()).append("/").append(steps.size()).append("\n\n");

        for (TaskStep step : steps) {
            String icon = switch (step.getStatus()) {
                case PENDING -> "⬜";
                case RUNNING -> "🔵";
                case COMPLETED -> "✅";
                case FAILED -> "❌";
                case BLOCKED -> "🚫";
                case SKIPPED -> "⏭️";
            };
            sb.append(icon).append(" ").append(step.getIndex() + 1).append(". ")
              .append(step.getDescription()).append("\n");
        }

        if (waitingFor != null && !waitingFor.isBlank()) {
            sb.append("\n⏳ 等待用户补充：").append(waitingFor).append("\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("ActiveTask[%s: %s] status=%s steps=%d completed=%d",
            taskId, description, status, steps.size(), getCompletedCount());
    }
}
