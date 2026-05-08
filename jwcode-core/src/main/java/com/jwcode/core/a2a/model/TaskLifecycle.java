package com.jwcode.core.a2a.model;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * TaskLifecycle — 任务-步骤双层状态机。
 *
 * <p>实现A2A标准Task生命周期 + 步骤级状态追踪。
 * 主Agent视角看到的是Task级状态，专业Agent内部维护Step级状态。</p>
 *
 * <p>Task生命周期（A2A标准）：submitted → working → input-required → completed/failed/canceled</p>
 * <p>Step生命周期（内部）：pending → running → retrying → completed/failed/skipped/blocked</p>
 */
public class TaskLifecycle {

    private static final Logger logger = Logger.getLogger(TaskLifecycle.class.getName());

    // ==================== Task 级状态 ====================

    /** A2A标准Task状态 */
    public enum TaskStatus {
        SUBMITTED,
        WORKING,
        INPUT_REQUIRED,
        COMPLETED,
        FAILED,
        CANCELED;

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == CANCELED;
        }
    }

    // ==================== Step 定义 ====================

    /**
     * 步骤定义。
     */
    public static class Step {
        private final String id;
        private final String name;
        private final String description;
        private final boolean critical;
        private final List<String> dependencies;
        private StepStatus status;
        private ErrorSummary error;
        private int retryCount;
        private Instant startedAt;
        private Instant completedAt;
        private final Map<String, Object> metadata;

        private Step(String id, String name, String description, boolean critical,
                     List<String> dependencies) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.description = description;
            this.critical = critical;
            this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
            this.status = StepStatus.PENDING;
            this.retryCount = 0;
            this.metadata = new ConcurrentHashMap<>();
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isCritical() { return critical; }
        public List<String> getDependencies() { return dependencies; }
        public StepStatus getStatus() { return status; }
        public ErrorSummary getError() { return error; }
        public int getRetryCount() { return retryCount; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public Map<String, Object> getMetadata() { return metadata; }

        void setStatus(StepStatus status) {
            this.status = status;
            if (status == StepStatus.RUNNING || status == StepStatus.RETRYING) {
                this.startedAt = Instant.now();
            }
            if (status.isTerminal()) {
                this.completedAt = Instant.now();
            }
        }

        void setError(ErrorSummary error) { this.error = error; }
        void incrementRetryCount() { this.retryCount++; }

        @Override
        public String toString() {
            return String.format("Step{id=%s, name='%s', status=%s, retry=%d, critical=%s}",
                id, name, status, retryCount, critical);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String name;
            private String description;
            private boolean critical;
            private List<String> dependencies;

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder critical(boolean critical) { this.critical = critical; return this; }
            public Builder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }

            public Step build() {
                return new Step(id, name, description, critical, dependencies);
            }
        }
    }

    // ==================== TaskLifecycle 实例 ====================

    private final String taskId;
    private final String taskName;
    private final String taskDescription;
    private volatile TaskStatus status;
    private final List<Step> steps;
    private final Map<String, Step> stepMap;
    private volatile String currentStepId;
    private volatile ErrorSummary taskError;
    private volatile String waitingFor;
    private final Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private final Map<String, Object> artifacts;
    private final List<String> eventLog;

    public TaskLifecycle(String taskId, String taskName, String taskDescription) {
        this.taskId = Objects.requireNonNull(taskId);
        this.taskName = Objects.requireNonNull(taskName);
        this.taskDescription = taskDescription;
        this.status = TaskStatus.SUBMITTED;
        this.steps = new CopyOnWriteArrayList<>();
        this.stepMap = new ConcurrentHashMap<>();
        this.artifacts = new ConcurrentHashMap<>();
        this.eventLog = new CopyOnWriteArrayList<>();
        this.createdAt = Instant.now();
    }

    // ==================== Task 级操作 ====================

    public String getTaskId() { return taskId; }
    public String getTaskName() { return taskName; }
    public String getTaskDescription() { return taskDescription; }
    public TaskStatus getStatus() { return status; }
    public String getCurrentStepId() { return currentStepId; }
    public ErrorSummary getTaskError() { return taskError; }
    public String getWaitingFor() { return waitingFor; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Map<String, Object> getArtifacts() { return artifacts; }
    public List<String> getEventLog() { return List.copyOf(eventLog); }

    /**
     * 获取所有步骤的不可变快照。
     */
    public List<Step> getSteps() {
        return List.copyOf(steps);
    }

    /**
     * 获取指定步骤。
     */
    public Step getStep(String stepId) {
        return stepMap.get(stepId);
    }

    /**
     * 开始执行任务。
     */
    public synchronized void start() {
        if (status != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Task already started: " + status);
        }
        this.status = TaskStatus.WORKING;
        this.startedAt = Instant.now();
        addEvent("Task started");
    }

    /**
     * 完成任务。
     */
    public synchronized void complete() {
        if (status.isTerminal()) {
            return;
        }
        this.status = TaskStatus.COMPLETED;
        this.completedAt = Instant.now();
        addEvent("Task completed");
    }

    /**
     * 标记任务失败。
     */
    public synchronized void fail(ErrorSummary error) {
        if (status.isTerminal()) {
            return;
        }
        this.status = TaskStatus.FAILED;
        this.taskError = error;
        this.completedAt = Instant.now();
        addEvent("Task failed: " + error.toBusinessSummary());
    }

    /**
     * 取消任务。
     */
    public synchronized void cancel() {
        if (status.isTerminal()) {
            return;
        }
        this.status = TaskStatus.CANCELED;
        this.completedAt = Instant.now();
        addEvent("Task canceled");
    }

    /**
     * 等待用户输入。
     */
    public synchronized void waitForInput(String question) {
        this.status = TaskStatus.INPUT_REQUIRED;
        this.waitingFor = question;
        addEvent("Waiting for user input: " + question);
    }

    /**
     * 收到用户输入后恢复。
     */
    public synchronized void resumeFromInput() {
        if (status == TaskStatus.INPUT_REQUIRED) {
            this.status = TaskStatus.WORKING;
            this.waitingFor = null;
            addEvent("Resumed from user input");
        }
    }

    // ==================== Step 级操作 ====================

    /**
     * 添加步骤定义。
     */
    public void addStep(Step step) {
        Objects.requireNonNull(step);
        steps.add(step);
        stepMap.put(step.getId(), step);
        addEvent("Step added: " + step.getName());
    }

    /**
     * 批量添加步骤。
     */
    public void addSteps(List<Step> steps) {
        steps.forEach(this::addStep);
    }

    /**
     * 开始执行指定步骤。
     */
    public synchronized void startStep(String stepId) {
        Step step = stepMap.get(stepId);
        if (step == null) {
            throw new IllegalArgumentException("Step not found: " + stepId);
        }
        if (step.getStatus() != StepStatus.PENDING) {
            throw new IllegalStateException("Step already started: " + step.getStatus());
        }

        // 检查依赖
        for (String depId : step.getDependencies()) {
            Step dep = stepMap.get(depId);
            if (dep != null && dep.getStatus() != StepStatus.COMPLETED) {
                step.setStatus(StepStatus.BLOCKED);
                addEvent("Step blocked: " + step.getName() + " (dependency: " + depId + ")");
                return;
            }
        }

        step.setStatus(StepStatus.RUNNING);
        this.currentStepId = stepId;
        addEvent("Step started: " + step.getName());
    }

    /**
     * 完成指定步骤。
     */
    public synchronized void completeStep(String stepId) {
        Step step = stepMap.get(stepId);
        if (step == null) return;
        step.setStatus(StepStatus.COMPLETED);
        addEvent("Step completed: " + step.getName());

        // 检查是否所有步骤都已完成
        checkAllStepsCompleted();
    }

    /**
     * 标记指定步骤失败。
     */
    public synchronized void failStep(String stepId, ErrorSummary error) {
        Step step = stepMap.get(stepId);
        if (step == null) return;
        step.setError(error);
        step.setStatus(StepStatus.FAILED);
        addEvent("Step failed: " + step.getName() + " (" + error.toBusinessSummary() + ")");

        // 如果是关键步骤，整个任务失败
        if (step.isCritical()) {
            fail(ErrorSummary.criticalFailure(
                "关键步骤失败: " + step.getName() + " - " + error.getMessage(),
                error.isRequiresHumanIntervention()));
            return;
        }

        // 阻塞依赖该步骤的其他步骤
        blockDependentSteps(stepId);

        // 检查是否所有步骤都已终止
        checkAllStepsCompleted();
    }

    /**
     * 跳过指定步骤。
     */
    public synchronized void skipStep(String stepId) {
        Step step = stepMap.get(stepId);
        if (step == null) return;
        step.setStatus(StepStatus.SKIPPED);
        addEvent("Step skipped: " + step.getName());

        // 阻塞依赖该步骤的其他步骤
        blockDependentSteps(stepId);

        // 检查是否所有步骤都已终止
        checkAllStepsCompleted();
    }

    /**
     * 重试指定步骤。
     */
    public synchronized void retryStep(String stepId) {
        Step step = stepMap.get(stepId);
        if (step == null) return;
        if (step.getStatus() != StepStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed steps: " + step.getStatus());
        }
        step.incrementRetryCount();
        step.setStatus(StepStatus.RETRYING);
        step.setError(null);
        addEvent("Step retrying: " + step.getName() + " (attempt " + step.getRetryCount() + ")");
    }

    /**
     * 获取步骤级状态的聚合摘要（面向主Agent）。
     */
    public String getStepSummary() {
        long total = steps.size();
        long completed = steps.stream().filter(s -> s.getStatus() == StepStatus.COMPLETED).count();
        long failed = steps.stream().filter(s -> s.getStatus() == StepStatus.FAILED).count();
        long skipped = steps.stream().filter(s -> s.getStatus() == StepStatus.SKIPPED).count();
        long running = steps.stream().filter(s -> s.getStatus().isActive()).count();
        long pending = steps.stream().filter(s -> s.getStatus() == StepStatus.PENDING).count();

        return String.format("步骤进度: %d/%d 已完成 | %d 运行中 | %d 失败 | %d 跳过 | %d 待处理",
            completed, total, running, failed, skipped, pending);
    }

    // ==================== 内部方法 ====================

    private void blockDependentSteps(String failedStepId) {
        for (Step s : steps) {
            if (s.getDependencies().contains(failedStepId) && s.getStatus() == StepStatus.PENDING) {
                s.setStatus(StepStatus.BLOCKED);
                addEvent("Step blocked: " + s.getName() + " (dependency failed: " + failedStepId + ")");
            }
        }
    }

    private void checkAllStepsCompleted() {
        boolean allTerminal = steps.stream().allMatch(s -> s.getStatus().isTerminal());
        if (allTerminal) {
            boolean anyFailed = steps.stream().anyMatch(s -> s.getStatus().isFailed());
            if (anyFailed) {
                if (status != TaskStatus.FAILED) {
                    fail(ErrorSummary.domainAgentFailure("部分步骤失败", "请检查失败步骤的摘要"));
                }
            } else {
                complete();
            }
        }
    }

    private void addEvent(String event) {
        String log = String.format("[%s] %s", Instant.now().toString(), event);
        eventLog.add(log);
        logger.fine("[TaskLifecycle:" + taskId + "] " + event);
    }

    @Override
    public String toString() {
        return String.format("TaskLifecycle{id=%s, name='%s', status=%s, steps=%d/%d}",
            taskId, taskName, status,
            steps.stream().filter(s -> s.getStatus() == StepStatus.COMPLETED).count(),
            steps.size());
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建简单的任务生命周期（无步骤）。
     */
    public static TaskLifecycle simple(String taskId, String taskName) {
        return new TaskLifecycle(taskId, taskName, null);
    }

    /**
     * 创建带步骤的任务生命周期。
     */
    public static TaskLifecycle withSteps(String taskId, String taskName, List<Step> steps) {
        TaskLifecycle lifecycle = new TaskLifecycle(taskId, taskName, null);
        lifecycle.addSteps(steps);
        return lifecycle;
    }
}
