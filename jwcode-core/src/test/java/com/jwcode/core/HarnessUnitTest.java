package com.jwcode.core;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.llm.*;
import com.jwcode.core.resilience.*;
import com.jwcode.core.service.*;
import com.jwcode.core.tool.WorkspaceGuard;
import com.jwcode.core.agent.WorkspaceMemoryStore;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Harness 组件单元测试 — 原子级，无外部依赖 */
public class HarnessUnitTest {

    // ═══ ModelRouter ═══

    @Test void modelRouterCostTracking() {
        var r = new ModelRouter(new JwcodeConfig());
        r.recordCost("debug", "expensive", 5000);
        r.recordCost("debug", "cheap", 500);
        var c = r.getAvgCosts("debug");
        assertEquals(5000.0, c.get("expensive"), 0.01);
        assertEquals(500.0, c.get("cheap"), 0.01);
    }

    @Test void modelRouterEmptyCosts() {
        var r = new ModelRouter(new JwcodeConfig());
        assertTrue(r.getAvgCosts("nonexistent").isEmpty());
    }

    @Test void modelRouterRouteNullReturnsDefault() {
        var config = new JwcodeConfig();
        var p = new JwcodeConfig.ProviderConfig();
        var m = new JwcodeConfig.ModelDefinition(); m.setId("default-model");
        p.setModels(List.of(m)); config.setProviders(Map.of("t", p)); config.setDefaultProvider("t");
        assertEquals("default-model", new ModelRouter(config).route(null));
    }

    // ═══ TokenBudget ═══

    @Test void tokenBudgetConsumeAndRelease() {
        var b = TokenBudget.of(100_000);
        b.consume(1000, 500);
        assertEquals(1500, b.getUsedTotal());
        b.releaseTokens(750);
        assertTrue(b.getUsedTotal() < 1500);
    }

    @Test void tokenBudgetUsageRatio() {
        var b = TokenBudget.of(100_000);
        b.consume(70000, 0);
        assertTrue(b.usageRatio() > 0.69);
        b.consume(20000, 0);
        assertTrue(b.usageRatio() > 0.89);
    }

    // ═══ Zone Priority ═══

    @Test void zonePriorityToolResultsLowest() {
        assertEquals(1, ContextWindowManager.zonePriority("[ZONE:TOOL_RESULTS_START]"));
    }

    @Test void zonePrioritySystemHighest() {
        assertEquals(5, ContextWindowManager.zonePriority("[ZONE:SYSTEM]"));
    }

    @Test void zonePriorityConversationDefaults() {
        assertEquals(2, ContextWindowManager.zonePriority("[ZONE:CONVERSATION_START]"));
        assertEquals(3, ContextWindowManager.zonePriority("random text"));
    }

    // ═══ Compaction Strategy ═══

    @Test void compactionStrategyConstructs() {
        assertNotNull(new SimpleCompactionStrategy(null));
        assertEquals(8, new SimpleCompactionStrategy(null).getTailSize());
    }

    // ═══ AiRepair ═══

    @Test void aiRepairDefaults() {
        var r = new RecoveryProtocol.AiRepair();
        assertNull(r.llmService());
        assertTrue(r.errorAnalysisPrompt().contains("Analyze"));
    }

    @Test void autoRetryDelayGrowth() {
        var r = new RecoveryProtocol.AutoRetry(3, Duration.ofSeconds(1));
        assertTrue(r.getDelayForAttempt(0).toMillis() >= 1000);
        assertTrue(r.getDelayForAttempt(2).toMillis() >= 4000);
    }

    // ═══ DoctorService ═══

    @Test void doctorServiceAllChecks() {
        var results = new DoctorService().runAll();
        assertEquals(8, results.size());
        for (var r : results) {
            assertNotNull(r.name());
            assertNotNull(r.detail());
        }
    }

    @Test void doctorJavaCheckPasses() {
        var results = new DoctorService().runAll();
        var java = results.get(0);
        assertEquals("Java", java.name());
        assertTrue(java.ok(), "Java 17+ should pass");
    }

    // ═══ SettingsService ═══

    @Test void settingsServiceDefaults() {
        var s = new SettingsService();
        assertEquals("dark", s.get("theme"));
        // model 在 projectSettings 中有覆盖值(空), 优先于 userSettings
        assertNotNull(s.get("model"));
    }

    @Test void settingsServiceSetAndGet() {
        var s = new SettingsService();
        s.set("test-key", "test-value");
        assertEquals("test-value", s.get("test-key"));
    }

    @Test void settingsServiceExportImport() {
        var a = new SettingsService();
        a.set("custom", 42);
        var exported = a.exportSettings();

        var b = new SettingsService();
        assertNull(b.get("custom"));
        b.importSettings(exported);
        assertEquals(42, b.get("custom"));
    }

    @Test void settingsServiceSaveLoadRoundtrip() {
        var s = new SettingsService();
        s.set("roundtrip-test", "hello");
        s.save();
        var loaded = new SettingsService();
        loaded.load();
        assertEquals("hello", loaded.get("roundtrip-test"));
    }

    // ═══ WorkspaceGuard ═══

    @Test void workspaceGuardCurrentDirectoryPasses() {
        var guard = new WorkspaceGuard(Path.of(System.getProperty("user.dir")));
        assertTrue(guard.validatePath(Path.of(System.getProperty("user.dir"))).isEmpty());
    }

    @Test void workspaceGuardOutsideDirectoryFails() {
        Path ws = Path.of(System.getProperty("user.dir"));
        var guard = new WorkspaceGuard(ws);
        Path outside = Path.of("/totally/different/path");
        assertTrue(guard.validatePath(outside).isPresent());
    }

    // ═══ CacheControl / LLMMessage ═══

    @Test void llmMessageCacheControl() {
        var msg = LLMMessage.builder()
            .role(LLMMessage.Role.SYSTEM)
            .content("test")
            .cacheControl(LLMMessage.CacheControl.EPHEMERAL)
            .build();
        assertEquals(LLMMessage.CacheControl.EPHEMERAL, msg.getCacheControl());
        var fmt = msg.toOpenAIFormat();
        assertTrue(fmt.containsKey("cache_control"));
    }

    // ═══ WorkspaceMemoryStore ═══

    @Test void memoryStoreKeywordFallback() {
        Path dir = Path.of(System.getProperty("user.dir"));
        var store = new WorkspaceMemoryStore(dir);
        var results = store.semanticSearch("test query", 3);
        assertNotNull(results);
    }
}
