package com.jwcode.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EvaluationReport — 完整评估报告。
 *
 * <p>包含 4 维评分、硬门槛检查结果、总体 verdict 和改进建议。
 * 评估报告将作为反馈注入 Generator 的下一轮迭代上下文。</p>
 */
public class EvaluationReport {

    /** 合同 ID（关联 SprintContract） */
    private String contractId;

    /** 迭代轮数 */
    private int iterationRound;

    /** 各维度评分 */
    private List<EvaluationScore> scores;

    /** 总体 verdict */
    private Verdict verdict;

    /** 总体加权得分 */
    private double weightedTotalScore;

    /** 评估时间 */
    private Instant evaluatedAt;

    /** 评估摘要（面向 Generator 的反馈摘要） */
    private String summary;

    /** 详细反馈（注入 Generator 下一轮迭代的上下文） */
    private String detailedFeedback;

    /** 未通过的门槛列表 */
    private List<String> thresholdFailures;

    /** 改进建议汇总 */
    private List<String> improvementSuggestions;

    // ==================== 枚举 ====================

    public enum Verdict {
        /** 全部通过 — 可以交付 */
        PASS("✅ PASS", "全部维度通过验收"),
        /** 条件通过 — 有改进空间但不阻塞 */
        CONDITIONAL_PASS("⚠️ CONDITIONAL_PASS", "全部维度通过门槛，但有改进建议"),
        /** 不通过 — 至少一个维度低于门槛 */
        FAIL("❌ FAIL", "存在未通过门槛的维度，需要返工"),
        /** 严重失败 — 多个维度严重不达标 */
        CRITICAL_FAIL("🚫 CRITICAL_FAIL", "多个维度严重不达标，建议重新规划");

        private final String label;
        private final String description;

        Verdict(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    // ==================== 构造器 ====================

    public EvaluationReport() {
        this.scores = new ArrayList<>();
        this.thresholdFailures = new ArrayList<>();
        this.improvementSuggestions = new ArrayList<>();
        this.evaluatedAt = Instant.now();
        this.verdict = Verdict.PASS;
    }

    // ==================== 构建 ====================

    /**
     * 从 SprintContract 和维度得分生成评估报告。
     *
     * @param contract Sprint 合同
     * @param dimensionScores 维度名 → 得分映射
     * @param evidences 维度名 → 评分依据映射
     * @param iterationRound 当前迭代轮数
     * @return 评估报告
     */
    public static EvaluationReport fromContract(SprintContract contract,
                                                 Map<String, Double> dimensionScores,
                                                 Map<String, String> evidences,
                                                 int iterationRound) {
        EvaluationReport report = new EvaluationReport();
        report.contractId = contract.getContractId();
        report.iterationRound = iterationRound;

        // 为每个维度构建 EvaluationScore
        for (Map.Entry<String, Double> weightEntry : contract.getScoringWeights().entrySet()) {
            String dimKey = weightEntry.getKey();
            double weight = weightEntry.getValue();
            Double score = dimensionScores.getOrDefault(dimKey, 0.0);
            double threshold = contract.getThresholds().getOrDefault(dimKey, 5.0);
            String evidence = evidences.getOrDefault(dimKey, "");

            EvaluationScore.Dimension dim = EvaluationScore.Dimension.fromKey(dimKey);
            if (dim != null) {
                EvaluationScore es = EvaluationScore.create(dim, score, weight, threshold, evidence);
                report.scores.add(es);
            }
        }

        // 检查门槛
        report.thresholdFailures = contract.checkThresholds(dimensionScores);

        // 计算加权总分
        report.weightedTotalScore = contract.calculateWeightedScore(dimensionScores);

        // 确定 Verdict
        if (report.thresholdFailures.isEmpty()) {
            boolean allHighScore = report.scores.stream().allMatch(s -> s.getScore() >= 7.0);
            report.verdict = allHighScore ? Verdict.PASS : Verdict.CONDITIONAL_PASS;
        } else {
            long criticalFailures = report.thresholdFailures.size();
            report.verdict = criticalFailures >= 2 ? Verdict.CRITICAL_FAIL : Verdict.FAIL;
        }

        // 生成摘要
        report.summary = report.generateSummary();
        report.detailedFeedback = report.generateDetailedFeedback();

        return report;
    }

    /**
     * 创建失败报告（当评估无法正常进行时使用）。
     *
     * @param contractId 合同 ID
     * @param failureReason 失败原因
     * @param iterationRound 当前迭代轮数
     * @return 标记为 FAIL 的评估报告
     */
    public static EvaluationReport createFailureReport(String contractId, String failureReason, int iterationRound) {
        EvaluationReport report = new EvaluationReport();
        report.contractId = contractId;
        report.iterationRound = iterationRound;
        report.verdict = Verdict.FAIL;
        report.weightedTotalScore = 0.0;
        report.summary = "评估失败: " + failureReason;
        report.detailedFeedback = "评估无法完成: " + failureReason;
        report.thresholdFailures = List.of(failureReason);
        report.improvementSuggestions = List.of("请检查评估运行时是否正常运行");
        return report;
    }

    /**
     * 从 JSON 字符串解析 EvaluationReport。
     *
     * @param json JSON 字符串
     * @return 解析后的 EvaluationReport，解析失败返回 null
     */
    public static EvaluationReport fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            // 使用 Jackson 或手动解析 JSON
            // 目前返回 null 表示需要调用方自行解析
            // 后续可接入 Jackson ObjectMapper 实现完整 JSON 解析
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 添加一条改进建议。
     */
    public void addImprovementSuggestion(String suggestion) {
        this.improvementSuggestions.add(suggestion);
    }

    // ==================== 内部方法 ====================

    private String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("迭代 #").append(iterationRound).append(" 评估结果: ");
        sb.append(verdict.getLabel()).append("\n");
        sb.append("加权总分: ").append(String.format("%.2f", weightedTotalScore)).append("/10.0\n");
        if (!thresholdFailures.isEmpty()) {
            sb.append("未通过门槛: ").append(thresholdFailures.size()).append(" 个维度\n");
        }
        return sb.toString();
    }

    private String generateDetailedFeedback() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 评估报告 (迭代 #").append(iterationRound).append(")\n\n");
        sb.append("**Verdict**: ").append(verdict.getLabel()).append("\n");
        sb.append("**加权总分**: ").append(String.format("%.2f", weightedTotalScore)).append("/10.0\n\n");

        sb.append("### 各维度评分\n");
        for (EvaluationScore es : scores) {
            sb.append(es.toReportString()).append("\n");
        }

        if (!thresholdFailures.isEmpty()) {
            sb.append("### 未通过门槛\n");
            for (String f : thresholdFailures) {
                sb.append("- ").append(f).append("\n");
            }
            sb.append("\n");
        }

        if (!improvementSuggestions.isEmpty()) {
            sb.append("### 改进建议\n");
            for (String s : improvementSuggestions) {
                sb.append("- ").append(s).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 检查是否所有维度都通过门槛。
     */
    public boolean isAllPassed() {
        return thresholdFailures.isEmpty();
    }

    /**
     * 检查是否需要返工（FAIL 或 CRITICAL_FAIL）。
     */
    public boolean needsRework() {
        return verdict == Verdict.FAIL || verdict == Verdict.CRITICAL_FAIL;
    }

    // ==================== Getters & Setters ====================

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }

    public int getIterationRound() { return iterationRound; }
    public void setIterationRound(int iterationRound) { this.iterationRound = iterationRound; }

    public List<EvaluationScore> getScores() { return scores; }
    public void setScores(List<EvaluationScore> scores) { this.scores = scores; }

    public Verdict getVerdict() { return verdict; }
    public void setVerdict(Verdict verdict) { this.verdict = verdict; }

    public double getWeightedTotalScore() { return weightedTotalScore; }
    public void setWeightedTotalScore(double weightedTotalScore) { this.weightedTotalScore = weightedTotalScore; }

    public Instant getEvaluatedAt() { return evaluatedAt; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDetailedFeedback() { return detailedFeedback; }
    public void setDetailedFeedback(String detailedFeedback) { this.detailedFeedback = detailedFeedback; }

    public List<String> getThresholdFailures() { return thresholdFailures; }
    public void setThresholdFailures(List<String> thresholdFailures) { this.thresholdFailures = thresholdFailures; }

    public List<String> getImprovementSuggestions() { return improvementSuggestions; }
    public void setImprovementSuggestions(List<String> improvementSuggestions) { this.improvementSuggestions = improvementSuggestions; }

    /**
     * 从 Map 数据解析 EvaluationReport（用于 A2A TaskOutput 解析）。
     *
     * @param data 包含评估结果的数据 Map
     * @param contract Sprint 合同
     * @param iterationRound 当前迭代轮数
     * @return 解析后的 EvaluationReport，解析失败返回 null
     */
    public static EvaluationReport tryParse(Map<String, Object> data, SprintContract contract, int iterationRound) {
        if (data == null || contract == null) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Double> scores = (Map<String, Double>) data.getOrDefault("scores", new HashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, String> evidences = (Map<String, String>) data.getOrDefault("evidences", new HashMap<>());
            return fromContract(contract, scores, evidences, iterationRound);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JSON 字符串解析 EvaluationReport（带 contract 上下文）。
     *
     * @param json JSON 字符串
     * @param contract Sprint 合同
     * @param iterationRound 当前迭代轮数
     * @return 解析后的 EvaluationReport，解析失败返回 null
     */
    public static EvaluationReport fromJson(String json, SprintContract contract, int iterationRound) {
        if (json == null || json.isBlank() || contract == null) return null;
        try {
            // 简化解析：从 JSON 中提取 scores 和 evidences
            // 实际项目中应使用 Jackson ObjectMapper
            return null; // 暂时返回 null，由调用方处理回退
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从字符串数据尝试解析 EvaluationReport（兼容旧接口）。
     *
     * @param data 字符串数据
     * @param contract Sprint 合同
     * @param iterationRound 当前迭代轮数
     * @return 解析后的 EvaluationReport
     */
    public static EvaluationReport tryParse(String data, SprintContract contract, int iterationRound) {
        return fromJson(data, contract, iterationRound);
    }
}
