package com.jwcode.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * RunHistoryStore — CRUD for persisted test runs under .jwcode/test-runs/.
 *
 * <p>Each run is a directory containing run-metadata.json and optionally trace.jsonl.
 * This store provides list, summary, and cleanup operations for historical runs.</p>
 */
public class RunHistoryStore {

    private static final Logger LOG = Logger.getLogger(RunHistoryStore.class.getName());
    private static final int MAX_RUNS_TO_KEEP = 30;
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path baseDir;
    private final ObjectMapper mapper;

    public RunHistoryStore() {
        this(Paths.get(System.getProperty("user.dir"), ".jwcode", "test-runs"));
    }

    public RunHistoryStore(Path baseDir) {
        this.baseDir = baseDir;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /**
     * Ensure the base directory exists.
     */
    public void init() throws IOException {
        Files.createDirectories(baseDir);
    }

    /**
     * List all run directories, sorted by name (which includes timestamp).
     */
    public List<Path> listRuns() throws IOException {
        if (!Files.exists(baseDir)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(baseDir)) {
            return stream
                .filter(Files::isDirectory)
                .sorted(Comparator.reverseOrder())
                .toList();
        }
    }

    /**
     * Read metadata for a specific run.
     */
    public Optional<Map<String, Object>> readMetadata(String runId) {
        Path metaFile = baseDir.resolve(runId).resolve("run-metadata.json");
        if (!Files.exists(metaFile)) return Optional.empty();
        try {
            String content = Files.readString(metaFile, StandardCharsets.UTF_8);
            Map<String, Object> meta = mapper.readValue(content,
                new TypeReference<Map<String, Object>>() {});
            return Optional.of(meta);
        } catch (IOException e) {
            LOG.warning("Failed to read metadata for run " + runId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * List all runs with their metadata, newest first.
     */
    public List<RunSummary> listRunSummaries() throws IOException {
        List<RunSummary> summaries = new ArrayList<>();
        for (Path runDir : listRuns()) {
            String runId = runDir.getFileName().toString();
            Optional<Map<String, Object>> metaOpt = readMetadata(runId);
            if (metaOpt.isPresent()) {
                Map<String, Object> meta = metaOpt.get();
                RunSummary summary = new RunSummary(
                    runId,
                    (String) meta.getOrDefault("scenarioId", "unknown"),
                    (String) meta.getOrDefault("description", ""),
                    (String) meta.getOrDefault("status", "unknown"),
                    (String) meta.getOrDefault("summary", ""),
                    (String) meta.getOrDefault("startedAt", null),
                    (String) meta.getOrDefault("completedAt", null),
                    meta.containsKey("eventCount") ? ((Number) meta.get("eventCount")).intValue() : 0
                );
                summaries.add(summary);
            }
        }
        return summaries;
    }

    /**
     * Count runs matching a status filter.
     */
    public RunCounts getRunCounts() throws IOException {
        int total = 0, passed = 0, failed = 0;
        for (Path runDir : listRuns()) {
            String runId = runDir.getFileName().toString();
            Optional<Map<String, Object>> metaOpt = readMetadata(runId);
            if (metaOpt.isPresent()) {
                total++;
                String status = (String) metaOpt.get().get("status");
                if ("passed".equals(status)) passed++;
                else if ("failed".equals(status)) failed++;
            }
        }
        return new RunCounts(total, passed, failed);
    }

    /**
     * Evict old runs, keeping at most {@value #MAX_RUNS_TO_KEEP}.
     */
    public int evictOldRuns() throws IOException {
        List<Path> runs = listRuns();
        if (runs.size() <= MAX_RUNS_TO_KEEP) return 0;

        int evicted = 0;
        // Keep newest MAX_RUNS_TO_KEEP, evict the rest (oldest first since listRuns returns newest first)
        for (int i = MAX_RUNS_TO_KEEP; i < runs.size(); i++) {
            Path runDir = runs.get(i);
            try {
                deleteDirectory(runDir);
                evicted++;
                LOG.info("Evicted old run: " + runDir.getFileName());
            } catch (IOException e) {
                LOG.warning("Failed to evict run " + runDir.getFileName() + ": " + e.getMessage());
            }
        }
        return evicted;
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
            }
        }
    }

    // ---- types ----

    public record RunSummary(
        String runId,
        String scenarioId,
        String description,
        String status,
        String summary,
        String startedAt,
        String completedAt,
        int eventCount
    ) {
        public String statusIcon() {
            return switch (status) {
                case "passed" -> "+";
                case "failed" -> "-";
                case "running" -> "~";
                default -> "?";
            };
        }

        @Override
        public String toString() {
            return String.format("%s %-45s %-12s %s",
                statusIcon(), runId, status.toUpperCase(), description);
        }
    }

    public record RunCounts(int total, int passed, int failed) {
        public double passRate() {
            return total > 0 ? (double) passed / total * 100.0 : 0.0;
        }
    }
}

