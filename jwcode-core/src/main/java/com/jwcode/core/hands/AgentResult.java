package com.jwcode.core.hands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public record AgentResult(
    String role,
    boolean success,
    String content,
    JsonNode structuredOutput,
    long tokenUsage,
    long durationMs,
    String errorMessage
) {
    public AgentResult {
        structuredOutput = structuredOutput == null ? JsonNodeFactory.instance.nullNode() : structuredOutput;
    }

    public static AgentResult success(String role, String content, JsonNode structuredOutput, long tokenUsage, long durationMs) {
        return new AgentResult(role, true, content, structuredOutput, tokenUsage, durationMs, null);
    }

    public static AgentResult failure(String role, String errorMessage, long durationMs) {
        return new AgentResult(role, false, null, null, 0, durationMs, errorMessage);
    }
}
