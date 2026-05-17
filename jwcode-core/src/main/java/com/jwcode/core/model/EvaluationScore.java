package com.jwcode.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * EvaluationScore — 评估分数模型（EvaluatorAgent 核心）。
 *
 * <p>4 维加权评分体系，支持硬门槛否决机制。
 * 权重配置通过 {@link SprintContract} 传递，支持任务级覆盖。</p>
 *
 * <p>评分维度：</p>
 * <ul>
 *   <li><b>PRODUCT_DEPTH</b> — 产品设计深度（功能完整性、用户体验设计）</li>
 *   <li><b>FUNCTIONALITY</b> — 功能性（正确性、边界处理、错误处理）</li>
 *   <li><b>VISUAL_DESIGN</b> — 视觉设计（UI 一致性、审美质量、响应式）</li>
 *   <li><b>CODE_QUALITY</b> — 代码质量（可维护性、性能、安全、测试覆盖）</li>
 * </ul>
 */
public class EvaluationScore {

    /** 评分维度枚举 */
    public enum Dimension {
        PRODUCT_DEPTH("product_depth", "产品设计深度"),
        FUNCTIONALITY("functionality", "功能性"),
        VISUAL_DESIGN("visual_design", "视觉设计"),
        CODE_QUALITY("code_quality", "代码质量");

        private final String key;
        private final String displayName;

        Dimension(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }

        public static Dimension fromKey(String key) {
            for (Dimension d : values()) {
                if (d.key.equals(key)) return d;
            }
            return null;
        }
    }

    /** 评分维度 */
    private Dimension dimension;

    /** 得分（0.0 - 10.0） */
    private double score;

    /** 权重（0.0 - 1.0，所有维度权重之和应为 1.0） */
    private double weight;

    /** 硬门槛（低于此值判定为不通过） */
    private double threshold;

    /** 是否通过门槛 */
    private boolean passed;

    /** 评分依据（具体观察到的现象） */
    private String evidence;

    /** 未通过的具体项列表 */
    private List<String> failures;

    /** 改进建议 */
    private List<String> suggestions;

    public EvaluationScore() {
        this.failures = new ArrayList<>();
        this.suggestions = new ArrayList<>();
        this.passed = true;
    }

    // ==================== 构建 ====================

    /**
     * 创建带完整信息的评分。
     */
    public static EvaluationScore create(Dimension dimension, double score, double weight,
                                          double threshold, String evidence) {
        EvaluationScore es = new EvaluationScore();
        es.dimension = dimension;
        es.score = Math.max(0.0, Math.min(10.0, score));
        es.weight = weight;
        es.threshold = threshold;
        es.evidence = evidence;
        es.passed = es.score >= threshold;
        return es;
    }

    /**
     * 添加一条失败项。
     */
    public void addFailure(String failure) {
        this.failures.add(failure);
        this.passed = false;
    }

    /**
     * 添加一条改进建议。
     */
    public void addSuggestion(String suggestion) {
        this.suggestions.add(suggestion);
    }

    // ==================== 计算 ====================

    /**
     * 获取加权得分（score * weight）。
     */
    public double getWeightedScore() {
        return score * weight;
    }

    /**
     * 获取与门槛的差距（负值表示低于门槛）。
     */
    public double getGapToThreshold() {
        return score - threshold;
    }

    // ==================== Getters & Setters ====================

    public Dimension getDimension() { return dimension; }
    public void setDimension(Dimension dimension) { this.dimension = dimension; }

    public double getScore() { return score; }
    public void setScore(double score) {
        this.score = Math.max(0.0, Math.min(10.0, score));
        this.passed = this.score >= threshold;
    }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) {
        this.threshold = threshold;
        this.passed = this.score >= threshold;
    }

    public boolean isPassed() { return passed; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public List<String> getFailures() { return failures; }
    public void setFailures(List<String> failures) { this.failures = failures; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    // ==================== 报告 ====================

    /**
     * 单个维度的评估报告字符串。
     */
    public String toReportString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  - **").append(dimension.getDisplayName()).append("**");
        sb.append(": ").append(String.format("%.1f", score)).append("/10.0");
        sb.append(" (权重=").append(String.format("%.2f", weight)).append(")");
        sb.append(" [").append(passed ? "✅ 通过" : "❌ 未通过").append("]");
        sb.append("\n");
        if (evidence != null && !evidence.isEmpty()) {
            sb.append("    依据: ").append(evidence).append("\n");
        }
        if (!failures.isEmpty()) {
            sb.append("    失败项:\n");
            for (String f : failures) {
                sb.append("    - ").append(f).append("\n");
            }
        }
        if (!suggestions.isEmpty()) {
            sb.append("    建议:\n");
            for (String s : suggestions) {
                sb.append("    - ").append(s).append("\n");
            }
        }
        return sb.toString();
    }
}
