package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowResult(
    String runId,
    WorkflowStatus status,
    JsonNode output,
    String errorMessage
) {
    public static WorkflowResult success(String runId, JsonNode output) {
        return new WorkflowResult(runId, WorkflowStatus.COMPLETED, output, null);
    }

    public static WorkflowResult failed(String runId, WorkflowStatus status, String errorMessage) {
        return new WorkflowResult(runId, status, null, errorMessage);
    }
}
