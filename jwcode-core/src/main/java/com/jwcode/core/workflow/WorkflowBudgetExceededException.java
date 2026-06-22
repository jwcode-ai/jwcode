package com.jwcode.core.workflow;

public class WorkflowBudgetExceededException extends RuntimeException {
    private final WorkflowStatus status;

    public WorkflowBudgetExceededException(String message) {
        this(message, WorkflowStatus.FAILED_BUDGET);
    }

    public WorkflowBudgetExceededException(String message, WorkflowStatus status) {
        super(message);
        this.status = status == null ? WorkflowStatus.FAILED_BUDGET : status;
    }

    public WorkflowStatus status() {
        return status;
    }
}
