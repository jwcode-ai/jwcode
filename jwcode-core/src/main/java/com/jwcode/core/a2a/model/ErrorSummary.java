package com.jwcode.core.a2a.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ErrorSummary — 结构化错误摘要。
 *
 * <p>跨Agent边界的错误信息载体，遵循"三层摘要机制"：
 * <ul>
 *   <li>Tool Agent层：错误类型 + 修复尝试次数 + 最终失败原因(1句话)</li>
 *   <li>专业Agent层：步骤失败摘要 + 建议的替代方案</li>
 *   <li>主Agent层：任务状态(Failed) + 业务级失败原因 + 是否需要人工介入</li>
 * </ul>
 * </p>
 *
 * <p>设计原则：不传递原始堆栈、不传递工具内部状态、不传递敏感信息。</p>
 */
public class ErrorSummary {

    /** 错误类型（如 PERMISSION_DENIED, COMPILATION_ERROR, FILE_NOT_FOUND） */
    private final String errorType;

    /** 一句话描述（面向业务，不包含技术细节） */
    private final String message;

    /** 是否可重试 */
    private final boolean retryable;

    /** 已重试次数 */
    private final int retryCount;

    /** 最大重试次数 */
    private final int maxRetries;

    /** 恢复建议（面向专业Agent，如"换用B方案"、"跳过该步骤"） */
    private final String recoveryHint;

    /** 来源层级: TOOL_AGENT / DOMAIN_AGENT / ORCHESTRATOR */
    private final String sourceLayer;

    /** 是否关键路径失败（关键路径失败会导致整个任务终止） */
    private final boolean criticalPath;

    /** 是否需要人工介入 */
    private final boolean requiresHumanIntervention;

    /** 内部日志（仅用于调试，不向上传递） */
    private final List<String> internalLog;

    private ErrorSummary(String errorType, String message, boolean retryable,
                         int retryCount, int maxRetries, String recoveryHint,
                         String sourceLayer, boolean criticalPath,
                         boolean requiresHumanIntervention,
                         List<String> internalLog) {
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.retryable = retryable;
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
        this.recoveryHint = recoveryHint;
        this.sourceLayer = Objects.requireNonNull(sourceLayer, "sourceLayer must not be null");
        this.criticalPath = criticalPath;
        this.requiresHumanIntervention = requiresHumanIntervention;
        this.internalLog = internalLog != null
            ? Collections.unmodifiableList(internalLog)
            : Collections.emptyList();
    }

    // ==================== Getters ====================

    public String getErrorType() { return errorType; }
    public String getMessage() { return message; }
    public boolean isRetryable() { return retryable; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public String getRecoveryHint() { return recoveryHint; }
    public String getSourceLayer() { return sourceLayer; }
    public boolean isCriticalPath() { return criticalPath; }
    public boolean isRequiresHumanIntervention() { return requiresHumanIntervention; }
    public List<String> getInternalLog() { return internalLog; }

    /**
     * 获取面向主Agent的业务级摘要（仅一句话）
     */
    public String toBusinessSummary() {
        return "[" + errorType + "] " + message;
    }

    @Override
    public String toString() {
        return String.format("ErrorSummary{type=%s, msg='%s', retryable=%s, retry=%d/%d, layer=%s}",
            errorType, message, retryable, retryCount, maxRetries, sourceLayer);
    }

    // ==================== 工厂方法 ====================

    /**
     * Tool Agent 层失败摘要。
     * 不传递原始命令、堆栈跟踪、工具详情。
     */
    public static ErrorSummary toolAgentFailure(String message, boolean retryable,
                                                 int retryCount, int maxRetries) {
        return new ErrorSummary(
            "TOOL_AGENT_ERROR", message, retryable,
            retryCount, maxRetries, null,
            "TOOL_AGENT", false, false, null
        );
    }

    /**
     * Tool Agent 层失败摘要（带恢复建议）。
     */
    public static ErrorSummary toolAgentFailure(String message, boolean retryable,
                                                 int retryCount, int maxRetries,
                                                 String recoveryHint) {
        return new ErrorSummary(
            "TOOL_AGENT_ERROR", message, retryable,
            retryCount, maxRetries, recoveryHint,
            "TOOL_AGENT", false, false, null
        );
    }

    /**
     * 专业Agent层失败摘要。
     * 包含步骤替代建议，不包含单步重试过程。
     */
    public static ErrorSummary domainAgentFailure(String message, String recoveryHint) {
        return new ErrorSummary(
            "DOMAIN_AGENT_ERROR", message, false,
            0, 0, recoveryHint,
            "DOMAIN_AGENT", false, false, null
        );
    }

    /**
     * 关键路径失败摘要（会导致整个任务终止）。
     */
    public static ErrorSummary criticalFailure(String message, boolean requiresHuman) {
        return new ErrorSummary(
            "CRITICAL_ERROR", message, false,
            0, 0, null,
            "ORCHESTRATOR", true, requiresHuman, null
        );
    }

    /**
     * 带内部日志的完整构造（内部日志不向上传递）。
     */
    public static ErrorSummary withInternalLog(String errorType, String message,
                                                boolean retryable, int retryCount,
                                                int maxRetries, String recoveryHint,
                                                String sourceLayer,
                                                List<String> internalLog) {
        return new ErrorSummary(
            errorType, message, retryable,
            retryCount, maxRetries, recoveryHint,
            sourceLayer, false, false, internalLog
        );
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String errorType;
        private String message;
        private boolean retryable;
        private int retryCount;
        private int maxRetries;
        private String recoveryHint;
        private String sourceLayer = "TOOL_AGENT";
        private boolean criticalPath;
        private boolean requiresHumanIntervention;
        private List<String> internalLog;

        public Builder errorType(String errorType) { this.errorType = errorType; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder retryable(boolean retryable) { this.retryable = retryable; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder recoveryHint(String recoveryHint) { this.recoveryHint = recoveryHint; return this; }
        public Builder sourceLayer(String sourceLayer) { this.sourceLayer = sourceLayer; return this; }
        public Builder criticalPath(boolean criticalPath) { this.criticalPath = criticalPath; return this; }
        public Builder requiresHumanIntervention(boolean requiresHumanIntervention) { this.requiresHumanIntervention = requiresHumanIntervention; return this; }
        public Builder internalLog(List<String> internalLog) { this.internalLog = internalLog; return this; }

        public ErrorSummary build() {
            return new ErrorSummary(errorType, message, retryable, retryCount,
                maxRetries, recoveryHint, sourceLayer, criticalPath,
                requiresHumanIntervention, internalLog);
        }
    }
}
