package com.jwcode.core.hook;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hook 系统集成测试
 *
 * <p>测试 Hook 注册中心、执行链的完整链路。</p>
 */
@DisplayName("Hook 系统集成测试")
public class HookSystemIntegrationTest {

    private HookRegistry registry;
    private HookChain chain;

    @BeforeEach
    void setUp() {
        registry = new HookRegistry();
        chain = HookChain.createSimple(registry);
    }

    // ==================== HookRegistry 测试 ====================

    @Test
    @DisplayName("Hook 注册中心 - 注册与获取 Hook 执行器")
    void testRegisterAndGetHook() {
        HookExecutor mockHook = Mockito.mock(HookExecutor.class);
        Mockito.when(mockHook.getName()).thenReturn("test-hook");
        Mockito.when(mockHook.isEnabled()).thenReturn(true);

        registry.register(mockHook);

        List<HookExecutor> hooks = registry.getAllExecutors();
        assertAll("Hook 注册验证",
            () -> assertFalse(hooks.isEmpty(), "应获取到已注册的 Hook"),
            () -> assertTrue(hooks.contains(mockHook), "列表应包含注册的 Hook")
        );
    }

    @Test
    @DisplayName("Hook 注册中心 - 批量注册")
    void testRegisterAll() {
        HookExecutor h1 = Mockito.mock(HookExecutor.class);
        Mockito.when(h1.getName()).thenReturn("h1");
        Mockito.when(h1.isEnabled()).thenReturn(true);
        HookExecutor h2 = Mockito.mock(HookExecutor.class);
        Mockito.when(h2.getName()).thenReturn("h2");
        Mockito.when(h2.isEnabled()).thenReturn(true);

        registry.registerAll(List.of(h1, h2));
        assertEquals(2, registry.getAllExecutors().size());
    }

    @Test
    @DisplayName("Hook 注册中心 - 注销 Hook")
    void testUnregisterHook() {
        HookExecutor hook = Mockito.mock(HookExecutor.class);
        Mockito.when(hook.getName()).thenReturn("test-hook");
        Mockito.when(hook.isEnabled()).thenReturn(true);

        registry.register(hook);
        assertFalse(registry.getAllExecutors().isEmpty(), "注册后应存在");

        registry.unregister("test-hook");
        assertTrue(registry.getAllExecutors().isEmpty(), "注销后应移除");
    }

    @Test
    @DisplayName("Hook 注册中心 - 清除所有")
    void testClear() {
        HookExecutor hook = Mockito.mock(HookExecutor.class);
        Mockito.when(hook.getName()).thenReturn("test-hook");
        Mockito.when(hook.isEnabled()).thenReturn(true);
        registry.register(hook);

        registry.clear();
        assertTrue(registry.getAllExecutors().isEmpty());
    }

    @Test
    @DisplayName("Hook 注册中心 - 统计信息")
    void testRegistryStats() {
        HookExecutor hook = Mockito.mock(HookExecutor.class);
        Mockito.when(hook.getName()).thenReturn("test-hook");
        Mockito.when(hook.isEnabled()).thenReturn(true);
        registry.register(hook);

        assertNotNull(registry.getStats());
    }

    @Test
    @DisplayName("Hook 注册中心 - 按事件类型获取执行器")
    void testGetExecutorsForEvent() {
        HookExecutor hook = Mockito.mock(HookExecutor.class);
        Mockito.when(hook.getName()).thenReturn("test-hook");
        Mockito.when(hook.isEnabled()).thenReturn(true);
        registry.register(hook);

        List<HookExecutor> executors = registry.getExecutorsFor(HookEventType.PRE_TOOL_USE);
        assertNotNull(executors);
    }

    // ==================== HookChain 测试 ====================

    @Test
    @DisplayName("HookChain - 执行链返回 ALLOW 决策")
    void testHookChainAllow() {
        HookExecutor allowHook = Mockito.mock(HookExecutor.class);
        Mockito.when(allowHook.getName()).thenReturn("allow-hook");
        Mockito.when(allowHook.isEnabled()).thenReturn(true);
        Mockito.when(allowHook.supportsEvent(Mockito.any())).thenReturn(true);
        Mockito.when(allowHook.getPriority()).thenReturn(HookPriority.USER);
        Mockito.when(allowHook.execute(Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(
                HookResult.allow("allow-hook", "允许")));

        registry.register(allowHook);

        HookContext context = HookContext.forPreToolUse(
            "session-1", "test-agent", "test-tool", null, null);

        HookResult result = chain.execute(context);
        assertNotNull(result, "决策结果不应为 null");
        assertEquals(HookDecision.ALLOW, result.getDecision(), "应返回 ALLOW");
    }

    @Test
    @DisplayName("HookChain - 拒绝决策")
    void testHookChainDeny() {
        HookExecutor denyHook = Mockito.mock(HookExecutor.class);
        Mockito.when(denyHook.getName()).thenReturn("deny-hook");
        Mockito.when(denyHook.isEnabled()).thenReturn(true);
        Mockito.when(denyHook.supportsEvent(Mockito.any())).thenReturn(true);
        Mockito.when(denyHook.getPriority()).thenReturn(HookPriority.USER);
        Mockito.when(denyHook.execute(Mockito.any()))
            .thenReturn(CompletableFuture.completedFuture(
                HookResult.deny("deny-hook", "拒绝执行")));

        registry.register(denyHook);

        HookContext context = HookContext.forPreToolUse(
            "session-1", "test-agent", "test-tool", null, null);

        HookResult result = chain.execute(context);
        assertNotNull(result, "决策结果不应为 null");
        assertEquals(HookDecision.DENY, result.getDecision(), "应返回 DENY");
    }

    // ==================== 完整链路 ====================

    @Test
    @DisplayName("完整链路：注册 → 执行 → 决策")
    void testFullHookChain() {
        HookExecutor hook = new HookExecutor() {
            @Override
            public CompletableFuture<HookResult> execute(HookContext context) {
                return CompletableFuture.completedFuture(
                    HookResult.allow("full-chain-hook", "通过"));
            }

            @Override
            public HookImplementationType getType() {
                return HookImplementationType.PROMPT;
            }

            @Override
            public String getName() {
                return "full-chain-hook";
            }
        };

        registry.register(hook);

        HookContext context = HookContext.forPreToolUse(
            "session-1", "test-agent", "test-tool", null, null);

        HookResult result = chain.execute(context);
        assertAll("完整 Hook 链路验证",
            () -> assertNotNull(result, "结果不应为 null"),
            () -> assertNotNull(result.getDecision(), "决策不应为 null"),
            () -> assertNotNull(result.getReason(), "原因不应为 null")
        );
    }
}
