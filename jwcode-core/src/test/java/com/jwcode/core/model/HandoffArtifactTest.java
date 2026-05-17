package com.jwcode.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HandoffArtifact 单元测试。
 *
 * <p>测试交接文档的创建、构建方法和摘要生成。</p>
 */
@DisplayName("HandoffArtifact 交接文档")
class HandoffArtifactTest {

    @Nested
    @DisplayName("创建交接文档")
    class ArtifactCreation {

        @Test
        @DisplayName("默认构造器生成有效 ID")
        void defaultConstructor() {
            HandoffArtifact artifact = new HandoffArtifact();
            assertNotNull(artifact.getArtifactId());
            assertNotNull(artifact.getCreatedAt());
        }

        @Test
        @DisplayName("工厂方法创建完整交接文档")
        void createWithFactory() {
            HandoffArtifact artifact = HandoffArtifact.create(
                "session-1", "task-1", "implementation",
                "完成了登录功能",
                "代码已提交，测试通过",
                "部署到测试环境"
            );

            assertEquals("session-1", artifact.getSessionId());
            assertEquals("task-1", artifact.getTaskId());
            assertEquals("implementation", artifact.getPhase());
            assertEquals("完成了登录功能", artifact.getCompletedWork());
            assertEquals("代码已提交，测试通过", artifact.getCurrentState());
            assertEquals("部署到测试环境", artifact.getNextActions());
        }

        @Test
        @DisplayName("从 SprintContract 创建交接文档")
        void fromContract() {
            SprintContract contract = SprintContract.createFrontendContract("登录页面", "task-1");
            contract.startNegotiation();
            contract.signByGenerator();
            contract.signByEvaluator();
            contract.startExecution();

            HandoffArtifact artifact = HandoffArtifact.fromContract(contract, "session-1");

            assertEquals("session-1", artifact.getSessionId());
            assertEquals("task-1", artifact.getTaskId());
            assertEquals(contract.getContractId(), artifact.getContractId());
            assertEquals("EXECUTING", artifact.getPhase());
        }
    }

    @Nested
    @DisplayName("构建方法")
    class BuilderMethods {

        @Test
        @DisplayName("添加待办事项")
        void addPendingItems() {
            HandoffArtifact artifact = new HandoffArtifact();
            artifact.addPendingItem("修复 bug #123");
            artifact.addPendingItem("添加单元测试");

            assertEquals(2, artifact.getPendingItems().size());
            assertTrue(artifact.getPendingItems().contains("修复 bug #123"));
        }

        @Test
        @DisplayName("添加决策记录")
        void addDecisions() {
            HandoffArtifact artifact = new HandoffArtifact();
            artifact.addDecision("采用微服务架构");
            artifact.addDecision("使用 PostgreSQL");

            assertEquals(2, artifact.getDecisions().size());
        }

        @Test
        @DisplayName("添加活跃问题")
        void addActiveIssues() {
            HandoffArtifact artifact = new HandoffArtifact();
            artifact.addActiveIssue("性能瓶颈待优化");
            artifact.addActiveIssue("缺少错误处理");

            assertEquals(2, artifact.getActiveIssues().size());
        }

        @Test
        @DisplayName("设置环境变量")
        void setEnvironmentValue() {
            HandoffArtifact artifact = new HandoffArtifact();
            artifact.setEnvironmentValue("java.version", "17");
            artifact.setEnvironmentValue("node.version", "18");

            assertEquals("17", artifact.getEnvironment().get("java.version"));
            assertEquals("18", artifact.getEnvironment().get("node.version"));
        }
    }

    @Nested
    @DisplayName("摘要生成")
    class SummaryGeneration {

        @Test
        @DisplayName("toSummary 包含关键信息")
        void toSummaryContainsKeyInfo() {
            HandoffArtifact artifact = HandoffArtifact.create(
                "session-1", "task-1", "testing",
                "完成了所有测试用例",
                "测试覆盖率 85%",
                "修复失败的集成测试"
            );
            artifact.addPendingItem("修复集成测试");
            artifact.addDecision("使用 JUnit 5");
            artifact.addActiveIssue("性能测试未通过");
            artifact.setSourceAgentType("Coder");
            artifact.setTargetAgentType("Tester");

            String summary = artifact.toSummary();
            assertTrue(summary.contains("task-1"), "应包含 taskId");
            assertTrue(summary.contains("testing"), "应包含 phase");
            assertTrue(summary.contains("完成了所有测试用例"), "应包含已完成工作");
            assertTrue(summary.contains("修复集成测试"), "应包含待办事项");
            assertTrue(summary.contains("使用 JUnit 5"), "应包含决策记录");
            assertTrue(summary.contains("性能测试未通过"), "应包含活跃问题");
            assertTrue(summary.contains("Coder"), "应包含来源 Agent 类型");
            assertTrue(summary.contains("Tester"), "应包含目标 Agent 类型");
            assertTrue(summary.contains("下一步行动"), "应包含下一步行动标题");
            assertTrue(summary.contains("修复失败的集成测试"), "应包含下一步行动内容");
        }
    }

    @Nested
    @DisplayName("ResetReason 枚举")
    class ResetReasonEnum {

        @Test
        @DisplayName("所有触发原因定义")
        void allReasonsDefined() {
            assertNotNull(HandoffArtifact.ResetReason.TOKEN_THRESHOLD_REACHED);
            assertNotNull(HandoffArtifact.ResetReason.ITERATION_LIMIT_REACHED);
            assertNotNull(HandoffArtifact.ResetReason.PHASE_TRANSITION);
            assertNotNull(HandoffArtifact.ResetReason.AGENT_REQUESTED);
            assertNotNull(HandoffArtifact.ResetReason.USER_TRIGGERED);
            assertNotNull(HandoffArtifact.ResetReason.SYSTEM_MAINTENANCE);
        }
    }
}
