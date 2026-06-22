package com.jwcode.core.hands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public record ToolEffectResult(
    String toolName,
    boolean success,
    JsonNode output,
    String errorMessage,
    long durationMs
) {
    public ToolEffectResult {
        output = output == null ? JsonNodeFactory.instance.nullNode() : output;
    }

    public static ToolEffectResult success(String toolName, JsonNode output, long durationMs) {
        return new ToolEffectResult(toolName, true, output, null, durationMs);
    }

    public static ToolEffectResult failure(String toolName, String errorMessage, long durationMs) {
        return new ToolEffectResult(toolName, false, null, errorMessage, durationMs);
    }
}
