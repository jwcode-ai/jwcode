package com.jwcode.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class ContextDumpTool {

    private static final Logger LOG = Logger.getLogger(ContextDumpTool.class.getName());
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"), ".jwcode", "test-runs");
    private final ObjectMapper mapper;

    public ContextDumpTool() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public String dumpRun(String runId) throws IOException {
        Path runDir = BASE_DIR.resolve(runId);
        if (!Files.exists(runDir)) return "Run not found: " + runId;
        StringBuilder sb = new StringBuilder();
        sb.append(repeat(72, "=")).append("\n");
        sb.append("  JWCode Context Dump - ").append(runId).append("\n");
        sb.append(repeat(72, "=")).append("\n\n");
        dumpMetadata(runDir, sb);
        dumpTimeline(runDir, sb);
        dumpUsageSummary(runDir, sb);
        sb.append(repeat(72, "=")).append("\n  End of dump\n").append(repeat(72, "=")).append("\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void dumpMetadata(Path runDir, StringBuilder sb) throws IOException {
        Path metaFile = runDir.resolve("run-metadata.json");
        if (!Files.exists(metaFile)) return;
        Map<String, Object> meta = mapper.readValue(Files.readString(metaFile, StandardCharsets.UTF_8), new TypeReference<>() {});
        sb.append("--- METADATA ---\n");
        sb.append("  scenario: ").append(meta.getOrDefault("scenarioId", "?")).append("\n");
        sb.append("  status:   ").append(meta.getOrDefault("status", "?")).append("\n");
        sb.append("  events:   ").append(meta.getOrDefault("eventCount", 0)).append("\n\n");
    }

    @SuppressWarnings("unchecked")
    private void dumpTimeline(Path runDir, StringBuilder sb) throws IOException {
        Path traceFile = runDir.resolve("trace.jsonl");
        if (!Files.exists(traceFile)) return;
        sb.append("--- EVENT TIMELINE ---\n");
        try (BufferedReader r = Files.newBufferedReader(traceFile, StandardCharsets.UTF_8)) {
            String line; int n = 0;
            while ((line = r.readLine()) != null && n < 200) {
                n++;
                Map<String, Object> e = mapper.readValue(line, new TypeReference<>() {});
                String type = (String) e.getOrDefault("eventType", "?");
                Object turn = e.getOrDefault("turnId", "?");
                Map<String, Object> inner = (Map<String, Object>) e.get("event");
                String detail = "";
                if (inner != null) {
                    String t = type;
                    if ("StepStart".equals(t)) detail = "[" + inner.getOrDefault("stepName", "?") + "]";
                    else if ("ToolCall".equals(t)) detail = "" + inner.getOrDefault("toolName", "?");
                    else if ("ToolResult".equals(t)) detail = inner.getOrDefault("toolName", "?") + " => " + (Boolean.TRUE.equals(inner.get("success")) ? "OK" : "FAIL");
                    else if ("TokenUsage".equals(t)) detail = "tokens=" + inner.getOrDefault("totalTokens", 0) + " cache=" + fmtPct((Number) inner.getOrDefault("cacheHitRate", 0.0));
                    else if ("Error".equals(t)) detail = "[" + inner.getOrDefault("source", "?") + "] " + inner.getOrDefault("message", "");
                    else if ("Checkpoint".equals(t)) detail = String.valueOf(inner.getOrDefault("summary", ""));
                    else if ("ContextCompressed".equals(t)) detail = "saved " + inner.getOrDefault("estimatedTokensSaved", 0) + " tokens";
                }
                sb.append(String.format("  [T%3s] %-20s %s\n", turn, type, detail));
            }
        }
        sb.append("\n");
    }

    @SuppressWarnings("unchecked")
    private void dumpUsageSummary(Path runDir, StringBuilder sb) throws IOException {
        Path traceFile = runDir.resolve("trace.jsonl");
        if (!Files.exists(traceFile)) return;
        int tp = 0, tc = 0, tcc = 0, tcr = 0, llm = 0, tools = 0, errs = 0;
        try (BufferedReader r = Files.newBufferedReader(traceFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                Map<String, Object> e = mapper.readValue(line, new TypeReference<>() {});
                String type = (String) e.getOrDefault("eventType", "");
                Map<String, Object> inner = (Map<String, Object>) e.get("event");
                if (inner == null) continue;
                if ("TokenUsage".equals(type)) { llm++; tp += i(inner, "promptTokens"); tc += i(inner, "completionTokens"); tcc += i(inner, "cacheCreationInputTokens"); tcr += i(inner, "cacheReadInputTokens"); }
                else if ("ToolCall".equals(type)) tools++;
                else if ("Error".equals(type)) errs++;
            }
        }
        int cacheTotal = tcr + tcc + tp;
        double hitRate = cacheTotal > 0 ? (double) tcr / cacheTotal : 0;
        sb.append("--- USAGE SUMMARY ---\n");
        sb.append(String.format("  LLM calls: %d  Tool calls: %d  Errors: %d\n", llm, tools, errs));
        sb.append(String.format("  Total tokens: %d\n", tp + tc));
        sb.append(String.format("  Cache creation: %d  read: %d\n", tcc, tcr));
        sb.append(String.format("  Cache hit rate: %.1f%%\n", hitRate * 100));
        String health = llm == 0 ? "N/A" : hitRate < 0.5 ? "POOR" : hitRate < 0.7 ? "FAIR" : hitRate < 0.85 ? "GOOD" : "EXCELLENT";
        sb.append(String.format("  Health: %s\n\n", health));
    }

    private static int i(Map<String, Object> m, String key) { Object v = m.get(key); return v instanceof Number n ? n.intValue() : 0; }
    private static String fmtPct(Number n) { return String.format("%.0f%%", n.doubleValue() * 100); }
    private static String repeat(int n, String s) { return String.join("", Collections.nCopies(n, s)); }
}
