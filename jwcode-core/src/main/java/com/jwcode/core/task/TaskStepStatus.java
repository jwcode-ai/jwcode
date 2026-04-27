package com.jwcode.core.task;

/**
 * 任务步骤的状态枚举。
 */
public enum TaskStepStatus {
    /**
     * 等待执行
     */
    PENDING,

    /**
     * 当前正在执行
     */
    RUNNING,

    /**
     * 执行成功完成
     */
    COMPLETED,

    /**
     * 执行失败
     */
    FAILED,

    /**
     * 因前置依赖失败而被阻塞
     */
    BLOCKED,

    /**
     * 被跳过（非关键步骤）
     */
    SKIPPED
}
