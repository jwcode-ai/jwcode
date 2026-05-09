package com.jwcode.core.tool;

import com.jwcode.core.a2a.model.ErrorSummary;
import com.jwcode.core.a2a.model.RetryPolicy;
import com.jwcode.core.a2a.retry.RetryOrchestrator;
import com.jwcode.core.a2a.retry.RetryStrategy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * ToolAgent — 第3层工具执行Agent。
 *
 * <p>职责：接收具体命令、调用MCP工具、执行、失败自诊断与修复。
 * 核心机制：3次自修复循环，失败返回结构化错误摘要（非原始堆栈）。</p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>不向上传递原始命令、堆栈跟踪、工具详情</li>
 *   <li>失败时返回：错误类型 + 修复尝试次数 + 最终失败原因(1句话)</li>
 *   <li>成功时返回：执行结果（不包含中间重试过程）</li>
 * </ul>
 * </p>
 *
 * <p>【重构】使用泛型 {@code <T>} 替代原始 {@code Supplier<Object>}，
 * 提供编译期类型安全。</p>
 *
 * @param <T> 操作结果的类型
 */
public class ToolAgent<T> {

    private static final Logger logger = Logger.getLogger(ToolAgent.class.getName());

    /** 默认自修复重试策略：3次重试，指数退避 */
    private static final RetryPolicy DEFAULT_SELF_HEAL_POLICY = RetryPolicy.builder()
        .maxRetries(3)
        .initialBackoffMs(500)
        .backoffMultiplier(1.5)
        .maxBackoffMs(10000)
        .retryableErrorTypes(List.of("TIMEOUT", "RATE_LIMIT", "RESOURCE_EXHAUSTED"))
        .nonRetryableErrorTypes(List.of("INVALID_INPUT", "PERMISSION_DENIED", "NOT_FOUND"))
        .build();

    private final RetryOrchestrator retryOrchestrator;
    private final RetryStrategy retryStrategy;

    public ToolAgent() {
        this.retryOrchestrator = new RetryOrchestrator();
        this.retryStrategy = RetryStrategy.exponentialBackoff();
    }

    public ToolAgent(RetryOrchestrator retryOrchestrator, RetryStrategy retryStrategy) {
        this.retryOrchestrator = retryOrchestrator;
        this.retryStrategy = retryStrategy;
    }

    /**
     * 执行工具操作（同步，带自修复）。
     *
     * @param toolName  工具名称
     * @param operation 类型安全操作
     * @return ToolAgentResult（不包含原始命令、堆栈跟踪）
     */
    public ToolAgentResult execute(String toolName, Supplier<T> operation) {
        long startTime = System.currentTimeMillis();
        try {
            T result = retryOrchestrator.executeWithRetry(
                operation, DEFAULT_SELF_HEAL_POLICY, retryStrategy);
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("[ToolAgent] " + toolName + " 执行成功 (" + elapsed + "ms)");
            return ToolAgentResult.success(toolName, result, elapsed);
        } catch (RetryOrchestrator.RetryExhaustedException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            ErrorSummary error = e.getErrorSummary();
            logger.warning("[ToolAgent] " + toolName + " 自修复耗尽 (attempts="
                + e.getAttemptCount() + ", " + elapsed + "ms): " + error.getMessage());
            return ToolAgentResult.failed(toolName, error, e.getAttemptCount(), elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            ErrorSummary error = ErrorSummary.toolAgentFailure(
                "未预期的错误: " + e.getMessage(), false, 0, 3);
            logger.severe("[ToolAgent] " + toolName + " 未预期异常 (" + elapsed + "ms): " + e.getMessage());
            return ToolAgentResult.failed(toolName, error, 0, elapsed);
        }
    }

    /**
     * 执行工具操作（异步，带自修复）。
     *
     * @param toolName  工具名称
     * @param operation 类型安全异步操作
     * @return CompletableFuture&lt;ToolAgentResult&gt;
     */
    public CompletableFuture<ToolAgentResult> executeAsync(String toolName,
                                                            Supplier<CompletableFuture<T>> operation) {
        long startTime = System.currentTimeMillis();
        return retryOrchestrator.executeWithRetryAsync(operation, DEFAULT_SELF_HEAL_POLICY, retryStrategy)
            .thenApply(result -> {
                long elapsed = System.currentTimeMillis() - startTime;
                logger.info("[ToolAgent] " + toolName + " 异步执行成功 (" + elapsed + "ms)");
                return ToolAgentResult.success(toolName, result, elapsed);
            })
            .exceptionally(throwable -> {
                long elapsed = System.currentTimeMillis() - startTime;
                if (throwable.getCause() instanceof RetryOrchestrator.RetryExhaustedException e) {
                    ErrorSummary error = e.getErrorSummary();
                    logger.warning("[ToolAgent] " + toolName + " 异步自修复耗尽 (attempts="
                        + e.getAttemptCount() + "): " + error.getMessage());
                    return ToolAgentResult.failed(toolName, error, e.getAttemptCount(), elapsed);
                }
                ErrorSummary error = ErrorSummary.toolAgentFailure(
                    "异步执行失败: " + throwable.getMessage(), false, 0, 3);
                return ToolAgentResult.failed(toolName, error, 0, elapsed);
            });
    }

    /**
     * 获取默认自修复策略。
     */
    public static RetryPolicy getDefaultSelfHealPolicy() {
        return DEFAULT_SELF_HEAL_POLICY;
    }

    /**
     * 创建带自定义重试策略的 ToolAgent。
     */
    @SuppressWarnings("unchecked")
    public static <T> ToolAgent<T> withCustomRetry(RetryPolicy policy, RetryStrategy strategy) {
        return new ToolAgent<>(new RetryOrchestrator(), strategy);
    }

    /**
     * 创建快速失败 ToolAgent（不重试）。
     */
    @SuppressWarnings("unchecked")
    public static <T> ToolAgent<T> fastFail() {
        return new ToolAgent<>(new RetryOrchestrator(), RetryStrategy.noRetry());
    }
}
