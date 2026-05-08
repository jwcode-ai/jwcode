package com.jwcode.core.a2a.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * RetryPolicy — 重试策略。
 *
 * <p>定义Agent或Tool的重试行为，包括最大重试次数、退避策略、
 * 可重试的错误类型列表、不可重试的错误类型列表。</p>
 *
 * <p>用于Agent Card中声明，让主Agent动态决策调度策略。</p>
 */
public class RetryPolicy {

    /** 最大重试次数（默认3次） */
    private final int maxRetries;

    /** 初始退避延迟（毫秒，默认500ms） */
    private final long initialBackoffMs;

    /** 退避乘数（默认1.5） */
    private final double backoffMultiplier;

    /** 最大退避延迟（毫秒，默认30秒） */
    private final long maxBackoffMs;

    /** 可重试的错误类型列表（空列表表示所有错误都可重试） */
    private final List<String> retryableErrorTypes;

    /** 不可重试的错误类型列表（优先级高于 retryableErrorTypes） */
    private final List<String> nonRetryableErrorTypes;

    private RetryPolicy(int maxRetries, long initialBackoffMs, double backoffMultiplier,
                        long maxBackoffMs, List<String> retryableErrorTypes,
                        List<String> nonRetryableErrorTypes) {
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffMs = maxBackoffMs;
        this.retryableErrorTypes = retryableErrorTypes != null
            ? Collections.unmodifiableList(retryableErrorTypes)
            : Collections.emptyList();
        this.nonRetryableErrorTypes = nonRetryableErrorTypes != null
            ? Collections.unmodifiableList(nonRetryableErrorTypes)
            : Collections.emptyList();
    }

    // ==================== Getters ====================

    public int getMaxRetries() { return maxRetries; }
    public long getInitialBackoffMs() { return initialBackoffMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public long getMaxBackoffMs() { return maxBackoffMs; }
    public List<String> getRetryableErrorTypes() { return retryableErrorTypes; }
    public List<String> getNonRetryableErrorTypes() { return nonRetryableErrorTypes; }

    /**
     * 判断指定错误类型是否可重试。
     */
    public boolean isRetryable(String errorType) {
        if (errorType == null) return false;
        // 非可重试列表优先级最高
        if (nonRetryableErrorTypes.contains(errorType)) return false;
        // 如果可重试列表为空，默认所有错误都可重试
        if (retryableErrorTypes.isEmpty()) return true;
        return retryableErrorTypes.contains(errorType);
    }

    /**
     * 计算第n次重试的退避延迟（毫秒）。
     *
     * @param attempt 重试次数（从1开始）
     * @return 退避延迟（毫秒）
     */
    public long computeBackoffMs(int attempt) {
        if (attempt <= 0) return 0;
        double delay = initialBackoffMs * Math.pow(backoffMultiplier, attempt - 1);
        return Math.min((long) delay, maxBackoffMs);
    }

    /**
     * 获取默认重试策略（3次重试，指数退避）。
     */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 500, 1.5, 30000,
            Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 获取快速失败策略（不重试）。
     */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0, 1.0, 0,
            Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 获取激进重试策略（5次重试，短退避）。
     */
    public static RetryPolicy aggressiveRetry() {
        return new RetryPolicy(5, 200, 1.3, 10000,
            Collections.emptyList(), List.of("INVALID_INPUT", "PERMISSION_DENIED"));
    }

    @Override
    public String toString() {
        return String.format("RetryPolicy{maxRetries=%d, backoff=%.1f^%d, maxBackoff=%dms}",
            maxRetries, backoffMultiplier, maxRetries, maxBackoffMs);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxRetries = 3;
        private long initialBackoffMs = 500;
        private double backoffMultiplier = 1.5;
        private long maxBackoffMs = 30000;
        private List<String> retryableErrorTypes;
        private List<String> nonRetryableErrorTypes;

        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder initialBackoffMs(long initialBackoffMs) { this.initialBackoffMs = initialBackoffMs; return this; }
        public Builder backoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; return this; }
        public Builder maxBackoffMs(long maxBackoffMs) { this.maxBackoffMs = maxBackoffMs; return this; }
        public Builder retryableErrorTypes(List<String> retryableErrorTypes) { this.retryableErrorTypes = retryableErrorTypes; return this; }
        public Builder nonRetryableErrorTypes(List<String> nonRetryableErrorTypes) { this.nonRetryableErrorTypes = nonRetryableErrorTypes; return this; }

        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, initialBackoffMs, backoffMultiplier,
                maxBackoffMs, retryableErrorTypes, nonRetryableErrorTypes);
        }
    }
}
