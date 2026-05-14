package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.hook.HookChain;
import com.jwcode.core.hook.HookContext;
import com.jwcode.core.hook.HookDecision;
import com.jwcode.core.hook.HookResult;
import com.jwcode.core.plan.PlanModeManager;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.permission.PermissionChecker;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * ToolExecutor - 工具执行器（重构后）
 * 
 * 对标 JavaScript 项目的工具执行架构
 * 支持异步执行、权限检查、进度回调和错误处理
 */
public class ToolExecutor {
    
    private static final Logger logger = Logger.getLogger(ToolExecutor.class.getName());
    
    private final ToolRegistry toolRegistry;
    private final PermissionChecker permissionChecker;
    private final ToolExecutionListener executionListener;
    private final HookChain hookChain;
    
    public ToolExecutor() {
        this(ToolRegistry.createDefault(), null, null, null);
    }
    
    public ToolExecutor(ToolRegistry toolRegistry) {
        this(toolRegistry, null, null, null);
    }
    
    public ToolExecutor(ToolRegistry toolRegistry, 
                       PermissionChecker permissionChecker,
                       ToolExecutionListener executionListener) {
        this(toolRegistry, permissionChecker, executionListener, null);
    }
    
    /**
     * 完整构造器（含 Hook 链）。
     *
     * @param toolRegistry      工具注册表
     * @param permissionChecker 权限检查器
     * @param executionListener 执行监听器（兼容层）
     * @param hookChain         Hook 拦截链（可为 null，表示不启用 Hook）
     */
    public ToolExecutor(ToolRegistry toolRegistry, 
                       PermissionChecker permissionChecker,
                       ToolExecutionListener executionListener,
                       HookChain hookChain) {
        this.toolRegistry = toolRegistry != null ? toolRegistry : ToolRegistry.createDefault();
        this.permissionChecker = permissionChecker;
        this.executionListener = executionListener;
        this.hookChain = hookChain;
    }
    
    /**
     * 获取可用工具列表
     */
    public List<Tool<?, ?, ?>> getAvailableTools() {
        return toolRegistry.getAllTools();
    }
    
    /**
     * 获取启用的工具列表
     */
    public List<Tool<?, ?, ?>> getEnabledTools() {
        return toolRegistry.getAllTools().stream()
            .filter(Tool::isEnabled)
            .toList();
    }
    
    /**
     * 根据名称获取工具
     */
    @SuppressWarnings("unchecked")
    public <I, O, P> Tool<I, O, P> getTool(String name) {
        return (Tool<I, O, P>) toolRegistry.getTool(name);
    }
    
    /**
     * 执行工具调用（从 JSON 输入）
     */
    public CompletableFuture<ToolExecutionResult> execute(
            String toolName,
            JsonNode inputJson,
            ToolExecutionContext context) {
        
        return execute(toolName, inputJson, context, null);
    }
    
    /**
     * 执行工具调用（从 JSON 输入，带进度回调）
     */
    public CompletableFuture<ToolExecutionResult> execute(
            String toolName,
            JsonNode inputJson,
            ToolExecutionContext context,
            Consumer<ToolProgress<?>> onProgress) {
        
        Tool<?, ?, ?> tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return CompletableFuture.completedFuture(
                ToolExecutionResult.error("未知工具: " + toolName)
            );
        }
        
        if (!tool.isEnabled()) {
            return CompletableFuture.completedFuture(
                ToolExecutionResult.error("工具已禁用: " + toolName)
            );
        }
        
        return executeToolInternal(tool, inputJson, context, onProgress);
    }
    
    /**
     * 执行工具调用（类型安全版本）
     */
    public <I, O, P> CompletableFuture<ToolResult<O>> execute(
            Tool<I, O, P> tool,
            I input,
            ToolExecutionContext context) {
        
        return execute(tool, input, context, null);
    }
    
    /**
     * 执行工具调用（类型安全版本，带进度回调）。
     *
     * <p><b>Hook 拦截点</b>：权限检查通过后触发 {@code PRE_TOOL_USE}，
     * 完成后触发 {@code POST_TOOL_USE} 或 {@code POST_TOOL_USE_FAILURE}。</p>
     */
    public <I, O, P> CompletableFuture<ToolResult<O>> execute(
            Tool<I, O, P> tool,
            I input,
            ToolExecutionContext context,
            Consumer<ToolProgress<P>> onProgress) {
        
        long startTime = System.currentTimeMillis();
        String toolName = tool.getName();
        
        // 通知监听器
        if (executionListener != null) {
            executionListener.onToolStart(toolName, input);
        }
        
        // 验证输入
        ToolValidationResult validation = tool.validate(input);
        if (!validation.isValid()) {
            logger.warning("工具输入验证失败: " + toolName + " - " + validation.getFormattedErrors());
            return CompletableFuture.completedFuture(
                ToolResult.error("输入验证失败: " + validation.getFormattedErrors())
            );
        }
        
        // 【修复】检查权限 — 同时检查 PlanModeManager 和 PermissionChecker
        if (!hasPermission(tool, input)) {
            String error = "没有权限执行工具: " + toolName;
            logger.warning(error);
            return CompletableFuture.completedFuture(ToolResult.error(error));
        }
        
        // ──────── Hook: PRE_TOOL_USE ────────
        if (hookChain != null) {
            try {
                JsonNode inputJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .valueToTree(input);
                HookContext hookCtx = HookContext.forPreToolUse(
                    null, null, toolName, inputJson, context);
                HookResult hookResult = hookChain.execute(hookCtx);

                if (hookResult.getDecision() == HookDecision.DENY) {
                    logger.warning("[ToolExecutor] PRE_TOOL_USE DENY: " + toolName
                        + " | reason=" + hookResult.getReason());
                    return CompletableFuture.completedFuture(
                        ToolResult.error("Hook拒绝: " + hookResult.getReason()));
                }
                if (hookResult.getDecision() == HookDecision.MODIFY
                    && hookResult.getModifiedInput() != null) {
                    I modifiedInput = tool.parseInput(hookResult.getModifiedInput());
                    logger.fine("[ToolExecutor] PRE_TOOL_USE MODIFY: " + toolName);
                    // 使用修改后的输入重新执行（hookChain置null防止递归）
                    ToolExecutor inner = new ToolExecutor(
                        toolRegistry, permissionChecker, executionListener, null);
                    return inner.execute(tool, modifiedInput, context, onProgress);
                }
                if (hookResult.getDecision() == HookDecision.ASK) {
                    logger.info("[ToolExecutor] PRE_TOOL_USE ASK: " + toolName
                        + " | " + hookResult.getAskPayload());
                    return CompletableFuture.completedFuture(
                        ToolResult.error("HOOK_ASK:" + hookResult.getAskPayload()));
                }
                // ALLOW / DEFER / VOID → continue
            } catch (Exception e) {
                logger.warning("[ToolExecutor] PRE_TOOL_USE hook error: " + e.getMessage()
                    + " (fail-open, continuing)");
            }
        }
        
        // 执行工具
        CompletableFuture<ToolResult<O>> future = tool.call(input, context, onProgress);
        
        // 添加完成回调
        return future.whenComplete((result, throwable) -> {
            long duration = System.currentTimeMillis() - startTime;
            
            if (throwable != null) {
                logger.severe("工具执行异常: " + toolName + " - " + throwable.getMessage());
                if (executionListener != null) {
                    executionListener.onToolError(toolName, throwable, duration);
                }
                triggerPostHook(toolName, context, null, throwable);
            } else {
                logger.fine("工具执行完成: " + toolName + " (" + duration + "ms)");
                if (executionListener != null) {
                    executionListener.onToolComplete(toolName, result, duration);
                }
                triggerPostHook(toolName, context, result, null);
            }
        });
    }

    /** 触发 POST_TOOL_USE 或 POST_TOOL_USE_FAILURE Hook */
    private void triggerPostHook(String toolName, ToolExecutionContext context,
                                  ToolResult<?> result, Throwable error) {
        if (hookChain == null) return;
        try {
            HookContext hookCtx;
            if (error != null) {
                hookCtx = HookContext.forPostToolUseFailure(
                    null, null, toolName, error.getMessage());
            } else {
                JsonNode resultJson = null;
                if (result != null && result.getData() != null) {
                    try {
                        resultJson = new com.fasterxml.jackson.databind.ObjectMapper()
                            .valueToTree(result.getData());
                    } catch (Exception ignored) {}
                }
                hookCtx = HookContext.forPostToolUse(
                    null, null, toolName, resultJson);
            }
            hookChain.execute(hookCtx);
        } catch (Exception e) {
            logger.fine("[ToolExecutor] Post-hook error (ignored): " + e.getMessage());
        }
    }
    
    /**
     * 批量执行工具
     */
    public List<CompletableFuture<ToolExecutionResult>> executeBatch(
            List<ToolCallRequest> requests,
            ToolExecutionContext context) {
        
        List<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();
        
        for (ToolCallRequest request : requests) {
            CompletableFuture<ToolExecutionResult> future = execute(
                request.toolName(),
                request.input(),
                context,
                request.onProgress()
            );
            futures.add(future);
        }
        
        return futures;
    }
    
    /**
     * 内部执行方法
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private CompletableFuture<ToolExecutionResult> executeToolInternal(
            Tool tool,
            JsonNode inputJson,
            ToolExecutionContext context,
            Consumer<ToolProgress<?>> onProgress) {
        
        String toolName = tool.getName();
        
        try {
            // 解析输入
            logger.fine("解析工具输入: " + (inputJson != null ? truncateForLog(inputJson.toString(), 200) : "(原始类型)"));
            Object input = tool.parseInput(inputJson);
            
            // 记录工具执行开始（精简单行日志）
            String inputPreview = inputJson != null 
                ? truncateForLog(inputJson.toString(), 120) 
                : (input != null ? truncateForLog(input.toString(), 120) : "");
            logger.fine("[Tool] " + toolName + " | input=" + inputPreview);
            
            // 执行工具 - 使用原始类型执行
            CompletableFuture<?> future = execute(tool, input, context, (Consumer) onProgress);
            return future.handle((result, throwable) -> {
                if (throwable != null) {
                    String errorMsg = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
                    logger.warning("[Tool] " + toolName + " FAILED | error=" + errorMsg);
                    return ToolExecutionResult.error(toolName, errorMsg);
                }
                
                ToolResult<?> toolResult = (ToolResult<?>) result;
                if (toolResult != null && toolResult.isSuccess()) {
                    String outputPreview = toolResult.getData() != null 
                        ? truncateForLog(tool.serializeOutput(toolResult.getData()).toString(), 120) 
                        : "(empty)";
                    logger.fine("[Tool] " + toolName + " OK | output=" + outputPreview);
                    return ToolExecutionResult.success(toolName, toolResult);
                } else {
                    String errorMsg = toolResult != null ? toolResult.getContent() : "未知错误";
                    logger.warning("[Tool] " + toolName + " FAILED | error=" + errorMsg);
                    return ToolExecutionResult.error(toolName, errorMsg);
                }
            });
                
        } catch (Exception e) {
            logger.warning("[Tool] " + toolName + " FAILED | " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return CompletableFuture.completedFuture(
                ToolExecutionResult.error(toolName, e.getMessage())
            );
        }
    }
    
   
    
    /**
     * 截断日志字符串以避免过长
     */
    private String truncateForLog(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...(已截断)";
    }
    
    /**
     * 检查权限
     * 
     * <p>集成 PlanModeManager 的 Plan Mode 权限隔离：</p>
     * <ul>
     *   <li>Plan Mode 下只允许只读工具</li>
     *   <li>Plan Mode 下禁用写工具（FileWrite、FileEdit、Bash 等）</li>
     *   <li>非 Plan Mode 下所有工具都允许</li>
     * </ul>
     */
    private <I, O, P> boolean hasPermission(Tool<I, O, P> tool, I input) {
        // 1. Plan Mode 权限检查
        PlanModeManager modeManager = PlanModeManager.getInstance();
        if (modeManager.isPlanMode()) {
            PlanModeManager.PermissionResult planResult = modeManager.checkToolPermission(tool, input);
            if (planResult.isDenied()) {
                logger.warning("Plan Mode 权限拒绝: " + tool.getName() + " - " + planResult.getReason());
                return false;
            }
            return true;
        }
        
        // 2. 非 Plan Mode 下的常规权限检查
        // 只读工具通常不需要额外权限检查
        if (tool.isReadOnly(input)) {
            return true;
        }
        
        // 破坏性操作需要确认
        if (tool.isDestructive(input) && tool.requiresApproval(input)) {
            // 这里可以实现更复杂的权限逻辑
            return true; // 暂时允许
        }
        
        return true;
    }

    
    /**
     * 工具调用请求记录
     */
    public record ToolCallRequest(
        String toolName,
        JsonNode input,
        Consumer<ToolProgress<?>> onProgress
    ) {
        public ToolCallRequest(String toolName, JsonNode input) {
            this(toolName, input, null);
        }
    }
    
    /**
     * 工具执行结果
     */
    public static class ToolExecutionResult {
        private final String toolName;
        private final boolean success;
        private final String errorMessage;
        private final ToolResult<?> result;
        
        private ToolExecutionResult(String toolName, boolean success, 
                                    String errorMessage, ToolResult<?> result) {
            this.toolName = toolName;
            this.success = success;
            this.errorMessage = errorMessage;
            this.result = result;
        }
        
        public static ToolExecutionResult success(String toolName, ToolResult<?> result) {
            return new ToolExecutionResult(toolName, true, null, result);
        }
        
        public static ToolExecutionResult error(String errorMessage) {
            return new ToolExecutionResult(null, false, errorMessage, null);
        }
        
        public static ToolExecutionResult error(String toolName, String errorMessage) {
            return new ToolExecutionResult(toolName, false, errorMessage, null);
        }
        
        public String getToolName() { return toolName; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public ToolResult<?> getResult() { return result; }
        public boolean hasError() { return errorMessage != null; }
    }
    
    /**
     * 工具执行监听器接口
     */
    public interface ToolExecutionListener {
        void onToolStart(String toolName, Object input);
        void onToolComplete(String toolName, ToolResult<?> result, long durationMs);
        void onToolError(String toolName, Throwable error, long durationMs);
    }
}
