package com.jwcode.core.llm;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 预算 — 替代粗暴的 {@code maxIterations}，让 AI 自主规划资源。
 *
 * <p>核心思想：不是"你最多能调用我 50 次"，而是"这是你的 token 预算，自己规划"。</p>
 *
 * <p>预算紧张时，自动生成建议注入到 system prompt，引导 AI 高效使用上下文。</p>
 */
public class TokenBudget {

    private final long totalBudget;
    private final AtomicLong usedPromptTokens = new AtomicLong(0);
    private final AtomicLong usedCompletionTokens = new AtomicLong(0);
    private final AtomicLong reservedTokens = new AtomicLong(0);
    private double lastAdvisedRatio = 0.0;

    public TokenBudget(long totalBudget) {
        if (totalBudget <= 0) {
            throw new IllegalArgumentException("Token budget must be positive");
        }
        this.totalBudget = totalBudget;
    }

    public static TokenBudget unlimited() {
        return new TokenBudget(Long.MAX_VALUE);
    }

    public static TokenBudget of(long totalBudget) {
        return new TokenBudget(totalBudget);
    }

    // ==================== 消费与预留 ====================

    /**
     * 消费 token（从 LLM 响应中记录）
     */
    public void consume(int promptTokens, int completionTokens) {
        usedPromptTokens.addAndGet(promptTokens);
        usedCompletionTokens.addAndGet(completionTokens);
    }

    /**
     * 预留 token（为即将执行的关键操作预留预算）
     */
    public void reserve(long tokens) {
        reservedTokens.addAndGet(tokens);
    }

    /**
     * 释放预留的 token
     */
    public void release(long tokens) {
        reservedTokens.updateAndGet(current -> Math.max(0, current - tokens));
    }

    /**
     * 重置已使用的 token 计数（通常在上下文压缩后调用）
     */
    public void reset() {
        usedPromptTokens.set(0);
        usedCompletionTokens.set(0);
        lastAdvisedRatio = 0.0;
    }

    // ==================== 查询状态 ====================

    public long getTotalBudget() {
        return totalBudget;
    }

    public long getUsedPromptTokens() {
        return usedPromptTokens.get();
    }

    public long getUsedCompletionTokens() {
        return usedCompletionTokens.get();
    }

    public long getUsedTotal() {
        return usedPromptTokens.get() + usedCompletionTokens.get();
    }

    public long getReservedTokens() {
        return reservedTokens.get();
    }

    public long getRemaining() {
        return Math.max(0, totalBudget - getUsedTotal() - reservedTokens.get());
    }

    public boolean isExhausted() {
        return getUsedTotal() + reservedTokens.get() >= totalBudget;
    }

    public double usageRatio() {
        return (double) getUsedTotal() / totalBudget;
    }

    public double projectedUsageRatio(int estimatedRemainingCalls, int avgTokensPerCall) {
        long projected = getUsedTotal() + (long) estimatedRemainingCalls * avgTokensPerCall;
        return Math.min(1.0, (double) projected / totalBudget);
    }

    // ==================== 分级建议 ====================

    /**
     * 根据预算消耗率生成 system prompt 建议文本。
     * 仅在使用率首次跨越预设阈值时返回建议，避免每次迭代重复提示。
     */
    public String toPromptAdvice() {
        double ratio = usageRatio();
        double[] thresholds = {0.5, 0.7, 0.85, 0.95};

        // 找到当前比率对应的最高阈值
        double currentThreshold = 0.0;
        for (double t : thresholds) {
            if (ratio >= t) {
                currentThreshold = t;
            }
        }

        // 如果当前阈值没有超过上次报告的阈值，不重复提示
        if (currentThreshold <= lastAdvisedRatio) {
            return "";
        }

        lastAdvisedRatio = currentThreshold;

        if (currentThreshold == 0.5) {
            return String.format(
                "[Token Budget] Used %.0f%% (%,d / %,d). Monitor usage.",
                ratio * 100, getUsedTotal(), totalBudget
            );
        }
        if (currentThreshold == 0.7) {
            return String.format(
                "[Token Budget] Used %.0f%% (%,d / %,d). Recommendations:\n" +
                "1. Use /compact to compress context when possible.\n" +
                "2. Prefer Grep over large FileRead operations.\n" +
                "3. Batch related file reads into single requests.",
                ratio * 100, getUsedTotal(), totalBudget
            );
        }
        if (currentThreshold == 0.85) {
            return String.format(
                "[Token Budget] CRITICAL — Used %.0f%% (%,d / %,d). Immediate actions:\n" +
                "1. COMPRESS context now with /compact.\n" +
                "2. STOP reading large files; use Grep for targeted searches.\n" +
                "3. MERGE multiple small edits into single FileEditTool calls.\n" +
                "4. Finish the task as soon as core objectives are met.",
                ratio * 100, getUsedTotal(), totalBudget
            );
        }
        return String.format(
            "[Token Budget] EXHAUSTED — Used %.0f%% (%,d / %,d).\n" +
            "You MUST finish immediately. Add [FINISH] marker now. No further tool calls.",
            ratio * 100, getUsedTotal(), totalBudget
        );
    }

    @Override
    public String toString() {
        return String.format("TokenBudget{total=%,d, used=%,d (prompt=%,d, completion=%,d), reserved=%,d, ratio=%.1f%%}",
            totalBudget, getUsedTotal(), getUsedPromptTokens(), getUsedCompletionTokens(),
            getReservedTokens(), usageRatio() * 100);
    }
}
