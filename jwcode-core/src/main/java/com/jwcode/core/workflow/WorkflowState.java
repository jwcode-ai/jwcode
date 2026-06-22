package com.jwcode.core.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class WorkflowState {
    private final String runId;
    private WorkflowStatus status = WorkflowStatus.CREATED;
    private Instant startedAt;
    private Instant updatedAt;
    private final Map<String, CompletedEffect> completedEffects = new LinkedHashMap<>();
    private final Map<String, String> scheduledEffects = new LinkedHashMap<>();
    private int agentCalls;
    private int toolCalls;
    private int completedPhases;
    private long tokensUsed;
    private JsonNode lastOutput = JsonNodeFactory.instance.nullNode();

    public WorkflowState(String runId) {
        this.runId = runId;
    }

    public void apply(WorkflowEvent event) {
        updatedAt = event.timestamp();
        switch (event.type()) {
            case "run.started", "run.resumed" -> {
                status = WorkflowStatus.RUNNING;
                if (startedAt == null) {
                    startedAt = event.timestamp();
                }
            }
            case "run.paused" -> status = WorkflowStatus.PAUSED;
            case "run.finished" -> status = WorkflowStatus.COMPLETED;
            case "run.failed" -> status = WorkflowStatus.FAILED;
            case "run.cancelled" -> status = WorkflowStatus.CANCELLED;
            case "budget.exceeded" -> status = WorkflowStatus.FAILED_BUDGET;
            case "phase.completed" -> completedPhases++;
            case "effect.scheduled" -> {
                String effectId = stringData(event, "effectId");
                if (effectId != null) {
                    scheduledEffects.put(effectId, stringData(event, "nodeId"));
                }
            }
            case "effect.completed" -> {
                String effectId = stringData(event, "effectId");
                String artifactRef = stringData(event, "artifactRef");
                String kind = stringData(event, "kind");
                if (effectId != null) {
                    completedEffects.put(effectId, new CompletedEffect(effectId, artifactRef, kind));
                    if ("agent".equals(kind)) {
                        agentCalls++;
                    } else if ("tool".equals(kind)) {
                        toolCalls++;
                    }
                }
                Object tokens = event.data().get("tokens");
                if (tokens instanceof Number n) {
                    tokensUsed += n.longValue();
                }
            }
            default -> {
                // Projection ignores events that do not affect resumability.
            }
        }
    }

    private static String stringData(WorkflowEvent event, String key) {
        Object value = event.data().get(key);
        return value == null ? null : value.toString();
    }

    public Optional<CompletedEffect> completedEffect(String effectId) {
        return Optional.ofNullable(completedEffects.get(effectId));
    }

    public String runId() {
        return runId;
    }

    public WorkflowStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public int agentCalls() {
        return agentCalls;
    }

    public int toolCalls() {
        return toolCalls;
    }

    public long tokensUsed() {
        return tokensUsed;
    }

    public int completedEffectsCount() {
        return completedEffects.size();
    }

    public int completedPhasesCount() {
        return completedPhases;
    }

    public JsonNode lastOutput() {
        return lastOutput;
    }

    public void lastOutput(JsonNode lastOutput) {
        this.lastOutput = lastOutput == null ? JsonNodeFactory.instance.nullNode() : lastOutput;
    }

    public record CompletedEffect(String effectId, String artifactRef, String kind) {
    }
}
