package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.hook.HookApprovalManager;
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
    private static final com.fasterxml.jackson.databind.ObjectMapper SHARED_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    private final ToolRegistry toolRegistry;
    private final PermissionChecker permissionChecker;
    private final ToolExecutionListener executionListener;
    private volatile HookChain hookChain;
    private final ConsecutiveFailureTracker failureTracker = new ConsecutiveFailureTracker();

    private final PlanModeManager planModeManager = PlanModeManager.getInstance();
    
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
     * 设置 Hook 拦截链（支持构造后注入）。
     * <p>线程安全：使用 volatile 保证多线程可见性。</p>
     *
     * @param hookChain Hook 拦截链，可为 null 表示禁用 Hook
     */
    public void setHookChain(HookChain hookChain) {
        this.hookChain = hookChain;
    }

    /**
     * 获取当前 Hook 拦截链。
     */
    public HookChain getHookChain() {
        return hookChain;
    }

    /**
     * 获取工具注册表。
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
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
        // Plan Mode isolation: reject write/execute tools
        if (!planModeManager.isToolAllowedInCurrentMode(tool.getCategory(), tool.getSideEffects().stream().findFirst().orElse(null), toolName)) {
            return CompletableFuture.completedFuture(ToolExecutionResult.error(toolName,
                "[Plan Mode] " + toolName + " blocked -- write/execute tools require Act Mode"));
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
                JsonNode inputJson = SHARED_MAPPER
                    .valueToTree(input);
                HookContext hookCtx = HookContext.forPreToolUse(
                    null, null, toolName, inputJson, context);
                HookResult hookResult = hookChain.execute(hookCtx);

                if (hookResult.getDecision() == HookDecision.DENY) {
                    logger.warning("[Hook] PRE_TOOL_USE DENY: " + toolName
                        + " | reason=" + hookResult.getReason());
                    return CompletableFuture.completedFuture(
                        ToolResult.error("Hook拒绝: " + hookResult.getReason()));
                }
                if (hookResult.getDecision() == HookDecision.MODIFY
                    && hookResult.getModifiedInput() != null) {
                    I modifiedInput = tool.parseInput(hookResult.getModifiedInput());
                    logger.fine("[Hook] PRE_TOOL_USE MODIFY: " + toolName);
                    // 使用修改后的输入重新执行（保留 hookChain 确保安全策略一致）
                    ToolExecutor inner = new ToolExecutor(
                        toolRegistry, permissionChecker, executionListener, hookChain);
                    return inner.execute(tool, modifiedInput, context, onProgress);
                }
                if (hookResult.getDecision() == HookDecision.ASK) {
                    String askPayload = hookResult.getAskPayload();
                    logger.info("[Hook] PRE_TOOL_USE ASK: " + toolName
                        + " | " + askPayload);
                    // 发起审批请求，一直等待用户决策（不超时）
                    try {
                        String sessionId = context != null && context.getSession() != null
                            ? context.getSession().getId() : null;
                        Boolean approved = HookApprovalManager.getInstance()
                            .requestApproval(toolName, askPayload != null ? askPayload : "", -1, sessionId)
                            .get();
                        if (approved == null || !approved) {
                            return CompletableFuture.completedFuture(
                                ToolResult.error("用户拒绝了 Hook 审批: " + askPayload));
                        }
                        logger.info("[Hook] PRE_TOOL_USE ASK approved by user: " + toolName);
                        // 审批通过，继续执行
                    } catch (Exception e) {
                        logger.warning("[Hook] PRE_TOOL_USE ASK error: " + e.getMessage());
                        return CompletableFuture.completedFuture(
                            ToolResult.error("Hook审批异常: " + e.getMessage()));
                    }
                }
                // ALLOW / DEFER / VOID → continue
            } catch (Exception e) {
                logger.warning("[Hook] PRE_TOOL_USE error: " + e.getMessage()
                    + " (fail-open, continuing)");
            }
        }
        
        // 闭环3: 写操作前自动保存检查点
        if (tool.isDestructive(input)) {
            saveWriteCheckpoint(toolName, context);
        }

        // 执行工具
        CompletableFuture<ToolResult<O>> future = tool.call(input, context, onProgress);
        
        // 添加完成回调
        return future.whenComplete((result, throwable) -> {
            long duration = System.currentTimeMillis() - startTime;

            if (throwable != null) {
                logger.severe("工具执行异常: " + toolName + " - " + throwable.getMessage());
                // 早期退出：跟踪连续同类失败，超过阈值时注入策略切换信号
                failureTracker.recordFailure(toolName, throwable.getMessage());
            } else if (result != null && !result.isSuccess()) {
                String errMsg = result.getContent() != null ? result.getContent() : "未知错误";
                String earlyExitMsg = failureTracker.recordFailure(toolName, errMsg);
                // 将策略切换信号注入到错误结果中，让 Agent 感知
                if (earlyExitMsg != null) {
                    logger.warning("[EarlyExit] Injecting strategy-change prompt for " + toolName);
                    String currentContent = result.getContent() != null ? result.getContent() : "";
                    result.setContent(currentContent + earlyExitMsg);
                }
            } else {
                // 成功则重置该工具的错误计数
                failureTracker.recordSuccess(toolName);
            }

            if (throwable != null) {
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

    /** 触发 POST_TOOL_USE 或 POST_TOOL_USE_FAILURE Hook，并将 hook contextOutput 注入工具结果 */
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
                        resultJson = SHARED_MAPPER
                            .valueToTree(result.getData());
                    } catch (Exception ignored) {
                        logger.fine("Failed to serialize tool result: " + ignored.getMessage());
                    }
                }
                hookCtx = HookContext.forPostToolUse(
                    null, null, toolName, resultJson);
            }
            logger.info("[Hook] POST_TOOL_USE: " + toolName
                + (error != null ? " FAILURE" : " OK"));
            HookResult hookResult = hookChain.execute(hookCtx);

            // 将 Hook 的 contextOutput 注入工具结果，使 Agent 能"看到"外部信号
            if (hookResult != null && hookResult.hasContextOutput() && result != null) {
                String injection = com.jwcode.core.hook.HookContextInjector.fromResult(
                    hookResult, "PostToolUse");
                if (!injection.isEmpty()) {
                    String currentContent = result.getContent() != null ? result.getContent() : "";
                    result.setContent(currentContent + injection);
                    logger.fine("[Hook] Injected contextOutput from " + hookResult.getHookName()
                        + " into tool result for " + toolName);
                }
            }
        } catch (Exception e) {
            logger.fine("[Hook] Post-hook error (ignored): " + e.getMessage());
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
    /** 闭环3: 写操作前自动保存检查点，失败可回退 */
    private void saveWriteCheckpoint(String toolName, ToolExecutionContext context) {
        try {
            String dir = System.getProperty("user.dir", ".");
            var cpm = new com.jwcode.core.planner.checkpoint.CheckpointManager(
                java.nio.file.Path.of(dir));
            // 使用当前 Session ID 和工具名作为任务 ID
            String taskId = (context != null && context.getSession() != null
                ? context.getSession().getId() : "auto") + "-" + toolName;
            var cp = com.jwcode.core.planner.checkpoint.CheckpointManager.Checkpoint.builder()
                .taskId(taskId)
                .contextJson("{\"tool\":\"" + toolName + "\"}")
                .resultsJson("{}")
                .busJson("{}")
                .timelineJson("[]")
                .build();
            cpm.saveCheckpoint(cp);
            logger.fine("[AutoCheckpoint] Saved before " + toolName);
        } catch (Exception e) {
            logger.fine("[AutoCheckpoint] Failed: " + e.getMessage());
        }
    }

    private String truncateForLog(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...(已截断)";
    }
    
    /**
     * 检查权限
     * 
     * <p>集成 PlanModeManager 的 Plan Mode 权限隔离 + PermissionChecker 运行时权限检查：</p>
     * <ul>
     *   <li>Plan Mode 下只允许只读工具</li>
     *   <li>Plan Mode 下禁用写工具（FileWrite、FileEdit、Bash 等）</li>
     *   <li>非 Plan Mode 下通过 PermissionChecker 做细粒度权限判断</li>
     * </ul>
     */
    private <I, O, P> boolean hasPermission(Tool<I, O, P> tool, I input) {
        // 1. Plan Mode 权限检查（带 null-safety 保护）
        PlanModeManager modeManager = PlanModeManager.getInstance();
        if (modeManager != null && modeManager.isPlanMode()) {
            PlanModeManager.PermissionResult planResult = modeManager.checkToolPermission(tool, input);
            if (planResult != null && planResult.isDenied()) {
                logger.warning("Plan Mode 权限拒绝: " + tool.getName() + " - " + planResult.getReason());
                return false;
            }
            // Plan Mode 下通过后直接放行（不再走 PermissionChecker）
            return true;
        }
        
        // 2. 【修复】集成 PermissionChecker 运行时权限检查
        //    非 Plan Mode 下，如果配置了 PermissionChecker，则用它做细粒度检查
        if (permissionChecker != null && tool != null) {
            String toolName = tool.getName();
            
            // 根据工具类型选择对应的权限检查
            boolean hasPerm;
            switch (toolName) {
                case "BashTool":
                case "PowerShell":
                    hasPerm = permissionChecker.hasPermission("execute", toolName);
                    break;
                case "REPL":
                    hasPerm = permissionChecker.hasPermission("execute", "REPL");
                    break;
                case "Git":
                    hasPerm = permissionChecker.hasPermission("execute", "Git");
                    break;
                case "Download":
                    hasPerm = permissionChecker.hasPermission("write", "Download");
                    break;
                case "NotebookEdit":
                    hasPerm = permissionChecker.hasPermission("write", "NotebookEdit");
                    break;
                case "ScheduleCron":
                    hasPerm = permissionChecker.hasPermission("execute", "ScheduleCron");
                    break;
                default:
                    // 其他工具：只读工具直接放行，破坏性操作需确认
                    if (tool.isReadOnly(input)) {
                        hasPerm = true;
                    } else if (tool.isDestructive(input) && tool.requiresApproval(input)) {
                        hasPerm = permissionChecker.hasPermission("write", toolName);
                    } else {
                        hasPerm = true;
                    }
                    break;
            }
            
            if (!hasPerm) {
                logger.warning("PermissionChecker 拒绝: " + tool.getName());
                return false;
            }
            return true;
        }
        
        // 3. 无 PermissionChecker 时的兜底逻辑
        if (tool != null) {
            if (tool.isReadOnly(input)) {
                return true;
            }
            
            // 破坏性操作需要确认
            if (tool.isDestructive(input) && tool.requiresApproval(input)) {
                return true; // 暂时允许
            }
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
