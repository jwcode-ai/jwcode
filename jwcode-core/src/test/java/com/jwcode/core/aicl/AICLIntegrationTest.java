package com.jwcode.core.aicl;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AICL 上下文语言集成测试
 *
 * <p>测试上下文管理器、上下文构建器、语义搜索、意图推理、向量存储等
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

    // ==================== SemanticSearch 测试 ====================

    @Test
    @DisplayName("语义搜索 - 搜索功能接口")
    void testSemanticSearch() {
        SemanticSearch search = new SemanticSearch();

        assertAll("语义搜索验证",
            () -> assertNotNull(search, "语义搜索不应为 null"),
            () -> assertDoesNotThrow(() -> search.search("测试查询"),
                "搜索操作不应抛出异常")
        );
    }

    @Test
    @DisplayName("语义搜索 - 带 TOP-K 和类型过滤")
    void testSemanticSearchWithFilters() {
        SemanticSearch search = new SemanticSearch();

        assertDoesNotThrow(() -> {
            List<SemanticSearch.SearchResult> results = search.search("query", 5, "java");
            assertNotNull(results, "搜索结果不应为 null");
        }, "带过滤参数的搜索不应抛出异常");
    }

    // ==================== IntentInference 测试 ====================

    @Test
    @DisplayName("意图推理 - 推理用户意图")
    void testIntentInference() {
        IntentInference inference = new IntentInference();

        assertAll("意图推理验证",
            () -> assertNotNull(inference, "意图推理不应为 null"),
            () -> assertDoesNotThrow(() -> inference.infer("请帮我写一个 Java 函数"),
                "推理操作不应抛出异常")
        );
    }

    // ==================== VectorStore 测试 ====================

    @Test
    @DisplayName("向量存储 - 存储和检索向量")
    void testVectorStore() {
        VectorStore vectorStore = new VectorStore();

        assertAll("向量存储验证",
            () -> assertNotNull(vectorStore, "向量存储不应为 null"),
            () -> assertDoesNotThrow(() -> vectorStore.store("vec-1", new float[]{1.0f, 2.0f, 3.0f}),
                "存储向量不应抛出异常"),
            () -> assertDoesNotThrow(() -> vectorStore.retrieve("vec-1"),
                "检索向量不应抛出异常")
        );
    }

    // ==================== ContextTracker 测试 ====================

    @Test
    @DisplayName("上下文跟踪器 - 跟踪上下文变更")
    void testContextTracker() {
        ContextTracker tracker = new ContextTracker();

        assertAll("上下文跟踪器验证",
            () -> assertNotNull(tracker, "上下文跟踪器不应为 null"),
            () -> assertDoesNotThrow(() -> tracker.trackChange("ctx-1", "change-event"),
                "跟踪变更不应抛出异常")
        );
    }

    // ==================== ContextRestorer 测试 ====================

    @Test
    @DisplayName("上下文恢复器 - 恢复上下文状态")
    void testContextRestorer() {
        ContextRestorer restorer = new ContextRestorer();

        assertAll("上下文恢复器验证",
            () -> assertNotNull(restorer, "上下文恢复器不应为 null"),
            () -> assertDoesNotThrow(() -> restorer.saveSnapshot("ctx-1"),
                "保存快照不应抛出异常"),
            () -> assertDoesNotThrow(() -> restorer.restore("ctx-1"),
                "恢复上下文不应抛出异常")
        );
    }

    // ==================== ContextMapper 测试 ====================

    @Test
    @DisplayName("上下文映射器 - 上下文间映射")
    void testContextMapper() {
        ContextMapper mapper = new ContextMapper();

        assertAll("上下文映射器验证",
            () -> assertNotNull(mapper, "上下文映射器不应为 null"),
            () -> assertDoesNotThrow(() -> mapper.map("source-ctx", "target-ctx"),
                "映射操作不应抛出异常")
        );
    }

    // ==================== AIContext 模型测试 ====================

    @Test
    @DisplayName("AI 上下文模型 - 创建和属性")
    void testAIContextModel() {
        AIContext context = new AIContext("ctx-1", "user-message", 0.85);

        assertAll("AI 上下文模型验证",
            () -> assertNotNull(context, "AI 上下文模型不应为 null"),
            () -> assertEquals("ctx-1", context.getId(), "ID 匹配"),
            () -> assertTrue(context.getRelevanceScore() > 0, "相关性分数应大于0")
        );
    }

    // ==================== 完整 AICL 流程 ====================

    @Test
    @DisplayName("完整 AICL 流程：上下文创建 → 语义搜索 → 意图推理 → 结果整合")
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

        // 3. 语义搜索
        SemanticSearch search = new SemanticSearch();
        assertDoesNotThrow(() -> {
            List<SemanticSearch.SearchResult> results = search.search("java code example");
            assertNotNull(results, "搜索结果不应为 null");
        }, "搜索操作应正常");

        // 4. 意图推理
        IntentInference inference = new IntentInference();
        assertDoesNotThrow(() -> {
            String intent = inference.infer("Implement a sorting algorithm");
            assertNotNull(intent, "推理结果不应为 null");
        }, "推理操作应正常");

        // 5. 清理
        assertDoesNotThrow(() -> contextManager.destroyContext(contextId),
            "清理上下文不应抛出异常");
    }
}
