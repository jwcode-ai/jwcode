package com.jwcode.core.query;

import com.jwcode.core.model.Message;
import com.jwcode.core.tool.ToolExecutionResult;

import java.util.List;
import java.util.Objects;

/**
 * QueryResult - 查询结果
 *
 * 功能说明：
 * 封装查询执行的结果，包括成功、失败和工具执行等情况。
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class QueryResult {

    private final Status status;
    private final Message message;
    private final List<ToolExecutionResult> toolResults;
    private final String errorMessage;

    public enum Status { SUCCESS, ERROR, TOOL_EXECUTION }

    private QueryResult(Status status, Message message, List<ToolExecutionResult> toolResults, String errorMessage) {
        this.status = status;
        this.message = message;
        this.toolResults = toolResults;
        this.errorMessage = errorMessage;
    }

    public Status getStatus() { return status; }
    public Message getMessage() { return message; }
    public List<ToolExecutionResult> getToolResults() { return toolResults; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }

    public static QueryResult success(Message message) {
        return new QueryResult(Status.SUCCESS, Objects.requireNonNull(message), null, null);
    }

    public static QueryResult error(String message) {
        return new QueryResult(Status.ERROR, null, null, Objects.requireNonNull(message));
    }

    public static QueryResult toolExecution(List<ToolExecutionResult> results) {
        return new QueryResult(Status.TOOL_EXECUTION, null, results, null);
    }
}