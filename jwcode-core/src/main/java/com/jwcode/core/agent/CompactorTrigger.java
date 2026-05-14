package com.jwcode.core.agent;

import com.jwcode.core.llm.TokenBudget;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;

import java.util.List;
import java.util.logging.Logger;

/**
 * CompactorTrigger — 压缩触发策略。
 *
 * <p>定义何时触发上下文压缩，以及选择何种压缩策略。
 * 支持多种触发原因，每种原因对应不同的压缩策略和阈值。</p>
 */
public class CompactorTrigger {

    private static final Logger logger = Logger.getLogger(CompactorTrigger.class.getName());

    /** 触发原因 */
    public enum TriggerReason {
        /** Token使用率超阈值 */
        TOKEN_HIGH_WATERMARK,
        /** 用户手动触发（/compact 命令） */
        MANUAL_REQUEST,
        /** Agent主动请求 */
        AGENT_REQUEST,
        /** 保存检查点前 */
        CHECKPOINT_BEFORE,
        /** 子任务完成时 */
        TASK_COMPLETION,
        /** 会话消息数超限 */
        SESSION_LIMIT
    }

    /** 压缩策略 */
    public enum Strategy {
        /** 结构化压缩（推荐）：强制XML输出，按优先级保留信息 */
        STRUCTURED,
        /** AICL 优先级淘汰（v1.1）：基于 6 级优先级+生命周期的渐进式淘汰 */
        AICL_PRIORITY,
        /** 智能压缩（默认）：保留尾部8条 + 关键任务目标 */
        SMART,
        /** 激进压缩：仅保留尾部4条 + 摘要 */
        AGGRESSIVE,
        /** 最小压缩：仅移除工具结果噪声，不调用LLM */
        MINIMAL
    }

    /** 各触发原因的默认阈值 */
    private static final double TOKEN_WATERMARK_THRESHOLD = 0.70;
    private static final int CHECKPOINT_MESSAGE_THRESHOLD = 20;
    private static final int TASK_COMPLETION_MESSAGE_THRESHOLD = 30;
    private static final int SESSION_LIMIT_THRESHOLD = 50;
    private static final int MIN_MESSAGES_FOR_COMPACT = 10;

    /**
     * 判断是否需要压缩。
     *
     * @param session 会话
     * @param budget  Token预算
     * @param reason  触发原因
     * @return true 表示需要压缩
     */
    public boolean shouldCompact(Session session, TokenBudget budget, TriggerReason reason) {
        if (session == null) return false;
        int messageCount = session.getMessages().size();

        return switch (reason) {
            case TOKEN_HIGH_WATERMARK -> {
                boolean overWatermark = budget != null && budget.usageRatio() > TOKEN_WATERMARK_THRESHOLD;
                boolean enoughMessages = messageCount > MIN_MESSAGES_FOR_COMPACT;
                yield overWatermark && enoughMessages;
            }
            case MANUAL_REQUEST -> messageCount > MIN_MESSAGES_FOR_COMPACT;
            case AGENT_REQUEST -> messageCount > MIN_MESSAGES_FOR_COMPACT;
            case CHECKPOINT_BEFORE -> messageCount > CHECKPOINT_MESSAGE_THRESHOLD;
            case TASK_COMPLETION -> messageCount > TASK_COMPLETION_MESSAGE_THRESHOLD;
            case SESSION_LIMIT -> messageCount > SESSION_LIMIT_THRESHOLD;
        };
    }

    /**
     * 根据触发原因选择压缩策略。
     */
    public Strategy selectStrategy(TriggerReason reason) {
        return switch (reason) {
            case TOKEN_HIGH_WATERMARK -> Strategy.AICL_PRIORITY;
            case MANUAL_REQUEST -> Strategy.AGGRESSIVE;
            case AGENT_REQUEST -> Strategy.AICL_PRIORITY;
            case CHECKPOINT_BEFORE -> Strategy.MINIMAL;
            case TASK_COMPLETION -> Strategy.AICL_PRIORITY;
            case SESSION_LIMIT -> Strategy.AGGRESSIVE;
        };
    }

    /**
     * 根据策略获取尾部保留消息数。
     */
    public int getTailSize(Strategy strategy) {
        return switch (strategy) {
            case STRUCTURED -> 8;
            case AICL_PRIORITY -> 0; // AICL 不按 tail 截断，由优先级引擎决策
            case SMART -> 8;
            case AGGRESSIVE -> 4;
            case MINIMAL -> Integer.MAX_VALUE; // 不截断，仅清理噪声
        };
    }

    /**
     * 获取触发原因的友好描述。
     */
    public String getReasonDescription(TriggerReason reason) {
        return switch (reason) {
            case TOKEN_HIGH_WATERMARK -> "Token 使用率超过阈值，自动触发 AICL 优先级淘汰";
            case MANUAL_REQUEST -> "用户手动触发上下文压缩";
            case AGENT_REQUEST -> "Agent 主动请求 AICL 优先级淘汰";
            case CHECKPOINT_BEFORE -> "保存检查点前执行最小压缩";
            case TASK_COMPLETION -> "子任务完成，AICL 清理上下文";
            case SESSION_LIMIT -> "会话消息数超限，执行激进压缩";
        };
    }

    /**
     * 获取压缩建议（供Agent决策时参考）。
     */
    public String getAdvice(Session session, TokenBudget budget, TriggerReason reason) {
        if (!shouldCompact(session, budget, reason)) {
            return "当前无需压缩";
        }

        Strategy strategy = selectStrategy(reason);
        int messageCount = session.getMessages().size();
        int tailSize = getTailSize(strategy);
        int estimatedAfter = Math.min(tailSize + 2, messageCount); // 粗略估计

        return String.format("建议压缩: 原因=%s, 策略=%s, 当前消息数=%d, 预计压缩至约%d条",
            getReasonDescription(reason), strategy, messageCount, estimatedAfter);
    }
}
