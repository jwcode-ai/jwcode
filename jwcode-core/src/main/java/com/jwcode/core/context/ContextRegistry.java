package com.jwcode.core.context;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * ContextRegistry — 上下文源注册与发现中心。
 *
 * <p>维持一个 Location 作用域的 source 注册表。支持：
 * <ul>
 *   <li>编程式注册 / 注销</li>
 *   <li>按 scope 分组（system, project, plugin, session）</li>
 *   <li>批量加载所有 source 的当前值</li>
 *   <li>热加载 (基于scope)</li>
 * </ul>
 */
public class ContextRegistry {

    private static final Logger logger = Logger.getLogger(ContextRegistry.class.getName());

    /** 按 key 索引的 source */
    private final Map<String, ContextSource<?>> sources = new ConcurrentHashMap<>();
    /** 按 scope 分组的 source key 列表 */
    private final Map<String, List<String>> scopeIndex = new ConcurrentHashMap<>();
    /** 有序 source key 列表（渲染顺序） */
    private final List<String> orderedKeys = new CopyOnWriteArrayList<>();

    // ==================== 注册 ====================

    /**
     * 注册一个上下文源。
     *
     * @param source 上下文源
     * @param scope  作用域（system, project, plugin, session）
     */
    public synchronized void register(ContextSource<?> source, String scope) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(scope);

        String key = source.getKey();
        if (sources.putIfAbsent(key, source) != null) {
            logger.fine("[ContextRegistry] Source already registered: " + key);
            return;
        }
        scopeIndex.computeIfAbsent(scope, k -> new CopyOnWriteArrayList<>()).add(key);
        orderedKeys.add(key);
        logger.info("[ContextRegistry] Registered: " + key + " (scope=" + scope + ")");
    }

    /**
     * 批量注册。
     */
    public synchronized void registerAll(Map<String, ContextSource<?>> sources, String scope) {
        sources.forEach((key, source) -> register(source, scope));
    }

    /**
     * 注销一个上下文源。
     */
    public synchronized void unregister(String key) {
        ContextSource<?> removed = sources.remove(key);
        if (removed != null) {
            orderedKeys.remove(key);
            scopeIndex.values().forEach(list -> list.remove(key));
            logger.info("[ContextRegistry] Unregistered: " + key);
        }
    }

    /**
     * 按 scope 注销所有源（用于插件热卸载）。
     */
    public synchronized void unregisterScope(String scope) {
        List<String> keys = scopeIndex.get(scope);
        if (keys != null) {
            List.copyOf(keys).forEach(this::unregister);
            scopeIndex.remove(scope);
        }
    }

    // ==================== 查询 ====================

    /**
     * 获取指定 key 的 source。
     */
    public ContextSource<?> getSource(String key) {
        return sources.get(key);
    }

    /**
     * 获取所有已注册 source（按注册顺序）。
     */
    public List<ContextSource<?>> getAllSources() {
        return orderedKeys.stream()
            .map(sources::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 加载所有 source 的当前值。
     */
    public Map<String, ContextValue<?>> loadAll() {
        Map<String, ContextValue<?>> result = new LinkedHashMap<>();
        for (ContextSource<?> source : getAllSources()) {
            result.put(source.getKey(), source.load());
        }
        return result;
    }

    /**
     * 获取指定 scope 的 source。
     */
    public List<ContextSource<?>> getSourcesByScope(String scope) {
        List<String> keys = scopeIndex.get(scope);
        if (keys == null) return List.of();
        return keys.stream()
            .map(sources::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 获取所有 scope 名称。
     */
    public Set<String> getScopes() {
        return scopeIndex.keySet();
    }

    /**
     * 获取统计快照。
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSources", sources.size());
        Map<String, Integer> byScope = new LinkedHashMap<>();
        scopeIndex.forEach((scope, keys) -> byScope.put(scope, keys.size()));
        stats.put("sourcesByScope", byScope);
        return stats;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建包含所有内置源的注册表。
     */
    public static ContextRegistry createWithBuiltins() {
        ContextRegistry registry = new ContextRegistry();
        BuiltinContextSources.registerAll(registry);
        return registry;
    }
}
