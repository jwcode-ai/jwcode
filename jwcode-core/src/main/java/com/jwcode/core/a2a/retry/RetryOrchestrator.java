package com.jwcode.core.a2a.retry;

import com.jwcode.core.a2a.model.ErrorSummary;
import com.jwcode.core.a2a.model.RetryPolicy;
import com.jwcode.core.a2a.model.TaskLifecycle;

import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * RetryOrchestrator — 分层重试编排器。
 *
 * <p>实现三层重试策略：
 * <ol>
 *   <li><b>Tool Agent层（自修复）</b>：执行命令 → 失败 → LLM分析错误 → 生成修复命令 → 重试。循环3次仍失败则返回结构化错误摘要。</li>
 *   <li><b>专业Agent层（步骤替代）</b>：收到Tool Agent失败 → 判断retryable → 换参数/换工具重试 或 跳过该步骤。</li>
 *   <li><b>主Agent层（任务重排）</b>：收到子任务失败 → 判断关键路径 → 非关键路径标记部分完成，关键路径终止流程。</li>
 * </ol>
 * </p>
 */
public class RetryOrchestrator {

    private static final Logger logger = Logger.getLogger(RetryOrchestrator.class.getName());

    private final ScheduledExecutorService scheduler;

    public RetryOrchestrator() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "retry-orchestrator");
            t.setDaemon(true);
            return t;
        });
    }

    public RetryOrchestrator(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 执行带重试的操作（同步阻塞）。
     *
     * @param operation  操作
     * @param policy     重试策略
     * @param strategy   重试策略算法
     * @param <T>        返回类型
     * @return 操作结果
     * @throws RetryExhaustedException 所有重试耗尽
     */
    public <T> T executeWithRetry(Supplier<T> operation,
                                   RetryPolicy policy,
                                   RetryStrategy strategy) throws RetryExhaustedException {
        int attempt = 0;
        while (true) {
            try {
                T result = operation.get();
                if (attempt > 0) {
                    logger.info("[RetryOrchestrator] 重试成功 (attempt=" + attempt + ")");
                }
                return result;
            } catch (Exception e) {
                attempt++;
                ErrorSummary error = ErrorSummary.toolAgentFailure(
                    e.getMessage(),
                    policy.isRetryable(classifyError(e)),
                    attempt,
                    policy.getMaxRetries()
                );

                if (!strategy.shouldRetry(attempt, policy.getMaxRetries(), error)) {
                    throw new RetryExhaustedException(attempt, error, e);
                }

                long delayMs = strategy.computeDelayMs(attempt, policy);
                logger.warning("[RetryOrchestrator] 操作失败 (attempt=" + attempt
                    + "/" + policy.getMaxRetries() + "), 等待 " + delayMs + "ms 后重试: " + error.getMessage());

                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RetryExhaustedException(attempt, error, ie);
                    }
                }
            }
        }
    }

    /**
     * 执行带重试的操作（异步）。
     *
     * @param operation  异步操作
     * @param policy     重试策略
     * @param strategy   重试策略算法
     * @param <T>        返回类型
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> executeWithRetryAsync(Supplier<CompletableFuture<T>> operation,
                                                           RetryPolicy policy,
                                                           RetryStrategy strategy) {
        CompletableFuture<T> result = new CompletableFuture<>();
        executeRetryAsync(operation, policy, strategy, result, 0);
        return result;
    }

    private <T> void executeRetryAsync(Supplier<CompletableFuture<T>> operation,
                                        RetryPolicy policy,
                                        RetryStrategy strategy,
                                        CompletableFuture<T> result,
                                        int attempt) {
        operation.get().whenComplete((value, throwable) -> {
            if (throwable == null) {
                result.complete(value);
                return;
            }

            int newAttempt = attempt + 1;
            ErrorSummary error = ErrorSummary.toolAgentFailure(
                throwable.getMessage(),
                policy.isRetryable(classifyError(throwable)),
                newAttempt,
                policy.getMaxRetries()
            );

            if (!strategy.shouldRetry(newAttempt, policy.getMaxRetries(), error)) {
                result.completeExceptionally(new RetryExhaustedException(newAttempt, error, throwable));
                return;
            }

            long delayMs = strategy.computeDelayMs(newAttempt, policy);
            logger.warning("[RetryOrchestrator] 异步操作失败 (attempt=" + newAttempt
                + "/" + policy.getMaxRetries() + "), 等待 " + delayMs + "ms 后重试");

            scheduler.schedule(
                () -> executeRetryAsync(operation, policy, strategy, result, newAttempt),
                delayMs, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * 专业Agent层：步骤级重试/替代决策。
     *
     * @param lifecycle 任务生命周期
     * @param stepId    步骤ID
     * @param error     错误摘要
     * @param policy    重试策略
     * @param strategy  重试策略算法
     * @return 决策结果
     */
    public StepDecision decideStepAction(TaskLifecycle lifecycle, String stepId,
                                          ErrorSummary error, RetryPolicy policy,
                                          RetryStrategy strategy) {
        TaskLifecycle.Step step = lifecycle.getStep(stepId);
        if (step == null) {
            return StepDecision.fail("步骤不存在: " + stepId);
        }

        int currentRetries = step.getRetryCount();

        // 判断是否可重试
        if (error.isRetryable() && strategy.shouldRetry(currentRetries, policy.getMaxRetries(), error)) {
            return StepDecision.retry(
                String.format("重试步骤 (第 %d/%d 次)", currentRetries + 1, policy.getMaxRetries()));
        }

        // 判断是否有恢复建议
        if (error.getRecoveryHint() != null && !error.getRecoveryHint().isEmpty()) {
            return StepDecision.alternative(error.getRecoveryHint());
        }

        // 判断是否可跳过
        if (!step.isCritical()) {
            return StepDecision.skip("非关键步骤失败，跳过");
        }

        // 关键路径失败
        return StepDecision.fail("关键步骤失败，终止任务");
    }

    /**
     * 主Agent层：任务级重排决策。
     *
     * @param lifecycle 任务生命周期
     * @param error     错误摘要
     * @return 重排决策
     */
    public TaskDecision decideTaskAction(TaskLifecycle lifecycle, ErrorSummary error) {
        if (error.isCriticalPath()) {
            return TaskDecision.terminate(error.getMessage());
        }

        // 检查是否有替代方案
        if (error.getRecoveryHint() != null) {
            return TaskDecision.replan(error.getRecoveryHint());
        }

        // 部分完成
        return TaskDecision.partialComplete(
            "任务部分完成，失败原因: " + error.getMessage());
    }

    /**
     * 关闭调度器。
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 内部类型 ====================

    /**
     * 步骤级决策。
     */
    public static class StepDecision {
        public enum Action { RETRY, ALTERNATIVE, SKIP, FAIL }

        private final Action action;
        private final String reason;

        private StepDecision(Action action, String reason) {
            this.action = action;
            this.reason = reason;
        }

        public Action getAction() { return action; }
        public String getReason() { return reason; }

        public static StepDecision retry(String reason) { return new StepDecision(Action.RETRY, reason); }
        public static StepDecision alternative(String reason) { return new StepDecision(Action.ALTERNATIVE, reason); }
        public static StepDecision skip(String reason) { return new StepDecision(Action.SKIP, reason); }
        public static StepDecision fail(String reason) { return new StepDecision(Action.FAIL, reason); }

        @Override
        public String toString() {
            return "StepDecision{" + action + ": " + reason + "}";
        }
    }

    /**
     * 任务级决策。
     */
    public static class TaskDecision {
        public enum Action { CONTINUE, REPLAN, PARTIAL_COMPLETE, TERMINATE }

        private final Action action;
        private final String reason;

        private TaskDecision(Action action, String reason) {
            this.action = action;
            this.reason = reason;
        }

        public Action getAction() { return action; }
        public String getReason() { return reason; }

        public static TaskDecision replan(String reason) { return new TaskDecision(Action.REPLAN, reason); }
        public static TaskDecision partialComplete(String reason) { return new TaskDecision(Action.PARTIAL_COMPLETE, reason); }
        public static TaskDecision terminate(String reason) { return new TaskDecision(Action.TERMINATE, reason); }

        @Override
        public String toString() {
            return "TaskDecision{" + action + ": " + reason + "}";
        }
    }

    /**
     * 重试耗尽异常。
     */
    public static class RetryExhaustedException extends RuntimeException {
        private final int attemptCount;
        private final ErrorSummary errorSummary;

        public RetryExhaustedException(int attemptCount, ErrorSummary errorSummary, Throwable cause) {
            super("重试耗尽 (attempt=" + attemptCount + "): " + errorSummary.getMessage(), cause);
            this.attemptCount = attemptCount;
            this.errorSummary = errorSummary;
        }

        public int getAttemptCount() { return attemptCount; }
        public ErrorSummary getErrorSummary() { return errorSummary; }
    }

    // ==================== 辅助方法 ====================

    private String classifyError(Throwable e) {
        if (e == null) return "UNKNOWN";
        String msg = e.getMessage();
        if (msg == null) return "UNKNOWN";
        String upper = msg.toUpperCase();
        if (upper.contains("TIMEOUT") || upper.contains("TIMEOUT")) return "TIMEOUT";
        if (upper.contains("PERMISSION") || upper.contains("ACCESS_DENIED")) return "PERMISSION_DENIED";
        if (upper.contains("NOT_FOUND") || upper.contains("NO_SUCH")) return "NOT_FOUND";
        if (upper.contains("RATE") || upper.contains("LIMIT")) return "RATE_LIMIT";
        if (upper.contains("INVALID") || upper.contains("ILLEGAL")) return "INVALID_INPUT";
        return "UNKNOWN";
    }
}
