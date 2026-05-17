package com.jwcode.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HandoffArtifact — 交接文档模型（Context Reset 协议核心）。
 *
 * <p>当 Agent 上下文接近上限时，不压缩而是"重启进程+结构化交接"。
 * HandoffArtifact 是旧 Agent 交给新 Agent 的"接力棒"，
 * 包含完成任务所需的所有结构化状态。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li><b>自包含</b>：新 Agent 仅凭此文档即可恢复工作状态，无需访问旧上下文</li>
 *   <li><b>结构化</b>：所有字段均为结构化数据，便于程序化处理</li>
 *   <li><b>可序列化</b>：支持 JSON 序列化，写入 {@code .jwcode/handoff/} 目录</li>
 * </ul>
 */
public class HandoffArtifact {

    /** 交接文档唯一标识 */
    private String artifactId;

    /** 会话 ID */
    private String sessionId;

    /** 任务 ID */
    private String taskId;

    /** 关联的 SprintContract ID */
    private String contractId;

    /** 当前阶段 */
    private String phase;

    /** 已完成工作摘要 */
    private String completedWork;

    /** 当前状态描述（代码状态、配置等） */
    private String currentState;

    /** 待办事项 */
    private List<String> pendingItems;

    /** 关键决策记录 */
    private List<String> decisions;

    /** 代码变更摘要 */
    private String codeState;

    /** 活跃问题 */
    private List<String> activeIssues;

    /** 环境快照 */
    private Map<String, Object> environment;

    /** 下一步行动 */
    private String nextActions;

    /** 创建时间 */
    private Instant createdAt;

    /** 来源 Agent 类型 */
    private String sourceAgentType;

    /** 目标 Agent 类型 */
    private String targetAgentType;

    /** 扩展属性 */
    private Map<String, Object> metadata;

    // ==================== 触发原因枚举 ====================

    public enum ResetReason {
        /** Token 使用率超过阈值 */
        TOKEN_THRESHOLD_REACHED,
        /** 迭代循环超过轮数 */
        ITERATION_LIMIT_REACHED,
        /** 任务阶段切换 */
        PHASE_TRANSITION,
        /** Agent 主动请求 */
        AGENT_REQUESTED,
        /** 用户手动触发 */
        USER_TRIGGERED,
        /** 系统维护 */
        SYSTEM_MAINTENANCE
    }

    // ==================== 构造器 ====================

    public HandoffArtifact() {
        this.artifactId = UUID.randomUUID().toString().substring(0, 12);
        this.pendingItems = new ArrayList<>();
        this.decisions = new ArrayList<>();
        this.activeIssues = new ArrayList<>();
        this.environment = new HashMap<>();
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
    }

    // ==================== 工厂方法 ====================

    /**
     * 从当前会话状态创建交接文档。
     */
    public static HandoffArtifact create(String sessionId, String taskId, String phase,
                                          String completedWork, String currentState,
                                          String nextActions) {
        HandoffArtifact artifact = new HandoffArtifact();
        artifact.sessionId = sessionId;
        artifact.taskId = taskId;
        artifact.phase = phase;
        artifact.completedWork = completedWork;
        artifact.currentState = currentState;
        artifact.nextActions = nextActions;
        return artifact;
    }

    /**
     * 从 SprintContract 创建交接文档。
     */
    public static HandoffArtifact fromContract(SprintContract contract, String sessionId) {
        HandoffArtifact artifact = new HandoffArtifact();
        artifact.sessionId = sessionId;
        artifact.taskId = contract.getTaskId();
        artifact.contractId = contract.getContractId();
        artifact.phase = contract.getStatus().name();
        artifact.completedWork = "已完成 " + contract.getCurrentIteration()
            + "/" + contract.getMaxIterations() + " 轮迭代";
        artifact.currentState = "合同状态: " + contract.getStatus().name()
            + ", 功能: " + contract.getFeature();
        artifact.nextActions = "继续执行 Sprint 合同，当前迭代 #" + (contract.getCurrentIteration() + 1);
        return artifact;
    }

    // ==================== 构建方法 ====================

    /**
     * 添加一条待办事项。
     */
    public void addPendingItem(String item) {
        this.pendingItems.add(item);
    }

    /**
     * 添加一条决策记录。
     */
    public void addDecision(String decision) {
        this.decisions.add(decision);
    }

    /**
     * 添加一条活跃问题。
     */
    public void addActiveIssue(String issue) {
        this.activeIssues.add(issue);
    }

    /**
     * 设置环境变量。
     */
    public void setEnvironmentValue(String key, Object value) {
        this.environment.put(key, value);
    }

    // ==================== 序列化辅助 ====================

    /**
     * 生成交接文档的摘要（供新 Agent 快速了解上下文）。
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Handoff Artifact: ").append(artifactId).append("\n\n");
        sb.append("**任务**: ").append(taskId).append("\n");
        sb.append("**阶段**: ").append(phase).append("\n");
        sb.append("**来源**: ").append(sourceAgentType).append(" → ").append(targetAgentType).append("\n\n");

        sb.append("### 已完成工作\n").append(completedWork).append("\n\n");

        if (!pendingItems.isEmpty()) {
            sb.append("### 待办事项\n");
            for (String item : pendingItems) {
                sb.append("- ").append(item).append("\n");
            }
            sb.append("\n");
        }

        if (!decisions.isEmpty()) {
            sb.append("### 关键决策\n");
            for (String d : decisions) {
                sb.append("- ").append(d).append("\n");
            }
            sb.append("\n");
        }

        if (!activeIssues.isEmpty()) {
            sb.append("### 活跃问题\n");
            for (String issue : activeIssues) {
                sb.append("- ").append(issue).append("\n");
            }
            sb.append("\n");
        }

        sb.append("### 下一步行动\n").append(nextActions).append("\n");
        return sb.toString();
    }

    // ==================== Getters & Setters ====================

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getCompletedWork() { return completedWork; }
    public void setCompletedWork(String completedWork) { this.completedWork = completedWork; }

    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }

    public List<String> getPendingItems() { return pendingItems; }
    public void setPendingItems(List<String> pendingItems) { this.pendingItems = pendingItems; }

    public List<String> getDecisions() { return decisions; }
    public void setDecisions(List<String> decisions) { this.decisions = decisions; }

    public String getCodeState() { return codeState; }
    public void setCodeState(String codeState) { this.codeState = codeState; }

    public List<String> getActiveIssues() { return activeIssues; }
    public void setActiveIssues(List<String> activeIssues) { this.activeIssues = activeIssues; }

    public Map<String, Object> getEnvironment() { return environment; }
    public void setEnvironment(Map<String, Object> environment) { this.environment = environment; }

    public String getNextActions() { return nextActions; }
    public void setNextActions(String nextActions) { this.nextActions = nextActions; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getSourceAgentType() { return sourceAgentType; }
    public void setSourceAgentType(String sourceAgentType) { this.sourceAgentType = sourceAgentType; }

    public String getTargetAgentType() { return targetAgentType; }
    public void setTargetAgentType(String targetAgentType) { this.targetAgentType = targetAgentType; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
