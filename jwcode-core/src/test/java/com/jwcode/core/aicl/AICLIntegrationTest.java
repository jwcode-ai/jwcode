package com.jwcode.core.aicl;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AICL 上下文语言集成测试
 *
 * <p>测试上下文管理器、上下文构建器等
 * AI 上下文层的完整功能链路。</p>
 */
@DisplayName("AICL 上下文语言集成测试")
public class AICLIntegrationTest {

    private AIContextManager contextManager;
    private ContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        contextManager = new AIContextManager();
        contextBuilder = new ContextBuilder();
    }

    // ==================== AIContextManager 测试 ====================

    @Test
    @DisplayName("AI 上下文管理器 - 创建和基本操作")
    void testContextManagerCreation() {
        assertAll("上下文管理器验证",
            () -> assertNotNull(contextManager, "上下文管理器不应为 null"),
            () -> assertDoesNotThrow(() -> contextManager.initialize(),
                "初始化不应抛出异常")
        );
    }

    @Test
    @DisplayName("AI 上下文管理器 - 上下文生命周期")
    void testContextLifecycle() {
        String contextId = contextManager.createContext("test-context");
        assertAll("上下文生命周期验证",
            () -> assertNotNull(contextId, "上下文 ID 不应为 null"),
            () -> assertTrue(contextManager.hasContext(contextId), "上下文应存在"),
            () -> assertDoesNotThrow(() -> contextManager.destroyContext(contextId),
                "销毁上下文不应抛出异常"),
            () -> assertFalse(contextManager.hasContext(contextId), "销毁后上下文不应存在")
        );
    }

    // ==================== ContextBuilder 测试 ====================

    @Test
    @DisplayName("上下文构建器 - 构建上下文")
    void testContextBuilder() {
        ContextBuilder.BuiltContext context = contextBuilder
            .withId("ctx-1")
            .withType("conversation")
            .withPayload(Map.of("key", "value"))
            .build();

        assertAll("上下文构建验证",
            () -> assertNotNull(context, "构建的上下文不应为 null"),
            () -> assertEquals("ctx-1", context.getId(), "ID 匹配"),
            () -> assertEquals("conversation", context.getType(), "类型匹配"),
            () -> assertNotNull(context.getPayload(), "负载不应为 null")
        );
    }

    @Test
    @DisplayName("上下文构建器 - 空 ID 应抛异常")
    void testBuilderEmptyId() {
        assertThrows(IllegalArgumentException.class,
            () -> contextBuilder.withId("").build(),
            "空 ID 应抛出异常");
    }

    // ==================== 完整 AICL 流程 ====================

    @Test
    @DisplayName("完整 AICL 流程：上下文创建 → 构建 → 清理")
    void testCompleteAICLFlow() {
        // 1. 创建上下文
        String contextId = contextManager.createContext("development-session");
        assertNotNull(contextId, "上下文 ID 不应为 null");

        // 2. 使用 ContextBuilder 构建
        ContextBuilder.BuiltContext builtContext = contextBuilder
            .withId(contextId)
            .withType("code-generation")
            .build();
        assertNotNull(builtContext, "构建的上下文不应为 null");

        // 3. 清理
        assertDoesNotThrow(() -> contextManager.destroyContext(contextId),
            "清理上下文不应抛出异常");
    }
}
