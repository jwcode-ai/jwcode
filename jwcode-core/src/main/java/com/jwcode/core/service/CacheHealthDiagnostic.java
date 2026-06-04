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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * CacheHealthDiagnostic - analyzes cache hit rate patterns in a trace run.
 *
 * <p>Detects the anomaly patterns from the article:
 * <ul>
 *   <li>Always 0%: cache_control not enabled or breakpoint position wrong</li>
 *   <li>Sudden drop to 0 after working: TTL expiry, system prompt changed, or tool set changed</li>
 *   <li>Fluctuating: possible competition for cache prefix (rare)</li>
 *   <li>High creation + high read: breakpoint position too early, missing growth portion</li>
 * </ul>
 *
 * <p>Usage: {@code diagnostic.analyze(runId)} returns a human-readable diagnostic report.</p>
 */
public class CacheHealthDiagnostic {

    private static final Logger LOG = Logger.getLogger(CacheHealthDiagnostic.class.getName());
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"), ".jwcode", "test-runs");
    private final ObjectMapper mapper;

    public CacheHealthDiagnostic() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public record CacheTurn(int turnId, int promptTokens, int completionTokens,
                             int cacheCreation, int cacheRead, double hitRate) {}

    public record CacheDiagnosis(List<CacheTurn> turns, String pattern, String recommendation,
                                  double avgHitRate, boolean healthy) {}

    /**
     * Analyze a run and return a diagnostic report as formatted text.
     */
    public String analyze(String runId) throws IOException {
        Path traceFile = BASE_DIR.resolve(runId).resolve("trace.jsonl");
        if (!Files.exists(traceFile)) return "Run not found: " + runId;

        CacheDiagnosis d = runDiagnosis(traceFile);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Cache Health Diagnostic: ").append(runId).append(" ===\n\n");
        sb.append(String.format("  Avg hit rate: %.1f%%\n", d.avgHitRate * 100));
        sb.append(String.format("  Healthy: %s\n", d.healthy));
        sb.append(String.format("  Pattern: %s\n", d.pattern));
        sb.append(String.format("  Recommendation: %s\n", d.recommendation));

        sb.append("\n  Per-turn cache data:\n");
        sb.append(String.format("  %-6s %-10s %-12s %-12s %-10s\n", "Turn", "HitRate", "Prompt", "CacheCreate", "CacheRead"));
        for (CacheTurn t : d.turns) {
            sb.append(String.format("  %-6d %-10s %-12d %-12d %-10d\n",
                t.turnId, String.format("%.0f%%", t.hitRate * 100),
                t.promptTokens, t.cacheCreation, t.cacheRead));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private CacheDiagnosis runDiagnosis(Path traceFile) throws IOException {
        List<CacheTurn> turns = new ArrayList<>();
        double sumHitRate = 0;
        int turnIdx = 0;
        boolean hadHighHitRate = false;
        boolean hadDrop = false;
        boolean fluctuating = false;
        double prevHitRate = -1;

        try (BufferedReader r = Files.newBufferedReader(traceFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                Map<String, Object> e = mapper.readValue(line, new TypeReference<>() {});
                if (!"TokenUsage".equals(e.getOrDefault("eventType", ""))) continue;
                turnIdx++;
                Map<String, Object> inner = (Map<String, Object>) e.get("event");
                if (inner == null) continue;

                int prompt = i(inner, "promptTokens");
                int completion = i(inner, "completionTokens");
                int cc = i(inner, "cacheCreationInputTokens");
                int cr = i(inner, "cacheReadInputTokens");
                int cacheTotal = cr + cc + prompt;
                double hitRate = cacheTotal > 0 ? (double) cr / cacheTotal : 0;

                turns.add(new CacheTurn(turnIdx, prompt, completion, cc, cr, hitRate));
                sumHitRate += hitRate;

                if (hitRate > 0.5) hadHighHitRate = true;
                if (prevHitRate > 0.5 && hitRate < 0.1) hadDrop = true;
                if (prevHitRate >= 0 && Math.abs(hitRate - prevHitRate) > 0.5) fluctuating = true;
                prevHitRate = hitRate;
            }
        }

        double avgHitRate = turns.isEmpty() ? 0 : sumHitRate / turns.size();
        boolean healthy = avgHitRate >= 0.7;
        String pattern;
        String recommendation;

        if (turns.isEmpty()) {
            pattern = "No LLM calls in this run";
            recommendation = "The test may not have reached the point of calling the LLM. Check test scenario setup.";
        } else if (avgHitRate < 0.01) {
            pattern = "Always ~0% — cache never engaged";
            recommendation = "Check that cache_control breakpoints are configured. Verify prompt structure has stable prefix portions.";
        } else if (hadDrop) {
            pattern = "Sudden drop detected — cache was working then stopped";
            recommendation = "Suspect TTL expiry, system prompt modification, or tool list change mid-session. Dump prompts before and after the drop point.";
        } else if (hadHighHitRate && fluctuating) {
            pattern = "Fluctuating hit rate — unstable cache pattern";
            recommendation = "Unusual. Check if multiple sessions compete for cache prefix, or if prompt structure varies significantly between turns.";
        } else if (avgHitRate > 0.3 && avgHitRate < 0.7) {
            pattern = "Moderate hit rate — partial caching";
            recommendation = "Cache is partially working. Consider moving breakpoints later in the prompt to cover more of the growing portion.";
        } else {
            pattern = "Normal";
            recommendation = "Cache appears healthy. Continue monitoring for regressions after prompt changes.";
        }

        return new CacheDiagnosis(turns, pattern, recommendation, avgHitRate, healthy);
    }

    private static int i(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }
}
