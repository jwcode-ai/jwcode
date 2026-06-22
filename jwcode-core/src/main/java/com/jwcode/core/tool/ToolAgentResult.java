package com.jwcode.core.tool;

import java.util.Objects;

/**
 * ToolAgentResult — Tool Agent 执行结果。
 *
 * <p>封装工具执行的结构化结果，包含：
 * <ul>
 *   <li>执行状态（成功/失败）</li>
 *   <li>执行结果数据</li>
 *   <li>失败时的结构化错误摘要（不包含原始命令、堆栈跟踪）</li>
 *   <li>自修复尝试次数</li>
 * </ul>
 * </p>
 */
public class ToolAgentResult {

    /** 执行状态 */
    public enum Status {
        SUCCESS,
        FAILED,
        PARTIAL
    }

    private final Status status;
    private final String toolName;
    private final Object result;
    private final ErrorSummary errorSummary;
    private final int selfHealAttempts;
    private final long executionTimeMs;

    private ToolAgentResult(Status status, String toolName, Object result,
                            ErrorSummary errorSummary, int selfHealAttempts,
                            long executionTimeMs) {
        this.status = Objects.requireNonNull(status);
        this.toolName = Objects.requireNonNull(toolName);
        this.result = result;
        this.errorSummary = errorSummary;
        this.selfHealAttempts = selfHealAttempts;
        this.executionTimeMs = executionTimeMs;
    }

    // ==================== Getters ====================

    public Status getStatus() { return status; }
    public String getToolName() { return toolName; }
    public Object getResult() { return result; }
    public ErrorSummary getErrorSummary() { return errorSummary; }
    public int getSelfHealAttempts() { return selfHealAttempts; }
    public long getExecutionTimeMs() { return executionTimeMs; }

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isFailed() { return status == Status.FAILED; }

    /**
     * 获取面向专业Agent的摘要（仅一句话）。
     */
    public String toAgentSummary() {
        if (isSuccess()) {
            return "[" + toolName + "] 执行成功";
        }
        return errorSummary != null
            ? errorSummary.toBusinessSummary()
            : "[" + toolName + "] 执行失败";
    }

    @Override
    public String toString() {
        return String.format("ToolAgentResult{status=%s, tool=%s, attempts=%d, time=%dms}",
            status, toolName, selfHealAttempts, executionTimeMs);
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建成功结果。
     */
    public static ToolAgentResult success(String toolName, Object result) {
        return new ToolAgentResult(Status.SUCCESS, toolName, result, null, 0, 0);
    }

    /**
     * 创建成功结果（带执行时间）。
     */
    public static ToolAgentResult success(String toolName, Object result, long executionTimeMs) {
        return new ToolAgentResult(Status.SUCCESS, toolName, result, null, 0, executionTimeMs);
    }

    /**
     * 创建失败结果（带自修复尝试次数）。
     */
    public static ToolAgentResult failed(String toolName, ErrorSummary errorSummary,
                                          int selfHealAttempts, long executionTimeMs) {
        return new ToolAgentResult(Status.FAILED, toolName, null,
            errorSummary, selfHealAttempts, executionTimeMs);
    }

    /**
     * 创建部分成功结果。
     */
    public static ToolAgentResult partial(String toolName, Object result,
                                           ErrorSummary errorSummary) {
        return new ToolAgentResult(Status.PARTIAL, toolName, result,
            errorSummary, 0, 0);
    }
}
