package com.jwcode.core.service;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolCategory;
import com.jwcode.core.tool.SideEffect;
import com.jwcode.core.tool.execution.ToolExecutionStateMachine;
import com.jwcode.core.tool.execution.ToolProgress;
import com.jwcode.core.permission.PermissionManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ToolExecutionService 端到端链路集成测试。
 * 
 * <p>覆盖以下核心链路：</p>
 * <ul>
 *   <li>🔧 工具注册/反注册</li>
 *   <li>⚡ 工具执行全生命周期（注册→执行→完成→历史）</li>
 *   <li>🔒 权限校验集成</li>
 *   <li>⏱️ 超时/并发处理</li>
 *   <li>🔗 链式调用（工具A结果→工具B参数）</li>
 * </ul>
 * 
 * <h3>测试策略</h3>
 * <ul>
 *   <li>使用 Mock 工具定义（ToolDefinition），避免依赖真实工具环境</li>
 *   <li>PermissionManager 使用 mock 控制权限校验行为</li>
 *   <li>CompletableFuture 模拟异步执行</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolExecutionServiceIntegrationTest {

    private ToolExecutionService service;
    private PermissionManager permissionManager;

    // Mock 工具定义
    private static final String TOOL_READ = "MockReadTool";
    private static final String TOOL_WRITE = "MockWriteTool";
    private static final String TOOL_TIMEOUT = "MockTimeoutTool";

    @BeforeEach
    void setUp() {
        permissionManager = mock(PermissionManager.class);
        service = new ToolExecutionService(permissionManager);
    }

    @AfterEach
    void tearDown() {
        // 清理注册的工具
        service.unregisterAllTools();
    }

    // ========================================================================
    // 1. 工具注册/反注册
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("工具注册成功后可查询到")
    void testToolRegistration() {
        ToolDefinition toolDef = createMockTool(TOOL_READ, ToolCategory.READ);

        service.registerTool(toolDef);

        Optional<ToolDefinition> retrieved = service.getTool(TOOL_READ);
        assertTrue(retrieved.isPresent(), "注册后应能查询到工具");
        assertEquals(TOOL_READ, retrieved.get().getName());
        assertEquals(ToolCategory.READ, retrieved.get().getCategory());
    }

    @Test
    @Order(2)
    @DisplayName("工具反注册后不可查询")
    void testToolUnregistration() {
        ToolDefinition toolDef = createMockTool(TOOL_READ, ToolCategory.READ);
        service.registerTool(toolDef);

        boolean unregistered = service.unregisterTool(TOOL_READ);
        assertTrue(unregistered, "反注册应返回 true");

        Optional<ToolDefinition> retrieved = service.getTool(TOOL_READ);
        assertFalse(retrieved.isPresent(), "反注册后应查询不到工具");
    }

    @Test
    @Order(3)
    @DisplayName("重复注册相同工具名应覆盖或抛出异常")
    void testDuplicateToolRegistration() {
        ToolDefinition toolDef1 = createMockTool(TOOL_READ, ToolCategory.READ);
        ToolDefinition toolDef2 = createMockTool(TOOL_READ, ToolCategory.WRITE);

        service.registerTool(toolDef1);
        service.registerTool(toolDef2);

        Optional<ToolDefinition> retrieved = service.getTool(TOOL_READ);
        assertTrue(retrieved.isPresent());
        // 最后一次注册的类别应为 WRITE（覆盖策略）
        assertEquals(ToolCategory.WRITE, retrieved.get().getCategory());
    }

    // ========================================================================
    // 2. 工具执行基础功能
    // ========================================================================

    @Test
    @Order(4)
    @DisplayName("执行已注册工具，返回成功结果")
    void testExecuteToolSuccess() throws Exception {
        ToolDefinition toolDef = createMockTool(TOOL_READ, ToolCategory.READ);
        service.registerTool(toolDef);
        when(permissionManager.canExecute(anyString())).thenReturn(true);

        ToolExecutionResult result = service.executeTool(TOOL_READ, "{\"args\":[]}")
                .get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(result.isSuccess(), "执行应成功");
    }

    @Test
    @Order(5)
    @DisplayName("执行未注册工具应抛出异常")
    void testExecuteUnregisteredTool() {
        when(permissionManager.canExecute(anyString())).thenReturn(true);

        CompletableFuture<ToolExecutionResult> future = service.executeTool("NonExistent", "{}");

        assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS),
                "未注册工具应抛出异常");
    }

    // ========================================================================
    // 3. 权限校验集成
    // ========================================================================

    @Test
    @Order(6)
    @DisplayName("权限校验在工具执行前触发")
    void testPermissionCheckBeforeExecute() throws Exception {
        ToolDefinition toolDef = createMockTool(TOOL_READ, ToolCategory.READ);
        service.registerTool(toolDef);
        when(permissionManager.canExecute(TOOL_READ)).thenReturn(true);

        service.executeTool(TOOL_READ, "{}").get(5, TimeUnit.SECONDS);

        // 验证权限管理器被调用
        verify(permissionManager, times(1)).canExecute(TOOL_READ);
    }

    @Test
    @Order(7)
    @DisplayName("权限不足阻止工具执行")
    void testPermissionDeniedBlocksExecution() {
        ToolDefinition toolDef = createMockTool(TOOL_WRITE, ToolCategory.WRITE);
        service.registerTool(toolDef);
        when(permissionManager.canExecute(TOOL_WRITE)).thenReturn(false);

        CompletableFuture<ToolExecutionResult> future = service.executeTool(TOOL_WRITE, "{}");

        assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS),
                "权限不足应阻止执行");
    }

    // ========================================================================
    // 4. 完整生命周期
    // ========================================================================

    @Test
    @Order(8)
    @DisplayName("工具完整生命周期：注册→执行→完成→历史记录")
    void testExecuteToolLifecycle() throws Exception {
        ToolDefinition toolDef = createMockTool(TOOL_READ, ToolCategory.READ);
        service.registerTool(toolDef);
        when(permissionManager.canExecute(TOOL_READ)).thenReturn(true);

        // 执行
        ToolExecutionResult result = service.executeTool(TOOL_READ, "{\"cmd\":\"ls\"}")
                .get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());

        // 查询历史
        List<ToolExecutionHistory> history = service.getExecutionHistory();
        assertFalse(history.isEmpty(), "执行历史不应为空");
        assertEquals(1, history.size(), "应有 1 条历史记录");

        ToolExecutionHistory latest = history.get(0);
        assertEquals(TOOL_READ, latest.getToolName());
        assertTrue(latest.isCompleted(), "执行应标记为已完成");
    }

    // ========================================================================
    // 5. 超时处理
    // ========================================================================

    @Test
    @Order(9)
    @DisplayName("工具执行超时应正确处理")
    void testToolExecutionTimeout() {
        ToolDefinition toolDef = createMockTool(TOOL_TIMEOUT, ToolCategory.READ);
        service.registerTool(toolDef);
        when(permissionManager.canExecute(TOOL_TIMEOUT)).thenReturn(true);

        // 设置超时
        CompletableFuture<ToolExecutionResult> future = service.executeToolWithTimeout(
                TOOL_TIMEOUT, "{}", 1, TimeUnit.SECONDS);

        assertThrows(TimeoutException.class,
                () -> future.get(2, TimeUnit.SECONDS),
                "超时工具应抛出 TimeoutException");
    }

    // ========================================================================
    // 6. 并发执行
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("多个工具可以并发执行")
    void testConcurrentToolExecution() throws Exception {
        ToolDefinition toolDef = createMockTool(TOOL_READ, ToolCategory.READ);
        service.registerTool(toolDef);
        when(permissionManager.canExecute(anyString())).thenReturn(true);

        // 并发执行 5 次
        CompletableFuture<ToolExecutionResult>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = service.executeTool(TOOL_READ, "{\"task\":\"task" + i + "\"}");
        }

        // 全部完成后验证
        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        for (CompletableFuture<ToolExecutionResult> future : futures) {
            assertTrue(future.get().isSuccess(), "并发执行应全部成功");
        }

        // 历史记录应为 5 条
        assertEquals(5, service.getExecutionHistory().size());
    }

    // ========================================================================
    // 7. 链式调用
    // ========================================================================

    @Test
    @Order(11)
    @DisplayName("链式调用：工具A结果作为工具B参数")
    void testMultipleToolChainedExecution() throws Exception {
        ToolDefinition toolA = createMockTool("ChainToolA", ToolCategory.READ);
        ToolDefinition toolB = createMockTool("ChainToolB", ToolCategory.WRITE);
        service.registerTool(toolA);
        service.registerTool(toolB);
        when(permissionManager.canExecute(anyString())).thenReturn(true);

        // 执行工具A
        ToolExecutionResult resultA = service.executeTool("ChainToolA", "{\"action\":\"generate\"}")
                .get(5, TimeUnit.SECONDS);
        assertTrue(resultA.isSuccess());

        // 使用工具A的结果作为工具B的输入
        String inputForB = "{\"input\":\"" + resultA.getOutput() + "\"}";
        ToolExecutionResult resultB = service.executeTool("ChainToolB", inputForB)
                .get(5, TimeUnit.SECONDS);
        assertTrue(resultB.isSuccess());

        // 历史应有 2 条记录
        assertEquals(2, service.getExecutionHistory().size());
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private ToolDefinition createMockTool(String name, ToolCategory category) {
        return new ToolDefinition() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public ToolCategory getCategory() {
                return category;
            }

            @Override
            public String getDescription() {
                return "Mock tool for testing: " + name;
            }

            @Override
            public CompletableFuture<ToolExecutionResult> execute(
                    String input, ToolExecutionStateMachine stateMachine) {
                return CompletableFuture.completedFuture(
                        new ToolExecutionResult(true, "Executed: " + name, null));
            }
        };
    }
}
