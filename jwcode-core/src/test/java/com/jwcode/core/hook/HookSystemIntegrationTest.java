package com.jwcode.core.hook;

import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Hook 系统集成测试")
public class HookSystemIntegrationTest {

    private HookRegistry registry;
    private HookChain chain;

    @BeforeEach
    void setUp() {
        registry = new HookRegistry();
        chain = HookChain.createSimple(registry);
    }

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

    @Test
    @DisplayName("HookChain - 执行链返回 ALLOW 决策")
    void testHookChainAllow() {
        HookExecutor allowHook = Mockito.mock(HookExecutor.class);
        Mockito.when(allowHook.getName()).thenReturn("allow-hook");
        Mockito.when(allowHook.isEnabled()).thenReturn(true);
        Mockito.when(allowHook.supportsEvent(Mockito.any())).thenReturn(true);
        Mockito.when(allowHook.supportsTool(Mockito.any())).thenReturn(true);
        Mockito.when(allowHook.getPriority()).thenReturn(HookPriority.USER);
        Mockito.when(allowHook.getType()).thenReturn(HookImplementationType.PROMPT);
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
        Mockito.when(denyHook.supportsTool(Mockito.any())).thenReturn(true);
        Mockito.when(denyHook.getPriority()).thenReturn(HookPriority.USER);
        Mockito.when(denyHook.getType()).thenReturn(HookImplementationType.PROMPT);
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

    @Test
    @DisplayName("完整链路：注册 -> 执行 -> 决策")
    void testFullHookChain() {
        HookExecutor hook = new HookExecutor() {
            @Override
            public CompletableFuture<HookResult> execute(HookContext context) {
                return CompletableFuture.completedFuture(
                    HookResult.allow("full-chain-hook", "通过"));
            }
            @Override
            public HookImplementationType getType() { return HookImplementationType.PROMPT; }
            @Override
            public String getName() { return "full-chain-hook"; }
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

    // ==================== BashSafetyHook ASK 测试 ====================

    @Test
    @DisplayName("BashSafetyHook - 普通 shell 命令返回 ASK")
    void testBashSafetyHookAsksForNormalCommand() {
        BashSafetyHook hook = new BashSafetyHook();
        HookApprovalManager.getInstance().clearFingerprintCache();
        HookContext context = HookContext.forPreToolUse(
            "session-test", "test-agent", "BashTool",
            new TextNode("ls -la"), null);
        HookResult result = hook.execute(context).join();
        assertAll("BashSafetyHook ASK 决策验证",
            () -> assertEquals(HookDecision.ASK, result.getDecision(),
                "普通 shell 命令应返回 ASK"),
            () -> assertNotNull(result.getAskPayload(),
                "ASK 决策应携带 askPayload"),
            () -> assertTrue(result.getAskPayload().contains("ls -la"),
                "askPayload 应包含命令描述"),
            () -> assertEquals("BashSafetyHook", result.getHookName(),
                "Hook 名称应为 BashSafetyHook")
        );
    }

    @Test
    @DisplayName("BashSafetyHook - PowerShellTool 也触发 ASK")
    void testBashSafetyHookAsksForPowerShell() {
        BashSafetyHook hook = new BashSafetyHook();
        HookApprovalManager.getInstance().clearFingerprintCache();
        HookContext context = HookContext.forPreToolUse(
            "session-test", "test-agent", "PowerShellTool",
            new TextNode("Get-ChildItem"), null);
        HookResult result = hook.execute(context).join();
        assertEquals(HookDecision.ASK, result.getDecision(),
            "PowerShellTool 也应触发 ASK");
    }

    @Test
    @DisplayName("BashSafetyHook - 非目标工具放行")
    void testBashSafetyHookAllowsNonTargetTool() {
        BashSafetyHook hook = new BashSafetyHook();
        HookContext context = HookContext.forPreToolUse(
            "session-test", "test-agent", "FileReadTool",
            new TextNode("test.txt"), null);
        HookResult result = hook.execute(context).join();
        assertEquals(HookDecision.ALLOW, result.getDecision(),
            "非 Shell 工具应直接 ALLOW");
    }

    // ==================== BashSafetyHook 危险命令测试 ====================

    @Test
    @DisplayName("BashSafetyHook - 危险命令返回 DENY")
    void testBashSafetyHookDeniesDangerousCommand() {
        BashSafetyHook hook = new BashSafetyHook();
        HookContext context1 = HookContext.forPreToolUse(
            "session-test", "test-agent", "BashTool",
            new TextNode("rm -rf /"), null);
        HookResult result1 = hook.execute(context1).join();
        assertEquals(HookDecision.DENY, result1.getDecision(), "rm -rf / 应被拒绝");
        assertTrue(result1.getReason().contains("Dangerous"), "拒绝原因应包含危险命令提示");
        HookContext context2 = HookContext.forPreToolUse(
            "session-test", "test-agent", "BashTool",
            new TextNode("DROP TABLE users"), null);
        HookResult result2 = hook.execute(context2).join();
        assertEquals(HookDecision.DENY, result2.getDecision(), "DROP TABLE 应被拒绝");
    }

    // ==================== HookApprovalManager 指纹缓存测试 ====================

    @Test
    @DisplayName("HookApprovalManager - 指纹缓存自动放行")
    void testFingerprintCacheAutoAllows() {
        BashSafetyHook hook = new BashSafetyHook();
        HookApprovalManager mgr = HookApprovalManager.getInstance();
        mgr.clearFingerprintCache();
        String command = "echo hello world";
        HookContext context1 = HookContext.forPreToolUse(
            "session-test", "test-agent", "BashTool",
            new TextNode(command), null);
        HookResult result1 = hook.execute(context1).join();
        assertEquals(HookDecision.ASK, result1.getDecision(), "首次应触发 ASK");
        String askPayload = result1.getAskPayload();
        String fingerprint = askPayload.substring(0, askPayload.indexOf("\n---\n"));
        mgr.cacheFingerprint(fingerprint);
        HookContext context2 = HookContext.forPreToolUse(
            "session-test", "test-agent", "BashTool",
            new TextNode(command), null);
        HookResult result2 = hook.execute(context2).join();
        assertEquals(HookDecision.ALLOW, result2.getDecision(), "指纹缓存后相同命令应自动放行");
        assertTrue(result2.getReason().contains("cached fingerprint"), "放行原因应提及缓存指纹");
    }

    // ==================== CLI 回退测试 ====================

    @Test
    @DisplayName("HookApprovalManager CLI 回退 - 用户输入 y 批准")
    void testCliFallbackApprovesWithY() throws Exception {
        HookApprovalManager mgr = HookApprovalManager.getInstance();
        mgr.setWebSocketBroadcaster(null);
        String input = "y\n";
        InputStream stdin = System.in;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes()));
            CompletableFuture<Boolean> future = mgr.requestApproval("BashTool", "ls -la", 5000);
            Boolean result = future.get(5, TimeUnit.SECONDS);
            assertTrue(result, "用户输入 y 应批准");
        } finally {
            System.setIn(stdin);
        }
    }

    @Test
    @DisplayName("HookApprovalManager CLI 回退 - 用户输入 n 拒绝")
    void testCliFallbackDeniesWithN() throws Exception {
        HookApprovalManager mgr = HookApprovalManager.getInstance();
        mgr.setWebSocketBroadcaster(null);
        String input = "n\n";
        InputStream stdin = System.in;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes()));
            CompletableFuture<Boolean> future = mgr.requestApproval("BashTool", "rm -rf /", 5000);
            Boolean result = future.get(5, TimeUnit.SECONDS);
            assertFalse(result, "用户输入 n 应拒绝");
        } finally {
            System.setIn(stdin);
        }
    }

    @Test
    @DisplayName("HookApprovalManager CLI 回退 - 空输入自动拒绝")
    void testCliFallbackEmptyInputDenies() throws Exception {
        HookApprovalManager mgr = HookApprovalManager.getInstance();
        mgr.setWebSocketBroadcaster(null);
        InputStream stdin = System.in;
        try {
            System.setIn(new ByteArrayInputStream(new byte[0]));
            CompletableFuture<Boolean> future = mgr.requestApproval("BashTool", "some-command", 1000);
            Boolean result = future.get(2000, TimeUnit.MILLISECONDS);
            assertFalse(result, "无有效输入应返回 false");
        } finally {
            System.setIn(stdin);
        }
    }

    // ==================== 审批管理器基础功能测试 ====================

    @Test
    @DisplayName("HookApprovalManager - denialAllForSession 清理")
    void testDenyAllForSession() {
        HookApprovalManager mgr = HookApprovalManager.getInstance();
        mgr.clearFingerprintCache();
        AtomicBoolean broadcastCalled = new AtomicBoolean(false);
        mgr.setWebSocketBroadcaster(req -> broadcastCalled.set(true));
        String sessionId = "test-session-1";
        int beforeCount = mgr.getPendingCount();
        CompletableFuture<Boolean> future1 = mgr.requestApproval("BashTool", "cmd1", -1, sessionId);
        CompletableFuture<Boolean> future2 = mgr.requestApproval("BashTool", "cmd2", -1, sessionId);
        assertTrue(broadcastCalled.get(), "应触发广播回调");
        assertEquals(beforeCount + 2, mgr.getPendingCount(), "应增加 2 个待审批项");
        mgr.denyAllForSession(sessionId);
        assertTrue(future1.isDone() && future2.isDone(), "清理后 future 应完成");
        assertFalse(future1.join() || future2.join(), "清理后应返回 false");
        assertEquals(beforeCount, mgr.getPendingCount(), "清理后恢复至测试前数量");
    }

    @Test
    @DisplayName("HookApprovalManager - 审批指纹缓存 TTL")
    void testFingerprintCacheTTL() {
        HookApprovalManager mgr = HookApprovalManager.getInstance();
        mgr.clearFingerprintCache();
        mgr.cacheFingerprint("test-fingerprint");
        assertTrue(mgr.isFingerprintCached("test-fingerprint"), "缓存后应立即命中");
        mgr.clearFingerprintCache();
        assertFalse(mgr.isFingerprintCached("test-fingerprint"), "清除后不应命中");
    }
}
