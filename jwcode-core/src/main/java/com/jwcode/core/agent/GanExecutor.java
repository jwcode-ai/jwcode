package com.jwcode.core.agent;

import com.jwcode.core.a2a.A2AFacade;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.SprintContract;
import com.jwcode.core.planner.IntentAnalyzer.AnalysisResult;
import com.jwcode.core.service.IterativeSprintOrchestrator;
import com.jwcode.core.service.IterativeSprintOrchestrator.IterationResult;

import java.util.logging.Logger;

/**
 * GAN 迭代执行器 — 从 EnhancedOrchestratorAgent 提取。
 *
 * <p>负责 Generator ⇄ Evaluator 迭代循环的完整流程：
 * SprintContract 创建 → 权重配置选择 → 签约 → 迭代执行 → 结果格式化。</p>
 */
class GanExecutor {
    private static final Logger logger = Logger.getLogger(GanExecutor.class.getName());

    private final A2AFacade a2aFacade;
    private final LLMService llmService;

    GanExecutor(A2AFacade a2aFacade, LLMService llmService) {
        this.a2aFacade = a2aFacade;
        this.llmService = llmService;
    }

    String execute(AnalysisResult analysis, TaskOutput coderOutput, String taskId) {
        StringBuilder sb = new StringBuilder();
        try {
            SprintContract contract = selectContract(analysis, taskId, sb);
            contract.addAcceptanceCriterion("功能完整性：所有功能按规格实现");
            contract.addAcceptanceCriterion("正确性：核心逻辑无错误");
            contract.addAcceptanceCriterion("代码质量：符合项目编码规范");
            contract.addAcceptanceCriterion("边界处理：空状态、错误状态、异常输入");

            contract.startNegotiation();
            contract.signByGenerator();
            contract.signByEvaluator();

            sb.append("  > Sprint Contract 已签署: ").append(contract.getContractId()).append("\n");
            sb.append("  > 最大迭代轮数: ").append(contract.getMaxIterations()).append("\n\n");

            AgentRegistry registry = AgentRegistry.createDefault();
            IterativeSprintOrchestrator orchestrator =
                new IterativeSprintOrchestrator(registry, a2aFacade, llmService);
            IterationResult result = orchestrator.executeSprint(contract, coderOutput.getSummary());

            if (result.isSuccess()) {
                sb.append("  ✅ GAN 迭代循环完成: ").append(result.toSummary()).append("\n");
            } else {
                sb.append("  ⚠️ GAN 迭代循环未完全通过: ").append(result.toSummary()).append("\n");
                sb.append("  > 最后一次评估报告:\n");
                if (result.getLastReport() != null) {
                    sb.append("  > Verdict: ").append(result.getLastReport().getVerdict().getLabel()).append("\n");
                    sb.append("  > 加权总分: ").append(String.format("%.2f",
                        result.getLastReport().getWeightedTotalScore())).append("/10.0\n");
                }
            }
        } catch (Exception e) {
            logger.warning("GAN iteration failed: " + e.getMessage());
            sb.append("  ❌ GAN 迭代执行异常: ").append(e.getMessage()).append("\n");
        }
        return sb.toString();
    }

    private SprintContract selectContract(AnalysisResult analysis, String taskId, StringBuilder sb) {
        String tech = analysis.getTechStack();
        boolean isFrontend = tech != null
            && (tech.toLowerCase().contains("react") || tech.toLowerCase().contains("vue")
                || tech.toLowerCase().contains("ui") || tech.toLowerCase().contains("frontend"));
        boolean isBackend = tech != null
            && (tech.toLowerCase().contains("api") || tech.toLowerCase().contains("backend")
                || tech.toLowerCase().contains("database"));

        if (isFrontend) {
            sb.append("  > 使用前端权重配置（视觉设计权重最高: 0.35）\n");
            return SprintContract.createFrontendContract(analysis.getSummary(), taskId);
        }
        if (isBackend) {
            sb.append("  > 使用后端权重配置（功能性权重最高: 0.35）\n");
            return SprintContract.createBackendContract(analysis.getSummary(), taskId);
        }
        sb.append("  > 使用全栈权重配置\n");
        return SprintContract.createFullstackContract(analysis.getSummary(), taskId);
    }
}
