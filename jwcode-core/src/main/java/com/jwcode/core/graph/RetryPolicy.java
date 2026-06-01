package com.jwcode.core.graph;

import java.time.Duration;

/**
 * Retry configuration for graph node execution.
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double backoffMultiplier;
    private final Duration maxDelay;

    public RetryPolicy(int maxAttempts, Duration initialDelay,
                        double backoffMultiplier, Duration maxDelay) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelay = maxDelay;
    }

    public int getMaxAttempts() { return maxAttempts; }
    public Duration getInitialDelay() { return initialDelay; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public Duration getMaxDelay() { return maxDelay; }

    /** Default: 3 attempts, 1s initial delay, 2x backoff, 30s max. */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));
    }

    /** No retry. */
    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, 1.0, Duration.ZERO);
    }
}
