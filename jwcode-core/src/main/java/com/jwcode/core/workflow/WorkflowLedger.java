package com.jwcode.core.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkflowLedger {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final ObjectMapper STATE_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final String runId;
    private final Path runDirectory;
    private final Path eventsFile;
    private final WorkflowEventBus eventBus;
    private WorkflowShape workflowShape = new WorkflowShape(0, 0);
    private WorkflowBudget workflowBudget = WorkflowBudget.defaults();

    public WorkflowLedger(String runId, Path runDirectory) {
        this(runId, runDirectory, new WorkflowEventBus());
    }

    public WorkflowLedger(String runId, Path runDirectory, WorkflowEventBus eventBus) {
        this.runId = runId;
        this.runDirectory = runDirectory;
        this.eventsFile = runDirectory.resolve("events.jsonl");
        this.eventBus = eventBus == null ? new WorkflowEventBus() : eventBus;
        try {
            Files.createDirectories(runDirectory);
            if (!Files.exists(eventsFile)) {
                Files.createFile(eventsFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize workflow ledger: " + eventsFile, e);
        }
    }

    public synchronized WorkflowEvent append(String type, Map<String, Object> data) {
        long sequence = nextSequence();
        WorkflowState stateAfterEvent = replayState();
        stateAfterEvent.apply(new WorkflowEvent(null, runId, type, java.time.Instant.now(), sequence, data));
        WorkflowEvent event = WorkflowEvent.of(runId, type, sequence, data, stateAfterEvent, workflowShape, workflowBudget);
        appendEvent(event);
        return event;
    }

    public synchronized void appendEvent(WorkflowEvent event) {
        try {
            Files.writeString(
                eventsFile,
                MAPPER.writeValueAsString(event) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
            eventBus.publish(event);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append workflow event", e);
        }
    }

    public synchronized List<WorkflowEvent> replay() {
        try {
            List<WorkflowEvent> events = new ArrayList<>();
            for (String line : Files.readAllLines(eventsFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    events.add(MAPPER.readValue(line, WorkflowEvent.class));
                }
            }
            return events;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to replay workflow ledger", e);
        }
    }

    public WorkflowState replayState() {
        WorkflowState state = new WorkflowState(runId);
        for (WorkflowEvent event : replay()) {
            state.apply(event);
        }
        return state;
    }

    public boolean isCancelled() {
        return replayState().status() == WorkflowStatus.CANCELLED;
    }

    public boolean isPaused() {
        return replayState().status() == WorkflowStatus.PAUSED;
    }

    public boolean isTerminal() {
        WorkflowStatus status = replayState().status();
        return status == WorkflowStatus.COMPLETED
            || status == WorkflowStatus.FAILED
            || status == WorkflowStatus.CANCELLED
            || status == WorkflowStatus.FAILED_BUDGET;
    }

    public void saveState(WorkflowState state) {
        Path stateFile = runDirectory.resolve("state.json");
        try {
            STATE_MAPPER.writeValue(stateFile.toFile(), Map.of(
                "runId", state.runId(),
                "status", state.status().name(),
                "agentCalls", state.agentCalls(),
                "toolCalls", state.toolCalls(),
                "tokensUsed", state.tokensUsed(),
                "updatedAt", String.valueOf(state.updatedAt())));
            append("checkpoint.saved", Map.of("stateRef", "state.json"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize workflow state", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write workflow state", e);
        }
    }

    public WorkflowEventBus eventBus() {
        return eventBus;
    }

    public void setWorkflowProgressModel(WorkflowShape workflowShape, WorkflowBudget workflowBudget) {
        this.workflowShape = workflowShape == null ? new WorkflowShape(0, 0) : workflowShape;
        this.workflowBudget = workflowBudget == null ? WorkflowBudget.defaults() : workflowBudget;
    }

    public Path runDirectory() {
        return runDirectory;
    }

    private long nextSequence() {
        return replay().stream().mapToLong(WorkflowEvent::sequence).max().orElse(0L) + 1L;
    }
}
