package com.jwcode.core.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * 提示缓存优化器 — 最大化提示缓存命中率。
 *
 * <p>参考 Claude Code 的 prompt caching 策略：
 * <ul>
 *   <li>Cache-aware message ordering — 不变部分在前（系统提示等）</li>
 *   <li>Cache breakpoint detection — SHA-256 变更检测</li>
 *   <li>Cache warming — 空闲时预热缓存</li>
 *   <li>Cache hit rate metrics — 实时监控缓存命中率</li>
 * </ul>
 */
public class PromptCacheOptimizer {
    private static final Logger logger = Logger.getLogger(PromptCacheOptimizer.class.getName());

    /** 缓存策略 */
    public enum CacheStrategy {
        AGGRESSIVE,   // 尽可能多地缓存
        CONSERVATIVE, // 仅缓存系统提示
        ADAPTIVE      // 根据命中率动态调整（默认）
    }

    /** 缓存段 */
    public record CacheSegment(
        String id,
        String content,
        String sha256,
        long timestamp,
        int priority  // 越高的优先级越靠前
    ) {}

    /** 缓存统计 */
    public static class CacheStats {
        private long totalRequests = 0;
        private long cacheHits = 0;
        private long cacheMisses = 0;
        private long tokensSaved = 0;
        private Instant lastBreak = null;
        private String lastBreakReason = null;

        public synchronized void recordHit(long tokens) {
            cacheHits++;
            tokensSaved += tokens;
            totalRequests++;
            logger.fine("[PromptCache] HIT (saved " + tokens + " tokens)");
        }

        public synchronized void recordMiss(String reason) {
            cacheMisses++;
            lastBreak = Instant.now();
            lastBreakReason = reason;
            totalRequests++;
            logger.fine("[PromptCache] MISS: " + reason);
        }

        public synchronized double getHitRate() {
            return totalRequests == 0 ? 0 : (double) cacheHits / totalRequests;
        }

        public synchronized long getTokensSaved() { return tokensSaved; }
        public synchronized long getTotalRequests() { return totalRequests; }
        public synchronized Instant getLastBreak() { return lastBreak; }
        public synchronized String getLastBreakReason() { return lastBreakReason; }

        public synchronized Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("hit_rate", String.format("%.1f%%", getHitRate() * 100));
            map.put("hits", cacheHits);
            map.put("misses", cacheMisses);
            map.put("tokens_saved", tokensSaved);
            map.put("last_break", lastBreak != null ? lastBreak.toString() : "N/A");
            map.put("last_break_reason", lastBreakReason != null ? lastBreakReason : "N/A");
            return map;
        }
    }

    private final CacheStats stats = new CacheStats();
    private CacheStrategy strategy = CacheStrategy.ADAPTIVE;
    private final Map<String, CacheSegment> cachedSegments = new LinkedHashMap<>();
    private String lastSystemPromptHash;

    // 缓存断点检测阈值
    private static final int MAX_CACHEABLE_TOKENS = 100_000;
    private static final double MIN_HIT_RATE_FOR_AGGRESSIVE = 0.7;

    /**
     * 计算 SHA-256 哈希。
     */
    public static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(content.hashCode());
        }
    }

    /**
     * 优化消息顺序以最大化缓存命中率。
     *
     * <p>原则：将不变的内容（系统提示、工具定义）放在变化的内容（对话历史）之前。
     */
    public List<LLMMessage> optimizeMessageOrder(List<LLMMessage> messages) {
        if (messages == null || messages.size() < 2) return messages;

        List<LLMMessage> result = new ArrayList<>();

        // 1. System prompt 始终在最前
        for (LLMMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                result.add(msg);
            }
        }

        // 2. 工具定义
        for (LLMMessage msg : messages) {
            if ("tool_definition".equals(msg.getRole())) {
                result.add(msg);
            }
        }

        // 3. 早期上下文（前 5 轮对话）— 相对稳定
        List<LLMMessage> conversation = new ArrayList<>();
        for (LLMMessage msg : messages) {
            if (!"system".equals(msg.getRole()) && !"tool_definition".equals(msg.getRole())) {
                conversation.add(msg);
            }
        }

        // 4. 全部对话内容
        result.addAll(conversation);

        return result;
    }

    /**
     * 检测到系统提示变化时的处理。
     */
    public boolean detectSystemPromptChange(String newSystemPrompt) {
        String newHash = sha256(newSystemPrompt);
        if (lastSystemPromptHash != null && !lastSystemPromptHash.equals(newHash)) {
            stats.recordMiss("system_prompt_changed");
            lastSystemPromptHash = newHash;
            return true; // 缓存已失效
        }
        lastSystemPromptHash = newHash;
        return false;
    }

    /**
     * 记录缓存命中。
     */
    public void recordHit(long estimatedSavedTokens) {
        stats.recordHit(estimatedSavedTokens);
        adaptStrategy();
    }

    /**
     * 记录缓存未命中。
     */
    public void recordMiss(String reason) {
        stats.recordMiss(reason);
        adaptStrategy();
    }

    /**
     * 动态调整缓存策略。
     */
    private void adaptStrategy() {
        if (strategy == CacheStrategy.ADAPTIVE) {
            double hitRate = stats.getHitRate();
            if (hitRate < MIN_HIT_RATE_FOR_AGGRESSIVE) {
                // 命中率低，切换到保守策略，减少不必要的缓存开销
                strategy = CacheStrategy.CONSERVATIVE;
                logger.info("[PromptCache] 切换到 CONSERVATIVE 策略 (hit_rate="
                    + String.format("%.1f%%", hitRate * 100) + ")");
            }
        }
    }

    /**
     * 估算可缓存的 token 数量。
     * 粗略估算: 4 字符 ≈ 1 token
     */
    public static long estimateTokens(String content) {
        if (content == null || content.isEmpty()) return 0;
        return Math.round(content.length() / 4.0);
    }

    /**
     * 获取可缓存的消息前缀（系统提示 + 工具定义等不变部分）。
     */
    public int findCacheablePrefixLength(List<LLMMessage> messages) {
        int cacheableTokens = 0;
        int count = 0;

        for (LLMMessage msg : messages) {
            if ("system".equals(msg.getRole()) || "tool_definition".equals(msg.getRole())) {
                cacheableTokens += estimateTokens(msg.getContent());
                count++;
            } else {
                break; // 遇到对话内容就停止
            }
        }

        return cacheableTokens > MAX_CACHEABLE_TOKENS ? count - 1 : count;
    }

    // ==== Getters ====

    public CacheStats getStats() { return stats; }
    public CacheStrategy getStrategy() { return strategy; }
    public void setStrategy(CacheStrategy s) { this.strategy = s; }

    public Map<String, Object> getCacheReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("strategy", strategy.name());
        report.putAll(stats.toMap());
        report.put("cached_segments", cachedSegments.size());
        return report;
    }
}
