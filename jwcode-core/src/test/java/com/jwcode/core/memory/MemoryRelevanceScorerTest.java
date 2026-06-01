package com.jwcode.core.memory;

import com.jwcode.core.index.EmbeddingService;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Collections;

@DisplayName("MemoryRelevanceScorer — 语义记忆相关性评分测试")
class MemoryRelevanceScorerTest {

    private ExtractMemoriesService.MemoryEntry makeEntry(String name, String description, String body) {
        ExtractMemoriesService.MemoryEntry e = new ExtractMemoriesService.MemoryEntry();
        e.name = name;
        e.description = description;
        e.body = body;
        return e;
    }

    // === KEYWORD 模式测试 ===

    @Test
    @DisplayName("KEYWORD 模式：精确匹配得高分")
    void keywordExactMatch() {
        MemoryRelevanceScorer scorer = new MemoryRelevanceScorer(null, MemoryRelevanceScorer.ScoringMode.KEYWORD, 0.6, 0.4);
        var entries = List.of(
            makeEntry("auth", "authentication and login flow", "handles OAuth2 and JWT tokens"),
            makeEntry("db", "database connection pooling", "HikariCP config"),
            makeEntry("ci", "CI/CD pipeline setup", "GitHub Actions workflow")
        );
        var results = scorer.score(entries, "authentication OAuth2 login", 3);
        assertFalse(results.isEmpty());
        assertEquals("auth", results.get(0).entry().name);
        assertTrue(results.get(0).score() > 0.3);
    }

    @Test
    @DisplayName("KEYWORD 模式：无匹配返回空")
    void keywordNoMatch() {
        MemoryRelevanceScorer scorer = new MemoryRelevanceScorer(null, MemoryRelevanceScorer.ScoringMode.KEYWORD, 0.6, 0.4);
        var entries = List.of(
            makeEntry("auth", "authentication and login flow", "handles OAuth2 and JWT")
        );
        var results = scorer.score(entries, "xyzzy nonexistent terms", 3);
        assertEquals(0, results.size());
    }

    @Test
    @DisplayName("KEYWORD 模式：部分匹配中等分数")
    void keywordPartialMatch() {
        MemoryRelevanceScorer scorer = new MemoryRelevanceScorer(null, MemoryRelevanceScorer.ScoringMode.KEYWORD, 0.6, 0.4);
        var entries = List.of(
            makeEntry("db", "database pooling", "HikariCP settings"),
            makeEntry("ci", "CI pipeline", "GitHub Actions")
        );
        var results = scorer.score(entries, "database optimization", 3);
        assertFalse(results.isEmpty());
        assertEquals("db", results.get(0).entry().name);
    }

    // === null / 边界值测试 ===

    @Test
    @DisplayName("空 entries 返回空列表")
    void emptyEntriesReturnsEmpty() {
        MemoryRelevanceScorer scorer = new MemoryRelevanceScorer(null);
        var results = scorer.score(Collections.emptyList(), "query", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("null query 返回空列表")
    void nullQueryReturnsEmpty() {
        MemoryRelevanceScorer scorer = new MemoryRelevanceScorer(null);
        var entries = List.of(makeEntry("x", "desc", "body"));
        var results = scorer.score(entries, null, 5);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("topK 截断正确")
    void topKTruncation() {
        MemoryRelevanceScorer scorer = new MemoryRelevanceScorer(null, MemoryRelevanceScorer.ScoringMode.KEYWORD, 0.6, 0.4);
        var entries = List.of(
            makeEntry("a", "java build", "maven"),
            makeEntry("b", "python script", "pip"),
            makeEntry("c", "java test", "junit"),
            makeEntry("d", "js frontend", "react")
        );
        var results = scorer.score(entries, "java", 2);
        assertEquals(2, results.size());
    }

    // === 排序验证 ===

    @Test
    @DisplayName("结果按 score 降序排列")
    void resultsSortedByScoreDesc() {
        MemoryRelevanceScorer scorer = new MemoryRelevanceScorer(null, MemoryRelevanceScorer.ScoringMode.KEYWORD, 0.6, 0.4);
        var entries = List.of(
            makeEntry("low", "partial match here", "some query terms"),
            makeEntry("high", "exact match query", "the query terms appear here")
        );
        var results = scorer.score(entries, "match query", 3);
        assertEquals(2, results.size());
        assertTrue(results.get(0).score() >= results.get(1).score());
    }
}
