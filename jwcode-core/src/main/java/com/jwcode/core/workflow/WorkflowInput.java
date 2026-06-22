package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Map;

public record WorkflowInput(
    String sessionId,
    JsonNode payload,
    Map<String, Object> metadata
) {
    public WorkflowInput {
        payload = payload == null ? JsonNodeFactory.instance.objectNode() : payload;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static WorkflowInput of(String sessionId, JsonNode payload) {
        return new WorkflowInput(sessionId, payload, Map.of());
    }

    public String projectId() {
        Object value = metadata.get("projectId");
        return value == null ? null : value.toString();
    }

    public boolean memoryEnabled() {
        Object value = metadata.get("memoryEnabled");
        return value == null || Boolean.parseBoolean(value.toString());
    }

    public String checkpointPolicy() {
        Object value = metadata.get("checkpointPolicy");
        return value == null ? "phase" : value.toString();
    }

    public boolean forceResume() {
        Object value = metadata.get("forceResume");
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
