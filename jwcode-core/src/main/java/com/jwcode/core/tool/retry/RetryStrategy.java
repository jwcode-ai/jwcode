package com.jwcode.core.tool.retry;

import com.jwcode.core.tool.ErrorSummary;

import java.util.function.Predicate;

@FunctionalInterface
public interface RetryStrategy {
    boolean shouldRetry(int attempt, int maxRetries, ErrorSummary error);

    default long computeDelayMs(int attempt, RetryPolicy policy) {
        return policy.computeBackoffMs(attempt);
    }

    static RetryStrategy exponentialBackoff() {
        return (attempt, maxRetries, error) -> {
            if (attempt >= maxRetries) return false;
            if (error == null) return true;
            return error.isRetryable();
        };
    }

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

    static RetryStrategy immediateFor(Predicate<ErrorSummary> predicate) {
        return (attempt, maxRetries, error) -> {
            if (attempt >= maxRetries) return false;
            if (error == null) return true;
            return error.isRetryable() && predicate.test(error);
        };
    }

    static RetryStrategy noRetry() {
        return (attempt, maxRetries, error) -> false;
    }

    static RetryStrategy adaptive() {
        return (attempt, maxRetries, error) -> {
            if (attempt >= maxRetries) return false;
            if (error == null) return true;
            if (!error.isRetryable()) return false;
            String type = error.getErrorType();
            if (type == null) return attempt < maxRetries;
            if (type.contains("TIMEOUT")) return attempt < 2;
            if (type.contains("RESOURCE") || type.contains("RATE_LIMIT")) return attempt < 3;
            return attempt < maxRetries;
        };
    }
}
