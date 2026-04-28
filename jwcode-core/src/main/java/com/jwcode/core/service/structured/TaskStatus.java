package com.jwcode.core.service.structured;

/**
 * 任务状态枚举
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public enum TaskStatus {
    /** 任务正在进行中 */
    ACTIVE,
    /** 任务已完成 */
    COMPLETED,
    /** 任务已被放弃/取消 */
    ABANDONED
}