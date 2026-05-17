package com.jwcode.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SprintContract 单元测试。
 *
 * <p>测试合同状态机、签署流程、工厂方法、加权评分和门槛检查。</p>
 */
@DisplayName("SprintContract 合同模型")
class SprintContractTest {

    @Nested
    @DisplayName("合同状态机")
    class ContractStateMachine {

        @Test
        @DisplayName("新建合同状态为 DRAFT")
        void newContractIsDraft() {
            SprintContract contract = new SprintContract();
            assertEquals(SprintContract.ContractStatus.DRAFT, contract.getStatus());
        }

        @Test
        @DisplayName("DRAFT → NEGOTIATING 转换")
        void draftToNegotiating() {
            SprintContract contract = new SprintContract();
            contract.startNegotiation();
            assertEquals(SprintContract.ContractStatus.NEGOTIATING, contract.getStatus());
        }

        @Test
        @DisplayName("NEGOTIATING → SIGNED 当双方签署")
        void negotiatingToSigned() {
            SprintContract contract = new SprintContract();
            contract.startNegotiation();

            contract.signByGenerator();
            assertFalse(contract.isFullySigned()); // 仅一方签署

            contract.signByEvaluator();
            assertTrue(contract.isFullySigned());
            assertEquals(SprintContract.ContractStatus.SIGNED, contract.getStatus());
        }

        @Test
        @DisplayName("SIGNED → EXECUTING 转换")
        void signedToExecuting() {
            SprintContract contract = createSignedContract();

            contract.startExecution();
            assertEquals(SprintContract.ContractStatus.EXECUTING, contract.getStatus());
        }

        @Test
        @DisplayName("EXECUTING → COMPLETED 转换")
        void executingToCompleted() {
            SprintContract contract = createSignedContract();
            contract.startExecution();

            contract.complete();
            assertEquals(SprintContract.ContractStatus.COMPLETED, contract.getStatus());
        }

        @Test
        @DisplayName("EXECUTING → FAILED 转换")
        void executingToFailed() {
            SprintContract contract = createSignedContract();
            contract.startExecution();

            contract.fail();
            assertEquals(SprintContract.ContractStatus.FAILED, contract.getStatus());
        }

        @Test
        @DisplayName("终端状态不可转换")
        void terminalStatesCannotTransition() {
            SprintContract completed = createSignedContract();
            completed.startExecution();
            completed.complete();
            assertTrue(completed.getStatus().isTerminal());

            SprintContract failed = createSignedContract();
            failed.startExecution();
            failed.fail();
            assertTrue(failed.getStatus().isTerminal());
        }

        @Test
        @DisplayName("状态转换合法性检查")
        void canTransitionToValidation() {
            SprintContract.ContractStatus draft = SprintContract.ContractStatus.DRAFT;
            assertTrue(draft.canTransitionTo(SprintContract.ContractStatus.NEGOTIATING));
            assertFalse(draft.canTransitionTo(SprintContract.ContractStatus.SIGNED));
            assertFalse(draft.canTransitionTo(SprintContract.ContractStatus.COMPLETED));

            SprintContract.ContractStatus completed = SprintContract.ContractStatus.COMPLETED;
            assertFalse(completed.canTransitionTo(SprintContract.ContractStatus.DRAFT));
            assertFalse(completed.canTransitionTo(SprintContract.ContractStatus.EXECUTING));
        }

        @Test
        @DisplayName("非法状态转换抛出异常")
        void illegalTransitionThrowsException() {
            SprintContract contract = new SprintContract();
            assertThrows(IllegalStateException.class, contract::signByGenerator);
            assertThrows(IllegalStateException.class, contract::startExecution);
            assertThrows(IllegalStateException.class, contract::complete);
            assertThrows(IllegalStateException.class, contract::fail);
        }

        private SprintContract createSignedContract() {
            SprintContract contract = new SprintContract();
            contract.startNegotiation();
            contract.signByGenerator();
            contract.signByEvaluator();
            return contract;
        }
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("前端合同：视觉设计权重最高")
        void frontendContractHasHighestVisualWeight() {
            SprintContract contract = SprintContract.createFrontendContract("登录页面", "task-1");

            assertNotNull(contract.getContractId());
            assertEquals("task-1", contract.getTaskId());
            assertEquals("登录页面", contract.getFeature());

            Map<String, Double> weights = contract.getScoringWeights();
            assertEquals(0.35, weights.get(SprintContract.DIM_VISUAL_DESIGN), 0.001);
            assertTrue(weights.get(SprintContract.DIM_VISUAL_DESIGN) >
                       weights.get(SprintContract.DIM_FUNCTIONALITY));
        }

        @Test
        @DisplayName("后端合同：功能性权重最高")
        void backendContractHasHighestFunctionalityWeight() {
            SprintContract contract = SprintContract.createBackendContract("API 接口", "task-2");

            Map<String, Double> weights = contract.getScoringWeights();
            assertEquals(0.35, weights.get(SprintContract.DIM_FUNCTIONALITY), 0.001);
            assertTrue(weights.get(SprintContract.DIM_FUNCTIONALITY) >
                       weights.get(SprintContract.DIM_VISUAL_DESIGN));
        }

        @Test
        @DisplayName("全栈合同：权重分布均衡")
        void fullstackContractHasBalancedWeights() {
            SprintContract contract = SprintContract.createFullstackContract("用户管理系统", "task-3");

            Map<String, Double> weights = contract.getScoringWeights();
            assertEquals(4, weights.size());
            double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.001);
        }
    }

    @Nested
    @DisplayName("合同操作")
    class ContractOperations {

        @Test
        @DisplayName("增加验收标准")
        void addAcceptanceCriteria() {
            SprintContract contract = new SprintContract();
            contract.addAcceptanceCriterion("功能完整");
            contract.addAcceptanceCriterion("无性能问题");

            assertEquals(2, contract.getAcceptanceCriteria().size());
            assertTrue(contract.getAcceptanceCriteria().contains("功能完整"));
        }

        @Test
        @DisplayName("迭代轮数计数")
        void iterationCounting() {
            SprintContract contract = new SprintContract();
            contract.setMaxIterations(3);

            assertTrue(contract.hasRemainingIterations());
            assertEquals(0, contract.getCurrentIteration());

            contract.incrementIteration();
            assertEquals(1, contract.getCurrentIteration());
            assertTrue(contract.hasRemainingIterations());

            contract.incrementIteration();
            assertEquals(2, contract.getCurrentIteration());
            assertTrue(contract.hasRemainingIterations()); // 2 < 3，还有剩余

            contract.incrementIteration();
            assertEquals(3, contract.getCurrentIteration());
            assertFalse(contract.hasRemainingIterations()); // 3 不小于 3，无剩余
        }

        @Test
        @DisplayName("达到最大迭代轮数后不可继续")
        void maxIterationsReached() {
            SprintContract contract = new SprintContract();
            contract.setMaxIterations(2);

            assertTrue(contract.incrementIteration()); // 第1次
            assertFalse(contract.incrementIteration()); // 第2次，已达上限
            assertEquals(2, contract.getCurrentIteration());
        }
    }

    @Nested
    @DisplayName("加权评分")
    class WeightedScoring {

        @Test
        @DisplayName("计算加权总分")
        void calculateWeightedScore() {
            SprintContract contract = SprintContract.createFullstackContract("测试", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 8.0,
                SprintContract.DIM_FUNCTIONALITY, 7.0,
                SprintContract.DIM_VISUAL_DESIGN, 6.0,
                SprintContract.DIM_CODE_QUALITY, 9.0
            );

            double weighted = contract.calculateWeightedScore(scores);
            // 0.25*8 + 0.30*7 + 0.20*6 + 0.25*9 = 2.0 + 2.1 + 1.2 + 2.25 = 7.55
            assertEquals(7.55, weighted, 0.01);
        }

        @Test
        @DisplayName("缺失维度不影响计算")
        void missingDimensionsAreSkipped() {
            SprintContract contract = SprintContract.createFrontendContract("测试", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 8.0,
                SprintContract.DIM_FUNCTIONALITY, 7.0
                // 缺少 VISUAL_DESIGN 和 CODE_QUALITY
            );

            double weighted = contract.calculateWeightedScore(scores);
            assertTrue(weighted > 0);
        }
    }

    @Nested
    @DisplayName("硬门槛检查")
    class ThresholdCheck {

        @Test
        @DisplayName("所有维度通过门槛")
        void allDimensionsPass() {
            SprintContract contract = SprintContract.createFullstackContract("测试", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 6.0,
                SprintContract.DIM_FUNCTIONALITY, 6.0,
                SprintContract.DIM_VISUAL_DESIGN, 6.0,
                SprintContract.DIM_CODE_QUALITY, 6.0
            );

            List<String> failures = contract.checkThresholds(scores);
            assertTrue(failures.isEmpty());
        }

        @Test
        @DisplayName("某维度低于门槛时返回失败")
        void dimensionBelowThreshold() {
            SprintContract contract = SprintContract.createFullstackContract("测试", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 6.0,
                SprintContract.DIM_FUNCTIONALITY, 3.0, // 低于 5.0
                SprintContract.DIM_VISUAL_DESIGN, 6.0,
                SprintContract.DIM_CODE_QUALITY, 6.0
            );

            List<String> failures = contract.checkThresholds(scores);
            assertFalse(failures.isEmpty());
            assertTrue(failures.get(0).contains(SprintContract.DIM_FUNCTIONALITY));
        }

        @Test
        @DisplayName("多个维度低于门槛")
        void multipleDimensionsBelowThreshold() {
            SprintContract contract = SprintContract.createFullstackContract("测试", "task-1");

            Map<String, Double> scores = Map.of(
                SprintContract.DIM_PRODUCT_DEPTH, 6.0,
                SprintContract.DIM_FUNCTIONALITY, 3.0,
                SprintContract.DIM_VISUAL_DESIGN, 2.0,
                SprintContract.DIM_CODE_QUALITY, 6.0
            );

            List<String> failures = contract.checkThresholds(scores);
            assertEquals(2, failures.size());
        }
    }

    @Nested
    @DisplayName("Builder 模式")
    class BuilderPattern {

        @Test
        @DisplayName("使用 Builder 创建合同")
        void buildContractWithBuilder() {
            SprintContract contract = SprintContract.builder()
                .taskId("task-99")
                .feature("搜索功能")
                .addCriterion("搜索结果准确")
                .addCriterion("响应时间 < 200ms")
                .putWeight(SprintContract.DIM_FUNCTIONALITY, 0.5)
                .putWeight(SprintContract.DIM_CODE_QUALITY, 0.5)
                .putThreshold(SprintContract.DIM_FUNCTIONALITY, 6.0)
                .maxIterations(5)
                .build();

            assertEquals("task-99", contract.getTaskId());
            assertEquals("搜索功能", contract.getFeature());
            assertEquals(2, contract.getAcceptanceCriteria().size());
            assertEquals(2, contract.getScoringWeights().size());
            assertEquals(5, contract.getMaxIterations());
        }
    }
}
