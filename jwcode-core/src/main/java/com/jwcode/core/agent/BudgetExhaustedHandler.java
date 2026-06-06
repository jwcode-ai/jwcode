package com.jwcode.core.agent;

import com.jwcode.core.llm.TokenBudget;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 预算耗尽处理器
 *
 * <p>实现 Token 预算耗尽时的自动处理机制：
 * - 70% 触发低预算警告
 * - 85% 触发关键预算警告，自动调用压缩
 * - 95% 触发耗尽警告，触发子任务拆分
 *
 * <p>核心职责：
 * - 监听 TokenBudget 状态变化
 * - 在适当阈值触发压缩或子任务拆分
 * - 避免预算耗尽导致任务中断
 *
 * @author JWCode Team
 * @since 2.0.0
 */
public class BudgetExhaustedHandler {

    private static final Logger logger = LoggerFactory.getLogger(BudgetExhaustedHandler.class);

    // ==================== 阈值配置 ====================

    /** 低预算阈值（建议提示） */
    private final double lowThreshold;

    /** 关键预算阈值（触发压缩） */
    private final double criticalThreshold;

    /** 耗尽阈值（触发子任务拆分） */
    private final double exhaustedThreshold;

    // ==================== 依赖服务 ====================

    private final SubTaskSplitter subTaskSplitter;

    // ==================== 状态 ====================

    private final Map<String, BudgetState> sessionBudgetStates;
    private final CopyOnWriteArrayList<BudgetListener> listeners;
    private final AtomicBoolean autoCompactEnabled;

    // ==================== 构造函数 ====================

    public BudgetExhaustedHandler(SubTaskSplitter subTaskSplitter) {
        this(lowThreshold(), criticalThreshold(), exhaustedThreshold(), subTaskSplitter);
    }

    public BudgetExhaustedHandler(
            double lowThreshold,
            double criticalThreshold,
            double exhaustedThreshold,
            SubTaskSplitter subTaskSplitter) {

        this.lowThreshold = lowThreshold;
        this.criticalThreshold = criticalThreshold;
        this.exhaustedThreshold = exhaustedThreshold;
        this.subTaskSplitter = subTaskSplitter;
        this.sessionBudgetStates = new HashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.autoCompactEnabled = new AtomicBoolean(true);
    }

    // ==================== 默认阈值 ====================

    public static double lowThreshold() { return 0.7; }
    public static double criticalThreshold() { return 0.85; }
    public static double exhaustedThreshold() { return 0.95; }

    // ==================== 核心方法 ====================

    /**
     * 检查并处理预算状态
     *
     * @param session 当前会话
     * @param budget 当前 Token 预算
     * @return 处理结果
     */
    public BudgetAction checkAndHandle(Session session, TokenBudget budget) {
        if (session == null || budget == null) {
            return BudgetAction.NONE;
        }

        String sessionId = session.getId();
        double usageRatio = budget.usageRatio();

        BudgetState state = sessionBudgetStates.computeIfAbsent(
            sessionId,
            k -> new BudgetState(sessionId)
        );

        BudgetAction action = determineAction(state, usageRatio);

        if (action == BudgetAction.NONE) {
            return action;
        }

        state.update(usageRatio);
        return executeAction(session, budget, action, state);
    }

    private BudgetAction determineAction(BudgetState state, double usageRatio) {
        if (usageRatio >= exhaustedThreshold && !state.exhaustedHandled) {
            return BudgetAction.SPLIT_TASK;
        }
        if (usageRatio >= criticalThreshold && !state.criticalHandled) {
            return BudgetAction.COMPACT;
        }
        if (usageRatio >= lowThreshold && !state.lowHandled) {
            return BudgetAction.WARN;
        }
        return BudgetAction.NONE;
    }

    private BudgetAction executeAction(Session session, TokenBudget budget, BudgetAction action, BudgetState state) {
        logger.info("[BudgetHandler] 执行动作: {} | 原因: {}", action, session.getId());

        switch (action) {
            case WARN: {
                state.lowHandled = true;
                notifyListeners(new BudgetEvent(
                    session.getId(),
                    BudgetLevel.LOW,
                    budget.usageRatio(),
                    budget.getRemaining(),
                    "Token 使用率达到 70%，建议关注"
                ));
                return action;
            }

            case COMPACT: {
                if (!autoCompactEnabled.get()) {
                    logger.info("[BudgetHandler] 自动压缩已禁用，跳过");
                    return BudgetAction.NONE;
                }

                state.criticalHandled = true;
                boolean success = performCompact(session, budget);

                notifyListeners(new BudgetEvent(
                    session.getId(),
                    BudgetLevel.CRITICAL,
                    budget.usageRatio(),
                    budget.getRemaining(),
                    success ? "压缩成功" : "压缩失败"
                ));

                return success ? BudgetAction.COMPACT_SUCCESS : BudgetAction.NONE;
            }

            case SPLIT_TASK: {
                state.exhaustedHandled = true;
                List<Session> subSessions = subTaskSplitter.splitAndFork(session);

                notifyListeners(new BudgetEvent(
                    session.getId(),
                    BudgetLevel.EXHAUSTED,
                    budget.usageRatio(),
                    budget.getRemaining(),
                    "已拆分为 " + subSessions.size() + " 个子任务"
                ));

                return BudgetAction.SPLIT_TASK;
            }

            default:
                return action;
        }
    }

    /**
     * 执行上下文压缩 — 保留最近 50% 的消息，截断旧消息。
     */
    private boolean performCompact(Session session, TokenBudget budget) {
        logger.info("[BudgetHandler] 开始压缩上下文 | sessionId={}", session.getId());

        try {
            List<Message> messages = session.getMessages();
            if (messages.isEmpty()) {
                return false;
            }

            int keepCount = Math.max(5, messages.size() / 2);
            List<Message> retained = new ArrayList<>(messages.subList(
                messages.size() - keepCount, messages.size()));

            session.setMessages(retained);
            budget.reset();
            session.markCompacted();

            logger.info("[BudgetHandler] 压缩完成 | 保留 {} 条消息 (原 {} 条)",
                keepCount, messages.size());
            return true;

        } catch (Exception e) {
            logger.error("[BudgetHandler] 压缩异常", e);
            return false;
        }
    }

    // ==================== 事件通知 ====================

    public void addListener(BudgetListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(BudgetListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(BudgetEvent event) {
        for (BudgetListener listener : listeners) {
            try {
                listener.onBudgetEvent(event);
            } catch (Exception e) {
                logger.warn("[BudgetHandler] 预算事件监听器异常", e);
            }
        }
    }

    // ==================== 配置方法 ====================

    public void setAutoCompactEnabled(boolean enabled) {
        autoCompactEnabled.set(enabled);
        logger.info("[BudgetHandler] 自动压缩: {}", enabled ? "启用" : "禁用");
    }

    public boolean isAutoCompactEnabled() {
        return autoCompactEnabled.get();
    }

    public void resetSessionState(String sessionId) {
        sessionBudgetStates.remove(sessionId);
        logger.debug("[BudgetHandler] 重置会话状态: {}", sessionId);
    }

    public void clearAllStates() {
        sessionBudgetStates.clear();
        logger.info("[BudgetHandler] 已清除所有会话状态");
    }

    // ==================== 内部类 ====================

    private static class BudgetState {
        final String sessionId;
        double lastUsageRatio = 0.0;
        Instant lastUpdateAt = Instant.now();
        boolean lowHandled = false;
        boolean criticalHandled = false;
        boolean exhaustedHandled = false;

        BudgetState(String sessionId) {
            this.sessionId = sessionId;
        }

        void update(double usageRatio) {
            this.lastUsageRatio = usageRatio;
            this.lastUpdateAt = Instant.now();
        }

        void reset() {
            lowHandled = false;
            criticalHandled = false;
            exhaustedHandled = false;
        }
    }

    // ==================== 事件和枚举 ====================

    public enum BudgetLevel {
        LOW,      // 70% - 警告
        CRITICAL, // 85% - 压缩
        EXHAUSTED // 95% - 拆分
    }

    public enum BudgetAction {
        NONE,           // 无动作
        WARN,           // 警告
        COMPACT,        // 压缩
        COMPACT_SUCCESS,// 压缩成功
        SPLIT_TASK      // 拆分任务
    }

    public record BudgetEvent(
        String sessionId,
        BudgetLevel level,
        double usageRatio,
        long remainingTokens,
        String message
    ) {}

    @FunctionalInterface
    public interface BudgetListener {
        void onBudgetEvent(BudgetEvent event);
    }

    // ==================== 便捷方法 ====================

    /**
     * 创建默认处理器
     */
    public static BudgetExhaustedHandler createDefault() {
        return new BudgetExhaustedHandler(new SubTaskSplitter());
    }

    @Override
    public String toString() {
        return String.format("BudgetExhaustedHandler{low=%.0f%%, critical=%.0f%%, exhausted=%.0f%%, autoCompact=%s}",
            lowThreshold * 100, criticalThreshold * 100, exhaustedThreshold * 100, autoCompactEnabled.get());
    }
}
