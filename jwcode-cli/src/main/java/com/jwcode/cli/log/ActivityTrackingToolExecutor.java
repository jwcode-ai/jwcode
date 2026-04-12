package com.jwcode.cli.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 带活动追踪的工具执行器
 * 
 * 包装 ToolExecutor，在执行工具时自动记录活动日志，
 * 让用户能看到 AI 正在做什么。
 */
public class ActivityTrackingToolExecutor {
    
    private static final Logger logger = Logger.getLogger(ActivityTrackingToolExecutor.class.getName());
    
    private final ToolExecutor delegate;
    private final ActivityLogger activityLogger;
    private final Map<String, String> toolToActivityMap = new ConcurrentHashMap<>();
    
    public ActivityTrackingToolExecutor() {
        this(new ToolExecutor(), ActivityLogger.getInstance());
    }
    
    public ActivityTrackingToolExecutor(ToolExecutor delegate) {
        this(delegate, ActivityLogger.getInstance());
    }
    
    public ActivityTrackingToolExecutor(ToolExecutor delegate, ActivityLogger activityLogger) {
        this.delegate = delegate;
        this.activityLogger = activityLogger;
    }
    
    // ============ 核心执行方法 ============
    
    /**
     * 执行工具调用（带活动追踪）
     */
    public CompletableFuture<ToolExecutionResult> execute(
            String toolName,
            JsonNode inputJson,
            ToolExecutionContext context) {
        
        return execute(toolName, inputJson, context, null);
    }
    
    /**
     * 执行工具调用（带活动追踪和进度回调）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public CompletableFuture<ToolExecutionResult> execute(
            String toolName,
            JsonNode inputJson,
            ToolExecutionContext context,
            Consumer<ToolProgress<?>> onProgress) {
        
        // 1. 记录活动开始
        String activityId = startToolActivity(toolName, inputJson);
        
        // 2. 包装进度回调以更新活动
        Consumer<ToolProgress<?>> wrappedProgress = progress -> {
            updateToolProgress(activityId, toolName, progress);
            if (onProgress != null) {
                onProgress.accept(progress);
            }
        };
        
        // 3. 执行工具 - 使用内部返回类型
        CompletableFuture future = delegate.execute(toolName, inputJson, context, wrappedProgress);
        
        // 4. 处理结果
        return future.whenComplete((result, throwable) -> {
            if (throwable instanceof Throwable t) {
                failToolActivity(activityId, toolName, t.getMessage());
            } else if (result instanceof ToolExecutionResult toolResult) {
                if (toolResult.isSuccess()) {
                    completeToolActivity(activityId, toolName, toolResult);
                } else {
                    failToolActivity(activityId, toolName, toolResult.getErrorMessage());
                }
            } else {
                completeToolActivity(activityId, toolName, null);
            }
        });
    }
    
    /**
     * 批量执行工具（带活动追踪）
     */
    public List<CompletableFuture<ToolExecutionResult>> executeBatch(
            List<ToolCallRequest> requests,
            ToolExecutionContext context) {
        
        // 记录批量任务开始
        String batchActivityId = activityLogger.startActivity(
            ActivityType.PROGRESS_START,
            "批量执行 " + requests.size() + " 个工具"
        );
        
        List<CompletableFuture<ToolExecutionResult>> futures = new java.util.ArrayList<>();
        int total = requests.size();
        
        for (int i = 0; i < requests.size(); i++) {
            ToolCallRequest request = requests.get(i);
            final int index = i + 1;
            
            // 更新批量进度
            activityLogger.updateProgress(batchActivityId, (int)((double) index / total * 100),
                "执行中: " + request.toolName());
            
            CompletableFuture<ToolExecutionResult> future = execute(
                request.toolName(),
                request.input(),
                context,
                request.onProgress()
            );
            
            futures.add(future);
        }
        
        // 等待所有完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .whenComplete((v, t) -> {
                long successCount = futures.stream()
                    .filter(f -> {
                        try {
                            return f.get().isSuccess();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .count();
                
                activityLogger.completeActivity(batchActivityId,
                    "完成: " + successCount + "/" + total + " 成功");
            });
        
        return futures;
    }
    
    // ============ 活动记录方法 ============
    
    private String startToolActivity(String toolName, JsonNode inputJson) {
        ActivityType type = ActivityType.fromToolName(toolName);
        String description = buildToolDescription(toolName, inputJson);
        
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("toolName", toolName);
        if (inputJson != null) {
            metadata.put("input", inputJson.toString());
        }
        
        String activityId = activityLogger.startActivity(type, description, metadata);
        toolToActivityMap.put(toolName + "-" + System.currentTimeMillis(), activityId);
        
        return activityId;
    }
    
    private void updateToolProgress(String activityId, String toolName, ToolProgress<?> progress) {
        if (progress == null) return;
        
        int percent = progress.getPercentComplete();
        String message = progress.getMessage();
        
        // 如果提供了百分比，更新进度
        if (percent >= 0) {
            activityLogger.updateProgress(activityId, percent, message);
        }
        
        // 添加详细元数据
        Object data = progress.getData();
        if (data != null) {
            activityLogger.addActivityMetadata(activityId, "progressData", data.toString());
        }
    }
    
    private void completeToolActivity(String activityId, String toolName, ToolExecutionResult result) {
        String summary = buildResultSummary(toolName, result);
        activityLogger.completeActivity(activityId, summary);
    }
    
    private void failToolActivity(String activityId, String toolName, String error) {
        activityLogger.failActivity(activityId, error);
    }
    
    // ============ 描述构建方法 ============
    
    private String buildToolDescription(String toolName, JsonNode inputJson) {
        StringBuilder sb = new StringBuilder();
        
        switch (toolName) {
            case "FileReadTool" -> {
                String path = getStringValue(inputJson, "file_path", "path");
                sb.append("读取文件: ").append(path != null ? path : "未知文件");
                
                Integer startLine = getIntValue(inputJson, "start_line", "startLine");
                Integer endLine = getIntValue(inputJson, "end_line", "endLine");
                if (startLine != null || endLine != null) {
                    sb.append(" [行 ").append(startLine != null ? startLine : 1)
                      .append("-").append(endLine != null ? endLine : "end").append("]");
                }
            }
            case "FileWriteTool" -> {
                String path = getStringValue(inputJson, "file_path", "path");
                sb.append("写入文件: ").append(path != null ? path : "未知文件");
            }
            case "EditTool", "FileEditTool", "StrReplaceFileTool" -> {
                String path = getStringValue(inputJson, "file_path", "path");
                sb.append("编辑文件: ").append(path != null ? path : "未知文件");
            }
            case "GlobTool" -> {
                String pattern = getStringValue(inputJson, "pattern");
                sb.append("搜索文件: ").append(pattern != null ? pattern : "*");
            }
            case "GrepTool" -> {
                String pattern = getStringValue(inputJson, "pattern");
                String path = getStringValue(inputJson, "path");
                sb.append("搜索代码: ").append(pattern != null ? pattern : "*");
                if (path != null) {
                    sb.append(" 在 ").append(path);
                }
            }
            case "BashTool", "ShellTool" -> {
                String command = getStringValue(inputJson, "command");
                sb.append("执行: ").append(command != null ? truncate(command, 50) : "未知命令");
            }
            case "PowerShellTool" -> {
                String command = getStringValue(inputJson, "command");
                sb.append("执行 PowerShell: ").append(command != null ? truncate(command, 50) : "未知命令");
            }
            case "WebSearchTool" -> {
                String query = getStringValue(inputJson, "query");
                sb.append("搜索: ").append(query != null ? truncate(query, 50) : "");
            }
            case "WebFetchTool" -> {
                String url = getStringValue(inputJson, "url");
                sb.append("获取网页: ").append(url != null ? truncate(url, 50) : "");
            }
            default -> sb.append(toolName);
        }
        
        return sb.toString();
    }
    
    private String buildResultSummary(String toolName, ToolExecutionResult result) {
        if (result == null || !result.isSuccess()) {
            return "失败";
        }
        
        ToolResult<?> toolResult = result.getResult();
        if (toolResult == null) {
            return "完成";
        }
        
        Object data = toolResult.getData();
        if (data == null) {
            return "成功";
        }
        
        // 根据工具类型生成摘要
        return switch (toolName) {
            case "FileReadTool" -> "读取完成";
            case "FileWriteTool" -> "文件已写入";
            case "GlobTool" -> "搜索完成";
            case "GrepTool" -> "搜索完成";
            case "BashTool", "ShellTool", "PowerShellTool" -> "命令执行完成";
            case "WebSearchTool" -> "搜索完成";
            default -> "成功";
        };
    }
    
    // ============ JSON 辅助方法 ============
    
    private String getStringValue(JsonNode node, String... fieldNames) {
        if (node == null) return null;
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                JsonNode value = node.get(fieldName);
                if (value != null && !value.isNull()) {
                    return value.asText();
                }
            }
        }
        return null;
    }
    
    private Integer getIntValue(JsonNode node, String... fieldNames) {
        if (node == null) return null;
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                JsonNode value = node.get(fieldName);
                if (value != null && value.isNumber()) {
                    return value.asInt();
                }
            }
        }
        return null;
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    // ============ 委托方法 ============
    
    public List<Tool<?, ?, ?>> getAvailableTools() {
        return delegate.getAvailableTools();
    }
    
    public List<Tool<?, ?, ?>> getEnabledTools() {
        return delegate.getEnabledTools();
    }
    
    public <I, O, P> Tool<I, O, P> getTool(String name) {
        return delegate.getTool(name);
    }
    
    public ActivityLogger getActivityLogger() {
        return activityLogger;
    }
    
    // ============ 工具调用请求记录 ============
    
    public record ToolCallRequest(
        String toolName,
        JsonNode input,
        Consumer<ToolProgress<?>> onProgress
    ) {
        public ToolCallRequest(String toolName, JsonNode input) {
            this(toolName, input, null);
        }
    }
}
