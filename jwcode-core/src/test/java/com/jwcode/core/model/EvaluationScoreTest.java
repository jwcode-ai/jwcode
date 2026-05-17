package com.jwcode.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvaluationScore 单元测试。
 *
 * <p>测试评分创建、门槛检查、加权计算和报告生成。</p>
 */
@DisplayName("EvaluationScore 评估分数")
class EvaluationScoreTest {

    @Nested
    @DisplayName("评分创建")
    class ScoreCreation {

        @Test
        @DisplayName("创建有效评分")
        void createValidScore() {
            EvaluationScore score = EvaluationScore.create(
                EvaluationScore.Dimension.FUNCTIONALITY,
                7.5, 0.35, 5.0,
                "核心功能正确实现"
            );

            assertEquals(EvaluationScore.Dimension.FUNCTIONALITY, score.getDimension());
            assertEquals(7.5, score.getScore(), 0.001);
            assertEquals(0.35, score.getWeight(), 0.001);
            assertEquals(5.0, score.getThreshold(), 0.001);
            assertTrue(score.isPassed());
            assertEquals("核心功能正确实现", score.getEvidence());
        }

        @Test
        @DisplayName("分数被限制在 0-10 范围内")
        void scoreIsClamped() {
            EvaluationScore over = EvaluationScore.create(
                EvaluationScore.Dimension.VISUAL_DESIGN, 15.0, 0.3, 5.0, "");
            assertEquals(10.0, over.getScore(), 0.001);

            EvaluationScore under = EvaluationScore.create(
                EvaluationScore.Dimension.VISUAL_DESIGN, -5.0, 0.3, 5.0, "");
            assertEquals(0.0, under.getScore(), 0.001);
        }

        @Test
        @DisplayName("低于门槛时 passed 为 false")
        void belowThresholdNotPassed() {
            EvaluationScore score = EvaluationScore.create(
                EvaluationScore.Dimension.CODE_QUALITY, 3.0, 0.25, 5.0, "代码质量差");
            assertFalse(score.isPassed());
        }
    }

    @Nested
    @DisplayName("失败项管理")
    class FailureManagement {

        @Test
        @DisplayName("添加失败项后 passed 变为 false")
        void addFailureMakesNotPassed() {
            EvaluationScore score = EvaluationScore.create(
                EvaluationScore.Dimension.FUNCTIONALITY, 8.0, 0.3, 5.0, "");

            assertTrue(score.isPassed());
            score.addFailure("空指针异常未处理");
            assertFalse(score.isPassed());
            assertEquals(1, score.getFailures().size());
        }

        @Test
        @DisplayName("添加改进建议")
        void addSuggestions() {
            EvaluationScore score = EvaluationScore.create(
                EvaluationScore.Dimension.CODE_QUALITY, 6.0, 0.25, 5.0, "");

            score.addSuggestion("添加单元测试");
            score.addSuggestion("提取公共方法");

            assertEquals(2, score.getSuggestions().size());
        }
    }

    @Nested
    @DisplayName("计算")
    class Calculations {

        @Test
        @DisplayName("加权得分计算")
        void weightedScore() {
            EvaluationScore score = EvaluationScore.create(
                EvaluationScore.Dimension.PRODUCT_DEPTH, 8.0, 0.3, 5.0, "");
            assertEquals(2.4, score.getWeightedScore(), 0.001); // 8.0 * 0.3
        }

        @Test
        @DisplayName("与门槛的差距")
        void gapToThreshold() {
            EvaluationScore passed = EvaluationScore.create(
                EvaluationScore.Dimension.FUNCTIONALITY, 7.0, 0.3, 5.0, "");
            assertEquals(2.0, passed.getGapToThreshold(), 0.001);

            EvaluationScore failed = EvaluationScore.create(
                EvaluationScore.Dimension.FUNCTIONALITY, 3.0, 0.3, 5.0, "");
            assertEquals(-2.0, failed.getGapToThreshold(), 0.001);
        }
    }

    @Nested
    @DisplayName("维度枚举")
    class DimensionEnum {

        @Test
        @DisplayName("通过 key 查找维度")
        void fromKey() {
            assertEquals(EvaluationScore.Dimension.PRODUCT_DEPTH,
                EvaluationScore.Dimension.fromKey("product_depth"));
            assertEquals(EvaluationScore.Dimension.FUNCTIONALITY,
                EvaluationScore.Dimension.fromKey("functionality"));
            assertEquals(EvaluationScore.Dimension.VISUAL_DESIGN,
                EvaluationScore.Dimension.fromKey("visual_design"));
            assertEquals(EvaluationScore.Dimension.CODE_QUALITY,
                EvaluationScore.Dimension.fromKey("code_quality"));
        }

        @Test
        @DisplayName("未知 key 返回 null")
        void unknownKeyReturnsNull() {
            assertNull(EvaluationScore.Dimension.fromKey("unknown_dimension"));
        }

        @Test
        @DisplayName("维度显示名称")
        void displayNames() {
            assertEquals("产品设计深度", EvaluationScore.Dimension.PRODUCT_DEPTH.getDisplayName());
            assertEquals("功能性", EvaluationScore.Dimension.FUNCTIONALITY.getDisplayName());
            assertEquals("视觉设计", EvaluationScore.Dimension.VISUAL_DESIGN.getDisplayName());
            assertEquals("代码质量", EvaluationScore.Dimension.CODE_QUALITY.getDisplayName());
        }
    }

    @Nested
    @DisplayName("报告生成")
    class ReportGeneration {

        @Test
        @DisplayName("生成报告字符串包含关键信息")
        void toReportString() {
            EvaluationScore score = EvaluationScore.create(
                EvaluationScore.Dimension.FUNCTIONALITY, 7.5, 0.35, 5.0,
                "所有 API 端点正常工作");
            score.addFailure("缺少输入验证");
            score.addSuggestion("添加参数边界检查");

            String report = score.toReportString();
            assertTrue(report.contains("功能性"), "应包含维度名称");
            assertTrue(report.contains("7.5"), "应包含分数");
            assertTrue(report.contains("✅") || report.contains("❌"), "应包含通过状态图标");
            assertTrue(report.contains("缺少输入验证"), "应包含失败项");
            assertTrue(report.contains("添加参数边界检查"), "应包含改进建议");
        }

        @Test
        @DisplayName("未通过的评分报告显示 ❌")
        void failedScoreReport() {
            EvaluationScore score = EvaluationScore.create(
                EvaluationScore.Dimension.CODE_QUALITY, 3.0, 0.25, 5.0, "安全问题");
            String report = score.toReportString();
            assertTrue(report.contains("❌"));
        }
    }
}
