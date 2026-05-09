package com.jwcode.core.a2a.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A2ATask — A2A 协议中的任务模型。
 *
 * <p>代表一个由 Orchestrator 提交给子Agent 执行的任务单元。
 * 包含任务 ID、类型、输入参数、状态、输出结果等完整生命周期信息。</p>
 */
public class A2ATask {

    /** 任务唯一标识 */
    private final String taskId;

    /** 任务类型（对应 Skill ID） */
    private final String skillId;

    /** 任务描述 */
    private final String description;

    /** 输入参数 */
    private final Map<String, Object> input;

    /** 任务状态 */
    private TaskStatus status;

    /** 输出结果 */
    private TaskOutput output;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private final LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 任务标签 */
    private final List<String> tags;

    /** 优先级（0=最低, 10=最高） */
    private final int priority;

    public A2ATask(String taskId, String skillId, String description,
                   Map<String, Object> input, TaskStatus status,
                   TaskOutput output, String errorMessage,
                   LocalDateTime createdAt, LocalDateTime updatedAt,
                   List<String> tags, int priority) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.skillId = Objects.requireNonNull(skillId, "skillId must not be null");
        this.description = description;
        this.input = input != null ? Collections.unmodifiableMap(input) : Collections.emptyMap();
        this.status = status != null ? status : TaskStatus.PENDING;
        this.output = output;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.tags = tags != null ? Collections.unmodifiableList(tags) : Collections.emptyList();
        this.priority = priority;
    }

    // ==================== 状态枚举 ====================

    public enum TaskStatus {
        /** 等待执行 */
        PENDING,
        /** 已分配（已指派给 Agent，等待 Agent 确认） */
        ASSIGNED,
        /** 正在执行 */
        RUNNING,
        /** 执行成功 */
        COMPLETED,
        /** 执行失败 */
        FAILED,
        /** 已取消 */
        CANCELLED,
        /** 超时 */
        TIMEOUT
    }

    // ==================== Getters ====================

    public String getTaskId() { return taskId; }
    public String getSkillId() { return skillId; }
    public String getDescription() { return description; }
    public Map<String, Object> getInput() { return input; }
    public TaskStatus getStatus() { return status; }
    public TaskOutput getOutput() { return output; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<String> getTags() { return tags; }
    public int getPriority() { return priority; }

    // ==================== 状态变更 ====================

    public void start() {
        this.status = TaskStatus.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete(TaskOutput output) {
        this.status = TaskStatus.COMPLETED;
        this.output = output;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isTerminal() {
        return status == TaskStatus.COMPLETED
            || status == TaskStatus.FAILED
            || status == TaskStatus.CANCELLED;
    }

    @Override
    public String toString() {
        return "A2ATask{id='" + taskId + "', skill='" + skillId +
               "', status=" + status + "}";
    }

    // ==================== Factory ====================

    /**
     * 创建一个新的待执行任务
     */
    public static A2ATask create(String skillId, String description,
                                  Map<String, Object> input) {
        return new A2ATask(
            UUID.randomUUID().toString().substring(0, 8),
            skillId, description, input,
            TaskStatus.PENDING, null, null,
            LocalDateTime.now(), LocalDateTime.now(),
            Collections.emptyList(), 5
        );
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskId;
        private String skillId;
        private String description;
        private Map<String, Object> input;
        private TaskStatus status;
        private TaskOutput output;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<String> tags;
        private int priority;

        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder skillId(String skillId) { this.skillId = skillId; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder input(Map<String, Object> input) { this.input = input; return this; }
        public Builder status(TaskStatus status) { this.status = status; return this; }
        public Builder output(TaskOutput output) { this.output = output; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }

        public A2ATask build() {
            return new A2ATask(taskId, skillId, description, input,
                status, output, errorMessage, createdAt, updatedAt,
                tags, priority);
        }
    }
}
