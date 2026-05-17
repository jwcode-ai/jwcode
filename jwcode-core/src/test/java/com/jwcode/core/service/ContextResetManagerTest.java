package com.jwcode.core.service;

import com.jwcode.core.model.HandoffArtifact;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextResetManager 单元测试。
 *
 * <p>测试触发条件检查、交接文档的保存/加载/删除。</p>
 */
@DisplayName("ContextResetManager 上下文重置管理器")
class ContextResetManagerTest {

    @TempDir
    Path tempDir;

    private ContextResetManager manager;

    @BeforeEach
    void setUp() {
        manager = new ContextResetManager(tempDir);
    }

    @Nested
    @DisplayName("触发条件检查")
    class TriggerCheck {

        @Test
        @DisplayName("Token 使用率超过 85% 触发重置")
        void tokenThresholdTriggersReset() {
            HandoffArtifact.ResetReason reason = manager.checkResetNeeded(0.90, 1, false, false);
            assertEquals(HandoffArtifact.ResetReason.TOKEN_THRESHOLD_REACHED, reason);
        }

        @Test
        @DisplayName("Token 使用率低于阈值不触发")
        void tokenBelowThresholdNoReset() {
            HandoffArtifact.ResetReason reason = manager.checkResetNeeded(0.50, 1, false, false);
            assertNull(reason);
        }

        @Test
        @DisplayName("Agent 主动请求触发重置")
        void agentRequestTriggersReset() {
            HandoffArtifact.ResetReason reason = manager.checkResetNeeded(0.50, 1, false, true);
            assertEquals(HandoffArtifact.ResetReason.AGENT_REQUESTED, reason);
        }

        @Test
        @DisplayName("迭代超过 3 轮触发重置")
        void iterationLimitTriggersReset() {
            HandoffArtifact.ResetReason reason = manager.checkResetNeeded(0.50, 4, false, false);
            assertEquals(HandoffArtifact.ResetReason.ITERATION_LIMIT_REACHED, reason);
        }

        @Test
        @DisplayName("阶段切换触发重置")
        void phaseTransitionTriggersReset() {
            HandoffArtifact.ResetReason reason = manager.checkResetNeeded(0.50, 1, true, false);
            assertEquals(HandoffArtifact.ResetReason.PHASE_TRANSITION, reason);
        }

        @Test
        @DisplayName("Token 阈值优先级最高")
        void tokenThresholdHasHighestPriority() {
            // 即使同时满足多个条件，Token 阈值优先
            HandoffArtifact.ResetReason reason = manager.checkResetNeeded(0.90, 4, true, true);
            assertEquals(HandoffArtifact.ResetReason.TOKEN_THRESHOLD_REACHED, reason);
        }
    }

    @Nested
    @DisplayName("交接文档管理")
    class HandoffArtifactManagement {

        @Test
        @DisplayName("保存交接文档到文件系统")
        void saveHandoffArtifact() throws Exception {
            HandoffArtifact artifact = HandoffArtifact.create(
                "session-1", "task-1", "testing",
                "完成了测试", "测试通过", "部署"
            );

            String path = manager.saveHandoffArtifact(artifact);
            assertNotNull(path);
            assertTrue(path.contains("session-1_task-1.json"));

            File savedFile = new File(path);
            assertTrue(savedFile.exists());
        }

        @Test
        @DisplayName("加载已保存的交接文档")
        void loadHandoffArtifact() throws Exception {
            HandoffArtifact artifact = HandoffArtifact.create(
                "session-2", "task-2", "implementation",
                "实现了功能", "代码已提交", "编写测试"
            );
            artifact.addPendingItem("编写集成测试");
            artifact.addDecision("使用 REST API");

            manager.saveHandoffArtifact(artifact);

            HandoffArtifact loaded = manager.loadHandoffArtifact("session-2", "task-2");
            assertNotNull(loaded);
            assertEquals("session-2", loaded.getSessionId());
            assertEquals("task-2", loaded.getTaskId());
            assertEquals("implementation", loaded.getPhase());
            assertEquals(1, loaded.getPendingItems().size());
            assertEquals(1, loaded.getDecisions().size());
        }

        @Test
        @DisplayName("删除交接文档")
        void deleteHandoffArtifact() throws Exception {
            HandoffArtifact artifact = HandoffArtifact.create(
                "session-3", "task-3", "testing", "测试", "通过", ""
            );
            manager.saveHandoffArtifact(artifact);

            boolean deleted = manager.deleteHandoffArtifact("session-3", "task-3");
            assertTrue(deleted);
        }

        @Test
        @DisplayName("加载不存在的交接文档抛出异常")
        void loadNonExistentThrowsException() {
            assertThrows(Exception.class, () ->
                manager.loadHandoffArtifact("nonexistent", "nonexistent"));
        }

        @Test
        @DisplayName("列出所有交接文档")
        void listHandoffArtifacts() throws Exception {
            HandoffArtifact a1 = HandoffArtifact.create("s1", "t1", "phase", "work", "state", "next");
            HandoffArtifact a2 = HandoffArtifact.create("s2", "t2", "phase", "work", "state", "next");
            manager.saveHandoffArtifact(a1);
            manager.saveHandoffArtifact(a2);

            File[] files = manager.listHandoffArtifacts();
            assertEquals(2, files.length);
        }
    }

    @Nested
    @DisplayName("重置执行")
    class ResetExecution {

        @Test
        @DisplayName("执行完整重置流程")
        void executeFullReset() throws Exception {
            HandoffArtifact artifact = HandoffArtifact.create(
                "session-4", "task-4", "implementation",
                "完成了 80% 的工作",
                "代码状态稳定",
                "完成剩余 20%"
            );

            String path = manager.executeReset(artifact,
                HandoffArtifact.ResetReason.TOKEN_THRESHOLD_REACHED);

            assertNotNull(path);
            assertTrue(new File(path).exists());
        }

        @Test
        @DisplayName("从交接文档恢复上下文")
        void restoreFromReset() throws Exception {
            HandoffArtifact artifact = HandoffArtifact.create(
                "session-5", "task-5", "testing",
                "测试完成", "全部通过", "部署到生产"
            );
            manager.saveHandoffArtifact(artifact);

            HandoffArtifact restored = manager.restoreFromReset("session-5", "task-5");
            assertNotNull(restored);
            assertEquals("testing", restored.getPhase());
            assertEquals("部署到生产", restored.getNextActions());
        }
    }
}
