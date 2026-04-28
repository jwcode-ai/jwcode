package com.jwcode.core.service.structured;

/**
 * 评估触发类型枚举
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public enum EvaluationTrigger {
    /** 阈值触发 - 活跃消息数超过限制 */
    THRESHOLD,
    /** 任务边界触发 - 任务完成/切换 */
    TASK_BOUNDARY,
    /** 手动触发 - 用户主动要求清理 */
    MANUAL,
    /** 定时触发 - 每N轮对话后自动评估 */
    PERIODIC,
    /** 隐性评估 - AI 主动建议清理 */
    IMPLICIT
}