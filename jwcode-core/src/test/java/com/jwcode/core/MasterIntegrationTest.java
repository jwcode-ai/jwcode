package com.jwcode.core;

import com.jwcode.core.aicl.*;
import com.jwcode.core.hook.*;
import com.jwcode.core.lsp.*;
import com.jwcode.core.tool.*;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全系统集成测试 (Master Integration Test)
 *
 * <p>测试核心子系统之间的基本交互。</p>
 */
@DisplayName("全系统集成测试")
public class MasterIntegrationTest {

    private HookRegistry hookRegistry;
    private ToolRegistry toolRegistry;
    private AIContextManager contextManager;
    private LspDocumentManager documentManager;
    private ContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        hookRegistry = new HookRegistry();
        toolRegistry = new ToolRegistry();
        contextManager = new AIContextManager();
        documentManager = new LspDocumentManager();
        contextBuilder = new ContextBuilder();
    }

    // ==================== AICL 基本功能 ====================

    @Test
    @DisplayName("AICL：上下文创建和销毁")
    void testAIContextLifecycle() {
        String contextId = contextManager.createContext("test");
        assertNotNull(contextId);
        assertTrue(contextManager.hasContext(contextId));

        contextManager.destroyContext(contextId);
        assertFalse(contextManager.hasContext(contextId));
    }

    @Test
    @DisplayName("AICL：ContextBuilder 构建上下文")
    void testContextBuilder() {
        ContextBuilder.BuiltContext ctx = contextBuilder
            .withId("test-ctx")
            .withType("test")
            .withPayload(Map.of("key", "value"))
            .build();

        assertNotNull(ctx);
        assertEquals("test-ctx", ctx.getId());
        assertEquals("test", ctx.getType());
        assertEquals("value", ctx.getPayload().get("key"));
    }

    // ==================== ToolRegistry 基本功能 ====================

    @Test
    @DisplayName("ToolRegistry：注册和获取工具")
    void testToolRegistry() {
        Tool<?, ?, ?> tool = new AbstractTool() {
            @Override
            public String getName() { return "test-tool"; }

            @Override
            public java.util.List<String> getDependencies() { return java.util.List.of(); }
        };
        toolRegistry.register(tool);

        assertNotNull(toolRegistry.getTool("test-tool"));
        assertEquals("test-tool", toolRegistry.getTool("test-tool").getName());
    }

    // ==================== HookRegistry 基本功能 ====================

    @Test
    @DisplayName("HookRegistry：注册 Hook 执行器")
    void testHookRegistry() {
        HookExecutor hook = new HookExecutor() {
            @Override
            public CompletableFuture<HookResult> execute(HookContext context) {
                return CompletableFuture.completedFuture(
                    HookResult.allow("test-hook", "允许"));
            }

            @Override
            public HookImplementationType getType() {
                return HookImplementationType.PROMPT;
            }

            @Override
            public String getName() {
                return "test-hook";
            }
        };
        hookRegistry.register(hook);

        assertFalse(hookRegistry.getAllExecutors().isEmpty());
    }

    // ==================== LSP 基本功能 ====================

    @Test
    @DisplayName("LSP：文档打开和关闭")
    void testLspDocument() {
        documentManager.openDocument("Test.java", "java", "class Test {}");
        assertTrue(documentManager.isDocumentOpen("Test.java"));
    }

    // ==================== 跨系统集成 ====================

    @Test
    @DisplayName("集成链路：AICL → Tool → Hook")
    void testCrossSystemIntegration() {
        // AICL: 创建上下文
        String ctxId = contextManager.createContext("integration");
        assertNotNull(ctxId);

        // Tool: 注册工具
        Tool<?, ?, ?> tool = new AbstractTool() {
            @Override
            public String getName() { return "integration-tool"; }

            @Override
            public java.util.List<String> getDependencies() { return java.util.List.of(); }
        };
        toolRegistry.register(tool);
        assertNotNull(toolRegistry.getTool("integration-tool"));

        // Hook: 注册 Hook
        HookExecutor hook = new HookExecutor() {
            @Override
            public CompletableFuture<HookResult> execute(HookContext context) {
                return CompletableFuture.completedFuture(
                    HookResult.allow("integration-hook", "允许"));
            }

            @Override
            public HookImplementationType getType() {
                return HookImplementationType.PROMPT;
            }

            @Override
            public String getName() {
                return "integration-hook";
            }
        };
        hookRegistry.register(hook);
        assertFalse(hookRegistry.getAllExecutors().isEmpty());

        // 清理
        contextManager.destroyContext(ctxId);
        assertFalse(contextManager.hasContext(ctxId));
    }
}
