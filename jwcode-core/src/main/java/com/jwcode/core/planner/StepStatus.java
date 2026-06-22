package com.jwcode.core.planner;

public enum StepStatus {
    PENDING,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED,
    SKIPPED,
    BLOCKED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED || this == BLOCKED;
    }

    public boolean isFailed() {
        return this == FAILED || this == BLOCKED;
    }

    public boolean isActive() {
        return this == RUNNING || this == RETRYING;
    }
}
