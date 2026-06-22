package com.jwcode.core.workflow.ir;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolNode(
    String id,
    String toolName,
    JsonNode input,
    int maxRetries,
    long timeoutMs
) implements WorkflowNode {
    public ToolNode {
        maxRetries = Math.max(0, maxRetries);
        timeoutMs = Math.max(0, timeoutMs);
    }
}
