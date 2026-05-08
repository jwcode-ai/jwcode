package com.jwcode.core.a2a.retry;

import com.jwcode.core.a2a.model.ErrorSummary;
import com.jwcode.core.a2a.model.RetryPolicy;

import java.util.function.Predicate;

/**
 * RetryStrategy — 重试策略接口。
 *
 * <p>定义重试决策逻辑，支持不同的重试策略实现：
 * <ul>
 *   <li>指数退避（默认）</li>
 *   <li>固定间隔</li>
 *   <li>立即重试（仅对特定错误类型）</li>
 *   <li>不重试（快速失败）</li>
 * </ul>
 * </p>
 */
@FunctionalInterface
public interface RetryStrategy {

    /**
     * 判断是否应该重试。
     *
     * @param attempt      当前已重试次数
     * @param maxRetries   最大重试次数
     * @param error        错误摘要
     * @return true 表示应该重试
     */
    boolean shouldRetry(int attempt, int maxRetries, ErrorSummary error);

    /**
     * 计算第n次重试的等待时间（毫秒）。
     *
     * @param attempt 重试次数（从1开始）
     * @param policy  重试策略
     * @return 等待时间（毫秒）
     */
    default long computeDelayMs(int attempt, RetryPolicy policy) {
        return policy.computeBackoffMs(attempt);
    }

    // ==================== 内置策略 ====================

    /**
     * 指数退避策略（默认）。
     * 每次重试等待时间指数增长。
     */
    static RetryStrategy exponentialBackoff() {
        return (attempt, maxRetries, error) -> {
            if (attempt >= maxRetries) return false;
            if (error == null) return true;
            return error.isRetryable();
        };
    }

    /**
     * 固定间隔策略。
     * 每次重试等待固定时间。
     */
    static RetryStrategy fixedDelay(long delayMs) {
        return new RetryStrategy() {
            @Override
            public boolean shouldRetry(int attempt, int maxRetries, ErrorSummary error) {
                if (attempt >= maxRetries) return false;
                if (error == null) return true;
                return error.isRetryable();
            }

            @Override
            public long computeDelayMs(int attempt, RetryPolicy policy) {
                return delayMs;
            }
        };
    }

    /**
     * 立即重试策略（仅对特定错误类型）。
     * 不等待直接重试，适用于临时性错误。
     */
    static RetryStrategy immediateFor(Predicate<ErrorSummary> predicate) {
        return (attempt, maxRetries, error) -> {
            if (attempt >= maxRetries) return false;
            if (error == null) return true;
            return error.isRetryable() && predicate.test(error);
        };
    }

    /**
     * 不重试策略（快速失败）。
     */
    static RetryStrategy noRetry() {
        return (attempt, maxRetries, error) -> false;
    }

    /**
     * 自适应策略。
     * 根据错误类型动态选择重试行为：
     * - 超时类错误：立即重试（最多2次）
     * - 资源类错误：指数退避（最多3次）
     * - 权限类错误：不重试
     */
    static RetryStrategy adaptive() {
        return (attempt, maxRetries, error) -> {
            if (attempt >= maxRetries) return false;
            if (error == null) return true;
            if (!error.isRetryable()) return false;

            String type = error.getErrorType();
            if (type == null) return attempt < maxRetries;

            // 超时类错误：最多重试2次
            if (type.contains("TIMEOUT") || type.contains("TIMEOUT")) {
                return attempt < 2;
            }
            // 资源类错误：最多重试3次
            if (type.contains("RESOURCE") || type.contains("RATE_LIMIT")) {
                return attempt < 3;
            }
            // 其他可重试错误：使用最大重试次数
            return attempt < maxRetries;
        };
    }
}
