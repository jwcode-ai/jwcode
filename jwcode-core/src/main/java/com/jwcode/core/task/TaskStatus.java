package com.jwcode.core.task;

/**
 * 统一任务状态枚举。
 *
 * <p>合并了后台任务系统（Task/TaskStore）和会话级激活任务（ActiveTask）的状态需求。</p>
 */
public enum TaskStatus {

    // ========== 后台任务系统兼容状态 ==========
    PENDING("等待中"),
    RUNNING("执行中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    STOPPED("已停止"),
    CANCELLED("已取消"),

    // ========== 会话级任务生命周期新增状态 ==========
    NONE("无当前任务"),
    PLANNING("规划中"),
    PLANNED("已规划"),
    EXECUTING("执行中"),
    WAITING_INPUT("等待输入");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否是活跃状态（尚未结束）
     */
    public boolean isActive() {
        return this == PENDING
            || this == RUNNING
            || this == PLANNING
            || this == PLANNED
            || this == EXECUTING
            || this == WAITING_INPUT;
    }

    /**
     * 是否是终止状态（已完成、失败、停止、取消）
     */
    public boolean isFinished() {
        return this == COMPLETED
            || this == FAILED
            || this == STOPPED
            || this == CANCELLED;
    }

    /**
     * 从字符串解析状态（大小写不敏感）
     */
    public static TaskStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return PENDING;
        }
        for (TaskStatus s : values()) {
            if (s.name().equalsIgnoreCase(status.trim())) {
                return s;
            }
        }
        return PENDING;
    }
}
