package com.jwcode.core.workflow;

import java.time.Duration;

public record WorkflowBudget(
    long maxTokens,
    int maxAgentCalls,
    int maxToolCalls,
    Duration maxWallTime,
    int maxParallelism,
    int maxLoopIterations,
    TokenPolicy tokenPolicy
) {
    public static WorkflowBudget defaults() {
        return new WorkflowBudget(1_000_000L, 100, 200, Duration.ofHours(2), 8, 20, TokenPolicy.HARD_STOP);
    }

    public WorkflowBudget(long maxTokens, int maxAgentCalls, int maxToolCalls,
                          Duration maxWallTime, int maxParallelism, int maxLoopIterations) {
        this(maxTokens, maxAgentCalls, maxToolCalls, maxWallTime, maxParallelism, maxLoopIterations, TokenPolicy.HARD_STOP);
    }

    public WorkflowBudget {
        maxTokens = maxTokens <= 0 ? Long.MAX_VALUE : maxTokens;
        maxAgentCalls = maxAgentCalls <= 0 ? Integer.MAX_VALUE : maxAgentCalls;
        maxToolCalls = maxToolCalls <= 0 ? Integer.MAX_VALUE : maxToolCalls;
        maxWallTime = maxWallTime == null || maxWallTime.isNegative() || maxWallTime.isZero()
            ? Duration.ofDays(3650)
            : maxWallTime;
        maxParallelism = maxParallelism <= 0 ? Integer.MAX_VALUE : maxParallelism;
        maxLoopIterations = maxLoopIterations <= 0 ? Integer.MAX_VALUE : maxLoopIterations;
        tokenPolicy = tokenPolicy == null ? TokenPolicy.HARD_STOP : tokenPolicy;
    }
}
