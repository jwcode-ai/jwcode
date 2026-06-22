package com.jwcode.core.runtime;

import com.jwcode.core.workflow.WorkflowRun;

public record RuntimeResult(
    RuntimeMode mode,
    boolean success,
    String message,
    WorkflowRun workflowRun,
    String errorMessage
) {
    public static RuntimeResult chat(String message) {
        return new RuntimeResult(RuntimeMode.CHAT, true, message, null, null);
    }

    public static RuntimeResult workflow(WorkflowRun run) {
        return new RuntimeResult(RuntimeMode.WORKFLOW, true, null, run, null);
    }

    public static RuntimeResult error(RuntimeMode mode, String errorMessage) {
        return new RuntimeResult(mode, false, null, null, errorMessage);
    }
}
