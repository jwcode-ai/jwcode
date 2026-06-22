package com.jwcode.core.llm;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * LLMQueryEnginePool — 轻量级对象池，按 sessionId 缓存 LLMQueryEngine 实例。
 *
 * <p><b>设计意图</b>：消除 {@link Workflow Runtime} 中每次子任务
 * 都 new LLMQueryEngine 的 GC 压力（构造器包含 TokenBudget、ObsPipeline、
 * ContextWindowManager 等多重对象分配）。</p>
 *
 * <p><b>池策略</b>：
 * <ul>
 *   <li>最大池大小 16，超出时 LRU 淘汰最久未使用的实例</li>
 *   <li>按 sessionId 作为 key，同一会话复用同一引擎</li>
 *   <li>会话关闭时调用 {@link #evict(String)} 归还资源</li>
 *   <li>线程安全（ConcurrentHashMap + synchronized 淘汰）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   LLMQueryEngine engine = pool.borrow(sessionId, session, llmService, toolExecutor, toolRegistry, agentRegistry);
 *   try {
 *       engine.query(prompt).get();
 *   } finally {
 *       // 通常不归还（复用），仅在会话关闭时 evict
 *   }
 * </pre>
 */
public class LLMQueryEnginePool {

    private static final Logger logger = Logger.getLogger(LLMQueryEnginePool.class.getName());

    /** 最大池容量 */
    private static final int MAX_POOL_SIZE = 16;

    /** JwcodeConfig for EngineConfig creation */
    private JwcodeConfig jwcodeConfig;

    /** 池存储：sessionId → PoolEntry */
    private final ConcurrentHashMap<String, PoolEntry> pool = new ConcurrentHashMap<>();

    private static class PoolEntry {
        final LLMQueryEngine engine;
        volatile long lastAccessTime;

        PoolEntry(LLMQueryEngine engine) {
            this.engine = engine;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void touch() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    /**
     * 从池中借取（如果已有则复用，否则新建）。
     *
     * @return 可复用的 LLMQueryEngine 实例
     */
    public LLMQueryEngine borrow(String sessionId,
                                  Session session,
                                  LLMService llmService,
                                  ToolExecutor toolExecutor,
                                  ToolRegistry toolRegistry,
                                  AgentRegistry agentRegistry) {
        // 先尝试命中缓存
        PoolEntry entry = pool.get(sessionId);
        if (entry != null) {
            entry.touch();
            logger.fine("[LLMQueryEnginePool] Reusing engine for session: " + sessionId);
            return entry.engine;
        }

        // 缓存未命中 → 新建前先检查容量
        if (pool.size() >= MAX_POOL_SIZE) {
            evictLRU();
        }

        // 新建引擎
        LLMQueryEngine engine = LLMQueryEngine.builder()
            .session(session)
            .llmService(llmService)
            .toolExecutor(toolExecutor)
            .toolRegistry(toolRegistry)
            .agentRegistry(agentRegistry)
            .config(jwcodeConfig != null ? LLMQueryEngine.EngineConfig.fromJwcodeConfig(jwcodeConfig) : LLMQueryEngine.EngineConfig.defaultConfig())
            .build();

        pool.put(sessionId, new PoolEntry(engine));
        logger.fine("[LLMQueryEnginePool] Created new engine for session: " + sessionId
            + " (pool size: " + pool.size() + "/" + MAX_POOL_SIZE + ")");
        return engine;
    }

    /**
     * 淘汰最久未使用的条目（LRU）。
     */
    private synchronized void evictLRU() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, PoolEntry> e : pool.entrySet()) {
            if (e.getValue().lastAccessTime < oldestTime) {
                oldestTime = e.getValue().lastAccessTime;
                oldestKey = e.getKey();
            }
        }

        if (oldestKey != null) {
            evict(oldestKey);
            logger.info("[LLMQueryEnginePool] Evicted LRU engine: " + oldestKey
                + " (pool size now: " + pool.size() + ")");
        }
    }

    /**
     * 显式移除指定会话的引擎实例。
     */
    public void evict(String sessionId) {
        pool.remove(sessionId);
        logger.fine("[LLMQueryEnginePool] Evicted engine for session: " + sessionId);
    }

    /**
     * 获取当前池大小（用于监控）。
     */
    public void setJwcodeConfig(JwcodeConfig config) {
        this.jwcodeConfig = config;
    }

    public int size() {
        return pool.size();
    }

    /**
     * 清空池（通常在系统关闭时调用）。
     */
    public void clear() {
        pool.clear();
        logger.info("[LLMQueryEnginePool] Pool cleared");
    }
}
