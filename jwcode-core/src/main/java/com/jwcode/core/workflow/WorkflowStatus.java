package com.jwcode.core.workflow;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    FAILED_BUDGET,
    PAUSED_BUDGET
}
