package com.jwcode.core.config;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * PromptSectionCache — 系统提示段落级缓存（对标 Claude Code systemPromptSections.ts）。
 *
 * <p>将 system prompt 拆分为独立章节，每个章节关联一个 versionToken。
 * 只有 versionToken 变化时才重新计算，避免每次组装都全量重建。</p>
 *
 * <h3>缓存的分段</h3>
 * <ul>
 *   <li>core — core.md 文件 mtime</li>
 *   <li>rules — rules.md 文件 mtime</li>
 *   <li>claudeMd — claudeMdContents 列表 hash</li>
 *   <li>protocols — protocols/ 目录最大 mtime</li>
 *   <li>tools — toolRegistry hash（工具数 + 名称列表）</li>
 *   <li>memoryTypes — 静态常量（永不变）</li>
 *   <li>memoryManifest — memoryManifest map hash</li>
 *   <li>environment — 30s TTL</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>线程安全（ConcurrentHashMap）</li>
 *   <li>惰性计算 — 只有被请求的章节才重建</li>
 *   <li>全量失效 — invalidateAll() 清除全部缓存</li>
 *   <li>不可变内容 — 返回的 content 是缓存引用，调用方不应修改</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 3.1.0
 */
public class PromptSectionCache {

    private static final Logger logger = Logger.getLogger(PromptSectionCache.class.getName());

    /** 环境信息缓存 TTL（毫秒） */
    private static final long ENVIRONMENT_TTL_MS = 30_000;

    private final Map<String, SectionEntry> cache = new ConcurrentHashMap<>();

    /**
     * 缓存条目。
     */
    private static class SectionEntry {
        final String content;
        final long cachedAt;

        SectionEntry(String content) {
            this.content = content;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return ttlMs > 0 && (System.currentTimeMillis() - cachedAt) > ttlMs;
        }
    }

    /**
     * 获取或计算一个提示章节。
     *
     * @param sectionKey   章节键（如 "core", "tools", "environment"）
     * @param versionToken 版本标识（变化时触发重建，如文件 mtime 或 hash）
     * @param ttlMs        TTL（毫秒），0 表示永不过期
     * @param computeFn    计算函数
     * @return 缓存或新计算的章节内容
     */
    public String getOrCompute(String sectionKey, String versionToken,
                                long ttlMs, Supplier<String> computeFn) {
        String cacheKey = sectionKey + "\0" + (versionToken != null ? versionToken : "");
        SectionEntry cached = cache.get(cacheKey);

        if (cached != null && !cached.isExpired(ttlMs)) {
            logger.finest("[PromptCache] HIT: " + sectionKey);
            return cached.content;
        }

        logger.fine("[PromptCache] MISS: " + sectionKey + " (recomputing)");
        String content = computeFn.get();
        cache.put(cacheKey, new SectionEntry(content));
        return content;
    }

    /**
     * 失效指定章节的所有缓存条目。
     */
    public void invalidate(String sectionKey) {
        String prefix = sectionKey + "\0";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
        logger.fine("[PromptCache] 失效: " + sectionKey);
    }

    /**
     * 失效全部缓存。
     */
    public void invalidateAll() {
        int count = cache.size();
        cache.clear();
        logger.fine("[PromptCache] 全部失效 (" + count + " 条目)");
    }

    /**
     * 环境信息（30s TTL）。
     */
    public String getEnvironment(String versionToken, Supplier<String> computeFn) {
        return getOrCompute("environment", versionToken, ENVIRONMENT_TTL_MS, computeFn);
    }

    /**
     * 获取缓存统计。
     */
    public CacheStats getStats() {
        return new CacheStats(cache.size());
    }

    public record CacheStats(int cachedSections) {}
}
