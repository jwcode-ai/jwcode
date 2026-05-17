package com.jwcode.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SprintContract — Sprint 合同模型。
 *
 * <p>在执行前，Generator 和 Evaluator 通过 SprintContract 对验收标准达成共识。
 * 合同状态机：DRAFT → NEGOTIATING → SIGNED → EXECUTING → COMPLETED / FAILED</p>
 *
 * <p>核心设计理念：</p>
 * <ul>
 *   <li><b>事前共识</b>：执行前双方对"做什么"和"什么算做完"达成一致</li>
 *   <li><b>权重即策略</b>：评分权重通过合同传递，支持任务级覆盖</li>
 *   <li><b>硬门槛否决</b>：任一维度低于阈值，整个 Sprint 判定失败</li>
 * </ul>
 */
public class SprintContract {

    /** 合同唯一标识 */
    private String contractId;

    /** 关联的任务 ID */
    private String taskId;

    /** 功能描述（来自 Planner） */
    private String feature;

    /** 验收标准列表（双方谈判结果） */
    private List<String> acceptanceCriteria;

    /** 评分维度权重配置（维度名 → 权重值） */
    private Map<String, Double> scoringWeights;

    /** 各维度硬门槛（维度名 → 最低通过分数 0.0-10.0） */
    private Map<String, Double> thresholds;

    /** 最大迭代轮数 */
    private int maxIterations;

    /** 当前迭代轮数 */
    private int currentIteration;

    /** 合同状态 */
    private ContractStatus status;

    /** Generator 确认签名 */
    private boolean signedByGenerator;

    /** Evaluator 确认签名 */
    private boolean signedByEvaluator;

    /** 交接文档路径（Context Reset 时使用） */
    private String handoffArtifactPath;

    /** 创建时间 */
    private Instant createdAt;

    /** 签署时间 */
    private Instant signedAt;

    /** 完成/失败时间 */
    private Instant completedAt;

    /** 扩展属性 */
    private Map<String, Object> metadata;

    // ==================== 枚举 ====================

    public enum ContractStatus {
        /** 草稿 — 初始状态 */
        DRAFT,
        /** 谈判中 — Generator 和 Evaluator 正在协商 */
        NEGOTIATING,
        /** 已签署 — 双方达成一致，可以开始执行 */
        SIGNED,
        /** 执行中 — Generator 正在实现 */
        EXECUTING,
        /** 已完成 — 所有验收标准通过 */
        COMPLETED,
        /** 失败 — 达到最大迭代轮数仍未通过 */
        FAILED;

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED;
        }

        public boolean canTransitionTo(ContractStatus target) {
            return switch (this) {
                case DRAFT -> target == NEGOTIATING;
                case NEGOTIATING -> target == SIGNED || target == DRAFT;
                case SIGNED -> target == EXECUTING;
                case EXECUTING -> target == COMPLETED || target == FAILED || target == SIGNED;
                case COMPLETED, FAILED -> false;
            };
        }
    }

    // ==================== 评分维度常量 ====================

    /** 产品设计深度 */
    public static final String DIM_PRODUCT_DEPTH = "product_depth";
    /** 功能性 */
    public static final String DIM_FUNCTIONALITY = "functionality";
    /** 视觉设计 */
    public static final String DIM_VISUAL_DESIGN = "visual_design";
    /** 代码质量 */
    public static final String DIM_CODE_QUALITY = "code_quality";

    // ==================== 构造器 ====================

    public SprintContract() {
        this.contractId = UUID.randomUUID().toString().substring(0, 12);
        this.acceptanceCriteria = new ArrayList<>();
        this.scoringWeights = new HashMap<>();
        this.thresholds = new HashMap<>();
        this.maxIterations = 3;
        this.currentIteration = 0;
        this.status = ContractStatus.DRAFT;
        this.signedByGenerator = false;
        this.signedByEvaluator = false;
        this.createdAt = Instant.now();
        this.metadata = new HashMap<>();
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建前端任务的默认合同（视觉设计权重最高）。
     */
    public static SprintContract createFrontendContract(String feature, String taskId) {
        SprintContract contract = new SprintContract();
        contract.setTaskId(taskId);
        contract.setFeature(feature);
        contract.getScoringWeights().put(DIM_PRODUCT_DEPTH, 0.30);
        contract.getScoringWeights().put(DIM_FUNCTIONALITY, 0.20);
        contract.getScoringWeights().put(DIM_VISUAL_DESIGN, 0.35);
        contract.getScoringWeights().put(DIM_CODE_QUALITY, 0.15);
        contract.getThresholds().put(DIM_PRODUCT_DEPTH, 5.0);
        contract.getThresholds().put(DIM_FUNCTIONALITY, 5.0);
        contract.getThresholds().put(DIM_VISUAL_DESIGN, 5.0);
        contract.getThresholds().put(DIM_CODE_QUALITY, 5.0);
        return contract;
    }

    /**
     * 创建后端任务的默认合同（功能性权重最高）。
     */
    public static SprintContract createBackendContract(String feature, String taskId) {
        SprintContract contract = new SprintContract();
        contract.setTaskId(taskId);
        contract.setFeature(feature);
        contract.getScoringWeights().put(DIM_PRODUCT_DEPTH, 0.25);
        contract.getScoringWeights().put(DIM_FUNCTIONALITY, 0.35);
        contract.getScoringWeights().put(DIM_VISUAL_DESIGN, 0.10);
        contract.getScoringWeights().put(DIM_CODE_QUALITY, 0.30);
        contract.getThresholds().put(DIM_PRODUCT_DEPTH, 5.0);
        contract.getThresholds().put(DIM_FUNCTIONALITY, 6.0);
        contract.getThresholds().put(DIM_VISUAL_DESIGN, 4.0);
        contract.getThresholds().put(DIM_CODE_QUALITY, 5.0);
        return contract;
    }

    /**
     * 创建全栈任务的默认合同。
     */
    public static SprintContract createFullstackContract(String feature, String taskId) {
        SprintContract contract = new SprintContract();
        contract.setTaskId(taskId);
        contract.setFeature(feature);
        contract.getScoringWeights().put(DIM_PRODUCT_DEPTH, 0.25);
        contract.getScoringWeights().put(DIM_FUNCTIONALITY, 0.30);
        contract.getScoringWeights().put(DIM_VISUAL_DESIGN, 0.20);
        contract.getScoringWeights().put(DIM_CODE_QUALITY, 0.25);
        contract.getThresholds().put(DIM_PRODUCT_DEPTH, 5.0);
        contract.getThresholds().put(DIM_FUNCTIONALITY, 5.0);
        contract.getThresholds().put(DIM_VISUAL_DESIGN, 5.0);
        contract.getThresholds().put(DIM_CODE_QUALITY, 5.0);
        return contract;
    }

    // ==================== 合同操作 ====================

    /**
     * 由 Generator 签署合同。
     *
     * <p>已签署状态（SIGNED）也允许调用此方法，避免因双方签署顺序
     * 导致的 IllegalStateException（Evaluator 先签署→status 变为 SIGNED→
     * Generator 再签署时检查 NEGOTIATING 失败）。
     * 这是 SprintContract 状态机的关键修复，确保不产生竞态条件。</p>
     *
     * @return true 如果双方都已签署，合同状态转为 SIGNED
     */
    public boolean signByGenerator() {
        if (status == ContractStatus.SIGNED && signedByEvaluator && !signedByGenerator) {
            this.signedByGenerator = true;
            return true;
        }
        if (status != ContractStatus.NEGOTIATING) {
            throw new IllegalStateException("合同必须在 NEGOTIATING 状态才能签署，当前状态: " + status);
        }
        this.signedByGenerator = true;
        return tryFinalizeSigning();
    }

    /**
     * 由 Evaluator 签署合同。
     *
     * <p>已签署状态（SIGNED）也允许调用此方法，避免因双方签署顺序
     * 导致的 IllegalStateException（Generator 先签署→status 变为 SIGNED→
     * Evaluator 再签署时检查 NEGOTIATING 失败）。
     * 这是 SprintContract 状态机的关键修复，确保不产生竞态条件。</p>
     *
     * @return true 如果双方都已签署，合同状态转为 SIGNED
     */
    public boolean signByEvaluator() {
        if (status == ContractStatus.SIGNED && signedByGenerator && !signedByEvaluator) {
            // 已由 Generator 先签署完成状态迁移：直接记录 Evaluator 签名
            this.signedByEvaluator = true;
            return true;
        }
        if (status != ContractStatus.NEGOTIATING) {
            throw new IllegalStateException("合同必须在 NEGOTIATING 状态才能签署，当前状态: " + status);
        }
        this.signedByEvaluator = true;
        return tryFinalizeSigning();
    }

    /**
     * 尝试最终签署 — 当双方都签署时，状态转为 SIGNED。
     */
    private boolean tryFinalizeSigning() {
        if (signedByGenerator && signedByEvaluator) {
            this.status = ContractStatus.SIGNED;
            this.signedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * 开始执行合同（状态从 SIGNED → EXECUTING）。
     */
    public void startExecution() {
        if (status != ContractStatus.SIGNED) {
            throw new IllegalStateException("合同必须在 SIGNED 状态才能开始执行，当前状态: " + status);
        }
        this.status = ContractStatus.EXECUTING;
    }

    /**
     * 完成合同（所有验收标准通过）。
     */
    public void complete() {
        if (status != ContractStatus.EXECUTING) {
            throw new IllegalStateException("合同必须在 EXECUTING 状态才能完成，当前状态: " + status);
        }
        this.status = ContractStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * 标记合同失败。
     */
    public void fail() {
        if (status != ContractStatus.EXECUTING) {
            throw new IllegalStateException("合同必须在 EXECUTING 状态才能标记失败，当前状态: " + status);
        }
        this.status = ContractStatus.FAILED;
        this.completedAt = Instant.now();
    }

    /**
     * 进入谈判状态。
     */
    public void startNegotiation() {
        if (status != ContractStatus.DRAFT) {
            throw new IllegalStateException("合同必须在 DRAFT 状态才能开始谈判，当前状态: " + status);
        }
        this.status = ContractStatus.NEGOTIATING;
    }

    /**
     * 增加一条验收标准。
     */
    public void addAcceptanceCriterion(String criterion) {
        this.acceptanceCriteria.add(criterion);
    }

    /**
     * 增加迭代轮数计数。
     *
     * @return true 如果还可以继续迭代，false 表示已达最大轮数
     */
    public boolean incrementIteration() {
        this.currentIteration++;
        return this.currentIteration < this.maxIterations;
    }

    /**
     * 检查是否还有剩余迭代次数。
     */
    public boolean hasRemainingIterations() {
        return this.currentIteration < this.maxIterations;
    }

    /**
     * 获取加权总分。
     *
     * @param dimensionScores 各维度得分
     * @return 加权总分
     */
    public double calculateWeightedScore(Map<String, Double> dimensionScores) {
        double totalScore = 0.0;
        double totalWeight = 0.0;

        for (Map.Entry<String, Double> entry : scoringWeights.entrySet()) {
            String dim = entry.getKey();
            double weight = entry.getValue();
            Double score = dimensionScores.get(dim);
            if (score != null) {
                totalScore += score * weight;
                totalWeight += weight;
            }
        }

        return totalWeight > 0 ? totalScore / totalWeight : 0.0;
    }

    /**
     * 检查各维度是否都通过硬门槛。
     *
     * @param dimensionScores 各维度得分
     * @return 未通过的门槛列表（空列表表示全部通过）
     */
    public List<String> checkThresholds(Map<String, Double> dimensionScores) {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Double> entry : thresholds.entrySet()) {
            String dim = entry.getKey();
            double threshold = entry.getValue();
            Double score = dimensionScores.get(dim);
            if (score == null || score < threshold) {
                failures.add(dim + " (得分=" + score + ", 门槛=" + threshold + ")");
            }
        }
        return failures;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String contractId;
        private String taskId;
        private String feature;
        private List<String> acceptanceCriteria = new ArrayList<>();
        private Map<String, Double> scoringWeights = new HashMap<>();
        private Map<String, Double> thresholds = new HashMap<>();
        private int maxIterations = 3;
        private ContractStatus status = ContractStatus.DRAFT;

        public Builder contractId(String contractId) { this.contractId = contractId; return this; }
        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder feature(String feature) { this.feature = feature; return this; }
        public Builder acceptanceCriteria(List<String> criteria) { this.acceptanceCriteria = criteria; return this; }
        public Builder addCriterion(String criterion) { this.acceptanceCriteria.add(criterion); return this; }
        public Builder scoringWeights(Map<String, Double> weights) { this.scoringWeights = weights; return this; }
        public Builder putWeight(String dimension, double weight) { this.scoringWeights.put(dimension, weight); return this; }
        public Builder thresholds(Map<String, Double> thresholds) { this.thresholds = thresholds; return this; }
        public Builder putThreshold(String dimension, double threshold) { this.thresholds.put(dimension, threshold); return this; }
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder status(ContractStatus status) { this.status = status; return this; }

        public SprintContract build() {
            SprintContract contract = new SprintContract();
            contract.contractId = this.contractId != null ? this.contractId : UUID.randomUUID().toString().substring(0, 12);
            contract.taskId = this.taskId;
            contract.feature = this.feature;
            contract.acceptanceCriteria = this.acceptanceCriteria;
            contract.scoringWeights = this.scoringWeights;
            contract.thresholds = this.thresholds;
            contract.maxIterations = this.maxIterations;
            contract.status = this.status;
            return contract;
        }
    }

    // ==================== Getters & Setters ====================

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }

    public List<String> getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(List<String> acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }

    public Map<String, Double> getScoringWeights() { return scoringWeights; }
    public void setScoringWeights(Map<String, Double> scoringWeights) { this.scoringWeights = scoringWeights; }

    public Map<String, Double> getThresholds() { return thresholds; }
    public void setThresholds(Map<String, Double> thresholds) { this.thresholds = thresholds; }

    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

    public int getCurrentIteration() { return currentIteration; }
    public void setCurrentIteration(int currentIteration) { this.currentIteration = currentIteration; }

    public ContractStatus getStatus() { return status; }
    public void setStatus(ContractStatus status) { this.status = status; }

    public boolean isSignedByGenerator() { return signedByGenerator; }
    public void setSignedByGenerator(boolean signedByGenerator) { this.signedByGenerator = signedByGenerator; }

    public boolean isSignedByEvaluator() { return signedByEvaluator; }
    public void setSignedByEvaluator(boolean signedByEvaluator) { this.signedByEvaluator = signedByEvaluator; }

    public boolean isFullySigned() { return signedByGenerator && signedByEvaluator; }

    public String getHandoffArtifactPath() { return handoffArtifactPath; }
    public void setHandoffArtifactPath(String handoffArtifactPath) { this.handoffArtifactPath = handoffArtifactPath; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getSignedAt() { return signedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
