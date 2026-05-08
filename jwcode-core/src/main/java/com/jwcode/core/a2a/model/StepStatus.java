package com.jwcode.core.a2a.model;

/**
 * StepStatus — 步骤级状态枚举。
 *
 * <p>用于专业Agent内部的步骤级状态追踪，对主Agent屏蔽工具执行细节。
 * 主Agent通过 TaskLifecycle 获取步骤级状态的聚合摘要。</p>
 */
public enum StepStatus {

    /** 等待执行 */
    PENDING,

    /** 正在执行 */
    RUNNING,

    /** 正在重试（第n次） */
    RETRYING,

    /** 执行成功 */
    COMPLETED,

    /** 执行失败（不可重试） */
    FAILED,

    /** 已跳过（非关键路径，失败后自动跳过） */
    SKIPPED,

    /** 因依赖失败而阻塞 */
    BLOCKED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED || this == BLOCKED;
    }

    public boolean isFailed() {
        return this == FAILED || this == BLOCKED;
    }

    public boolean isActive() {
        return this == RUNNING || this == RETRYING;
    }
}
