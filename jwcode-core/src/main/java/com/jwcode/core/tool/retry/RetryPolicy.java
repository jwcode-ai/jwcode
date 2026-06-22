package com.jwcode.core.tool.retry;

import java.util.Collections;
import java.util.List;

public class RetryPolicy {
    private final int maxRetries;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final long maxBackoffMs;
    private final List<String> retryableErrorTypes;
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

    public int getMaxRetries() { return maxRetries; }
    public long getInitialBackoffMs() { return initialBackoffMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public long getMaxBackoffMs() { return maxBackoffMs; }
    public List<String> getRetryableErrorTypes() { return retryableErrorTypes; }
    public List<String> getNonRetryableErrorTypes() { return nonRetryableErrorTypes; }

    public boolean isRetryable(String errorType) {
        if (errorType == null) return false;
        if (nonRetryableErrorTypes.contains(errorType)) return false;
        if (retryableErrorTypes.isEmpty()) return true;
        return retryableErrorTypes.contains(errorType);
    }

    public long computeBackoffMs(int attempt) {
        if (attempt <= 0) return 0;
        double delay = initialBackoffMs * Math.pow(backoffMultiplier, attempt - 1);
        return Math.min((long) delay, maxBackoffMs);
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 500, 1.5, 30000,
            Collections.emptyList(), Collections.emptyList());
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0, 1.0, 0,
            Collections.emptyList(), Collections.emptyList());
    }

    public static RetryPolicy aggressiveRetry() {
        return new RetryPolicy(5, 200, 1.3, 10000,
            Collections.emptyList(), List.of("INVALID_INPUT", "PERMISSION_DENIED"));
    }

    @Override
    public String toString() {
        return String.format("RetryPolicy{maxRetries=%d, backoff=%.1f^%d, maxBackoff=%dms}",
            maxRetries, backoffMultiplier, maxRetries, maxBackoffMs);
    }

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
