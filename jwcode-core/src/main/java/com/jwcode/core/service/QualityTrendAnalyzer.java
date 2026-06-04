package com.jwcode.core.service;

import com.jwcode.core.service.RunHistoryStore.RunSummary;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * QualityTrendAnalyzer — analyzes quality trends across historical test runs.
 *
 * <p>Reads run metadata from RunHistoryStore, computes per-dimension trends,
 * detects regressions, and generates trend reports.</p>
 */
public class QualityTrendAnalyzer {

    private static final Logger LOG = Logger.getLogger(QualityTrendAnalyzer.class.getName());
    private static final double REGRESSION_THRESHOLD = 0.10;

    private final RunHistoryStore store;

    public QualityTrendAnalyzer(RunHistoryStore store) {
        this.store = store;
    }

    public TrendReport analyze() throws IOException {
        List<RunSummary> summaries = store.listRunSummaries();
        if (summaries.isEmpty()) {
            return new TrendReport(List.of(), List.of(), "No historical runs found.");
        }

        List<TrendReport.TrendPoint> points = new ArrayList<>();
        List<TrendReport.Regression> regressions = new ArrayList<>();

        TrendReport.TrendPoint previous = null;
        List<RunSummary> reversed = new ArrayList<>(summaries);
        java.util.Collections.reverse(reversed);
        for (RunSummary summary : reversed) {
            Map<String, Object> meta = store.readMetadata(summary.runId()).orElse(null);
            if (meta == null) continue;

            TrendReport.TrendPoint point = extractPoint(summary, meta);
            points.add(point);

            if (previous != null && previous.score > 0 && point.score > 0) {
                double drop = previous.score - point.score;
                if (drop > REGRESSION_THRESHOLD) {
                    regressions.add(new TrendReport.Regression(
                        previous.runId, point.runId, previous.score, point.score, drop
                    ));
                }
            }
            previous = point;
        }

        String summaryText = buildSummaryText(points, regressions);
        return new TrendReport(points, regressions, summaryText);
    }

    @SuppressWarnings("unchecked")
    private TrendReport.TrendPoint extractPoint(RunSummary summary, Map<String, Object> meta) {
        double score = 0;
        Map<String, Double> dimensions = new LinkedHashMap<>();

        if (meta.containsKey("weightedScore")) {
            score = ((Number) meta.get("weightedScore")).doubleValue();
        } else if (meta.containsKey("scores")) {
            Map<String, Object> scores = (Map<String, Object>) meta.get("scores");
            if (scores != null) {
                double total = 0;
                for (Map.Entry<String, Object> e : scores.entrySet()) {
                    double v = e.getValue() instanceof Number n ? n.doubleValue() : 0;
                    dimensions.put(e.getKey(), v);
                    total += v;
                }
                score = dimensions.isEmpty() ? 0 : total / dimensions.size();
            }
        } else if ("passed".equals(summary.status())) {
            score = 10.0;
        }

        return new TrendReport.TrendPoint(
            summary.runId(),
            summary.description(),
            safeParse(summary.completedAt()),
            score,
            dimensions,
            summary.status()
        );
    }

    private String buildSummaryText(List<TrendReport.TrendPoint> points,
                                     List<TrendReport.Regression> regressions) {
        if (points.isEmpty()) return "No data.";

        StringBuilder sb = new StringBuilder();
        long passed = points.stream().filter(p -> "passed".equals(p.status)).count();
        long total = points.size();
        double avgScore = points.stream().mapToDouble(p -> p.score).average().orElse(0);

        TrendReport.TrendPoint latest = points.get(points.size() - 1);
        TrendReport.TrendPoint first = points.get(0);
        double overallDelta = latest.score - first.score;

        sb.append(String.format("Runs: %d (passed: %d, failed: %d)\n", total, passed, total - passed));
        sb.append(String.format("Avg score: %.2f\n", avgScore));
        sb.append(String.format("Trend: %.2f -> %.2f (%+.2f)\n", first.score, latest.score, overallDelta));

        if (!regressions.isEmpty()) {
            sb.append("Regressions: ").append(regressions.size()).append("\n");
            for (TrendReport.Regression r : regressions) {
                sb.append(String.format("  %s -> %s  (%.2f -> %.2f, drop=%.2f)\n",
                    truncateId(r.fromRunId), truncateId(r.toRunId),
                    r.fromScore, r.toScore, r.drop));
            }
        }

        if (!latest.dimensions.isEmpty()) {
            Map.Entry<String, Double> weakest = latest.dimensions.entrySet().stream()
                .min(Map.Entry.comparingByValue()).orElse(null);
            if (weakest != null) {
                sb.append(String.format("Weakest dimension: %s (%.2f)\n", weakest.getKey(), weakest.getValue()));
            }
        }

        return sb.toString();
    }

    private static LocalDateTime safeParse(String iso) {
        if (iso == null) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(iso, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e2) {
                return LocalDateTime.now();
            }
        }
    }

    private static String truncateId(String id) {
        if (id == null) return "?";
        return id.length() <= 20 ? id : id.substring(0, 17) + "...";
    }

    // ---- types ----

    public record TrendReport(
        List<TrendPoint> points,
        List<Regression> regressions,
        String summary
    ) {
        public record TrendPoint(
            String runId,
            String description,
            LocalDateTime timestamp,
            double score,
            Map<String, Double> dimensions,
            String status
        ) {}

        public record Regression(
            String fromRunId,
            String toRunId,
            double fromScore,
            double toScore,
            double drop
        ) {}

        public String toConsoleString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Quality Trend Analysis ===\n");
            sb.append(summary).append("\n\n");

            sb.append("Run history:\n");
            for (TrendPoint p : points) {
                String icon = "passed".equals(p.status) ? "+" : "failed".equals(p.status) ? "-" : " ";
                sb.append(String.format("  %s %-40s score=%.2f\n", icon, p.runId, p.score));
            }

            if (!regressions.isEmpty()) {
                sb.append("\nRegressions:\n");
                for (Regression r : regressions) {
                    sb.append(String.format("  %.2f -> %.2f (%s -> %s)\n",
                        r.fromScore, r.toScore, r.fromRunId, r.toRunId));
                }
            }
            return sb.toString();
        }
    }
}

