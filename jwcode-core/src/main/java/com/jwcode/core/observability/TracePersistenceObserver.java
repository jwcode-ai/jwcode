package com.jwcode.core.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * TracePersistenceObserver — persists ObservationPipeline events as structured JSONL trace files.
 *
 * <p>Each test run gets a directory under .jwcode/test-runs/<run-id>/. Events are written
 * line-by-line as JSON objects to trace.jsonl. A run-metadata.json summary is written on close.</p>
 *
 * <p>Thread-safe: uses per-run locks for concurrent writes within a single run.</p>
 */
public class TracePersistenceObserver implements ObservationPipeline.Observer {

    private static final Logger LOG = Logger.getLogger(TracePersistenceObserver.class.getName());
    private static final DateTimeFormatter RUN_ID_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path baseDir;
    private final ObjectMapper mapper;
    private final Map<String, RunContext> activeRuns;

    public TracePersistenceObserver() {
        this(Paths.get(System.getProperty("user.dir"), ".jwcode", "test-runs"));
    }

    public TracePersistenceObserver(Path baseDir) {
        this.baseDir = baseDir;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.activeRuns = new ConcurrentHashMap<>();
    }

    /**
     * Start a new trace run and return its run-id.
     */
    public String startRun(String scenarioId, String description) {
        String runId = scenarioId + "-" + LocalDateTime.now().format(RUN_ID_FMT);
        try {
            Path runDir = baseDir.resolve(runId);
            Files.createDirectories(runDir);
            RunContext ctx = new RunContext(runDir, runId, scenarioId, description);
            activeRuns.put(runId, ctx);

            // Write initial metadata
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("runId", runId);
            meta.put("scenarioId", scenarioId);
            meta.put("description", description);
            meta.put("startedAt", Instant.now().toString());
            meta.put("status", "running");
            writeJsonFile(runDir.resolve("run-metadata.json"), meta);

            LOG.info("Trace run started: " + runId);
        } catch (IOException e) {
            LOG.severe("Failed to start trace run: " + e.getMessage());
        }
        return runId;
    }

    /**
     * End a trace run with final status.
     */
    public void endRun(String runId, boolean passed, String summary, Map<String, Object> extraMeta) {
        RunContext ctx = activeRuns.remove(runId);
        if (ctx == null) {
            LOG.warning("Unknown run id: " + runId);
            return;
        }
        synchronized (ctx.lock) {
            try {
                ctx.writer.flush();
                ctx.writer.close();
                ctx.promptWriter.flush();
                ctx.promptWriter.close();
            } catch (IOException e) {
                LOG.warning("Error closing trace writer: " + e.getMessage());
            }

            // Write final metadata
            try {
                Map<String, Object> meta = readJsonFile(ctx.runDir.resolve("run-metadata.json"));
                if (meta == null) meta = new java.util.LinkedHashMap<>();
                meta.put("status", passed ? "passed" : "failed");
                meta.put("completedAt", Instant.now().toString());
                meta.put("summary", summary);
                meta.put("eventCount", ctx.eventCount);
                if (extraMeta != null) meta.putAll(extraMeta);
                writeJsonFile(ctx.runDir.resolve("run-metadata.json"), meta);
            } catch (Exception e) {
                LOG.warning("Error writing run metadata: " + e.getMessage());
            }

            LOG.info("Trace run ended: " + runId + " (" + (passed ? "PASS" : "FAIL") + ")");
        }
    }

    @Override
    public void onEvent(ObservationEvent event) {
        for (RunContext ctx : activeRuns.values()) {
            synchronized (ctx.lock) {
                try {
                    // Turn tracking: Model calls increment turn
                    if (event instanceof ObservationEvent.TokenUsage) {
                        ctx.turnId++;
                    }

                    // Store full prompt on StepPrompt events
                    if (event instanceof ObservationEvent.StepPrompt sp) {
                        Map<String, Object> promptRecord = new java.util.LinkedHashMap<>();
                        promptRecord.put("turnId", ctx.turnId);
                        promptRecord.put("taskId", sp.taskId());
                        promptRecord.put("stepIndex", sp.stepIndex());
                        promptRecord.put("timestamp", event.timestamp().toString());
                        promptRecord.put("agentType", sp.agentType());
                        promptRecord.put("description", sp.description());
                        promptRecord.put("prompt", sp.stepPrompt());
                        ctx.promptWriter.write(mapper.writeValueAsString(promptRecord));
                        ctx.promptWriter.newLine();
                        ctx.promptWriter.flush();
                    }

                    // Tool call loop detection
                    if (event instanceof ObservationEvent.ToolCall tc) {
                        if (tc.toolName().equals(ctx.lastToolName)) {
                            ctx.sameToolConsecutiveCount++;
                            if (ctx.sameToolConsecutiveCount >= 5) {
                                LOG.warning("[LOOP_DETECT] Tool '" + tc.toolName()
                                    + "' called " + ctx.sameToolConsecutiveCount
                                    + " consecutive times in run " + ctx.runId);
                            }
                        } else {
                            ctx.lastToolName = tc.toolName();
                            ctx.sameToolConsecutiveCount = 1;
                        }
                    }

                    // Main trace event
                    Map<String, Object> record = new java.util.LinkedHashMap<>();
                    record.put("runId", ctx.runId);
                    record.put("scenarioId", ctx.scenarioId);
                    record.put("turnId", ctx.turnId);
                    record.put("timestamp", event.timestamp().toString());
                    record.put("eventType", event.getClass().getSimpleName());
                    record.put("event", eventToMap(event));
                    String line = mapper.writeValueAsString(record);
                    ctx.writer.write(line);
                    ctx.writer.newLine();
                    ctx.eventCount++;
                } catch (IOException e) {
                    LOG.warning("Error writing trace event: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public String getObserverName() {
        return "TracePersistenceObserver";
    }

    /**
     * Returns the number of active trace runs.
     */
    public int getActiveRunCount() {
        return activeRuns.size();
    }

    /**
     * List all historical run directories.
     */
    public java.util.List<Path> listHistoricalRuns() throws IOException {
        if (!Files.exists(baseDir)) return java.util.Collections.emptyList();
        return Files.list(baseDir)
            .filter(Files::isDirectory)
            .sorted()
            .toList();
    }

    // ---- private helpers ----

    private Map<String, Object> eventToMap(ObservationEvent event) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        // Dispatch by sealed type
        if (event instanceof ObservationEvent.StepStart e) {
            map.put("stepName", e.stepName());
            map.put("description", e.description());
        } else if (event instanceof ObservationEvent.Thinking e) {
            map.put("stepName", e.stepName());
            map.put("content", truncate(e.content()));
        } else if (event instanceof ObservationEvent.ToolCall e) {
            map.put("toolName", e.toolName());
            map.put("toolCallId", e.toolCallId());
            map.put("arguments", truncate(e.arguments()));
        } else if (event instanceof ObservationEvent.ToolResult e) {
            map.put("toolName", e.toolName());
            map.put("toolCallId", e.toolCallId());
            map.put("success", e.success());
            map.put("elapsed", e.elapsed().toMillis());
            map.put("result", truncate(e.result()));
        } else if (event instanceof ObservationEvent.TokenUsage e) {
            map.put("promptTokens", e.promptTokens());
            map.put("completionTokens", e.completionTokens());
            map.put("totalTokens", e.totalTokens());
            map.put("model", e.model());
            map.put("cacheCreationInputTokens", e.cacheCreationInputTokens());
            map.put("cacheReadInputTokens", e.cacheReadInputTokens());
            map.put("cacheHitRate", e.cacheHitRate());
        } else if (event instanceof ObservationEvent.Error e) {
            map.put("source", e.source());
            map.put("message", e.message());
            map.put("recoveryHint", e.recoveryHint());
        } else if (event instanceof ObservationEvent.Checkpoint e) {
            map.put("summary", e.summary());
            map.put("detail", e.detail());
        } else if (event instanceof ObservationEvent.StepComplete e) {
            map.put("stepName", e.stepName());
            map.put("result", e.result());
        } else if (event instanceof ObservationEvent.TaskStateChanged e) {
            map.put("taskId", e.taskId());
            map.put("taskDescription", e.taskDescription());
            map.put("oldStatus", e.oldStatus() != null ? e.oldStatus().toString() : null);
            map.put("newStatus", e.newStatus() != null ? e.newStatus().toString() : null);
            map.put("reason", e.reason());
        } else if (event instanceof ObservationEvent.TaskPlanUpdated e) {
            map.put("taskId", e.taskId());
            map.put("totalSteps", e.totalSteps());
            map.put("completedSteps", e.completedSteps());
            map.put("currentStepDescription", e.currentStepDescription());
        } else if (event instanceof ObservationEvent.ContextCompressed e) {
            map.put("originalCount", e.originalCount());
            map.put("compressedCount", e.compressedCount());
            map.put("estimatedTokensSaved", e.estimatedTokensSaved());
            map.put("summary", e.summary());
        } else if (event instanceof ObservationEvent.ContentChunk e) {
            map.put("chunk", truncate(e.chunk()));
        } else if (event instanceof ObservationEvent.ThinkingChunk e) {
            map.put("chunk", truncate(e.chunk()));
        } else if (event instanceof ObservationEvent.WaitingForInput e) {
            map.put("taskId", e.taskId());
            map.put("question", e.question());
        } else if (event instanceof ObservationEvent.StepPrompt e) {
            map.put("taskId", e.taskId());
            map.put("stepIndex", e.stepIndex());
            map.put("description", e.description());
            map.put("agentType", e.agentType());
        }
        return map;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        if (s.length() <= 500) return s;
        return s.substring(0, 500) + "...";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonFile(Path path) {
        if (!Files.exists(path)) return null;
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return mapper.readValue(content, Map.class);
        } catch (IOException e) {
            return null;
        }
    }

    private void writeJsonFile(Path path, Map<String, Object> data) throws IOException {
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    // ---- inner types ----

    private static class RunContext {
        final Path runDir;
        final String runId;
        final String scenarioId;
        final String description;
        final ReentrantLock lock;
        final BufferedWriter writer;
        final BufferedWriter promptWriter;
        volatile int eventCount;
        volatile int turnId;
        volatile String lastToolName;
        volatile int sameToolConsecutiveCount;

        RunContext(Path runDir, String runId, String scenarioId, String description) throws IOException {
            this.runDir = runDir;
            this.runId = runId;
            this.scenarioId = scenarioId;
            this.description = description;
            this.lock = new ReentrantLock();
            Path promptsDir = runDir.resolve("prompts");
            Files.createDirectories(promptsDir);
            this.writer = Files.newBufferedWriter(
                runDir.resolve("trace.jsonl"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            this.promptWriter = Files.newBufferedWriter(
                promptsDir.resolve("prompts.jsonl"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            this.eventCount = 0;
            this.turnId = 0;
            this.lastToolName = null;
            this.sameToolConsecutiveCount = 0;
        }
    }
}

