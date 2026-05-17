package com.jwcode.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvaluationReport 单元测试。
 *
 * <p>测试评估报告的构建、verdict 判定和反馈生成。</p>
 */
@DisplayName("EvaluationReport 评估报告")
class EvaluationReportTest {

    @Nested
    @DisplayName("从合同构建报告")
    class FromContract {

        @Test
        @DisplayName("所有维度通过时 verdict 为 PASS")
        void allPassedGivesPassVerdict() {
            SprintContract contract = SprintContract.createFullstackContract("测试功能", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 8.0,
                SprintContract.DIM_FUNCTIONALITY, 8.0,
                SprintContract.DIM_VISUAL_DESIGN, 8.0,
                SprintContract.DIM_CODE_QUALITY, 8.0
            );
            Map<String, String> evidences = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, "设计完整",
                SprintContract.DIM_FUNCTIONALITY, "功能正确",
                SprintContract.DIM_VISUAL_DESIGN, "界面美观",
                SprintContract.DIM_CODE_QUALITY, "代码规范"
            );

            EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

            assertEquals(EvaluationReport.Verdict.PASS, report.getVerdict());
            assertTrue(report.isAllPassed());
            assertFalse(report.needsRework());
        }

        @Test
        @DisplayName("部分维度通过时 verdict 为 CONDITIONAL_PASS")
        void somePassedGivesConditionalPass() {
            SprintContract contract = SprintContract.createFullstackContract("测试功能", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 8.0,
                SprintContract.DIM_FUNCTIONALITY, 6.0,  // 刚过门槛
                SprintContract.DIM_VISUAL_DESIGN, 5.5,  // 刚过门槛
                SprintContract.DIM_CODE_QUALITY, 8.0
            );
            Map<String, String> evidences = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, "设计完整",
                SprintContract.DIM_FUNCTIONALITY, "功能基本正确",
                SprintContract.DIM_VISUAL_DESIGN, "界面一般",
                SprintContract.DIM_CODE_QUALITY, "代码规范"
            );

            EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

            assertEquals(EvaluationReport.Verdict.CONDITIONAL_PASS, report.getVerdict());
            assertTrue(report.isAllPassed());
        }

        @Test
        @DisplayName("某维度低于门槛时 verdict 为 FAIL")
        void oneBelowThresholdGivesFail() {
            SprintContract contract = SprintContract.createFullstackContract("测试功能", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 8.0,
                SprintContract.DIM_FUNCTIONALITY, 3.0,  // 低于 5.0
                SprintContract.DIM_VISUAL_DESIGN, 8.0,
                SprintContract.DIM_CODE_QUALITY, 8.0
            );
            Map<String, String> evidences = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, "设计完整",
                SprintContract.DIM_FUNCTIONALITY, "功能有严重bug",
                SprintContract.DIM_VISUAL_DESIGN, "界面美观",
                SprintContract.DIM_CODE_QUALITY, "代码规范"
            );

            EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

            assertEquals(EvaluationReport.Verdict.FAIL, report.getVerdict());
            assertFalse(report.isAllPassed());
            assertTrue(report.needsRework());
            assertEquals(1, report.getThresholdFailures().size());
        }

        @Test
        @DisplayName("多个维度低于门槛时 verdict 为 CRITICAL_FAIL")
        void multipleBelowThresholdGivesCriticalFail() {
            SprintContract contract = SprintContract.createFullstackContract("测试功能", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 8.0,
                SprintContract.DIM_FUNCTIONALITY, 2.0,  // 低于 5.0
                SprintContract.DIM_VISUAL_DESIGN, 3.0,  // 低于 5.0
                SprintContract.DIM_CODE_QUALITY, 8.0
            );
            Map<String, String> evidences = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, "设计完整",
                SprintContract.DIM_FUNCTIONALITY, "功能有严重bug",
                SprintContract.DIM_VISUAL_DESIGN, "界面丑陋",
                SprintContract.DIM_CODE_QUALITY, "代码规范"
            );

            EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

            assertEquals(EvaluationReport.Verdict.CRITICAL_FAIL, report.getVerdict());
            assertEquals(2, report.getThresholdFailures().size());
        }
    }

    @Nested
    @DisplayName("加权总分")
    class WeightedTotal {

        @Test
        @DisplayName("加权总分计算正确")
        void weightedTotalCalculation() {
            SprintContract contract = SprintContract.createFullstackContract("测试", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 10.0,
                SprintContract.DIM_FUNCTIONALITY, 10.0,
                SprintContract.DIM_VISUAL_DESIGN, 10.0,
                SprintContract.DIM_CODE_QUALITY, 10.0
            );
            Map<String, String> evidences = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, "",
                SprintContract.DIM_FUNCTIONALITY, "",
                SprintContract.DIM_VISUAL_DESIGN, "",
                SprintContract.DIM_CODE_QUALITY, ""
            );

            EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

            // 全栈权重: 0.25+0.30+0.20+0.25 = 1.0, 全部10分 → 加权总分 = 10.0
            assertEquals(10.0, report.getWeightedTotalScore(), 0.01);
        }
    }

    @Nested
    @DisplayName("反馈生成")
    class FeedbackGeneration {

        @Test
        @DisplayName("详细反馈包含各维度评分")
        void detailedFeedbackContainsScores() {
            SprintContract contract = SprintContract.createFullstackContract("测试", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 8.0,
                SprintContract.DIM_FUNCTIONALITY, 7.0,
                SprintContract.DIM_VISUAL_DESIGN, 6.0,
                SprintContract.DIM_CODE_QUALITY, 9.0
            );
            Map<String, String> evidences = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, "设计完整",
                SprintContract.DIM_FUNCTIONALITY, "功能正确",
                SprintContract.DIM_VISUAL_DESIGN, "界面一般",
                SprintContract.DIM_CODE_QUALITY, "代码规范"
            );

            EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 2);

            String feedback = report.getDetailedFeedback();
            assertTrue(feedback.contains("迭代 #2"));
            assertTrue(feedback.contains("产品设计深度"));
            assertTrue(feedback.contains("功能性"));
            assertTrue(feedback.contains("视觉设计"));
            assertTrue(feedback.contains("代码质量"));
            assertTrue(feedback.contains("8.0"));
            assertTrue(feedback.contains("7.0"));
        }

        @Test
        @DisplayName("改进建议列表管理")
        void improvementSuggestionsManagement() {
            SprintContract contract = SprintContract.createFullstackContract("测试", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 8.0,
                SprintContract.DIM_FUNCTIONALITY, 7.0,
                SprintContract.DIM_VISUAL_DESIGN, 6.0,
                SprintContract.DIM_CODE_QUALITY, 9.0
            );
            Map<String, String> evidences = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, "",
                SprintContract.DIM_FUNCTIONALITY, "",
                SprintContract.DIM_VISUAL_DESIGN, "",
                SprintContract.DIM_CODE_QUALITY, ""
            );

            EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);
            assertEquals(0, report.getImprovementSuggestions().size());

            report.addImprovementSuggestion("优化视觉设计一致性");
            assertEquals(1, report.getImprovementSuggestions().size());
            assertTrue(report.getImprovementSuggestions().contains("优化视觉设计一致性"));
        }
    }

    @Nested
    @DisplayName("Verdict 枚举")
    class VerdictEnum {

        @Test
        @DisplayName("Verdict 标签和描述")
        void verdictLabels() {
            assertEquals("✅ PASS", EvaluationReport.Verdict.PASS.getLabel());
            assertEquals("⚠️ CONDITIONAL_PASS", EvaluationReport.Verdict.CONDITIONAL_PASS.getLabel());
            assertEquals("❌ FAIL", EvaluationReport.Verdict.FAIL.getLabel());
            assertEquals("🚫 CRITICAL_FAIL", EvaluationReport.Verdict.CRITICAL_FAIL.getLabel());
        }
    }
}
