package com.jwcode.core.search;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 搜索缓存
 * 
 * 内存缓存实现，支持 TTL 过期策略
 * 缓存键：query + engine
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SearchCache {
    
    private static final Logger logger = Logger.getLogger(SearchCache.class.getName());
    
    // 缓存条目
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    // 默认 TTL：5 分钟
    private Duration defaultTtl = Duration.ofMinutes(5);
    
    // 最大缓存条目数
    private int maxSize = 1000;
    
    // 清理线程
    private final ScheduledExecutorService cleanupExecutor;
    
    // 统计信息
    private long hitCount = 0;
    private long missCount = 0;
    
    public SearchCache() {
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SearchCache-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // 每 60 秒清理一次过期条目
        cleanupExecutor.scheduleWithFixedDelay(
            this::cleanup,
            60,
            60,
            TimeUnit.SECONDS
        );
    }
    
    public SearchCache(Duration defaultTtl, int maxSize) {
        this();
        this.defaultTtl = defaultTtl;
        this.maxSize = maxSize;
    }
    
    /**
     * 获取缓存的搜索结果
     * 
     * @param query 搜索查询词
     * @param engine 搜索引擎名称
     * @return 缓存的结果，如果不存在或已过期则返回 null
     */
    public List<SearchResult> get(String query, String engine) {
        String key = generateKey(query, engine);
        CacheEntry entry = cache.get(key);
        
        if (entry == null) {
            missCount++;
            return null;
        }
        
        if (entry.isExpired()) {
            cache.remove(key);
            missCount++;
            return null;
        }
        
        hitCount++;
        return entry.getResults();
    }
    
    /**
     * 缓存搜索结果
     * 
     * @param query 搜索查询词
     * @param engine 搜索引擎名称
     * @param results 搜索结果
     */
    public void put(String query, String engine, List<SearchResult> results) {
        put(query, engine, results, defaultTtl);
    }
    
    /**
     * 缓存搜索结果（指定 TTL）
     * 
     * @param query 搜索查询词
     * @param engine 搜索引擎名称
     * @param results 搜索结果
     * @param ttl 存活时间
     */
    public void put(String query, String engine, List<SearchResult> results, Duration ttl) {
        // 检查缓存是否已满
        if (cache.size() >= maxSize) {
            evictOldest();
        }
        
        String key = generateKey(query, engine);
        cache.put(key, new CacheEntry(results, ttl));
        logger.fine("缓存搜索结果: " + key);
    }
    
    /**
     * 使缓存失效
     * 
     * @param query 搜索查询词
     * @param engine 搜索引擎名称
     */
    public void invalidate(String query, String engine) {
        String key = generateKey(query, engine);
        cache.remove(key);
        logger.fine("使缓存失效: " + key);
    }
    
    /**
     * 清空所有缓存
     */
    public void clear() {
        cache.clear();
        hitCount = 0;
        missCount = 0;
        logger.info("搜索缓存已清空");
    }
    
    /**
     * 检查缓存中是否存在
     * 
     * @param query 搜索查询词
     * @param engine 搜索引擎名称
     * @return true 如果缓存存在且未过期
     */
    public boolean contains(String query, String engine) {
        String key = generateKey(query, engine);
        CacheEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        cleanup(); // 先清理过期条目
        
        long total = hitCount + missCount;
        double hitRate = total > 0 ? (double) hitCount / total : 0.0;
        
        return new CacheStats(
            cache.size(),
            hitCount,
            missCount,
            hitRate,
            maxSize,
            defaultTtl
        );
    }
    
    /**
     * 设置默认 TTL
     */
    public void setDefaultTtl(Duration ttl) {
        this.defaultTtl = ttl;
    }
    
    /**
     * 设置最大缓存大小
     */
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
    
    /**
     * 关闭缓存（停止清理线程）
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateKey(String query, String engine) {
        String normalizedQuery = query != null ? query.trim().toLowerCase() : "";
        String normalizedEngine = engine != null ? engine.trim().toLowerCase() : "duckduckgo";
        return normalizedEngine + ":" + normalizedQuery;
    }
    
    /**
     * 清理过期条目
     */
    private void cleanup() {
        int beforeSize = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = beforeSize - cache.size();
        
        if (removed > 0) {
            logger.fine("清理了 " + removed + " 个过期缓存条目");
        }
    }
    
    /**
     * 驱逐最旧的条目（当缓存满时）
     */
    private void evictOldest() {
        String oldestKey = null;
        Instant oldestTime = Instant.now();
        
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().getCreatedAt().isBefore(oldestTime)) {
                oldestTime = entry.getValue().getCreatedAt();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            cache.remove(oldestKey);
            logger.fine("驱逐最旧缓存条目: " + oldestKey);
        }
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final List<SearchResult> results;
        private final Instant createdAt;
        private final Duration ttl;
        
        CacheEntry(List<SearchResult> results, Duration ttl) {
            this.results = results;
            this.createdAt = Instant.now();
            this.ttl = ttl;
        }
        
        List<SearchResult> getResults() {
            return results;
        }
        
        Instant getCreatedAt() {
            return createdAt;
        }
        
        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(ttl));
        }
    }
    
    /**
     * 缓存统计信息
     */
    public record CacheStats(
        int size,
        long hits,
        long misses,
        double hitRate,
        int maxSize,
        Duration ttl
    ) {
        @Override
        public String toString() {
            return String.format(
                "CacheStats{size=%d, hits=%d, misses=%d, hitRate=%.2f%%, maxSize=%d, ttl=%s}",
                size, hits, misses, hitRate * 100, maxSize, ttl
            );
        }
    }
}
