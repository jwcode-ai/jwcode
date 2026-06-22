package com.jwcode.core.tool;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured, bounded error summary shared by tool and workflow execution code.
 */
public class ErrorSummary {

    private final String errorType;
    private final String message;
    private final boolean retryable;
    private final int retryCount;
    private final int maxRetries;
    private final String recoveryHint;
    private final String sourceLayer;
    private final boolean criticalPath;
    private final boolean requiresHumanIntervention;
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

    public String toBusinessSummary() {
        return "[" + errorType + "] " + message;
    }

    @Override
    public String toString() {
        return String.format("ErrorSummary{type=%s, msg='%s', retryable=%s, retry=%d/%d, layer=%s}",
            errorType, message, retryable, retryCount, maxRetries, sourceLayer);
    }

    public static ErrorSummary toolAgentFailure(String message, boolean retryable,
                                                 int retryCount, int maxRetries) {
        return new ErrorSummary(
            "TOOL_AGENT_ERROR", message, retryable,
            retryCount, maxRetries, null,
            "TOOL_AGENT", false, false, null
        );
    }

    public static ErrorSummary toolAgentFailure(String message, boolean retryable,
                                                 int retryCount, int maxRetries,
                                                 String recoveryHint) {
        return new ErrorSummary(
            "TOOL_AGENT_ERROR", message, retryable,
            retryCount, maxRetries, recoveryHint,
            "TOOL_AGENT", false, false, null
        );
    }

    public static ErrorSummary domainAgentFailure(String message, String recoveryHint) {
        return new ErrorSummary(
            "DOMAIN_AGENT_ERROR", message, false,
            0, 0, recoveryHint,
            "DOMAIN_AGENT", false, false, null
        );
    }

    public static ErrorSummary criticalFailure(String message, boolean requiresHuman) {
        return new ErrorSummary(
            "CRITICAL_ERROR", message, false,
            0, 0, null,
            "ORCHESTRATOR", true, requiresHuman, null
        );
    }

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
