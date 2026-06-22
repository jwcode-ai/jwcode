package com.jwcode.core.hands;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolRequest(
    String toolName,
    JsonNode input
) {
}
