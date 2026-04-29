package com.jwcode.core.tool.execution;

/**
 * 工具执行状态枚举
 * 
 * 对应日志中 Tool 失败模式：
 * - PARSE → VALIDATE → EXECUTE → REPORT → DONE
 * - 失败时进入 CORRECTION 循环（最多2次）
 * - 超过限制进入 FAILED
 */
public enum ToolExecutionState {
    /** 解析阶段：JSON 反序列化 */
    PARSE,
    /** 校验阶段：Schema 强校验 */
    VALIDATE,
    /** 执行阶段：平台驱动执行 */
    EXECUTE,
    /** 纠错循环：最多 2 次 */
    CORRECTION,
    /** 上报阶段：结果写入记忆上下文 */
    REPORT,
    /** 完成：结果成功返回 */
    DONE,
    /** 失败：标记该工具调用失败 */
    FAILED
}