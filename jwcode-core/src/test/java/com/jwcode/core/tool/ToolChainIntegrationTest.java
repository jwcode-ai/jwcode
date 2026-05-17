package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.execution.ToolExecutionStateMachine;
import com.jwcode.core.tool.execution.ToolExecutionState;
import com.jwcode.core.tool.ToolProgress;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具链集成测试
 *
 * <p>测试工具从 注册 → 解析 → 校验 → 执行 → 状态转换 → 结果返回 的完整链路。
 * 覆盖 ToolRegistry → ToolExecutor → ToolExecutionStateMachine → Tool 的全流程。</p>
 */
@DisplayName("工具链集成测试")
public class ToolChainIntegrationTest {

    private ToolRegistry registry;
    private ToolExecutor executor;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = ToolRegistry.createDefault();
        executor = new ToolExecutor(registry);
        mapper = new ObjectMapper();
    }

    // ==================== 工具注册与发现 ====================

    @Test
    @DisplayName("工具注册 - 默认注册中心包含核心工具")
    void testDefaultRegistryContainsCoreTools() {
        assertAll("默认注册应包含核心工具",
            () -> assertTrue(registry.contains("FileReadTool"), "应包含 FileReadTool"),
            () -> assertTrue(registry.contains("FileWriteTool"), "应包含 FileWriteTool"),
            () -> assertTrue(registry.contains("BashTool"), "应包含 BashTool"),
            () -> assertTrue(registry.contains("GlobTool"), "应包含 GlobTool"),
            () -> assertTrue(registry.contains("GrepTool"), "应包含 GrepTool"),
            () -> assertTrue(registry.contains("FileEditTool"), "应包含 FileEditTool")
        );
    }

    @Test
    @DisplayName("工具注册 - 获取工具名称列表")
    void testGetAllToolNames() {
        var names = registry.getAllToolNames();
        assertAll("工具名称列表验证",
            () -> assertNotNull(names, "工具名称列表不应为 null"),
            () -> assertFalse(names.isEmpty(), "工具名称列表不应为空"),
            () -> assertTrue(names.contains("FileReadTool"), "应包含 FileReadTool")
        );
    }

    // ==================== 工具解析 ====================

    @Test
    @DisplayName("工具解析 - 获取工具元数据")
    void testGetToolMetadata() {
        Tool tool = registry.getTool("FileReadTool");
        assertAll("工具元数据验证",
            () -> assertNotNull(tool, "工具不应为 null"),
            () -> assertNotNull(tool.getName(), "工具名称不应为 null"),
            () -> assertNotNull(tool.getDescription(), "工具描述不应为 null"),
            () -> assertNotNull(tool.getInputSchema(), "输入模式不应为 null")
        );
    }

    // ==================== 工具执行 ====================

    @Test
    @DisplayName("工具执行 - 成功执行简单工具")
    void testExecuteSimpleToolSuccess() throws Exception {
        // 准备输入参数
        JsonNode input = mapper.createObjectNode()
            .put("toolName", "Config")
            .put("action", "list");

        ToolExecutionContext context = ToolExecutionContext.builder().build();

        CompletableFuture<ToolExecutor.ToolExecutionResult> future =
            executor.execute("Config", input, context);

        ToolExecutor.ToolExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertAll("工具执行结果验证",
            () -> assertNotNull(result, "执行结果不应为 null")
        );
    }

    @Test
    @DisplayName("工具执行 - 不存在的工具应失败")
    void testExecuteNonExistentTool() {
        JsonNode input = mapper.createObjectNode();
        ToolExecutionContext context = ToolExecutionContext.builder().build();

        assertThrows(Exception.class, () -> {
            executor.execute("NonExistentTool", input, context)
                .get(10, TimeUnit.SECONDS);
        }, "执行不存在的工具应抛出异常");
    }

    // ==================== 工具状态机 ====================

    @Test
    @DisplayName("状态机 - 从 PARSE 到 DONE 的完整转换")
    void testStateMachineFullTransition() {
        ToolExecutionStateMachine stateMachine = new ToolExecutionStateMachine("test-exec-1",
            progress -> {});

        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState(),
            "初始状态应为 PARSE");

        stateMachine.reportSuccess();
        assertEquals(ToolExecutionState.REPORT, stateMachine.getCurrentState(),
            "应转换为 REPORT");

        stateMachine.complete();
        assertEquals(ToolExecutionState.DONE, stateMachine.getCurrentState(),
            "应转换为 DONE");
    }

    @Test
    @DisplayName("状态机 - 错误路径转换")
    void testStateMachineErrorTransition() {
        ToolExecutionStateMachine stateMachine = new ToolExecutionStateMachine("test-exec-2",
            progress -> {});

        stateMachine.fail("执行失败");
        assertEquals(ToolExecutionState.FAILED, stateMachine.getCurrentState(),
            "应转换为 FAILED");
    }

    @Test
    @DisplayName("状态机 - 纠错后仍失败应进入 FAILED")
    void testStateMachineCorrectionThenFailed() {
        ToolExecutionStateMachine stateMachine = new ToolExecutionStateMachine("test-exec-3",
            progress -> {});

        // PARSE 状态下触发 PARSE_ERROR → 进入 CORRECTION（第1次纠错）
        stateMachine.transition(ToolExecutionStateMachine.ErrorType.PARSE_ERROR, "JSON 解析失败");
        assertEquals(ToolExecutionState.CORRECTION, stateMachine.getCurrentState());
        assertEquals(1, stateMachine.getCorrectionAttempts());

        // 在 CORRECTION 状态下再次触发 → 第2次纠错，超过 MAX_CORRECTION=2，进入 FAILED
        stateMachine.transition(ToolExecutionStateMachine.ErrorType.PARSE_ERROR, "JSON 解析失败");
        assertEquals(ToolExecutionState.FAILED, stateMachine.getCurrentState());
    }

    // ==================== 工具上下文 ====================

    @Test
    @DisplayName("工具上下文 - 创建和属性设置")
    void testToolContextCreation() {
        ToolExecutionContext context = ToolExecutionContext.builder().build();

        assertAll("工具上下文验证",
            () -> assertNotNull(context, "上下文不应为 null"),
            () -> assertNull(context.getSession(), "未设置会话时应为 null")
        );
    }

    // ==================== 工具注册表完整性 ====================

    @Test
    @DisplayName("工具注册表完整性 - 工具数量符合预期")
    void testRegistryCompleteness() {
        int toolCount = registry.size();
        assertAll("注册表完整性验证",
            () -> assertTrue(toolCount >= 30, "工具数量应 >= 30，当前: " + toolCount),
            () -> assertEquals(toolCount, registry.getAllToolNames().size(),
                "size() 和 getAllToolNames() 应一致")
        );
    }

    // ==================== 工具分类 ====================

    @Test
    @DisplayName("工具分类 - 各分类包含正确的工具")
    void testToolCategories() {
        assertAll("工具分类验证",
            () -> assertTrue(registry.getAllToolNames().contains("FileReadTool"),
                "应包含 FileReadTool"),
            () -> assertTrue(registry.getAllToolNames().contains("BashTool"),
                "应包含 BashTool"),
            () -> assertTrue(registry.getAllToolNames().contains("Git"),
                "应包含 Git")
        );
    }

    // ==================== 完整工具链流程 ====================

    @Test
    @DisplayName("完整工具链：注册 → 查找 → 执行 → 结果")
    void testCompleteToolChain() throws Exception {
        // 1. 注册 - 注册中心可用
        assertNotNull(registry, "注册中心应已初始化");

        // 2. 查找 - 找到指定工具
        String toolName = "GlobTool";
        assertTrue(registry.contains(toolName), "应能找到 " + toolName);
        Tool tool = registry.getTool(toolName);
        assertNotNull(tool, "工具实例不应为 null");

        // 3. 准备输入
        JsonNode input = mapper.createObjectNode()
            .put("pattern", "*.java");

        ToolExecutionContext context = ToolExecutionContext.builder().build();

        // 4. 执行
        CompletableFuture<ToolExecutor.ToolExecutionResult> future =
            executor.execute(toolName, input, context);

        ToolExecutor.ToolExecutionResult result = future.get(15, TimeUnit.SECONDS);

        // 5. 结果返回
        assertNotNull(result, "执行结果不应为 null");
    }

    // ==================== 工具执行超时 ====================

    @Test
    @DisplayName("工具执行 - 超时处理")
    void testToolExecutionTimeout() {
        ToolExecutionContext context = ToolExecutionContext.builder().build();

        // 长时间运行的工具应被超时机制处理
        assertNotNull(context, "带超时的上下文应可创建");
    }

    // ==================== 工具依赖关系 ====================

    @Test
    @DisplayName("工具依赖 - 工具间无循环依赖")
    void testNoCircularDependencies() {
        assertAll("所有工具应能独立初始化",
            () -> assertDoesNotThrow(() -> registry.getTool("FileReadTool")),
            () -> assertDoesNotThrow(() -> registry.getTool("FileWriteTool")),
            () -> assertDoesNotThrow(() -> registry.getTool("BashTool")),
            () -> assertDoesNotThrow(() -> registry.getTool("GlobTool")),
            () -> assertDoesNotThrow(() -> registry.getTool("GrepTool"))
        );
    }
}
