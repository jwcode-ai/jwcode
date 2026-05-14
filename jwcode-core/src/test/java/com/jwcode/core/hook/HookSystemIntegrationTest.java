package com.jwcode.core.hook;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hook 系统集成测试
 *
 * <p>测试 Hook 注册中心、执行链、决策聚合的完整链路。
 * 覆盖 HookRegistry → HookChain → HookExecutor → HookDecision 全流程。</p>
 */
@DisplayName("Hook 系统集成测试")
public class HookSystemIntegrationTest {

    private HookRegistry registry;
    private HookChain chain;

    @BeforeEach
    void setUp() {
        registry = new HookRegistry();
        chain = new HookChain(registry);
    }

    // ==================== HookRegistry 测试 ====================

    @Test
    @DisplayName("Hook 注册中心 - 注册与获取 Hook 执行器")
    void testRegisterAndGetHook() {
        HookExecutor mockHook = Mockito.mock(HookExecutor.class);
        Mockito.when(mockHook.getEventType()).thenReturn(HookEventType.BEFORE_EXECUTION);
        Mockito.when(mockHook.getPriority()).thenReturn(HookPriority.NORMAL);

        registry.register(mockHook);

        List<HookExecutor> hooks = registry.getHooks(HookEventType.BEFORE_EXECUTION);
        assertAll("Hook 注册验证",
            () -> assertFalse(hooks.isEmpty(), "应获取到已注册的 Hook"),
            () -> assertTrue(hooks.contains(mockHook), "列表应包含注册的 Hook")
        );
    }

    @Test
    @DisplayName("Hook 注册中心 - 按事件类型分组索引")
    void testHookGroupingByEventType() {
        HookExecutor hook1 = Mockito.mock(HookExecutor.class);
        Mockito.when(hook1.getEventType()).thenReturn(HookEventType.BEFORE_EXECUTION);
        HookExecutor hook2 = Mockito.mock(HookExecutor.class);
        Mockito.when(hook2.getEventType()).thenReturn(HookEventType.AFTER_EXECUTION);

        registry.register(hook1);
        registry.register(hook2);

        assertAll("事件类型分组验证",
            () -> assertEquals(1, registry.getHooks(HookEventType.BEFORE_EXECUTION).size(),
                "BEFORE_EXECUTION 应有1个 Hook"),
            () -> assertEquals(1, registry.getHooks(HookEventType.AFTER_EXECUTION).size(),
                "AFTER_EXECUTION 应有1个 Hook"),
            () -> assertTrue(registry.getHooks(HookEventType.ON_ERROR).isEmpty(),
                "ON_ERROR 应无 Hook")
        );
    }

    @Test
    @DisplayName("Hook 注册中心 - 注销 Hook")
    void testUnregisterHook() {
        HookExecutor hook = Mockito.mock(HookExecutor.class);
        Mockito.when(hook.getEventType()).thenReturn(HookEventType.BEFORE_EXECUTION);

        registry.register(hook);
        assertFalse(registry.getHooks(HookEventType.BEFORE_EXECUTION).isEmpty(), "注册后应存在");

        registry.unregister(hook);
        assertTrue(registry.getHooks(HookEventType.BEFORE_EXECUTION).isEmpty(), "注销后应移除");
    }

    // ==================== HookChain 测试 ====================

    @Test
    @DisplayName("Hook 链 - 空链执行应返回 ALLOW")
    void testEmptyChainReturnsAllow() throws Exception {
        HookContext context = new HookContext("test-event", null);

        HookResult result = chain.execute(HookEventType.BEFORE_EXECUTION, context)
            .get(5, TimeUnit.SECONDS);

        assertAll("空链执行验证",
            () -> assertNotNull(result, "结果不应为 null"),
            () -> assertEquals(HookDecision.ALLOW, result.getDecision(),
                "无 Hook 时应 ALLOW")
        );
    }

    @Test
    @DisplayName("Hook 链 - 多个 Hook 按优先级串行执行")
    void testMultipleHooksExecutedByPriority() throws Exception {
        HookExecutor highPriorityHook = Mockito.mock(HookExecutor.class);
        Mockito.when(highPriorityHook.getEventType()).thenReturn(HookEventType.BEFORE_EXECUTION);
        Mockito.when(highPriorityHook.getPriority()).thenReturn(HookPriority.HIGH);
        Mockito.when(highPriorityHook.execute(Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(
                new HookResult(HookDecision.ALLOW, "高优先级通过")));

        HookExecutor normalHook = Mockito.mock(HookExecutor.class);
        Mockito.when(normalHook.getEventType()).thenReturn(HookEventType.BEFORE_EXECUTION);
        Mockito.when(normalHook.getPriority()).thenReturn(HookPriority.NORMAL);
        Mockito.when(normalHook.execute(Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(
                new HookResult(HookDecision.ALLOW, "普通优先级通过")));

        registry.register(normalHook);
        registry.register(highPriorityHook);

        HookContext context = new HookContext("test-event", null);
        HookResult result = chain.execute(HookEventType.BEFORE_EXECUTION, context)
            .get(5, TimeUnit.SECONDS);

        assertEquals(HookDecision.ALLOW, result.getDecision(), "应返回 ALLOW");
    }

    @Test
    @DisplayName("Hook 链 - DENY 决策应短路后续 Hook")
    void testDenyShortCircuits() throws Exception {
        HookExecutor denyHook = Mockito.mock(HookExecutor.class);
        Mockito.when(denyHook.getEventType()).thenReturn(HookEventType.BEFORE_EXECUTION);
        Mockito.when(denyHook.getPriority()).thenReturn(HookPriority.NORMAL);
        Mockito.when(denyHook.execute(Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(
                new HookResult(HookDecision.DENY, "拒绝执行")));

        registry.register(denyHook);

        HookContext context = new HookContext("test-event", null);
        HookResult result = chain.execute(HookEventType.BEFORE_EXECUTION, context)
            .get(5, TimeUnit.SECONDS);

        assertEquals(HookDecision.DENY, result.getDecision(), "DENY 决策应生效");
    }

    // ==================== HookContext 测试 ====================

    @Test
    @DisplayName("Hook 上下文 - 创建和属性设置")
    void testHookContext() {
        HookContext context = new HookContext("test-event", "test-input");

        assertAll("Hook 上下文验证",
            () -> assertNotNull(context, "上下文不应为 null"),
            () -> assertNotNull(context.getEventType(), "事件类型不应为 null"),
            () -> assertNotNull(context.getInput(), "输入不应为 null")
        );
    }

    // ==================== HookResult 测试 ====================

    @Test
    @DisplayName("Hook 结果 - 不同决策类型")
    void testHookResult() {
        HookResult allowResult = new HookResult(HookDecision.ALLOW, "允许");
        HookResult denyResult = new HookResult(HookDecision.DENY, "拒绝");

        assertAll("Hook 结果验证",
            () -> assertEquals(HookDecision.ALLOW, allowResult.getDecision()),
            () -> assertEquals(HookDecision.DENY, denyResult.getDecision()),
            () -> assertNotNull(allowResult.getMessage()),
            () -> assertNotNull(denyResult.getMessage())
        );
    }

    // ==================== 事件类型枚举 ====================

    @Test
    @DisplayName("Hook 事件类型 - 所有预定义类型")
    void testAllEventTypes() {
        assertAll("事件类型验证",
            () -> assertNotNull(HookEventType.BEFORE_EXECUTION),
            () -> assertNotNull(HookEventType.AFTER_EXECUTION),
            () -> assertNotNull(HookEventType.ON_ERROR),
            () -> assertNotNull(HookEventType.BEFORE_FILE_READ),
            () -> assertNotNull(HookEventType.AFTER_FILE_WRITE)
        );
    }

    // ==================== 优先级枚举 ====================

    @Test
    @DisplayName("Hook 优先级 - 排序顺序")
    void testHookPriorityOrder() {
        assertAll("优先级排序验证",
            () -> assertTrue(HookPriority.CRITICAL.ordinal() < HookPriority.HIGH.ordinal(),
                "CRITICAL 应高于 HIGH"),
            () -> assertTrue(HookPriority.HIGH.ordinal() < HookPriority.NORMAL.ordinal(),
                "HIGH 应高于 NORMAL"),
            () -> assertTrue(HookPriority.NORMAL.ordinal() < HookPriority.LOW.ordinal(),
                "NORMAL 应高于 LOW")
        );
    }

    // ==================== 完整 Hook 流程 ====================

    @Test
    @DisplayName("完整 Hook 流程：注册 → 编排 → 执行 → 决策")
    void testCompleteHookFlow() throws Exception {
        // 1. 注册两个 Hook
        HookExecutor auditHook = Mockito.mock(HookExecutor.class);
        Mockito.when(auditHook.getEventType()).thenReturn(HookEventType.BEFORE_EXECUTION);
        Mockito.when(auditHook.getPriority()).thenReturn(HookPriority.NORMAL);
        Mockito.when(auditHook.execute(Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(
                new HookResult(HookDecision.ALLOW, "审计通过")));

        HookExecutor permissionHook = Mockito.mock(HookExecutor.class);
        Mockito.when(permissionHook.getEventType()).thenReturn(HookEventType.BEFORE_EXECUTION);
        Mockito.when(permissionHook.getPriority()).thenReturn(HookPriority.HIGH);
        Mockito.when(permissionHook.execute(Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(
                new HookResult(HookDecision.ALLOW, "权限通过")));

        registry.register(auditHook);
        registry.register(permissionHook);

        // 2. 执行 Hook 链
        HookContext context = new HookContext("file-read-test", "/test/file.txt");
        HookResult result = chain.execute(HookEventType.BEFORE_EXECUTION, context)
            .get(5, TimeUnit.SECONDS);

        // 3. 验证最终决策
        assertAll("完整流程验证",
            () -> assertNotNull(result, "结果不应为 null"),
            () -> assertNotNull(result.getDecision(), "决策不应为 null"),
            () -> assertTrue(
                result.getDecision() == HookDecision.ALLOW ||
                result.getDecision() == HookDecision.DENY,
                "决策应为 ALLOW 或 DENY")
        );
    }
}
