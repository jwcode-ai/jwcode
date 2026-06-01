package com.jwcode.core.compact;

import com.jwcode.core.agent.CompactorAgent;
import com.jwcode.core.agent.CompactorTrigger;
import com.jwcode.core.llm.TokenBudget;
import com.jwcode.core.model.Message;

import java.util.List;
import java.util.logging.Logger;

/**
 * GraduatedEscalationPipeline — 分级升级压缩管道（对标 Claude Code graduatedEscalationPipeline）。
 *
 * <p>7 级升级链，根据剩余 Token 比例自动升级压缩策略，
 * 避免一步跳到激进压缩造成信息丢失。</p>
 *
 * <h3>升级级别</h3>
 * <ul>
 *   <li><b>NONE</b> (>20K 剩余) — 无操作</li>
 *   <li><b>WARNING</b> (20K) — 日志建议</li>
 *   <li><b>ERROR</b> (15K) — 注入系统警告</li>
 *   <li><b>AUTO</b> (10K) — 自动触发 MINIMAL</li>
 *   <li><b>COMPACT</b> (5K) — 自动触发 SMART</li>
 *   <li><b>AGGRESSIVE</b> (3K) — 自动触发 AGGRESSIVE</li>
 *   <li><b>RESET</b> (0) — 触发 Context Reset</li>
 * </ul>
 *
 * <h3>冷却期</h3>
 * <p>压缩后 60s 冷却，防止频繁压缩抖动。</p>
 *
 * @author JWCode Team
 * @since 3.1.0
 */
public class GraduatedEscalationPipeline {

    private static final Logger logger = Logger.getLogger(GraduatedEscalationPipeline.class.getName());

    public enum EscalationLevel {
        NONE(20_000, "无操作"),
        WARNING(20_000, "建议压缩"),
        ERROR(15_000, "系统警告"),
        AUTO(10_000, "自动 MINIMAL 压缩"),
        COMPACT(5_000, "自动 SMART 压缩"),
        AGGRESSIVE(3_000, "自动 AGGRESSIVE 压缩"),
        RESET(0, "Context Reset");

        /** 触发此级别的剩余 token 阈值 */
        public final long remainingTokenThreshold;
        /** 人类可读的描述 */
        public final String description;

        EscalationLevel(long remainingTokenThreshold, String description) {
            this.remainingTokenThreshold = remainingTokenThreshold;
            this.description = description;
        }

        /**
         * 获取此级别的下一级（升级方向）。
         */
        public EscalationLevel next() {
            EscalationLevel[] values = values();
            int idx = this.ordinal() + 1;
            return idx < values.length ? values[idx] : RESET;
        }
    }

    /** 两次压缩之间的最小间隔（毫秒） */
    private static final long COOLDOWN_MS = 60_000;
    /** 最低压缩消息数 */
    private static final int MIN_MESSAGES_FOR_COMPACT = 10;

    private final CompactorAgent compactorAgent;
    private volatile long lastCompactionTime = 0;
    private volatile EscalationLevel currentLevel = EscalationLevel.NONE;

    public GraduatedEscalationPipeline(CompactorAgent compactorAgent) {
        this.compactorAgent = compactorAgent;
    }

    /**
     * 根据剩余 token 评估应升级到的级别。
     *
     * @param budget Token 预算
     * @return 当前应触发的升级级别
     */
    public EscalationLevel evaluate(TokenBudget budget) {
        if (budget == null) return EscalationLevel.NONE;

        long remaining = budget.getRemaining();
        for (EscalationLevel level : EscalationLevel.values()) {
            if (remaining >= level.remainingTokenThreshold) {
                return level;
            }
        }
        return EscalationLevel.RESET;
    }

    /**
     * 执行完整评估+压缩管道。
     *
     * @param messages 当前消息列表
     * @param budget   Token 预算
     * @return 升级结果
     */
    public EscalationResult run(List<Message> messages, TokenBudget budget) {
        EscalationLevel level = evaluate(budget);
        currentLevel = level;

        if (level == EscalationLevel.NONE) {
            return EscalationResult.none();
        }

        if (level == EscalationLevel.WARNING || level == EscalationLevel.ERROR) {
            logger.fine("[EscalationPipeline] " + level.description
                + ": 剩余 " + budget.getRemaining() + " tokens");
            return EscalationResult.warning(level);
        }

        // AUTO 及以上级别：需要实际压缩
        if (isInCooldown()) {
            logger.fine("[EscalationPipeline] 冷却中，跳过压缩 (上次压缩 "
                + (System.currentTimeMillis() - lastCompactionTime) / 1000 + "s 前)");
            return new EscalationResult(level, null, false, 0, null, "冷却中，跳过压缩");
        }

        if (messages.size() < MIN_MESSAGES_FOR_COMPACT) {
            return new EscalationResult(level, null, false, 0, null, "消息数不足，跳过压缩");
        }

        CompactorTrigger.Strategy strategy = levelToStrategy(level);
        int originalSize = messages.size();

        CompactorAgent.CompactionRequest request = new CompactorAgent.CompactionRequest(
            messages, strategy, List.of(), budget.getTotalBudget()
        );
        CompactorAgent.CompactionResult result = compactorAgent.compact(request);

        lastCompactionTime = System.currentTimeMillis();

        String msgCount = originalSize + " -> " + result.getCompactedSize() + " 条消息";
        return new EscalationResult(
            level, strategy, true,
            result.getTokensSaved(), msgCount,
            result.getSummary()
        );
    }

    /**
     * 压缩后重新评估 — 若仍不满足则继续升级。
     */
    public EscalationResult runWithReevaluation(List<Message> messages, TokenBudget budget, int maxRounds) {
        EscalationResult lastResult = EscalationResult.none();
        List<Message> current = messages;

        for (int round = 0; round < maxRounds; round++) {
            EscalationResult result = run(current, budget);
            if (!result.compacted()) break;

            lastResult = result;
            current = compactorAgent.compact(
                new CompactorAgent.CompactionRequest(
                    messages, levelToStrategy(evaluate(budget)),
                    List.of(), budget.getTotalBudget()
                )
            ).getCompactedMessages();

            // 检查是否还需要继续
            EscalationLevel newLevel = evaluate(budget);
            if (newLevel.ordinal() <= EscalationLevel.ERROR.ordinal()) break;
        }

        return lastResult;
    }

    /**
     * 升级级别 → 压缩策略映射。
     */
    private CompactorTrigger.Strategy levelToStrategy(EscalationLevel level) {
        return switch (level) {
            case AUTO       -> CompactorTrigger.Strategy.MINIMAL;
            case COMPACT    -> CompactorTrigger.Strategy.SMART;
            case AGGRESSIVE -> CompactorTrigger.Strategy.AGGRESSIVE;
            case RESET      -> CompactorTrigger.Strategy.RESET;
            default         -> CompactorTrigger.Strategy.AICL_PRIORITY;
        };
    }

    private boolean isInCooldown() {
        return System.currentTimeMillis() - lastCompactionTime < COOLDOWN_MS;
    }

    public EscalationLevel getCurrentLevel() { return currentLevel; }

    /** 重置冷却计时器（用于手动触发等场景） */
    public void resetCooldown() { lastCompactionTime = 0; }
}
