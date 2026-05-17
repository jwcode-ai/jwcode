package com.jwcode.core.service;

import com.jwcode.core.service.ToolExecutionService.ToolDefinition;
import com.jwcode.core.service.ToolExecutionService.ToolExecutionResult;
import com.jwcode.core.service.ToolExecutionService.ToolExecutionRecord;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolExecutionServiceIntegrationTest {

    private ToolExecutionService service;

    // Mock 工具定义
    private static final String TOOL_READ = "MockReadTool";
    private static final String TOOL_WRITE = "MockWriteTool";

    @BeforeEach
    void setUp() {
        service = new ToolExecutionService();
    }

    @AfterEach
    void tearDown() {
        // 清理注册的工具
        for (ToolDefinition tool : service.getRegisteredTools()) {
            service.unregisterTool(tool.name);
        }
    }

    // ========================================================================
    // 1. 工具注册/反注册
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("工具注册成功后可查询到")
    void testToolRegistration() {
        ToolDefinition toolDef = new ToolDefinition(TOOL_READ, "读取工具",
                new String[]{"path"}, "读取文件内容");

        service.registerTool(toolDef);

        List<ToolDefinition> tools = service.getRegisteredTools();
        boolean found = tools.stream().anyMatch(t -> t.name.equals(TOOL_READ));
        assertTrue(found, "注册后应能查询到工具");
    }

    @Test
    @Order(2)
    @DisplayName("工具反注册后不可查询")
    void testToolUnregistration() {
        ToolDefinition toolDef = new ToolDefinition(TOOL_READ, "读取工具",
                new String[]{"path"}, "读取文件内容");
        service.registerTool(toolDef);

        service.unregisterTool(TOOL_READ);

        List<ToolDefinition> tools = service.getRegisteredTools();
        boolean found = tools.stream().anyMatch(t -> t.name.equals(TOOL_READ));
        assertFalse(found, "反注册后应查询不到工具");
    }

    @Test
    @Order(3)
    @DisplayName("重复注册相同工具名应覆盖")
    void testDuplicateToolRegistration() {
        ToolDefinition toolDef1 = new ToolDefinition(TOOL_READ, "读取工具",
                new String[]{"path"}, "读取文件内容");
        ToolDefinition toolDef2 = new ToolDefinition(TOOL_READ, "读取工具v2",
                new String[]{"path", "encoding"}, "读取文件内容（带编码）");

        service.registerTool(toolDef1);
        service.registerTool(toolDef2);

        List<ToolDefinition> tools = service.getRegisteredTools();
        long count = tools.stream().filter(t -> t.name.equals(TOOL_READ)).count();
        assertEquals(1, count, "重复注册应覆盖，不应有重复条目");
    }

    // ========================================================================
    // 2. 工具执行基础功能
    // ========================================================================

    @Test
    @Order(4)
    @DisplayName("执行已注册工具，返回成功结果")
    void testExecuteToolSuccess() throws Exception {
        ToolDefinition toolDef = new ToolDefinition(TOOL_READ, "读取工具",
                new String[]{"path"}, "读取文件内容");
        service.registerTool(toolDef);

        ToolExecutionResult result = service.executeTool(TOOL_READ, Map.of("path", "/test.txt"))
                .get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(result.success, "执行应成功");
    }

    @Test
    @Order(5)
    @DisplayName("执行未注册工具应返回错误")
    void testExecuteUnregisteredTool() throws Exception {
        ToolExecutionResult result = service.executeTool("NonExistent", Map.of())
                .get(5, TimeUnit.SECONDS);

        assertFalse(result.success, "未注册工具应返回失败");
    }

    // ========================================================================
    // 3. 权限校验集成
    // ========================================================================

    @Test
    @Order(6)
    @DisplayName("工具执行需要必需参数")
    void testRequiredArgsCheck() throws Exception {
        ToolDefinition toolDef = new ToolDefinition(TOOL_READ, "读取工具",
                new String[]{"path"}, "读取文件内容");
        service.registerTool(toolDef);

        // 缺少必需参数 path
        ToolExecutionResult result = service.executeTool(TOOL_READ, Map.of())
                .get(5, TimeUnit.SECONDS);

        assertFalse(result.success, "缺少必需参数应返回失败");
        assertTrue(result.error != null && result.error.contains("path"),
                "错误信息应包含缺失的参数名");
    }

    @Test
    @Order(7)
    @DisplayName("权限设置可阻止工具执行")
    void testPermissionDeniedBlocksExecution() throws Exception {
        ToolDefinition toolDef = new ToolDefinition(TOOL_WRITE, "写入工具",
                new String[]{"path", "content"}, "写入文件内容");
        service.registerTool(toolDef);
        service.setToolPermission(TOOL_WRITE, ToolExecutionService.ToolPermissions.DENIED);

        ToolExecutionResult result = service.executeTool(TOOL_WRITE,
                Map.of("path", "/test.txt", "content", "hello"))
                .get(5, TimeUnit.SECONDS);

        assertFalse(result.success, "权限不足应阻止执行");
    }

    // ========================================================================
    // 4. 完整生命周期
    // ========================================================================

    @Test
    @Order(8)
    @DisplayName("工具完整生命周期：注册→执行→完成→历史记录")
    void testExecuteToolLifecycle() throws Exception {
        ToolDefinition toolDef = new ToolDefinition(TOOL_READ, "读取工具",
                new String[]{"path"}, "读取文件内容");
        service.registerTool(toolDef);

        // 执行
        ToolExecutionResult result = service.executeTool(TOOL_READ, Map.of("path", "/test.txt"))
                .get(5, TimeUnit.SECONDS);

        assertTrue(result.success);

        // 查询历史
        List<ToolExecutionRecord> history = service.getExecutionHistory(10);
        assertFalse(history.isEmpty(), "执行历史不应为空");
        assertEquals(1, history.size(), "应有 1 条历史记录");

        ToolExecutionRecord latest = history.get(0);
        assertEquals(TOOL_READ, latest.toolName);
    }

    // ========================================================================
    // 5. 并发执行
    // ========================================================================

    @Test
    @Order(9)
    @DisplayName("多个工具可以并发执行")
    void testConcurrentToolExecution() throws Exception {
        ToolDefinition toolDef = new ToolDefinition(TOOL_READ, "读取工具",
                new String[]{"path"}, "读取文件内容");
        service.registerTool(toolDef);

        // 并发执行 5 次
        @SuppressWarnings("unchecked")
        CompletableFuture<ToolExecutionResult>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = service.executeTool(TOOL_READ, Map.of("path", "/test" + i + ".txt"));
        }

        // 全部完成后验证
        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        for (CompletableFuture<ToolExecutionResult> future : futures) {
            assertTrue(future.get().success, "并发执行应全部成功");
        }

        // 历史记录应为 5 条
        assertEquals(5, service.getExecutionHistory(10).size());
    }

    // ========================================================================
    // 6. 链式调用
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("链式调用：工具A结果作为工具B参数")
    void testMultipleToolChainedExecution() throws Exception {
        ToolDefinition toolA = new ToolDefinition("ChainToolA", "链式工具A",
                new String[]{"action"}, "生成数据");
        ToolDefinition toolB = new ToolDefinition("ChainToolB", "链式工具B",
                new String[]{"input"}, "处理数据");
        service.registerTool(toolA);
        service.registerTool(toolB);

        // 执行工具A
        ToolExecutionResult resultA = service.executeTool("ChainToolA", Map.of("action", "generate"))
                .get(5, TimeUnit.SECONDS);
        assertTrue(resultA.success);

        // 使用工具A的结果作为工具B的输入
        ToolExecutionResult resultB = service.executeTool("ChainToolB",
                Map.of("input", resultA.message != null ? resultA.message : ""))
                .get(5, TimeUnit.SECONDS);
        assertTrue(resultB.success);

        // 历史应有 2 条记录
        assertEquals(2, service.getExecutionHistory(10).size());
    }
}
