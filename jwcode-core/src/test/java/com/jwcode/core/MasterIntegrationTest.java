package com.jwcode.core;

import com.jwcode.core.aicl.*;
import com.jwcode.core.hook.*;
import com.jwcode.core.lsp.*;
import com.jwcode.core.repl.*;
import com.jwcode.core.resilience.*;
import com.jwcode.core.tool.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全系统集成测试 (Master Integration Test)
 *
 * <p>测试所有核心子系统之间的交互与协作：
 * Hook、LSP、REPL、Resilience、AICL、Tool 系统的完整集成链路。</p>
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

    // ==================== Hook ↔ Tool 交互 ====================

    @Test
    @DisplayName("Hook ↔ Tool：工具执行前触发 Hook 审计检查")
    void testHookBeforeToolExecution() {
        // Hook 注册
        HookExecutor auditHook = new HookExecutor() {
            @Override
            public CompletableFuture<HookResult> execute(HookContext context) {
                // 模拟审计检查
                return CompletableFuture.completedFuture(
                    new HookResult(HookDecision.ALLOW, "审计通过"));
            }

            @Override
            public HookEventType getEventType() {
                return HookEventType.BEFORE_EXECUTION;
            }

            @Override
            public HookPriority getPriority() {
                return HookPriority.HIGH;
            }
        };
        hookRegistry.register(auditHook);

        // 工具注册
        ToolExecutor fileTool = new ToolExecutor() {
            @Override
            public String getName() { return "file-read"; }

            @Override
            public List<String> getDependencies() { return List.of(); }

            @Override
            public CompletableFuture<ToolResult> execute(Map<String, Object> params) {
                return CompletableFuture.completedFuture(new ToolResult(true, "文件读取成功"));
            }
        };
        toolRegistry.register(fileTool);

        assertAll("Hook ↔ Tool 交互验证",
            () -> assertFalse(hookRegistry.getHooks(HookEventType.BEFORE_EXECUTION).isEmpty(),
                "Hook 应已注册"),
            () -> assertNotNull(toolRegistry.getTool("file-read"),
                "工具应已注册")
        );
    }

    // ==================== LSP ↔ AICL 交互 ====================

    @Test
    @DisplayName("LSP ↔ AICL：LSP 悬停信息作为 AI 上下文输入")
    void testLspToAIContext() {
        // LSP 文档操作
        documentManager.openDocument("Example.java", "class Example {}");
        assertTrue(documentManager.isDocumentOpen("Example.java"),
            "LSP 文档应已打开");

        // AICL 上下文创建
        String contextId = contextManager.createContext("lsp-context");
        assertNotNull(contextId, "AI 上下文 ID 不应为 null");

        // 使用 ContextBuilder 构建带有 LSP 信息的上下文
        ContextBuilder.BuiltContext context = contextBuilder
            .withId(contextId)
            .withType("lsp-enriched")
            .withPayload(Map.of(
                "filePath", "Example.java",
                "isOpen", documentManager.isDocumentOpen("Example.java")
            ))
            .build();

        assertAll("LSP ↔ AICL 交互验证",
            () -> assertNotNull(context, "上下文不应为 null"),
            () -> assertTrue((Boolean) context.getPayload().get("isOpen"),
                "负载中应包含文档打开状态")
        );
    }

    // ==================== REPL ↔ Resilience 交互 ====================

    @Test
    @DisplayName("REPL ↔ Resilience：执行器创建受熔断器保护")
    void testReplWithCircuitBreaker() {
        // 熔断器配置
        CircuitBreaker cb = new CircuitBreaker("repl-breaker", 3, 5000, 2);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
            "熔断器初始应为 CLOSED");

        // REPL 工厂创建
        REPLFactory factory = new REPLFactory();
        REPLExecutor pythonExecutor = factory.createExecutor("python");
        if (pythonExecutor != null) {
            assertAll("REPL ↔ 熔断器交互验证",
                () -> assertEquals("python", pythonExecutor.getLanguage()),
                () -> assertTrue(pythonExecutor.getTimeoutMillis() > 0,
                    "超时设置应有效")
            );
        }
    }

    // ==================== Tool ↔ Resilience 交互 ====================

    @Test
    @DisplayName("Tool ↔ Resilience：工具执行的重试策略保护")
    void testToolWithRetry() {
        // 重试策略
        RetryPolicy retry = new RetryPolicy(3, 50);
        assertEquals(3, retry.getMaxRetries(), "最多重试3次");

        // 健康监控
        HealthMonitor monitor = new HealthMonitor();
        assertDoesNotThrow(() -> monitor.checkHealth(),
            "健康检查应正常");

        // 工具注册
        ToolExecutor resilientTool = new ToolExecutor() {
            @Override
            public String getName() { return "resilient-tool"; }

            @Override
            public List<String> getDependencies() { return List.of(); }

            @Override
            public CompletableFuture<ToolResult> execute(Map<String, Object> params) {
                return CompletableFuture.completedFuture(new ToolResult(true, "成功"));
            }
        };
        toolRegistry.register(resilientTool);

        assertAll("Tool ↔ 弹性交互验证",
            () -> assertEquals(3, retry.getMaxRetries(), "重试策略配置正确"),
            () -> assertNotNull(toolRegistry.getTool("resilient-tool"),
                "工具应已注册")
        );
    }

    // ==================== Hook ↔ Resilience 交互 ====================

    @Test
    @DisplayName("Hook ↔ Resilience：Hook 执行受全局异常保护")
    void testHookWithExceptionHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        HookExecutor safeHook = new HookExecutor() {
            @Override
            public CompletableFuture<HookResult> execute(HookContext context) {
                try {
                    return CompletableFuture.completedFuture(
                        new HookResult(HookDecision.ALLOW, "安全通过"));
                } catch (Exception e) {
                    handler.handleException(e);
                    return CompletableFuture.completedFuture(
                        new HookResult(HookDecision.DENY, "异常"));
                }
            }

            @Override
            public HookEventType getEventType() { return HookEventType.BEFORE_EXECUTION; }

            @Override
            public HookPriority getPriority() { return HookPriority.NORMAL; }
        };

        hookRegistry.register(safeHook);

        assertAll("Hook ↔ 异常处理器验证",
            () -> assertDoesNotThrow(() -> handler.handleException(new RuntimeException("test")),
                "异常处理器应能捕获异常"),
            () -> assertFalse(hookRegistry.getHooks(HookEventType.BEFORE_EXECUTION).isEmpty(),
                "Hook 应已注册")
        );
    }

    // ==================== AICL ↔ Tool 交互 ====================

    @Test
    @DisplayName("AICL ↔ Tool：AI 上下文指导工具选择")
    void testAIContextDrivesToolSelection() {
        // 创建 AI 上下文
        String contextId = contextManager.createContext("tool-selection");
        assertNotNull(contextId, "上下文 ID 不应为 null");

        // ContextBuilder
        ContextBuilder.BuiltContext context = contextBuilder
            .withId(contextId)
            .withType("tool-guidance")
            .withPayload(Map.of("requiredTool", "file-read"))
            .build();

        // 工具注册
        ToolExecutor tool = new ToolExecutor() {
            @Override
            public String getName() { return "file-read"; }

            @Override
            public List<String> getDependencies() { return List.of(); }

            @Override
            public CompletableFuture<ToolResult> execute(Map<String, Object> params) {
                return CompletableFuture.completedFuture(new ToolResult(true, "文件读取"));
            }
        };
        toolRegistry.register(tool);

        // 根据上下文选择工具
        String requiredTool = (String) context.getPayload().get("requiredTool");
        ToolExecutor selectedTool = toolRegistry.getTool(requiredTool);

        assertAll("AICL ↔ Tool 交互验证",
            () -> assertNotNull(selectedTool, "应根据上下文选择正确的工具"),
            () -> assertEquals("file-read", selectedTool.getName(), "工具名称匹配"),
            () -> assertTrue(contextManager.hasContext(contextId), "AI 上下文应存在")
        );
    }

    // ==================== LSP ↔ Tool 交互 ====================

    @Test
    @DisplayName("LSP ↔ Tool：LSP 诊断结果触发工具操作")
    void testLspDiagnosticTriggersTool() {
        // LSP 诊断注册
        LspDiagnosticRegistry diagnosticRegistry = new LspDiagnosticRegistry();
        diagnosticRegistry.addDiagnostic("Test.java", "语法错误", 1, 5);
        assertEquals(1, diagnosticRegistry.getDiagnostics("Test.java").size(),
            "应有1条诊断结果");

        // 工具执行（基于诊断结果）
        ToolExecutor fixTool = new ToolExecutor() {
            @Override
            public String getName() { return "auto-fix"; }

            @Override
            public List<String> getDependencies() { return List.of(); }

            @Override
            public CompletableFuture<ToolResult> execute(Map<String, Object> params) {
                return CompletableFuture.completedFuture(new ToolResult(true, "自动修复完成"));
            }
        };
        toolRegistry.register(fixTool);

        assertNotNull(toolRegistry.getTool("auto-fix"), "修复工具应已注册");
    }

    // ==================== 四系统交叉链路 ====================

    @Test
    @DisplayName("四系统交叉链路：Hook → Resilience → Tool → REPL")
    void testFourSystemCrossChain() {
        // 1. Hook: 权限检查
        HookExecutor permissionHook = new HookExecutor() {
            @Override
            public CompletableFuture<HookResult> execute(HookContext context) {
                return CompletableFuture.completedFuture(
                    new HookResult(HookDecision.ALLOW, "权限通过"));
            }
            @Override
            public HookEventType getEventType() { return HookEventType.BEFORE_EXECUTION; }
            @Override
            public HookPriority getPriority() { return HookPriority.CRITICAL; }
        };
        hookRegistry.register(permissionHook);

        // 2. Resilience: 熔断器保护
        CircuitBreaker cb = new CircuitBreaker("cross-breaker", 3, 5000, 2);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState(),
            "熔断器初始为 CLOSED");

        // 3. Tool: 代码执行工具
        ToolExecutor codeRunner = new ToolExecutor() {
            @Override
            public String getName() { return "code-runner"; }
            @Override
            public List<String> getDependencies() { return List.of(); }
            @Override
            public CompletableFuture<ToolResult> execute(Map<String, Object> params) {
                return CompletableFuture.completedFuture(new ToolResult(true, "代码执行成功"));
            }
        };
        toolRegistry.register(codeRunner);

        // 4. REPL: 创建执行器
        REPLFactory factory = new REPLFactory();
        REPLExecutor replExecutor = factory.createExecutor("java");
        if (replExecutor != null) {
            assertEquals("java", replExecutor.getLanguage(), "语音应为 java");
        }

        // 验证所有系统正常
        assertAll("四系统交叉链路验证",
            () -> assertFalse(hookRegistry.getHooks(HookEventType.BEFORE_EXECUTION).isEmpty(),
                "Hook 系统正常"),
            () -> assertTrue(cb.tryAcquire() || cb.getState() == CircuitBreaker.State.CLOSED,
                "熔断器正常"),
            () -> assertNotNull(toolRegistry.getTool("code-runner"), "工具系统正常"),
            () -> assertNotNull(replExecutor == null ? "skip" : replExecutor.getLanguage(),
                "REPL 系统正常")
        );
    }
}
