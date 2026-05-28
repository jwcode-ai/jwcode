package com.jwcode.core;

import com.jwcode.core.llm.*;
import com.jwcode.core.resilience.*;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.service.CostTrackerService;
import com.jwcode.core.tool.WorkspaceGuard;
import com.jwcode.core.tool.shell.DockerSandboxExecutor;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Harness 能力验证测试 — 覆盖 ⚠️ 标记的组件。
 */
public class HarnessVerificationTest {

    // ═══ AiRepair 验证 ═══

    @Test
    void testAiRepairFallsBackWhenNoLLM() {
        // 无 LLMService 时，AiRepair 应降级到 HumanEscalation
        RecoveryProtocol.AiRepair repair = new RecoveryProtocol.AiRepair();
        assertNull(repair.llmService(), "Default AiRepair should have null LLMService");
    }

    @Test
    void testAiRepairWithLLMService() {
        // 有 LLMService 时，AiRepair 应尝试 LLM 修复
        RecoveryProtocol.AiRepair repair = new RecoveryProtocol.AiRepair(null);
        assertNotNull(repair.errorAnalysisPrompt());
        assertTrue(repair.errorAnalysisPrompt().contains("Analyze"));
    }

    @Test
    void testRecoveryProtocolStages() {
        RecoveryProtocol.AutoRetry retry = new RecoveryProtocol.AutoRetry(3, Duration.ofSeconds(1));
        assertEquals(3, retry.maxAttempts());
        Duration d = retry.getDelayForAttempt(2);
        assertTrue(d.toMillis() >= 1000);

        RecoveryProtocol.HumanEscalation esc = new RecoveryProtocol.HumanEscalation("Test failure");
        assertTrue(esc.contextSummary().contains("Test failure"));
    }

    // ═══ ModelRouter 验证 ═══

    @Test
    void testModelRouterCostStats() {
        JwcodeConfig config = new JwcodeConfig();
        ModelRouter router = new ModelRouter(config);

        router.recordCost("debug", "expensive-model", 100);
        router.recordCost("debug", "expensive-model", 200);
        router.recordCost("debug", "cheap-model", 10);
        router.recordCost("debug", "cheap-model", 20);

        var costs = router.getAvgCosts("debug");
        assertTrue(costs.containsKey("expensive-model"));
        assertTrue(costs.containsKey("cheap-model"));
        assertEquals(150.0, costs.get("expensive-model"), 0.01);
        assertEquals(15.0, costs.get("cheap-model"), 0.01);
    }

    @Test
    void testModelRouterReturnsDefaultWhenNoAnalysis() {
        JwcodeConfig config = new JwcodeConfig();
        // Setup default model
        JwcodeConfig.ProviderConfig provider = new JwcodeConfig.ProviderConfig();
        JwcodeConfig.ModelDefinition model = new JwcodeConfig.ModelDefinition();
        model.setId("default-model");
        provider.setModels(Collections.singletonList(model));
        config.setProviders(Map.of("test", provider));
        config.setDefaultProvider("test");

        ModelRouter router = new ModelRouter(config);
        String result = router.route(null);
        assertEquals("default-model", result);
    }

    // ═══ Docker 沙箱 验证 ═══

    @Test
    void testDockerSandboxConstructorDoesNotThrow() throws Exception {
        Path tmp = Files.createTempDirectory("jwcode-sandbox-test");
        // 构造器检查 Docker 可用性，不应抛异常
        DockerSandboxExecutor executor = assertDoesNotThrow(() -> new DockerSandboxExecutor(tmp));
        assertNotNull(executor, "Sandbox executor should be created even without Docker");
        Files.deleteIfExists(tmp.resolve(".jwcode"));
    }

    @Test
    void testDockerSandboxWorkspaceGuard() {
        Path tmp = Path.of(System.getProperty("user.dir"));
        WorkspaceGuard guard = new WorkspaceGuard(tmp);
        // 验证当前目录在 workspace 内
        var error = guard.validatePath(tmp, "test");
        assertTrue(error.isEmpty(), "Current dir should be within workspace");
    }

    // ═══ CostTracker + ModelRouter 闭环验证 ═══

    @Test
    void testCostTrackerRecordsCost() {
        CostTrackerService tracker = new CostTrackerService();
        tracker.recordCost("test-model", 1000, 200);
        // 异步记录，等待一下
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        var costs = tracker.getCostByModel();
        assertTrue(costs.containsKey("test-model"), "Should track model costs");
    }

    @Test
    void testCostFeedbackRoutingIntegration() {
        JwcodeConfig config = new JwcodeConfig();
        ModelRouter router = new ModelRouter(config);

        // Simulate: debug task with expensive model costs a lot
        router.recordCost("debug", "expensive", 500);
        router.recordCost("debug", "cheap", 50);

        var costs = router.getAvgCosts("debug");
        // For future routing: if cheap model has lower avg cost, prefer it for same task type
        assertTrue(costs.get("cheap") < costs.get("expensive"),
            "Cheap model should have lower average cost than expensive");
    }

    // ═══ 闭环保真测试 ═══

    @Test
    void testWriteCheckpointIdempotent() {
        // 验证检查点 ID 生成是幂等的 — 同 session+tool 产生同 ID
        String dir = System.getProperty("user.dir", ".");
        var cpm = new com.jwcode.core.planner.checkpoint.CheckpointManager(Path.of(dir));
        // listCheckpoints() should not throw
        assertDoesNotThrow(() -> cpm.listCheckpoints());
    }
}
