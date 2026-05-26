package com.jwcode.core.llm;

import com.jwcode.core.model.Message;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Token 预算 — 替代粗暴的 {@code maxIterations}，让 AI 自主规划资源。
 *
 * <p>核心思想：不是"你最多能调用我 50 次"，而是"这是你的 token 预算，自己规划"。</p>
 *
 * <p>预算紧张时，自动生成建议注入到 system prompt，引导 AI 高效使用上下文。</p>
 */
public class TokenBudget {

    private static final Logger logger = Logger.getLogger(TokenBudget.class.getName());

    private final long totalBudget;
    private final AtomicLong usedPromptTokens = new AtomicLong(0);
    private final AtomicLong usedCompletionTokens = new AtomicLong(0);
    private final AtomicLong reservedTokens = new AtomicLong(0);
    // volatile 确保跨线程可见性，避免重复/遗漏告警
    private volatile double lastAdvisedRatio = 0.0;

    // 消息 token 估算常量（可配置，见 estimateMessageTokens）
    static final double EN_TOKENS_PER_WORD = 1.3;
    static final double ZH_TOKENS_PER_CHAR = 1.5;
    static final double JSON_TOKENS_PER_CHAR = 0.25;
    static final long TOKENS_PER_MESSAGE_OVERHEAD = 4; // role + formatting overhead

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
     * 释放指定数量的 token（压缩后调用，将释放的 token 归还到预算池）
     * 
     * <p>核心逻辑：按 prompt/completion 比例分摊释放。
     * 同时重置告警阈值，允许重新触发分级告警。</p>
     *
     * @param tokens 要释放的 token 数量
     */
    public void releaseTokens(long tokens) {
        if (tokens <= 0) return;
        long totalUsed = getUsedTotal();
        if (totalUsed <= 0) return;
        
        // 按 prompt/completion 比例分摊释放（带溢出保护）
        long promptUsed = usedPromptTokens.get();
        long completionUsed = usedCompletionTokens.get();
        // 使用 double 除法 + Math.round 避免整数截断误差
        long promptToRelease = Math.min(
            Math.round((double) tokens * promptUsed / totalUsed), promptUsed);
        long completionToRelease = Math.min(tokens - promptToRelease, completionUsed);
        
        usedPromptTokens.addAndGet(-promptToRelease);
        usedCompletionTokens.addAndGet(-completionToRelease);
        
        // 重置告警阈值，允许重新触发分级告警
        lastAdvisedRatio = 0.0;
        
        logger.fine("[TokenBudget] Released " + tokens + " tokens (prompt=" + promptToRelease 
            + ", completion=" + completionToRelease + "), remaining=" + getRemaining());
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

    /**
     * 预估一条消息的 token 数量。
     * <p>启发式策略（按优先级）：
     * <ol>
     *   <li>英文文本：~1.3 tokens/词（OpenAI 典型比率）</li>
     *   <li>中文文本：~1.5 tokens/字符</li>
     *   <li>JSON/代码：~0.25 tokens/字符（tokenizer 对结构化文本更高效）</li>
     *   <li>额外 +4 tokens 开销（role + 消息边界）</li>
     * </ol>
     * 误差通常在 ±15% 以内。如需精确值，应使用 LLM API 返回的 usage.prompt_tokens。
     * </p>
     */
    public static long estimateMessageTokens(com.jwcode.core.model.Message msg) {
        if (msg == null) return 0;
        String text = msg.getTextContent();
        if (text == null || text.isEmpty()) {
            return TOKENS_PER_MESSAGE_OVERHEAD;
        }

        int length = text.length();
        long zhChars = text.codePoints()
            .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
            .count();
        int nonZhChars = length - (int) zhChars;

        // 根据内容类型选择估算系数
        boolean looksLikeJson = text.trim().startsWith("{") || text.trim().startsWith("[");
        double tokensForContent;
        if (looksLikeJson) {
            tokensForContent = length * JSON_TOKENS_PER_CHAR;
        } else {
            // 混合文本：中文按字符，英文按词
            int engWords = nonZhChars > 0 ? nonZhChars / 5 : 0; // 粗略：平均 5 字符/词
            tokensForContent = (zhChars * ZH_TOKENS_PER_CHAR) + (engWords * EN_TOKENS_PER_WORD);
        }

        return TOKENS_PER_MESSAGE_OVERHEAD + (long) Math.ceil(tokensForContent);
    }

    /**
     * 预估消息列表的 token 总数。
     */
    public static long estimateMessagesTokens(List<com.jwcode.core.model.Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        long total = 0;
        for (com.jwcode.core.model.Message msg : messages) {
            total = Math.addExact(total, estimateMessageTokens(msg));
        }
        return total;
    }

    public double projectedUsageRatio(int estimatedRemainingCalls, long avgTokensPerCall) {
        long projected;
        try {
            projected = Math.addExact(getUsedTotal(),
                Math.multiplyExact((long) estimatedRemainingCalls, avgTokensPerCall));
        } catch (ArithmeticException e) {
            // 溢出 = 预算必定不够 → 返回 1.0
            return 1.0;
        }
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
