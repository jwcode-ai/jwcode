package com.jwcode.core.service;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.model.EvaluationReport;
import com.jwcode.core.model.SprintContract;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GAN 迭代 Sprint 集成测试。
 *
 * <p>测试完整的 Generator ⇄ Evaluator 迭代循环：
 * <ul>
 *   <li>SprintContract 全生命周期（DRAFT → NEGOTIATING → SIGNED → EXECUTING → COMPLETED/FAILED）</li>
 *   <li>IterativeSprintOrchestrator 的迭代仲裁逻辑</li>
 *   <li>EvaluationReport 的构建和 verdict 判定</li>
 *   <li>加权评分和硬门槛否决机制</li>
 * </ul>
 * </p>
 */
@DisplayName("GAN 迭代 Sprint 集成测试")
public class GanIterativeSprintIntegrationTest {

    private AgentRegistry agentRegistry;
    private IterativeSprintOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        agentRegistry = AgentRegistry.createDefault();
        orchestrator = new IterativeSprintOrchestrator(agentRegistry);
    }

    // ==================== SprintContract 全生命周期 ====================

    @Test
    @DisplayName("SprintContract 全生命周期：DRAFT → COMPLETED")
    void testContractFullLifecycleSuccess() {
        SprintContract contract = SprintContract.createFrontendContract("登录页面", "task-001");

        assertEquals(SprintContract.ContractStatus.DRAFT, contract.getStatus());

        contract.startNegotiation();
        assertEquals(SprintContract.ContractStatus.NEGOTIATING, contract.getStatus());

        contract.signByGenerator();
        assertFalse(contract.isFullySigned());

        contract.signByEvaluator();
        assertTrue(contract.isFullySigned());
        assertEquals(SprintContract.ContractStatus.SIGNED, contract.getStatus());

        contract.startExecution();
        assertEquals(SprintContract.ContractStatus.EXECUTING, contract.getStatus());

        contract.complete();
        assertEquals(SprintContract.ContractStatus.COMPLETED, contract.getStatus());
        assertTrue(contract.getStatus().isTerminal());
    }

    @Test
    @DisplayName("SprintContract 全生命周期：DRAFT → FAILED")
    void testContractFullLifecycleFailure() {
        SprintContract contract = SprintContract.createBackendContract("API 开发", "task-002");

        contract.startNegotiation();
        contract.signByGenerator();
        contract.signByEvaluator();
        contract.startExecution();

        contract.fail();
        assertEquals(SprintContract.ContractStatus.FAILED, contract.getStatus());
        assertTrue(contract.getStatus().isTerminal());
    }

    // ==================== 加权评分与门槛检查 ====================

    @Test
    @DisplayName("前端合同：视觉设计权重最高，影响加权总分最大")
    void testFrontendContractVisualWeightDominates() {
        SprintContract contract = SprintContract.createFrontendContract("用户首页", "task-003");

        // 场景 A: 视觉设计高分
        Map<String, Double> scoresA = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 5.0,
            SprintContract.DIM_FUNCTIONALITY, 5.0,
            SprintContract.DIM_VISUAL_DESIGN, 10.0,
            SprintContract.DIM_CODE_QUALITY, 5.0
        );

        // 场景 B: 功能性高分（但视觉设计低分）
        Map<String, Double> scoresB = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 5.0,
            SprintContract.DIM_FUNCTIONALITY, 10.0,
            SprintContract.DIM_VISUAL_DESIGN, 5.0,
            SprintContract.DIM_CODE_QUALITY, 5.0
        );

        double weightedA = contract.calculateWeightedScore(scoresA);
        double weightedB = contract.calculateWeightedScore(scoresB);

        // 前端任务中，视觉设计权重 0.35 > 功能性 0.20
        // 所以场景 A（视觉高分）的加权总分应高于场景 B（功能性高分）
        assertTrue(weightedA > weightedB,
            "前端任务中视觉设计权重最高，视觉高分应获得更高加权总分");
    }

    @Test
    @DisplayName("后端合同：功能性权重最高，影响加权总分最大")
    void testBackendContractFunctionalityWeightDominates() {
        SprintContract contract = SprintContract.createBackendContract("数据处理", "task-004");

        Map<String, Double> scoresA = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 5.0,
            SprintContract.DIM_FUNCTIONALITY, 10.0,
            SprintContract.DIM_VISUAL_DESIGN, 5.0,
            SprintContract.DIM_CODE_QUALITY, 5.0
        );

        Map<String, Double> scoresB = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 5.0,
            SprintContract.DIM_FUNCTIONALITY, 5.0,
            SprintContract.DIM_VISUAL_DESIGN, 10.0,
            SprintContract.DIM_CODE_QUALITY, 5.0
        );

        double weightedA = contract.calculateWeightedScore(scoresA);
        double weightedB = contract.calculateWeightedScore(scoresB);

        assertTrue(weightedA > weightedB,
            "后端任务中功能性权重最高，功能性高分应获得更高加权总分");
    }

    @Test
    @DisplayName("硬门槛否决：任一维度低于门槛则判定失败")
    void testThresholdVeto() {
        SprintContract contract = SprintContract.createFullstackContract("全栈功能", "task-005");

        Map<String, Double> scores = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 8.0,
            SprintContract.DIM_FUNCTIONALITY, 2.0,  // 严重低于门槛 5.0
            SprintContract.DIM_VISUAL_DESIGN, 8.0,
            SprintContract.DIM_CODE_QUALITY, 8.0
        );

        List<String> failures = contract.checkThresholds(scores);
        assertFalse(failures.isEmpty());
        assertTrue(failures.get(0).contains(SprintContract.DIM_FUNCTIONALITY));
    }

    @Test
    @DisplayName("多维度低于门槛时报告所有失败维度")
    void testMultipleThresholdFailures() {
        SprintContract contract = SprintContract.createFullstackContract("全栈功能", "task-006");

        Map<String, Double> scores = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 8.0,
            SprintContract.DIM_FUNCTIONALITY, 2.0,
            SprintContract.DIM_VISUAL_DESIGN, 3.0,
            SprintContract.DIM_CODE_QUALITY, 8.0
        );

        List<String> failures = contract.checkThresholds(scores);
        assertEquals(2, failures.size());
    }

    // ==================== EvaluationReport 构建 ====================

    @Test
    @DisplayName("EvaluationReport 从合同正确构建")
    void testEvaluationReportFromContract() {
        SprintContract contract = SprintContract.createFullstackContract("用户管理", "task-007");

        Map<String, Double> scores = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 8.5,
            SprintContract.DIM_FUNCTIONALITY, 7.0,
            SprintContract.DIM_VISUAL_DESIGN, 6.5,
            SprintContract.DIM_CODE_QUALITY, 9.0
        );
        Map<String, String> evidences = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, "功能完整，用户体验良好",
            SprintContract.DIM_FUNCTIONALITY, "核心逻辑正确，边界处理完整",
            SprintContract.DIM_VISUAL_DESIGN, "界面一致性较好",
            SprintContract.DIM_CODE_QUALITY, "代码结构清晰，测试覆盖率高"
        );

        EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

        assertEquals(contract.getContractId(), report.getContractId());
        assertEquals(1, report.getIterationRound());
        assertEquals(4, report.getScores().size());
        assertTrue(report.isAllPassed());
        assertNotNull(report.getDetailedFeedback());
        assertTrue(report.getDetailedFeedback().contains("评估报告"), "应包含评估报告标题");
        assertTrue(report.getDetailedFeedback().contains("8.5"), "应包含产品深度分数");
    }

    @Test
    @DisplayName("EvaluationReport 正确计算加权总分")
    void testEvaluationReportWeightedScore() {
        SprintContract contract = SprintContract.createFullstackContract("测试", "task-008");

        // 全栈权重: 0.25 + 0.30 + 0.20 + 0.25 = 1.0
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

        assertEquals(10.0, report.getWeightedTotalScore(), 0.01);
        assertEquals(EvaluationReport.Verdict.PASS, report.getVerdict());
    }

    @Test
    @DisplayName("EvaluationReport 检测到门槛失败")
    void testEvaluationReportDetectsFailures() {
        SprintContract contract = SprintContract.createFullstackContract("测试", "task-009");

        Map<String, Double> scores = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 8.0,
            SprintContract.DIM_FUNCTIONALITY, 3.0,  // 低于门槛
            SprintContract.DIM_VISUAL_DESIGN, 8.0,
            SprintContract.DIM_CODE_QUALITY, 8.0
        );
        Map<String, String> evidences = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, "",
            SprintContract.DIM_FUNCTIONALITY, "功能有严重缺陷",
            SprintContract.DIM_VISUAL_DESIGN, "",
            SprintContract.DIM_CODE_QUALITY, ""
        );

        EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

        assertFalse(report.isAllPassed());
        assertTrue(report.needsRework());
        assertEquals(1, report.getThresholdFailures().size());
    }

    // ==================== 迭代轮数 ====================

    @Test
    @DisplayName("迭代轮数计数正确")
    void testIterationCounting() {
        SprintContract contract = SprintContract.createFullstackContract("测试", "task-010");
        assertEquals(3, contract.getMaxIterations());
        assertEquals(0, contract.getCurrentIteration());

        assertTrue(contract.incrementIteration());
        assertEquals(1, contract.getCurrentIteration());

        assertTrue(contract.incrementIteration());
        assertEquals(2, contract.getCurrentIteration());

        assertFalse(contract.incrementIteration());
        assertEquals(3, contract.getCurrentIteration());
    }

    @Test
    @DisplayName("自定义最大迭代轮数")
    void testCustomMaxIterations() {
        SprintContract contract = SprintContract.builder()
            .taskId("task-011")
            .feature("测试")
            .maxIterations(5)
            .build();

        assertEquals(5, contract.getMaxIterations());

        for (int i = 0; i < 4; i++) {
            assertTrue(contract.incrementIteration());
        }
        assertFalse(contract.incrementIteration());
        assertEquals(5, contract.getCurrentIteration());
    }

    // ==================== 验收标准 ====================

    @Test
    @DisplayName("验收标准管理")
    void testAcceptanceCriteria() {
        SprintContract contract = SprintContract.createFrontendContract("登录页", "task-012");

        assertTrue(contract.getAcceptanceCriteria().isEmpty());

        contract.addAcceptanceCriterion("用户可以使用邮箱登录");
        contract.addAcceptanceCriterion("登录失败时显示错误信息");
        contract.addAcceptanceCriterion("支持记住密码功能");

        assertEquals(3, contract.getAcceptanceCriteria().size());
    }

    // ==================== IterativeSprintOrchestrator ====================

    @Test
    @DisplayName("IterativeSprintOrchestrator 初始化")
    void testOrchestratorInitialization() {
        assertNotNull(orchestrator);
    }

    @Test
    @DisplayName("IterativeSprintOrchestrator 执行 Sprint 返回结果")
    void testOrchestratorExecuteSprint() {
        SprintContract contract = SprintContract.createFrontendContract("测试功能", "task-013");
        contract.startNegotiation();
        contract.signByGenerator();
        contract.signByEvaluator();

        // 使用空输入执行 Sprint（实际执行需要 LLM，这里验证流程不抛异常）
        IterativeSprintOrchestrator.IterationResult result =
            orchestrator.executeSprint(contract, "测试 Generator 输出");

        // 注意：由于没有真实的 LLM 服务，Generator 和 Evaluator 会返回 null
        // 验证框架能正确处理这种情况
        assertNotNull(result);
        assertNotNull(result.getContract());
        assertEquals("task-013", result.getContract().getTaskId());
    }

    // ==================== 端到端场景 ====================

    @Test
    @DisplayName("端到端场景：前端任务使用正确权重")
    void testEndToEndFrontendTask() {
        // 模拟一个前端任务的全流程
        SprintContract contract = SprintContract.createFrontendContract(
            "实现用户个人中心页面", "e2e-001");

        // 验证权重
        assertEquals(0.35, contract.getScoringWeights().get(SprintContract.DIM_VISUAL_DESIGN), 0.001);
        assertEquals(0.20, contract.getScoringWeights().get(SprintContract.DIM_FUNCTIONALITY), 0.001);

        // 谈判
        contract.startNegotiation();
        contract.addAcceptanceCriterion("页面布局与设计稿一致");
        contract.addAcceptanceCriterion("所有交互功能正常");
        contract.signByGenerator();
        contract.signByEvaluator();
        assertEquals(SprintContract.ContractStatus.SIGNED, contract.getStatus());

        // 执行
        contract.startExecution();

        // 评估
        Map<String, Double> scores = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 7.0,
            SprintContract.DIM_FUNCTIONALITY, 8.0,
            SprintContract.DIM_VISUAL_DESIGN, 9.0,  // 视觉高分
            SprintContract.DIM_CODE_QUALITY, 7.0
        );
        Map<String, String> evidences = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, "功能完整",
            SprintContract.DIM_FUNCTIONALITY, "交互流畅",
            SprintContract.DIM_VISUAL_DESIGN, "设计精美，无 AI 味",
            SprintContract.DIM_CODE_QUALITY, "代码规范"
        );

        EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

        // 验证视觉高分获得较好加权总分
        assertTrue(report.getWeightedTotalScore() >= 7.0);
        assertTrue(report.isAllPassed());

        contract.complete();
        assertEquals(SprintContract.ContractStatus.COMPLETED, contract.getStatus());
    }

    @Test
    @DisplayName("端到端场景：后端任务功能性门槛否决")
    void testEndToEndBackendThresholdVeto() {
        SprintContract contract = SprintContract.createBackendContract(
            "实现用户认证 API", "e2e-002");

        // 后端任务功能性门槛更高（6.0）
        assertEquals(6.0, contract.getThresholds().get(SprintContract.DIM_FUNCTIONALITY), 0.001);

        contract.startNegotiation();
        contract.signByGenerator();
        contract.signByEvaluator();
        contract.startExecution();

        // 功能性得分低于后端门槛
        Map<String, Double> scores = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 7.0,
            SprintContract.DIM_FUNCTIONALITY, 4.0,  // 低于 6.0
            SprintContract.DIM_VISUAL_DESIGN, 7.0,
            SprintContract.DIM_CODE_QUALITY, 7.0
        );
        Map<String, String> evidences = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, "设计合理",
            SprintContract.DIM_FUNCTIONALITY, "认证逻辑有安全漏洞",
            SprintContract.DIM_VISUAL_DESIGN, "界面简洁",
            SprintContract.DIM_CODE_QUALITY, "代码规范"
        );

        EvaluationReport report = EvaluationReport.fromContract(contract, scores, evidences, 1);

        assertFalse(report.isAllPassed());
        assertTrue(report.needsRework());
        assertTrue(report.getThresholdFailures().get(0).contains(SprintContract.DIM_FUNCTIONALITY));
    }

    @Test
    @DisplayName("端到端场景：迭代改进后通过评估")
    void testEndToEndIterativeImprovement() {
        // 模拟迭代改进的过程
        SprintContract contract = SprintContract.createFullstackContract(
            "实现搜索功能", "e2e-003");
        contract.setMaxIterations(3);

        contract.startNegotiation();
        contract.signByGenerator();
        contract.signByEvaluator();

        // 第1轮：功能性未通过
        Map<String, Double> round1 = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 6.0,
            SprintContract.DIM_FUNCTIONALITY, 3.0,
            SprintContract.DIM_VISUAL_DESIGN, 6.0,
            SprintContract.DIM_CODE_QUALITY, 6.0
        );
        EvaluationReport r1 = EvaluationReport.fromContract(contract, round1,
            Map.of(SprintContract.DIM_FUNCTIONALITY, "搜索结果不准确"), 1);
        assertTrue(r1.needsRework());

        // 第2轮：改进后通过
        contract.incrementIteration();
        Map<String, Double> round2 = Map.of(
            SprintContract.DIM_PRODUCT_DEPTH, 7.0,
            SprintContract.DIM_FUNCTIONALITY, 7.0,
            SprintContract.DIM_VISUAL_DESIGN, 7.0,
            SprintContract.DIM_CODE_QUALITY, 7.0
        );
        EvaluationReport r2 = EvaluationReport.fromContract(contract, round2,
            Map.of(SprintContract.DIM_FUNCTIONALITY, "搜索结果准确"), 2);
        assertTrue(r2.isAllPassed());
    }
}
