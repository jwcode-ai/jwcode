package com.jwcode.core.hands;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AgentRequest(
    String role,
    String prompt,
    List<String> tools,
    JsonNode schema,
    JsonNode input
) {
    public AgentRequest {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
