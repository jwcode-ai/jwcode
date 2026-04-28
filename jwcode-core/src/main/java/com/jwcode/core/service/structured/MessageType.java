package com.jwcode.core.service.structured;

/**
 * 消息类型枚举
 * 
 * 定义消息的不同类型，用于 AI 评估时的分类处理
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public enum MessageType {
    /** 用户明确表达的核心意图/需求 */
    INTENT,
    /** 用户提出的问题 */
    QUESTION,
    /** AI 给出的方案/回答 */
    ANSWER,
    /** AI 请求用户澄清 */
    CLARIFICATION,
    /** 用户对澄清的回复 */
    CLARIFICATION_REPLY,
    /** 用户确认/同意 */
    CONFIRMATION,
    /** 用户拒绝/否定 */
    REJECTION,
    /** 工具调用的执行结果 */
    TOOL_RESULT,
    /** 系统通知/状态变更 */
    SYSTEM_EVENT,
    /** 错误信息 */
    ERROR,
    /** 中间过程的讨论，非最终决策 */
    INTERIM_DISCUSSION,
    /** 最终决策/结论 */
    DECISION
}