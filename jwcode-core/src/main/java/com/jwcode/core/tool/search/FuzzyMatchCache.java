package com.jwcode.core.tool.search;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 基于 Trie 的文件名模糊匹配索引 — 支持前缀搜索和模糊匹配。
 *
 * <p>用于 @ 文件引用弹窗的快速文件名搜索，预期性能 <10ms。
 */
public class FuzzyMatchCache {
    private static final Logger logger = Logger.getLogger(FuzzyMatchCache.class.getName());

    private final TrieNode root = new TrieNode();
    private final Map<String, Set<Path>> indexedFiles = new ConcurrentHashMap<>();
    private volatile boolean built = false;

    /**
     * 构建索引。
     */
    public void buildIndex(Collection<Path> files) {
        root.children.clear();
        indexedFiles.clear();

        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase();
            indexedFiles.computeIfAbsent(name, k -> ConcurrentHashMap.newKeySet()).add(file);
            insert(name, file);
        }
        built = true;
        logger.info("[FuzzyMatchCache] 索引已构建: " + indexedFiles.size() + " 个唯一文件名");
    }

    /**
     * 前缀搜索 — 找出所有以 prefix 开头的文件名。
     */
    public List<Path> searchByPrefix(String prefix) {
        if (!built || prefix == null || prefix.isEmpty()) return List.of();
        prefix = prefix.toLowerCase();

        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return List.of();
        }
        return collectPaths(node, 50);
    }

    /**
     * 模糊搜索 — 包含匹配 + 编辑距离启发式。
     */
    public List<Path> fuzzySearch(String query, int maxResults) {
        if (!built || query == null || query.isEmpty()) return List.of();
        query = query.toLowerCase();

        // 策略 1: 包含匹配
        Set<Path> results = new LinkedHashSet<>();
        for (var entry : indexedFiles.entrySet()) {
            if (entry.getKey().contains(query)) {
                results.addAll(entry.getValue());
                if (results.size() >= maxResults) break;
            }
        }

        // 策略 2: 如果包含匹配不足，用前缀匹配补充
        if (results.size() < maxResults) {
            results.addAll(searchByPrefix(query));
        }

        return new ArrayList<>(results).subList(0, Math.min(results.size(), maxResults));
    }

    /**
     * 索引是否已构建。
     */
    public boolean isBuilt() {
        return built;
    }

    /**
     * 失效索引。
     */
    public void invalidate() {
        built = false;
        root.children.clear();
        indexedFiles.clear();
    }

    // ─── Trie 实现 ───

    private void insert(String name, Path path) {
        TrieNode node = root;
        for (char c : name.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.paths.add(path);
    }

    private List<Path> collectPaths(TrieNode from, int limit) {
        List<Path> result = new ArrayList<>();
        Deque<TrieNode> stack = new ArrayDeque<>();
        stack.push(from);

        while (!stack.isEmpty() && result.size() < limit) {
            TrieNode node = stack.pop();
            result.addAll(node.paths);
            for (TrieNode child : node.children.values()) {
                stack.push(child);
            }
        }
        return result.subList(0, Math.min(result.size(), limit));
    }

    private static class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>();
        final Set<Path> paths = new LinkedHashSet<>();
    }
}
