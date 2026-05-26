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

    /**
     * 检查状态转换是否合法（白名单方式）。
     *
     * <p>合法转换表：</p>
     * <pre>
     *   PENDING    → RUNNING, PLANNING, CANCELLED
     *   RUNNING    → COMPLETED, FAILED, STOPPED
     *   PLANNING   → PLANNED, FAILED, CANCELLED
     *   PLANNED    → EXECUTING, CANCELLED
     *   EXECUTING  → COMPLETED, FAILED, STOPPED
     *   WAITING_INPUT → RUNNING, EXECUTING, CANCELLED
     *   COMPLETED  → (无合法转换)
     *   FAILED     → PENDING (重试)
     *   STOPPED    → PENDING (重试)
     *   CANCELLED  → (无合法转换)
     *   NONE       → PENDING, PLANNING
     * </pre>
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 如果转换合法返回 true
     */
    public static boolean isValidTransition(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true; // 相同状态允许（幂等更新）

        switch (from) {
            case PENDING:
                return to == RUNNING || to == PLANNING || to == CANCELLED;
            case RUNNING:
                return to == COMPLETED || to == FAILED || to == STOPPED;
            case PLANNING:
                return to == PLANNED || to == FAILED || to == CANCELLED;
            case PLANNED:
                return to == EXECUTING || to == CANCELLED;
            case EXECUTING:
                return to == COMPLETED || to == FAILED || to == STOPPED;
            case WAITING_INPUT:
                return to == RUNNING || to == EXECUTING || to == CANCELLED;
            case COMPLETED:
                return false; // 终止状态，不可转换
            case FAILED:
                return to == PENDING; // 仅允许重试
            case STOPPED:
                return to == PENDING; // 仅允许重试
            case CANCELLED:
                return false; // 终止状态，不可转换
            case NONE:
                return to == PENDING || to == PLANNING;
            default:
                return false;
        }
    }
}
