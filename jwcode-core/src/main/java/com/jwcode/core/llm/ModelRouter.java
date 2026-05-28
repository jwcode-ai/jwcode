package com.jwcode.core.llm;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.planner.IntentAnalyzer;

import java.util.logging.Logger;

/**
 * 动态模型路由器 — 基于任务特征自动选择最优模型。
 *
 * <p>三层策略：
 * <ol>
 *   <li>对话/简单任务 → 低延迟模型</li>
 *   <li>推理/规划任务 → 推理模型</li>
 *   <li>代码生成/重构 → 旗舰模型</li>
 * </ol>
 * 配置通过 {@code ~/.jwcode/config.yaml} 的 {@code model-routing} 段。
 */
public class ModelRouter {
    private static final Logger logger = Logger.getLogger(ModelRouter.class.getName());

    private final JwcodeConfig config;
    // 闭环1: 成本反馈 — 记录每个 taskType 在各模型上的平均成本
    private final java.util.Map<String, java.util.Map<String, double[]>> costStats = new java.util.concurrent.ConcurrentHashMap<>();

    public ModelRouter(JwcodeConfig config) {
        this.config = config;
    }

    /** 成本反馈：每次 LLM 调用后更新统计。CostTracker 接线到此处。 */
    public void recordCost(String taskType, String modelId, long costMicros) {
        if (taskType == null) taskType = "general";
        costStats.computeIfAbsent(taskType, k -> new java.util.concurrent.ConcurrentHashMap<>())
            .computeIfAbsent(modelId, k -> new double[]{0, 0}); // {totalCost, callCount}
        double[] stats = costStats.get(taskType).get(modelId);
        synchronized (stats) { stats[0] += costMicros; stats[1]++; }
    }

    /** 获取某类任务在各模型上的平均成本，用于路由优化 */
    public java.util.Map<String, Double> getAvgCosts(String taskType) {
        java.util.Map<String, Double> result = new java.util.LinkedHashMap<>();
        java.util.Map<String, double[]> stats = costStats.getOrDefault(taskType, java.util.Map.of());
        for (var e : stats.entrySet()) {
            double[] v = e.getValue();
            result.put(e.getKey(), v[1] > 0 ? v[0] / v[1] : 0);
        }
        return result;
    }

    /**
     * 从 IntentAnalyzer.AnalysisResult 提取特征并路由。
     */
    public String route(IntentAnalyzer.AnalysisResult analysis) {
        if (analysis == null) return config.getDefaultModel().getId();

        IntentAnalyzer.Complexity complexity = analysis.getComplexity() != null
            ? analysis.getComplexity() : IntentAnalyzer.Complexity.MEDIUM;

        boolean isConversational = isConversationalTask(analysis);
        boolean needsReasoning = needsReasoning(analysis);

        // 1. 纯对话/问答 → 轻量模型
        if (complexity == IntentAnalyzer.Complexity.SIMPLE && isConversational) {
            return selectModel("light", config.getDefaultModel().getId());
        }

        // 2. 需要深度推理 → 推理模型
        if (needsReasoning || complexity == IntentAnalyzer.Complexity.COMPLEX) {
            return selectModel("reasoning", config.getDefaultModel().getId());
        }

        // 3. 默认 → 旗舰模型
        return config.getDefaultModel().getId();
    }

    /**
     * 按名称路由（Plan 模式用）。
     */
    public String routeByName(String taskSummary) {
        if (taskSummary == null || taskSummary.isEmpty())
            return config.getDefaultModel().getId();

        String lower = taskSummary.toLowerCase();
        if (lower.contains("分析") || lower.contains("review") || lower.contains("explain"))
            return selectModel("light", config.getDefaultModel().getId());
        if (lower.contains("设计") || lower.contains("architect") || lower.contains("refactor"))
            return selectModel("reasoning", config.getDefaultModel().getId());
        return config.getDefaultModel().getId();
    }

    /** 按角色名查找模型中是否有匹配标记 */
    private String selectModel(String role, String fallback) {
        JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
        if (provider != null) {
            for (JwcodeConfig.ModelDefinition m : provider.getModels()) {
                if (m.getId() != null && m.getId().contains(role)) {
                    logger.fine("[ModelRouter] " + role + " → " + m.getId());
                    return m.getId();
                }
            }
        }
        return fallback;
    }

    private static boolean isConversationalTask(IntentAnalyzer.AnalysisResult a) {
        String s = a.getSummary() != null ? a.getSummary().toLowerCase() : "";
        IntentAnalyzer.TaskType t = a.getTaskType();
        return s.contains("how to") || s.contains("explain") || s.contains("what is")
            || s.contains("为什么") || s.contains("怎么") || s.contains("什么是")
            || t == IntentAnalyzer.TaskType.ANALYZE
            || t == IntentAnalyzer.TaskType.CHAT;
    }

    private static boolean needsReasoning(IntentAnalyzer.AnalysisResult a) {
        IntentAnalyzer.TaskType t = a.getTaskType();
        return t == IntentAnalyzer.TaskType.DEBUG
            || t == IntentAnalyzer.TaskType.REFACTOR
            || t == IntentAnalyzer.TaskType.FEATURE
            || t == IntentAnalyzer.TaskType.BUGFIX;
    }
}
