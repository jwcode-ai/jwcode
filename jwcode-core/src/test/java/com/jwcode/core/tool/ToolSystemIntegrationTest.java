package com.jwcode.core.tool;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具系统集成测试
 *
 * <p>测试工具注册中心、工具编排、参数验证等
 * 工具系统的完整功能链路。</p>
 */
@DisplayName("工具系统集成测试")
public class ToolSystemIntegrationTest {

    private ToolRegistry registry;
    private ToolOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        orchestrator = new ToolOrchestrator(registry);
    }

    // ==================== ToolRegistry 测试 ====================

    @Test
    @DisplayName("工具注册中心 - 注册和获取工具")
    void testRegisterAndGetTool() {
        Tool<?, ?, ?> mockTool = Mockito.mock(Tool.class);
        Mockito.when(mockTool.getName()).thenReturn("file-read");

        registry.register(mockTool);

        Tool<?, ?, ?> retrieved = registry.getTool("file-read");
        assertAll("工具注册验证",
            () -> assertNotNull(retrieved, "应获取到已注册的工具"),
            () -> assertEquals("file-read", retrieved.getName(), "工具名称匹配")
        );
    }

    @Test
    @DisplayName("工具注册中心 - 获取所有工具")
    void testGetAllTools() {
        Tool<?, ?, ?> tool1 = Mockito.mock(Tool.class);
        Mockito.when(tool1.getName()).thenReturn("tool1");
        Tool<?, ?, ?> tool2 = Mockito.mock(Tool.class);
        Mockito.when(tool2.getName()).thenReturn("tool2");

        registry.register(tool1);
        registry.register(tool2);

        List<Tool<?, ?, ?>> allTools = registry.getAllTools();
        assertEquals(2, allTools.size(), "应有2个工具");
    }

    @Test
    @DisplayName("工具注册中心 - 注销工具")
    void testUnregisterTool() {
        Tool<?, ?, ?> tool = Mockito.mock(Tool.class);
        Mockito.when(tool.getName()).thenReturn("temp-tool");

        registry.register(tool);
        assertNotNull(registry.getTool("temp-tool"), "注册后应存在");

        registry.unregister("temp-tool");
        assertThrows(java.util.NoSuchElementException.class,
            () -> registry.getTool("temp-tool"), "注销后应抛出异常");
    }

    // ==================== ToolOrchestrator 测试 ====================

    @Test
    @DisplayName("工具编排器 - 按依赖顺序执行")
    void testDependencyOrderExecution() throws Exception {
        Tool<?, ?, ?> step1 = Mockito.mock(Tool.class);
        Mockito.when(step1.getName()).thenReturn("step1");
        Mockito.when(step1.getDependencies()).thenReturn(List.of());

        Tool<?, ?, ?> step2 = Mockito.mock(Tool.class);
        Mockito.when(step2.getName()).thenReturn("step2");
        Mockito.when(step2.getDependencies()).thenReturn(List.of("step1"));

        registry.register(step1);
        registry.register(step2);

        Map<String, ToolResult<?>> results = orchestrator.executeAll()
            .get(10, TimeUnit.SECONDS);

        assertAll("编排执行验证",
            () -> assertNotNull(results, "结果不应为 null"),
            () -> assertTrue(results.containsKey("step1"), "应包含 step1 的结果"),
            () -> assertTrue(results.containsKey("step2"), "应包含 step2 的结果")
        );
    }

    // ==================== ToolResult 测试 ====================

    @Test
    @DisplayName("工具结果 - 成功/失败结果")
    void testToolResult() {
        ToolResult<String> success = new ToolResult<>("operation completed");
        success.setSuccess(true);
        success.setContent("operation completed");

        ToolResult<String> failure = ToolResult.error("failed");

        assertAll("工具结果验证",
            () -> assertTrue(success.isSuccess(), "成功结果应为 true"),
            () -> assertFalse(failure.isSuccess(), "失败结果应为 false"),
            () -> assertEquals("operation completed", success.getContent(), "输出匹配")
        );
    }

    // ==================== 完整工具链路 ====================

    @Test
    @DisplayName("完整工具链路：注册 → 编排 → 执行 → 收集结果")
    void testCompleteToolChain() throws Exception {
        // 注册工具
        Tool<?, ?, ?> tool = Mockito.mock(Tool.class);
        Mockito.when(tool.getName()).thenReturn("test-tool");
        Mockito.when(tool.getDependencies()).thenReturn(List.of());

        registry.register(tool);

        // 执行编排
        Map<String, ToolResult<?>> results = orchestrator.executeAll()
            .get(10, TimeUnit.SECONDS);

        // 验证结果
        assertAll("完整工具链路验证",
            () -> assertNotNull(results, "结果不应为 null"),
            () -> assertTrue(results.containsKey("test-tool"), "应包含测试工具结果"),
            () -> assertNotNull(results.get("test-tool"), "工具执行结果不应为 null")
        );
    }
}
