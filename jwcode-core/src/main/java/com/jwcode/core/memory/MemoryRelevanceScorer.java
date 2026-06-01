package com.jwcode.core.memory;

import com.jwcode.core.index.EmbeddingService;

import java.util.*;
import java.util.logging.Logger;

/**
 * MemoryRelevanceScorer — 语义记忆相关性评分器（对标 Claude Code findRelevantMemories.ts）。
 *
 * <p>对 memory 目录下的 .md 文件进行语义相关性评分，只将最相关的记忆注入 system prompt，
 * 避免全量注入浪费 Token。</p>
 *
 * <h3>三种评分模式</h3>
 * <ul>
 *   <li><b>HYBRID</b> — 语义 + 关键词混合（推荐，默认）</li>
 *   <li><b>SEMANTIC</b> — 纯嵌入余弦相似度</li>
 *   <li><b>KEYWORD</b> — TF-IDF 风格关键词重叠</li>
 * </ul>
 *
 * <h3>评分权重</h3>
 * <p>description 权重 0.6 / body 权重 0.4 — description 是精心撰写的一行摘要，
 * 比 body 更能反映主题相关性。</p>
 *
 * <h3>降级策略</h3>
 * <ul>
 *   <li>EmbeddingService 可用 → HYBRID/SEMANTIC 模式</li>
 *   <li>EmbeddingService 不可用 → 自动降级为 KEYWORD 模式</li>
 *   <li>embed() 返回 null/空 → 降级为 KEYWORD</li>
 * </ul>
 *
 * @author JWCode Team
 * @since 3.1.0
 */
public class MemoryRelevanceScorer {

    private static final Logger logger = Logger.getLogger(MemoryRelevanceScorer.class.getName());

    public enum ScoringMode {
        HYBRID,
        SEMANTIC,
        KEYWORD
    }

    /** 嵌入服务（可为 null） */
    private final EmbeddingService embeddingService;

    /** 默认模式 */
    private final ScoringMode defaultMode;

    /** description 权重 */
    private final double descriptionWeight;

    /** body 权重 */
    private final double bodyWeight;

    /** 默认 topK */
    private static final int DEFAULT_TOP_K = 5;

    // ==================== 构造函数 ====================

    public MemoryRelevanceScorer(EmbeddingService embeddingService) {
        this(embeddingService, ScoringMode.HYBRID, 0.6, 0.4);
    }

    public MemoryRelevanceScorer(EmbeddingService embeddingService,
                                  ScoringMode defaultMode,
                                  double descriptionWeight,
                                  double bodyWeight) {
        this.embeddingService = embeddingService;
        this.defaultMode = defaultMode;
        this.descriptionWeight = descriptionWeight;
        this.bodyWeight = bodyWeight;
    }

    // ==================== 公共 API ====================

    /**
     * 按相关性评分排序记忆条目。
     *
     * @param entries 记忆条目列表
     * @param query   查询字符串（当前用户消息或任务描述）
     * @param topK    最多返回条数
     * @return 排序后的评分记忆列表（score 降序）
     */
    public List<ScoredMemory> score(List<ExtractMemoriesService.MemoryEntry> entries,
                                     String query, int topK) {
        if (entries == null || entries.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 如果 EmbeddingService 不可用，降级为关键词模式
        ScoringMode mode = defaultMode;
        if (embeddingService == null && mode != ScoringMode.KEYWORD) {
            logger.fine("[MemoryScorer] EmbeddingService 不可用，降级为 KEYWORD 模式");
            mode = ScoringMode.KEYWORD;
        }

        // 评分
        List<ScoredMemory> scored = new ArrayList<>();
        for (ExtractMemoriesService.MemoryEntry entry : entries) {
            double score = switch (mode) {
                case HYBRID   -> hybridScore(entry, query);
                case SEMANTIC -> semanticScore(entry, query);
                case KEYWORD  -> keywordScore(entry, query);
            };

            if (score > 0) {
                scored.add(new ScoredMemory(entry, score));
            }
        }

        // 排序
        scored.sort(Collections.reverseOrder());

        // 截断
        int k = Math.min(topK, scored.size());
        return scored.subList(0, k);
    }

    /**
     * 使用默认 topK 评分。
     */
    public List<ScoredMemory> score(List<ExtractMemoriesService.MemoryEntry> entries, String query) {
        return score(entries, query, DEFAULT_TOP_K);
    }

    // ==================== 评分算法 ====================

    /**
     * 混合评分：语义相似度 + 关键词奖励。
     */
    private double hybridScore(ExtractMemoriesService.MemoryEntry entry, String query) {
        double semantic = semanticScore(entry, query);
        double keyword = keywordScore(entry, query);
        // 80% 语义 + 20% 关键词
        return 0.8 * semantic + 0.2 * keyword;
    }

    /**
     * 语义评分 — 使用嵌入向量的余弦相似度。
     * 对 description 和 body 分别评分后加权。
     */
    private double semanticScore(ExtractMemoriesService.MemoryEntry entry, String query) {
        if (embeddingService == null) {
            return keywordScore(entry, query);
        }

        try {
            float[] queryVec = embeddingService.embed(query);
            if (queryVec == null || queryVec.length == 0) {
                logger.fine("[MemoryScorer] embed 返回空，降级为 KEYWORD");
                return keywordScore(entry, query);
            }

            double descScore = 0;
            double bodyScore = 0;

            // description 相似度
            if (entry.description != null && !entry.description.isBlank()) {
                float[] descVec = embeddingService.embed(entry.description);
                descScore = cosineSimilarity(queryVec, descVec);
            }

            // body 相似度（截断到 500 字避免过长文本）
            if (entry.body != null && !entry.body.isBlank()) {
                String truncatedBody = entry.body.length() > 500
                    ? entry.body.substring(0, 500) : entry.body;
                float[] bodyVec = embeddingService.embed(truncatedBody);
                bodyScore = cosineSimilarity(queryVec, bodyVec);
            }

            // 加权融合
            double weighted = descScore * descriptionWeight + bodyScore * bodyWeight;

            // 如果有关键词匹配，加小奖励（0.05）
            double keywordBonus = keywordBonus(entry, query);

            return clamp(weighted + keywordBonus, 0.0, 1.0);
        } catch (Exception e) {
            logger.fine("[MemoryScorer] 语义评分失败: " + e.getMessage() + "，降级为 KEYWORD");
            return keywordScore(entry, query);
        }
    }

    /**
     * 关键词评分 — TF-IDF 风格：查询词在 description/body 中的覆盖率。
     */
    private double keywordScore(ExtractMemoriesService.MemoryEntry entry, String query) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return 0;

        double descScore = textCoverage(entry.description, queryTerms);
        double bodyScore = textCoverage(entry.body, queryTerms);

        return clamp(descScore * descriptionWeight + bodyScore * bodyWeight, 0.0, 1.0);
    }

    /**
     * 关键词小奖励（0-0.05 范围），用于语义评分中增加关键词匹配的微调。
     */
    private double keywordBonus(ExtractMemoriesService.MemoryEntry entry, String query) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return 0;

        int matched = 0;
        String combined = (entry.description != null ? entry.description.toLowerCase() : "")
            + " " + (entry.body != null ? entry.body.toLowerCase() : "");

        for (String term : queryTerms) {
            if (combined.contains(term)) matched++;
        }

        return Math.min(0.05, (double) matched / queryTerms.size() * 0.05);
    }

    // ==================== 工具方法 ====================

    /**
     * 查询词在文本中的覆盖率。
     */
    private double textCoverage(String text, Set<String> queryTerms) {
        if (text == null || text.isBlank() || queryTerms.isEmpty()) return 0;
        String lower = text.toLowerCase();
        int matched = 0;
        for (String term : queryTerms) {
            if (lower.contains(term)) matched++;
        }
        return (double) matched / queryTerms.size();
    }

    /**
     * 简易分词 — 按空格/标点分割取唯一小写词。
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();
        Set<String> tokens = new LinkedHashSet<>();
        for (String word : text.toLowerCase().split("[\\s,.!?;:()\\[\\]{}]+")) {
            word = word.trim();
            if (word.length() >= 2 && word.length() <= 50) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /**
     * 余弦相似度。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0;
        if (a.length != b.length) return 0;

        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0;
        return clamp(dot / (Math.sqrt(normA) * Math.sqrt(normB)), 0.0, 1.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 评分结果 ====================

    /**
     * 评分记忆 — 包含原始条目和评分。
     */
    public record ScoredMemory(ExtractMemoriesService.MemoryEntry entry, double score)
            implements Comparable<ScoredMemory> {

        @Override
        public int compareTo(ScoredMemory other) {
            return Double.compare(this.score, other.score);
        }

        @Override
        public String toString() {
            return String.format("[%.3f] %s (%s)", score,
                entry.name, entry.description != null ? entry.description : "");
        }
    }
}
