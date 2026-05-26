package com.jwcode.core.hook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HookChain — Hook 拦截链编排引擎。
 *
 * <p>核心职责：针对特定事件，按优先级串行执行所有匹配的 Hook，
 * 应用冲突裁决规则，聚合最终决策结果。</p>
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>从 {@link HookRegistry} 获取匹配当前事件的 Hook 执行器列表</li>
 *   <li>按 {@link HookPriority} 从高到低排序</li>
 *   <li>串行执行每个 Hook（高优先级先执行）</li>
 *   <li>遇到 {@code DENY} 或 {@code VOID} 立即短路，不再执行后续 Hook</li>
 *   <li>{@code MODIFY} 决策：将 {@code modifiedInput} 传递给下一个 Hook</li>
 *   <li>使用 {@link HookPriority.ConflictResolver} 聚合最终结果</li>
 * </ol>
 *
 * <h3>架构位置</h3>
 * <p>
 * HookChain 位于 Governance Layer（横向切面），横跨 4 层架构的各层边界。
 * 在 {@code ToolExecutor}、{@code StateMachine}、{@code A2AFacade} 等关键节点
 * 被调用，形成生命周期拦截网络。
 * </p>
 *
 * @author JWCode Team
 * @since 2.1.0
 */
public class HookChain {

    private static final Logger logger = Logger.getLogger(HookChain.class.getName());

    private final HookRegistry registry;
    private final HookAuditLogger auditLogger;
    private final long defaultTimeoutMs;

    /**
     * @param registry    Hook 注册表
     * @param auditLogger 审计日志（可为 null）
     */
    public HookChain(HookRegistry registry, HookAuditLogger auditLogger) {
        this(registry, auditLogger, 30_000);
    }

    /**
     * @param registry         Hook 注册表
     * @param auditLogger      审计日志
     * @param defaultTimeoutMs 默认超时（毫秒）
     */
    public HookChain(HookRegistry registry, HookAuditLogger auditLogger, long defaultTimeoutMs) {
        this.registry = registry;
        this.auditLogger = auditLogger;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    /**
     * 执行 Hook 链，返回聚合后的最终决策。
     *
     * @param context Hook 上下文
     * @return 最终决策结果
     */
    public HookResult execute(HookContext context) {
        HookEventType eventType = context.getEventType();

        // 1. 获取匹配的 Hook 执行器
        List<HookExecutor> executors = registry.getExecutorsFor(eventType);

        if (executors.isEmpty()) {
            HookResult result = HookResult.allow("default", "No hooks registered for " + eventType);
            auditLog(context, result);
            return result;
        }

        // 2. 过滤并排序
        List<HookExecutor> matched = executors.stream()
            .filter(HookExecutor::isEnabled)
            .filter(e -> e.supportsEvent(eventType))
            .sorted(Comparator.comparingInt(e -> -e.getPriority().getLevel())) // 降序
            .toList();

        if (matched.isEmpty()) {
            HookResult result = HookResult.allow("default", "No matching hooks for " + eventType);
            auditLog(context, result);
            return result;
        }

        // 3. 串行执行
        HookResult accumulated = null;
        for (HookExecutor executor : matched) {
            long startTime = System.currentTimeMillis();

            HookResult result;
            try {
                result = executeWithTimeout(executor, context);
            } catch (Exception e) {
                // 执行异常 → 按 fail-open/fail-closed 策略处理
                boolean failOpen = executor.isFailOpen();
                result = failOpen
                    ? HookResult.error(executor.getName(), e.getMessage())
                    : HookResult.errorFailClosed(executor.getName(), e.getMessage());
                logger.log(Level.WARNING,
                    "[HookChain] " + executor.getName() + " error (failOpen=" + failOpen + "): " + e.getMessage());
            }

            long duration = System.currentTimeMillis() - startTime;
            result = new HookResult.Builder(result.getDecision(), result.getHookName())
                .reason(result.getReason())
                .modifiedInput(result.getModifiedInput())
                .askPayload(result.getAskPayload())
                .deferToken(result.getDeferToken())
                .rollbackAction(result.getRollbackAction())
                .durationMs(duration)
                .build();

            // 4. 冲突裁决
            accumulated = HookPriority.ConflictResolver.merge(accumulated, result);

            // 5. 审计
            auditLog(context, result);

            // 6. WebSocket 广播（前端实时展示 Hook 拦截事件）
            HookEventBroadcaster.broadcast(context, result);

            // 7. 短路：终止性决策
            if (result.getDecision().isTerminal()) {
                logger.info("[HookChain] Short-circuit: " + executor.getName()
                    + " returned " + result.getDecision());
                break;
            }

            // 7. MODIFY 链式传递
            if (result.getDecision() == HookDecision.MODIFY && result.getModifiedInput() != null) {
                context = rebuildContextWithModifiedInput(context, result.getModifiedInput());
            }
        }

        if (accumulated == null) {
            accumulated = HookResult.allow("default", "All hooks completed without decision");
        }

        return accumulated;
    }

    /**
     * 异步执行 Hook 链。
     *
     * @param context Hook 上下文
     * @return 最终决策结果的 CompletableFuture
     */
    public CompletableFuture<HookResult> executeAsync(HookContext context) {
        return CompletableFuture.supplyAsync(() -> execute(context));
    }

    // ==================== 内部方法 ====================

    /**
     * 带超时控制的 Hook 执行。
     */
    private HookResult executeWithTimeout(HookExecutor executor, HookContext context)
        throws InterruptedException, ExecutionException, TimeoutException {

        long timeout = executor.getTimeoutMs();
        if (timeout <= 0) {
            timeout = defaultTimeoutMs;
        }

        CompletableFuture<HookResult> future = executor.execute(context);
        return future.get(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * 当 Hook 返回 MODIFY 时，使用修改后的输入重建上下文。
     */
    private HookContext rebuildContextWithModifiedInput(HookContext original, com.fasterxml.jackson.databind.JsonNode modifiedInput) {
        return new HookContext.Builder(original.getEventType())
            .sessionId(original.getSessionId())
            .agentName(original.getAgentName())
            .toolName(original.getToolName())
            .toolInput(modifiedInput)  // ← 修改后的输入
            .executionContext(original.getExecutionContext())
            .fromState(original.getFromState())
            .toState(original.getToState())
            .transitionReason(original.getTransitionReason())
            .sourceAgentName(original.getSourceAgentName())
            .targetAgentName(original.getTargetAgentName())
            .taskId(original.getTaskId())
            .build();
    }

    /**
     * 记录审计日志。
     */
    private void auditLog(HookContext context, HookResult result) {
        if (auditLogger != null) {
            auditLogger.record(context, result);
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取 Hook 注册表（用于查询和热重载）。
     */
    public HookRegistry getRegistry() {
        return registry;
    }

    /**
     * 获取审计日志。
     */
    public HookAuditLogger getAuditLogger() {
        return auditLogger;
    }

    /**
     * 创建不带审计日志的简单 HookChain（用于测试）。
     */
    public static HookChain createSimple(HookRegistry registry) {
        return new HookChain(registry, null);
    }
}
