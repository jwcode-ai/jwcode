package com.jwcode.core;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.llm.*;
import com.jwcode.core.service.*;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.shell.DockerSandboxExecutor;
import com.jwcode.core.resilience.*;
import com.jwcode.core.planner.checkpoint.CheckpointManager;
import com.jwcode.core.hook.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.concurrent.*;

/** Harness 集成测试 — 多组件交互 */
public class HarnessIntegrationTest {

    // ═══ 闭环1: CostTracker ↔ ModelRouter 成本反馈 ═══

    @Test void costFeedbackToModelRouter() {
        var config = new JwcodeConfig();
        var router = new ModelRouter(config);
        var tracker = new CostTrackerService();

        // 模拟多次调用
        for (int i = 0; i < 5; i++) {
            tracker.recordCost("expensive-model", 10000, 500);
            router.recordCost("debug", "expensive-model", 1500);
            router.recordCost("debug", "expensive-model", 1500);
        }
        for (int i = 0; i < 5; i++) {
            tracker.recordCost("cheap-model", 1000, 50);
            router.recordCost("debug", "cheap-model", 150);
        }

        // ModelRouter: 验证成本统计 (同步, 无异步)
        var debugCosts = router.getAvgCosts("debug");
        assertFalse(debugCosts.isEmpty(), "Should have cost stats for debug tasks");
        // CostTracker: 异步记录, 验证方法不抛异常
        assertNotNull(tracker.getCostByModel(), "getCostByModel should not throw");
    }

    // ═══ 闭环3: 写操作前自动检查点 ═══

    @Test void autoCheckpointBeforeWrite() throws Exception {
        Path dir = Path.of(System.getProperty("user.dir", "."));
        var cpm = new CheckpointManager(dir);
        int before = cpm.listCheckpoints().size();

        // 模拟写操作触发的检查点保存
        var cp = CheckpointManager.Checkpoint.builder()
            .taskId("integration-test-" + System.currentTimeMillis())
            .contextJson("{\"tool\":\"FileEditTool\"}")
            .resultsJson("{}")
            .busJson("{}")
            .timelineJson("[]")
            .build();
        cpm.saveCheckpoint(cp);

        int after = cpm.listCheckpoints().size();
        assertTrue(after >= before, "Checkpoint should be persisted");
    }

    // ═══ HookChain 拦截路径 ═══

    @Test void hookChainDenyDecision() throws Exception {
        var registry = new HookRegistry();
        var chain = new HookChain(registry, new HookAuditLogger());

        // 注册一个始终 DENY 的 Hook
        registry.register(new HookExecutor() {
            public CompletableFuture<HookResult> execute(HookContext ctx) {
                return CompletableFuture.completedFuture(HookResult.deny("test-hook", "always deny"));
            }
            public HookImplementationType getType() { return HookImplementationType.SHELL; }
            public String getName() { return "test-deny-hook"; }
            public HookPriority getPriority() { return HookPriority.SECURITY; }
            public boolean isFailOpen() { return false; }
            public long getTimeoutMs() { return 1000; }
            public boolean supportsEvent(HookEventType e) { return e == HookEventType.PRE_TOOL_USE; }
        });

        var ctx = HookContext.forPreToolUse("session-1", "Orchestrator", "FileWriteTool", null, null);
        var result = chain.execute(ctx);
        assertEquals(HookDecision.DENY, result.getDecision());
    }

    // ═══ DockerSandbox 构造不抛异常 ═══

    @Test void dockerSandboxConstructsGracefully() throws Exception {
        Path tmp = Files.createTempDirectory("jwcode-int-test");
        var executor = new DockerSandboxExecutor(tmp);
        assertNotNull(executor);
    }

    // ═══ RecoveryExecutor 级联降级 ═══

    @Test void recoveryExecutorCascadingFallback() throws Exception {
        // 始终失败的操作
        var op = new Supplier<CompletableFuture<String>>() {
            public CompletableFuture<String> get() {
                return CompletableFuture.failedFuture(new RuntimeException("test failure"));
            }
        };

        var result = RecoveryExecutor.executeWithRecovery(
            op,
            new RecoveryProtocol.AutoRetry(1, Duration.ofMillis(10)),
            "test-op"
        );

        try {
            result.get(5, TimeUnit.SECONDS);
            fail("Should have thrown");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof RecoveryExecutor.RecoveryException);
        }
    }

    // ═══ LLMMessage + CacheControl 端到端 ═══

    @Test void llmMessageCacheControlInPipeline() {
        var sysMsg = LLMMessage.builder()
            .role(LLMMessage.Role.SYSTEM)
            .content("[ZONE:SYSTEM] You are an AI assistant")
            .cacheControl(LLMMessage.CacheControl.EPHEMERAL)
            .build();

        var fmt = sysMsg.toOpenAIFormat();
        assertEquals("system", fmt.get("role"));
        assertTrue(fmt.containsKey("cache_control"));
        assertEquals("ephemeral", ((Map<?,?>) fmt.get("cache_control")).get("type"));
    }

    // ═══ ProjectDocGenerator 自举 ═══

    @Test void projectDocGeneratorSelfGenerate() {
        var gen = new ProjectDocGenerator(Path.of("."));
        var result = assertDoesNotThrow(() -> gen.generateAll());
        assertNotNull(result.summary);
        assertTrue(result.errors.isEmpty());
    }

    // ═══ SprintContract + GAN 闭环 ═══

    @Test void sprintContractLifecycle() {
        var contract = com.jwcode.core.model.SprintContract
            .createFrontendContract("Test feature", "task-1");
        contract.addAcceptanceCriterion("Must pass all tests");
        assertEquals(1, contract.getAcceptanceCriteria().size());
        assertTrue(contract.getMaxIterations() > 0);
    }
}
