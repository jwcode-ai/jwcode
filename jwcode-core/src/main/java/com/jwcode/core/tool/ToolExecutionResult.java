package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * ToolExecutionResult - 工具执行结果
 * 
 * 功能说明：
 * 封装工具执行的结果，包括成功和失败情况。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ToolExecutionResult {
    
    private final boolean success;
    private final String toolName;
    private final ToolResult<?> result;
    private final String errorMessage;
    
    private ToolExecutionResult(boolean success, String toolName, ToolResult<?> result, String errorMessage) {
        this.success = success;
        this.toolName = toolName;
        this.result = result;
        this.errorMessage = errorMessage;
    }
    
    public boolean isSuccess() { return success; }
    public String getToolName() { return toolName; }
    public ToolResult<?> getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    
    public static ToolExecutionResult success(String toolName, ToolResult<?> result) {
        return new ToolExecutionResult(true, toolName, Objects.requireNonNull(result), null);
    }
    
    public static ToolExecutionResult error(String errorMessage) {
        return new ToolExecutionResult(false, null, null, Objects.requireNonNull(errorMessage));
    }
}