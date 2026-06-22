package com.jwcode.core.tool.retry;

import com.jwcode.core.tool.ErrorSummary;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

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

    public <T> T executeWithRetry(Supplier<T> operation,
                                  RetryPolicy policy,
                                  RetryStrategy strategy) throws RetryExhaustedException {
        int attempt = 0;
        while (true) {
            try {
                T result = operation.get();
                if (attempt > 0) {
                    logger.info("[RetryOrchestrator] retry succeeded (attempt=" + attempt + ")");
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
                sleep(strategy.computeDelayMs(attempt, policy), attempt, error);
            }
        }
    }

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
            int nextAttempt = attempt + 1;
            ErrorSummary error = ErrorSummary.toolAgentFailure(
                throwable.getMessage(),
                policy.isRetryable(classifyError(throwable)),
                nextAttempt,
                policy.getMaxRetries()
            );
            if (!strategy.shouldRetry(nextAttempt, policy.getMaxRetries(), error)) {
                result.completeExceptionally(new RetryExhaustedException(nextAttempt, error, throwable));
                return;
            }
            scheduler.schedule(
                () -> executeRetryAsync(operation, policy, strategy, result, nextAttempt),
                strategy.computeDelayMs(nextAttempt, policy),
                TimeUnit.MILLISECONDS);
        });
    }

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

    private static void sleep(long delayMs, int attempt, ErrorSummary error) throws RetryExhaustedException {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryExhaustedException(attempt, error, e);
        }
    }

    private static String classifyError(Throwable throwable) {
        String message = throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase();
        if (message.contains("timeout")) return "TIMEOUT";
        if (message.contains("permission") || message.contains("denied")) return "PERMISSION_DENIED";
        if (message.contains("not found")) return "NOT_FOUND";
        if (message.contains("rate") || message.contains("429")) return "RATE_LIMIT";
        return "UNKNOWN";
    }

    public static class RetryExhaustedException extends Exception {
        private final int attemptCount;
        private final ErrorSummary errorSummary;

        public RetryExhaustedException(int attemptCount, ErrorSummary errorSummary, Throwable cause) {
            super(errorSummary != null ? errorSummary.getMessage() : "Retry exhausted", cause);
            this.attemptCount = attemptCount;
            this.errorSummary = errorSummary;
        }

        public int getAttemptCount() { return attemptCount; }
        public ErrorSummary getErrorSummary() { return errorSummary; }
    }
}
