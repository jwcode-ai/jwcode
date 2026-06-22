package com.jwcode.core.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WorkflowEvent(
    String eventId,
    String runId,
    String type,
    Instant timestamp,
    long sequence,
    Map<String, Object> data,
    String phaseId,
    String effectId,
    int completedEffects,
    int totalEffects,
    int completedPhases,
    int totalPhases,
    long tokensUsed,
    long tokensRemaining
) {
    public WorkflowEvent(String eventId, String runId, String type, Instant timestamp, long sequence, Map<String, Object> data) {
        this(eventId, runId, type, timestamp, sequence, data,
            stringData(data, "phaseId"),
            stringData(data, "effectId"),
            intData(data, "completedEffects"),
            intData(data, "totalEffects"),
            intData(data, "completedPhases"),
            intData(data, "totalPhases"),
            longData(data, "tokensUsed"),
            longData(data, "tokensRemaining"));
    }

    public WorkflowEvent {
        eventId = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId;
        timestamp = timestamp == null ? Instant.now() : timestamp;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static WorkflowEvent of(String runId, String type, long sequence, Map<String, Object> data) {
        return new WorkflowEvent(UUID.randomUUID().toString(), runId, type, Instant.now(), sequence, data);
    }

    public static WorkflowEvent of(String runId, String type, long sequence, Map<String, Object> data,
                                   WorkflowState state, WorkflowShape shape, WorkflowBudget budget) {
        int completedEffects = state == null ? 0 : state.completedEffectsCount();
        int completedPhases = state == null ? 0 : state.completedPhasesCount();
        int totalEffects = shape == null ? 0 : shape.totalEffects();
        int totalPhases = shape == null ? 0 : shape.totalPhases();
        long tokensUsed = state == null ? 0 : state.tokensUsed();
        long maxTokens = budget == null ? 0 : budget.maxTokens();
        long tokensRemaining = maxTokens == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, maxTokens - tokensUsed);
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            runId,
            type,
            Instant.now(),
            sequence,
            data,
            stringData(data, "phaseId"),
            stringData(data, "effectId"),
            completedEffects,
            totalEffects,
            completedPhases,
            totalPhases,
            tokensUsed,
            tokensRemaining);
    }

    private static String stringData(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value == null ? null : value.toString();
    }

    private static int intData(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static long longData(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
