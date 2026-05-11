package com.jwcode.core.tool;

import com.jwcode.core.a2a.model.ErrorSummary;
import com.jwcode.core.a2a.model.RetryPolicy;
import com.jwcode.core.a2a.retry.RetryOrchestrator;
import com.jwcode.core.a2a.retry.RetryStrategy;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 *   <li><strong>【工作区安全】</strong> 确保所有文件操作不超出工作目录范围</li>
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
        .nonRetryableErrorTypes(List.of("INVALID_INPUT", "PERMISSION_DENIED", "NOT_FOUND",
            "WORKSPACE_ACCESS_DENIED"))
        .build();

    private final RetryOrchestrator retryOrchestrator;
    private final RetryStrategy retryStrategy;

    /** 工作区安全守卫（可选，用于路径边界校验） */
    private final WorkspaceGuard workspaceGuard;

    // ==================== 构造器 ====================

    public ToolAgent() {
        this.retryOrchestrator = new RetryOrchestrator();
        this.retryStrategy = RetryStrategy.exponentialBackoff();
        this.workspaceGuard = null;
    }

    public ToolAgent(RetryOrchestrator retryOrchestrator, RetryStrategy retryStrategy) {
        this.retryOrchestrator = retryOrchestrator;
        this.retryStrategy = retryStrategy;
        this.workspaceGuard = null;
    }

    /**
     * 创建带工作区守卫的 ToolAgent。
     *
     * @param workspaceRoot 工作区根路径（所有文件操作必须在此目录内）
     */
    public ToolAgent(Path workspaceRoot) {
        this.retryOrchestrator = new RetryOrchestrator();
        this.retryStrategy = RetryStrategy.exponentialBackoff();
        this.workspaceGuard = workspaceRoot != null ? new WorkspaceGuard(workspaceRoot) : null;
    }

    /**
     * 完整构造器。
     *
     * @param retryOrchestrator 重试编排器
     * @param retryStrategy     重试策略
     * @param workspaceRoot     工作区根路径（可选，null 表示不校验）
     */
    public ToolAgent(RetryOrchestrator retryOrchestrator, RetryStrategy retryStrategy,
                     Path workspaceRoot) {
        this.retryOrchestrator = retryOrchestrator;
        this.retryStrategy = retryStrategy;
        this.workspaceGuard = workspaceRoot != null ? new WorkspaceGuard(workspaceRoot) : null;
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
     * 执行带路径校验的工具操作（同步，带自修复）。
     *
     * <p>在执行操作前，先校验目标路径是否在工作区内。
     * 如果校验失败，立即返回 WORKSPACE_ACCESS_DENIED 错误，不执行操作。</p>
     *
     * @param toolName   工具名称
     * @param targetPath 要操作的目标路径
     * @param operation  类型安全操作
     * @return ToolAgentResult
     */
    public ToolAgentResult executeWithPathCheck(String toolName, Path targetPath,
                                                 Supplier<T> operation) {
        // 工作区边界校验
        if (workspaceGuard != null) {
            Optional<ErrorSummary> pathError = workspaceGuard.validatePath(targetPath, toolName);
            if (pathError.isPresent()) {
                long elapsed = 0;
                logger.warning("[ToolAgent] " + toolName + " 工作区校验失败: " + pathError.get().getMessage());
                return ToolAgentResult.failed(toolName, pathError.get(), 0, elapsed);
            }
        }
        return execute(toolName, operation);
    }

    /**
     * 执行带路径校验的工具操作（同步，带自修复）。
     *
     * <p>校验原始路径（支持相对路径）是否在工作区内。</p>
     *
     * @param toolName   工具名称
     * @param rawPath    原始路径（可以是相对路径）
     * @param workingDir 当前工作目录（用于解析相对路径）
     * @param operation  类型安全操作
     * @return ToolAgentResult
     */
    public ToolAgentResult executeWithPathCheck(String toolName, String rawPath,
                                                 Path workingDir, Supplier<T> operation) {
        if (workspaceGuard != null) {
            try {
                // 解析并校验路径
                workspaceGuard.resolveAndValidate(rawPath, workingDir, toolName);
            } catch (WorkspaceGuard.WorkspaceAccessException e) {
                logger.warning("[ToolAgent] " + toolName + " 工作区校验失败: " + e.getMessage());
                return ToolAgentResult.failed(toolName, e.getErrorSummary(), 0, 0);
            }
        }
        return execute(toolName, operation);
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
     * 获取工作区守卫（可能为 null）。
     */
    public WorkspaceGuard getWorkspaceGuard() {
        return workspaceGuard;
    }

    /**
     * 是否有工作区守卫。
     */
    public boolean hasWorkspaceGuard() {
        return workspaceGuard != null;
    }

    /**
     * 获取工作区根路径（可能为 null）。
     */
    public Path getWorkspaceRoot() {
        return workspaceGuard != null ? workspaceGuard.getWorkspaceRoot() : null;
    }

    /**
     * 校验路径是否在工作区内。
     *
     * @param targetPath 要校验的路径
     * @param toolName   工具名称
     * @return 空 Optional 表示校验通过
     */
    public Optional<ErrorSummary> validatePath(Path targetPath, String toolName) {
        if (workspaceGuard == null) {
            return Optional.empty();
        }
        return workspaceGuard.validatePath(targetPath, toolName);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建带工作区守卫的 ToolAgent（推荐）。
     *
     * @param workspaceRoot 工作区根路径
     */
    public static <T> ToolAgent<T> withWorkspace(Path workspaceRoot) {
        return new ToolAgent<>(workspaceRoot);
    }

    /**
     * 创建带自定义重试策略和工作区守卫的 ToolAgent。
     */
    @SuppressWarnings("unchecked")
    public static <T> ToolAgent<T> withWorkspaceAndRetry(Path workspaceRoot,
                                                          RetryPolicy policy,
                                                          RetryStrategy strategy) {
        return new ToolAgent<>(new RetryOrchestrator(), strategy, workspaceRoot);
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

    /**
     * 创建快速失败 + 工作区校验 ToolAgent。
     */
    @SuppressWarnings("unchecked")
    public static <T> ToolAgent<T> fastFailWithWorkspace(Path workspaceRoot) {
        return new ToolAgent<>(new RetryOrchestrator(), RetryStrategy.noRetry(), workspaceRoot);
    }
}
