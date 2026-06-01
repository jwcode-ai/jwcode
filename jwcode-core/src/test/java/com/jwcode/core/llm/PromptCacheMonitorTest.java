package com.jwcode.core.llm;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptCacheMonitor — 缓存断裂检测测试")
class PromptCacheMonitorTest {

    private PromptCacheMonitor monitor;
    private final String prompt1 = "system prompt version 1";
    private final String prompt2 = "system prompt version 2 — modified";

    @BeforeEach
    void setUp() {
        monitor = new PromptCacheMonitor();
    }

    // === 初始状态 ===

    @Test
    @DisplayName("首次检测不报断裂")
    void firstCheckNoBreak() {
        PromptCacheMonitor.CacheBreakEvent event = monitor.check(prompt1, 1000, System.currentTimeMillis());
        assertFalse(event.broken());
        assertEquals(0.0, event.confidence());
    }

    // === systemPromptChanged 检测 ===

    @Test
    @DisplayName("Prompt 变化触发断裂 (置信度 0.9)")
    void promptChangeDetected() {
        monitor.check(prompt1, 1000, System.currentTimeMillis());
        PromptCacheMonitor.CacheBreakEvent event = monitor.check(prompt2, 1000, System.currentTimeMillis());
        assertTrue(event.broken());
        assertTrue(event.confidence() >= 0.9);
        assertTrue(event.reason().contains("systemPromptChanged"));
    }

    @Test
    @DisplayName("相同 Prompt 不触发断裂")
    void samePromptNoBreak() {
        monitor.check(prompt1, 1000, System.currentTimeMillis());
        PromptCacheMonitor.CacheBreakEvent event = monitor.check(prompt1, 1000, System.currentTimeMillis());
        assertFalse(event.broken());
    }

    // === 闲置检测 ===

    @Test
    @DisplayName("长时间闲置触发断裂")
    void idleBreakDetected() {
        long now = System.currentTimeMillis();
        monitor.recordAssistantMessage(now - 40 * 60 * 1000); // 40 分钟前
        PromptCacheMonitor.CacheBreakEvent event = monitor.check(prompt1, 1000, now);
        assertTrue(event.broken());
        assertTrue(event.reason().contains("timeSinceLastMessage"));
    }

    // === Token 变化检测 ===

    @Test
    @DisplayName("Token 剧烈变化触发断裂")
    void tokenDeltaDetected() {
        monitor.check(prompt1, 1000, System.currentTimeMillis());
        PromptCacheMonitor.CacheBreakEvent event = monitor.check(prompt1, 3000, System.currentTimeMillis());
        assertTrue(event.broken());
        assertTrue(event.reason().contains("tokenDelta"));
    }

    @Test
    @DisplayName("Token 小幅变化不触发")
    void smallTokenDeltaNoBreak() {
        monitor.check(prompt1, 1000, System.currentTimeMillis());
        PromptCacheMonitor.CacheBreakEvent event = monitor.check(prompt1, 1100, System.currentTimeMillis());
        // Token 变化 10% < 50%，若 prompt 相同且无闲置则不应触发
        assertEquals(0.0, event.confidence());
    }

    // === 多个启发式同时触发 ===

    @Test
    @DisplayName("两个启发式同时触发置信度为 1.0")
    void twoHeuristicsConfidenceOne() {
        long now = System.currentTimeMillis();
        monitor.recordAssistantMessage(now - 40 * 60 * 1000); // 闲置 40 分钟
        monitor.check(prompt1, 1000, now);
        // Prompt 变化 + 闲置 → 两个触发
        PromptCacheMonitor.CacheBreakEvent event = monitor.check(prompt2, 1000, now);
        assertTrue(event.broken());
        assertEquals(1.0, event.confidence());
    }
}
