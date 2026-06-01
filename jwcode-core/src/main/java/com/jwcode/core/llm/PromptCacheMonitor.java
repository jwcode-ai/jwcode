package com.jwcode.core.llm;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.logging.Logger;

/**
 * PromptCacheMonitor — Prompt 缓存断裂检测器（对标 Claude Code promptCacheBreakDetection.ts）。
 *
 * <p>当 system prompt 变化或长时间闲置后，Anthropic API 的 prompt cache 会断裂，
 * 重新计算成本高。此监控器使用三种启发式检测缓存断裂事件。</p>
 *
 * <h3>三种检测启发式</h3>
 * <ul>
 *   <li><b>systemPromptChanged</b> — SHA-256 hash 对比（置信度 0.9）</li>
 *   <li><b>timeSinceLastMessage</b> — 超过 30 分钟闲置（置信度 0.6）</li>
 *   <li><b>tokenDelta</b> — Token 数变化 > 50%（置信度 0.5）</li>
 * </ul>
 *
 * <p>两个及以上启发式同时触发 → 综合置信度 ≥ 1.0。</p>
 *
 * @author JWCode Team
 * @since 3.1.0
 */
public class PromptCacheMonitor {

    private static final Logger logger = Logger.getLogger(PromptCacheMonitor.class.getName());

    /** 超过此时间（毫秒）视为闲置断裂 */
    private static final long IDLE_BREAK_THRESHOLD_MS = 30 * 60 * 1000; // 30 分钟

    /** Token 变化超过此比例视为断裂 */
    private static final double TOKEN_DELTA_THRESHOLD = 0.5;

    private String lastPromptHash;
    private long lastTokenCount = -1;
    private long lastAssistantMessageTime = 0;
    private long lastCacheBreakTime = 0;

    /**
     * 缓存断裂事件。
     */
    public record CacheBreakEvent(
        boolean broken,
        double confidence,
        String reason,
        String hashBefore,
        String hashAfter,
        long timeSinceLastMessageMs
    ) {
        public static final CacheBreakEvent NO_BREAK = new CacheBreakEvent(
            false, 0.0, "缓存有效", null, null, 0
        );
    }

    /**
     * 检测缓存是否断裂。
     *
     * @param currentPrompt  当前 system prompt 完整文本
     * @param currentTokens  当前 token 数量
     * @param currentTimeMs  当前时间戳（毫秒）
     * @return 缓存断裂事件
     */
    public CacheBreakEvent check(String currentPrompt, long currentTokens, long currentTimeMs) {
        String currentHash = sha256(currentPrompt);
        int triggers = 0;
        double confidence = 0.0;
        StringBuilder reason = new StringBuilder();

        // 启发式 1: system prompt 变化
        boolean promptChanged = lastPromptHash != null && !lastPromptHash.equals(currentHash);
        if (promptChanged) {
            triggers++;
            confidence += 0.9;
            reason.append("systemPromptChanged");
        }

        // 启发式 2: 闲置超时
        long idleTime = 0;
        if (lastAssistantMessageTime > 0) {
            idleTime = currentTimeMs - lastAssistantMessageTime;
        }
        boolean idleBreak = idleTime > IDLE_BREAK_THRESHOLD_MS;
        if (idleBreak) {
            triggers++;
            confidence += 0.6;
            if (!reason.isEmpty()) reason.append(", ");
            reason.append("timeSinceLastMessage(").append(idleTime / 1000).append("s)");
        }

        // 启发式 3: Token 变化显著
        boolean tokenDelta = false;
        if (lastTokenCount > 0 && currentTokens > 0) {
            double delta = Math.abs((double)(currentTokens - lastTokenCount) / lastTokenCount);
            tokenDelta = delta > TOKEN_DELTA_THRESHOLD;
            if (tokenDelta) {
                triggers++;
                confidence += 0.5;
                if (!reason.isEmpty()) reason.append(", ");
                reason.append("tokenDelta(").append(String.format("%.0f%%", delta * 100)).append(")");
            }
        }

        // 两个及以上触发 → cap 置信度为 1.0
        if (triggers >= 2) {
            confidence = 1.0;
        }

        boolean broken = triggers > 0;
        CacheBreakEvent event = new CacheBreakEvent(
            broken, Math.min(confidence, 1.0),
            broken ? reason.toString() : "缓存有效",
            lastPromptHash, broken ? currentHash : null, idleTime
        );

        // 更新状态
        lastPromptHash = currentHash;
        lastTokenCount = currentTokens;

        if (broken) {
            lastCacheBreakTime = currentTimeMs;
            logger.fine("[CacheMonitor] 缓存断裂检测: " + reason + " (置信度=" + confidence + ")");
        }

        return event;
    }

    /**
     * 记录 assistant 消息时间戳。
     */
    public void recordAssistantMessage(long timestampMs) {
        this.lastAssistantMessageTime = timestampMs;
    }

    /**
     * 记录 assistant 消息时间戳（使用当前时间）。
     */
    public void recordAssistantMessage() {
        recordAssistantMessage(System.currentTimeMillis());
    }

    /**
     * 获取上次缓存断裂时间。
     */
    public long getLastCacheBreakTime() {
        return lastCacheBreakTime;
    }

    /**
     * 重置所有追踪状态。
     */
    public void reset() {
        lastPromptHash = null;
        lastTokenCount = -1;
        lastAssistantMessageTime = 0;
        lastCacheBreakTime = 0;
    }

    // ==================== 工具方法 ====================

    private String sha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
