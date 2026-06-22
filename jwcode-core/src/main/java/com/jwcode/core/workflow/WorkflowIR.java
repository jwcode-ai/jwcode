package com.jwcode.core.workflow;

import com.jwcode.core.workflow.ir.WorkflowNode;

public record WorkflowIR(
    String id,
    WorkflowNode root,
    WorkflowBudget budget,
    String schemaVersion
) {
    public WorkflowIR {
        if (root == null) {
            throw new IllegalArgumentException("WorkflowIR root is required");
        }
        budget = budget == null ? WorkflowBudget.defaults() : budget;
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "workflow-ir.v1" : schemaVersion;
    }
}
