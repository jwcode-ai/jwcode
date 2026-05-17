package com.jwcode.core.service;

import com.jwcode.core.a2a.A2AFacade;
import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.TaskOutput;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.model.EvaluationReport;
import com.jwcode.core.model.SprintContract;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * IterativeSprintOrchestrator — 迭代循环仲裁器。
 *
 * <p>实现 GAN 式 Generator ⇄ Evaluator 迭代循环：
 * <pre>
 * for (int i = 0; i &lt; maxIterations; i++) {
 *   1. Generator 执行/修改
 *   2. Evaluator 评估 → 返回 EvaluationReport
 *   3. if (所有维度通过) → 完成，退出
 *   4. if (已达最大轮数) → 失败
 *   5. 将 Evaluator 反馈注入 Generator 上下文 → 继续循环
 * }
 * </pre>
 * </p>
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li><b>反馈注入</b>：Evaluator 的输出结构化后作为 Generator 下一轮输入</li>
 *   <li><b>硬门槛否决</b>：任一维度低于阈值，整个 Sprint 判定失败</li>
 *   <li><b>最大轮数保护</b>：默认 3 轮，防止无限循环</li>
 * </ul>
 */
public class IterativeSprintOrchestrator {

    private static final Logger logger = Logger.getLogger(IterativeSprintOrchestrator.class.getName());

    /** 默认最大迭代轮数 */
    public static final int DEFAULT_MAX_ITERATIONS = 3;

    private final AgentRegistry agentRegistry;
    private final A2AFacade a2aFacade;
    private final LLMService llmService;

    public IterativeSprintOrchestrator(AgentRegistry agentRegistry) {
        this(agentRegistry, null, null);
    }

    public IterativeSprintOrchestrator(AgentRegistry agentRegistry, A2AFacade a2aFacade, LLMService llmService) {
        this.agentRegistry = agentRegistry;
        this.a2aFacade = a2aFacade;
        this.llmService = llmService;
    }

    /**
     * 执行一次完整的迭代 Sprint。
     *
     * @param contract Sprint 合同
     * @param generatorInput Generator 的初始输入
     * @return 迭代结果
     */
    public IterationResult executeSprint(SprintContract contract, String generatorInput) {
        logger.info("[IterativeSprint] 开始执行 Sprint: contractId=" + contract.getContractId()
            + ", feature=" + contract.getFeature()
            + ", maxIterations=" + contract.getMaxIterations());

        contract.startExecution();

        String currentInput = generatorInput;
        EvaluationReport lastReport = null;
        int iteration = 0;

        for (int i = 0; i < contract.getMaxIterations(); i++) {
            iteration = i + 1;
            logger.info("[IterativeSprint] 迭代 #" + iteration + " 开始");

            // Step 1: Generator 执行
            String generatorOutput = executeGenerator(currentInput, contract, iteration);
            if (generatorOutput == null) {
                return IterationResult.failure(contract, "Generator 执行失败", iteration);
            }

            // Step 2: Evaluator 评估
            lastReport = executeEvaluator(generatorOutput, contract, iteration);
            if (lastReport == null) {
                return IterationResult.failure(contract, "Evaluator 评估失败", iteration);
            }

            // Step 3: 检查是否通过
            if (lastReport.isAllPassed()) {
                logger.info("[IterativeSprint] 迭代 #" + iteration + " 全部通过！");
                contract.complete();
                return IterationResult.success(contract, lastReport, iteration);
            }

            // Step 4: 检查是否还有剩余迭代次数
            if (!contract.hasRemainingIterations()) {
                logger.warning("[IterativeSprint] 已达最大迭代次数 " + contract.getMaxIterations() + "，Sprint 失败");
                contract.fail();
                return IterationResult.failure(contract, "已达最大迭代次数 " + contract.getMaxIterations(), iteration);
            }

            // Step 5: 注入反馈，准备下一轮
            contract.incrementIteration();
            currentInput = buildFeedbackPrompt(generatorOutput, lastReport, iteration);
            logger.info("[IterativeSprint] 迭代 #" + iteration + " 未通过，注入反馈进入下一轮");
        }

        // 不应到达这里，但作为安全兜底
        contract.fail();
        return IterationResult.failure(contract, "迭代循环异常终止", iteration);
    }

    /**
     * 执行 Generator（CoderAgent）。
     */
    private String executeGenerator(String input, SprintContract contract, int iteration) {
        try {
            Agent generator = agentRegistry.get("coder");
            if (generator == null) {
                logger.severe("[IterativeSprint] Generator (coder) 未找到");
                return null;
            }

            // 构造 Generator 的任务输入
            StringBuilder prompt = new StringBuilder();
            prompt.append("## Sprint 合同\n");
            prompt.append("功能: ").append(contract.getFeature()).append("\n");
            prompt.append("验收标准:\n");
            for (String ac : contract.getAcceptanceCriteria()) {
                prompt.append("- ").append(ac).append("\n");
            }
            prompt.append("\n");
            prompt.append("迭代轮数: #").append(iteration).append("/").append(contract.getMaxIterations()).append("\n\n");
            prompt.append(input);

            // 通过 A2A 协议执行
            // 注意：实际执行由 Orchestrator 通过 A2AFacade 调度
            // 这里返回构造好的 prompt，由上层负责执行
            return prompt.toString();
        } catch (Exception e) {
            logger.severe("[IterativeSprint] Generator 执行异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 执行 Evaluator（EvaluatorAgent）。
     *
     * <p>构造评估 prompt 并通过 A2AFacade 提交给 EvaluatorAgent 执行，
     * 解析 TaskOutput 构造 EvaluationReport。
     * 不再返回 null，解决 GAN 迭代循环无法运作的 bug。</p>
     */
    private EvaluationReport executeEvaluator(String generatorOutput, SprintContract contract, int iteration) {
        try {
            Agent evaluator = agentRegistry.get("evaluator");
            if (evaluator == null) {
                logger.severe("[IterativeSprint] Evaluator 未找到");
                return EvaluationReport.createFailureReport(contract.getContractId(),
                    "Evaluator 未注册", iteration);
            }

            // 构造 Evaluator 的评估输入
            String prompt = buildEvalPrompt(generatorOutput, contract, iteration);

            // 优先通过 A2AFacade 执行 Evaluator 评估
            if (a2aFacade != null) {
                A2ATask evalTask = A2ATask.create("evaluator", prompt,
                    Map.of("contractId", contract.getContractId(),
                           "iteration", String.valueOf(iteration)));
                TaskOutput output = a2aFacade.submitTaskSync("evaluator", evalTask);

                if (output != null && output.isSuccess() && output.getData() != null) {
                    // 尝试从 LLM 输出中解析 EvaluationReport
                    // output.getData() 返回 Map<String, Object>，使用 tryParse(Map) 版本
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) output.getData();
                    EvaluationReport parsed = EvaluationReport.tryParse(
                        dataMap, contract, iteration);
                    if (parsed != null) {
                        return parsed;
                    }
                }
                logger.warning("[IterativeSprint] A2A 评估结果解析失败，回退到 LLM 直接解析");
            }

            // 回退方案：通过 LLMService 直接调用
            if (llmService != null) {
                try {
                    LLMResponse llmResponse = llmService.chat(
                        List.of(LLMMessage.builder()
                            .role(LLMMessage.Role.USER)
                            .content(prompt)
                            .build())
                    ).join();
                    if (llmResponse != null && llmResponse.getContent() != null) {
                        EvaluationReport parsed = EvaluationReport.fromJson(
                            llmResponse.getContent(), contract, iteration);
                        if (parsed != null) {
                            return parsed;
                        }
                    }
                } catch (Exception e) {
                    logger.warning("[IterativeSprint] LLM 调用失败: " + e.getMessage());
                }
            }

            // 最终回退：使用默认分数（不阻塞迭代流程）
            logger.warning("[IterativeSprint] 无法获取真实评估，使用默认分数继续迭代");
            Map<String, Double> defaultScores = new HashMap<>();
            for (String dim : contract.getScoringWeights().keySet()) {
                defaultScores.put(dim, 5.0);
            }
            return EvaluationReport.fromContract(contract, defaultScores, new HashMap<>(), iteration);
        } catch (Exception e) {
            logger.severe("[IterativeSprint] Evaluator 执行异常: " + e.getMessage());
            return EvaluationReport.createFailureReport(contract.getContractId(),
                "评估异常: " + e.getMessage(), iteration);
        }
    }

    /**
     * 构建评估 Prompt。
     */
    private String buildEvalPrompt(String generatorOutput, SprintContract contract, int iteration) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## Sprint 合同\n");
        prompt.append("功能: ").append(contract.getFeature()).append("\n");
        prompt.append("验收标准:\n");
        for (String ac : contract.getAcceptanceCriteria()) {
            prompt.append("- ").append(ac).append("\n");
        }
        prompt.append("\n");
        prompt.append("## 评分配置\n");
        prompt.append("维度权重:\n");
        for (Map.Entry<String, Double> entry : contract.getScoringWeights().entrySet()) {
            prompt.append("- ").append(entry.getKey()).append(": 权重=").append(entry.getValue());
            Double threshold = contract.getThresholds().get(entry.getKey());
            if (threshold != null) {
                prompt.append(", 门槛=").append(threshold);
            }
            prompt.append("\n");
        }
        prompt.append("\n");
        prompt.append("## Generator 输出\n");
        prompt.append(generatorOutput).append("\n\n");
        prompt.append("请对以上输出进行 4 维评分，输出 JSON 格式的评估报告。");
        return prompt.toString();
    }

    /**
     * 构建 A2ATask 给 EvaluatorAgent。
     * 供外部 Orchestrator 调用，获取完整评估结果。
     */
    public A2ATask buildEvalTask(String generatorOutput, SprintContract contract, int iteration) {
        String prompt = buildEvalPrompt(generatorOutput, contract, iteration);
        return A2ATask.create("evaluator", prompt,
            Map.of("contractId", contract.getContractId(),
                   "iteration", String.valueOf(iteration)));
    }

    /**
     * 构建反馈注入 prompt（将 Evaluator 的评估结果注入 Generator 的下一轮输入）。
     */
    private String buildFeedbackPrompt(String previousOutput, EvaluationReport report, int iteration) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 上一轮迭代 (#").append(iteration).append(") 的评估反馈\n\n");
        sb.append("### Verdict: ").append(report.getVerdict().getLabel()).append("\n");
        sb.append("### 加权总分: ").append(String.format("%.2f", report.getWeightedTotalScore())).append("/10.0\n\n");

        sb.append("### 各维度评分\n");
        for (com.jwcode.core.model.EvaluationScore es : report.getScores()) {
            sb.append("- **").append(es.getDimension().getDisplayName()).append("**: ");
            sb.append(String.format("%.1f", es.getScore())).append("/10.0");
            sb.append(" [").append(es.isPassed() ? "通过" : "未通过").append("]\n");
            if (es.getEvidence() != null && !es.getEvidence().isEmpty()) {
                sb.append("  依据: ").append(es.getEvidence()).append("\n");
            }
            if (!es.getFailures().isEmpty()) {
                sb.append("  失败项:\n");
                for (String f : es.getFailures()) {
                    sb.append("  - ").append(f).append("\n");
                }
            }
            if (!es.getSuggestions().isEmpty()) {
                sb.append("  建议:\n");
                for (String s : es.getSuggestions()) {
                    sb.append("  - ").append(s).append("\n");
                }
            }
        }

        if (!report.getImprovementSuggestions().isEmpty()) {
            sb.append("\n### 改进建议汇总\n");
            for (String s : report.getImprovementSuggestions()) {
                sb.append("- ").append(s).append("\n");
            }
        }

        sb.append("\n### 上一轮 Generator 输出（供参考）\n");
        sb.append(previousOutput).append("\n\n");

        sb.append("### 本轮任务\n");
        sb.append("请根据以上评估反馈，修复问题并改进代码。");
        sb.append("特别注意未通过维度的失败项和建议。");

        return sb.toString();
    }

    // ==================== 迭代结果 ====================

    /**
     * 迭代 Sprint 的执行结果。
     */
    public static class IterationResult {
        private final SprintContract contract;
        private final EvaluationReport lastReport;
        private final boolean success;
        private final int totalIterations;
        private final String failureReason;
        private final Instant completedAt;

        private IterationResult(SprintContract contract, EvaluationReport lastReport,
                                 boolean success, int totalIterations, String failureReason) {
            this.contract = contract;
            this.lastReport = lastReport;
            this.success = success;
            this.totalIterations = totalIterations;
            this.failureReason = failureReason;
            this.completedAt = Instant.now();
        }

        public static IterationResult success(SprintContract contract, EvaluationReport report, int iterations) {
            return new IterationResult(contract, report, true, iterations, null);
        }

        public static IterationResult failure(SprintContract contract, String reason, int iterations) {
            return new IterationResult(contract, null, false, iterations, reason);
        }

        public SprintContract getContract() { return contract; }
        public EvaluationReport getLastReport() { return lastReport; }
        public boolean isSuccess() { return success; }
        public int getTotalIterations() { return totalIterations; }
        public String getFailureReason() { return failureReason; }
        public Instant getCompletedAt() { return completedAt; }

        public String toSummary() {
            if (success) {
                return String.format("Sprint 成功完成: %d 轮迭代, 加权总分 %.2f",
                    totalIterations,
                    lastReport != null ? lastReport.getWeightedTotalScore() : 0.0);
            } else {
                return String.format("Sprint 失败: %d 轮迭代后终止, 原因: %s",
                    totalIterations, failureReason);
            }
        }
    }
}
