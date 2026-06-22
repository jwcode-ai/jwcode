package com.jwcode.core.workflow.ir;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AgentNode(
    String id,
    String role,
    String prompt,
    List<String> tools,
    JsonNode schema,
    int maxRetries,
    long timeoutMs
) implements WorkflowNode {
    public AgentNode {
        tools = tools == null ? List.of() : List.copyOf(tools);
        maxRetries = Math.max(0, maxRetries);
        timeoutMs = Math.max(0, timeoutMs);
    }
}
