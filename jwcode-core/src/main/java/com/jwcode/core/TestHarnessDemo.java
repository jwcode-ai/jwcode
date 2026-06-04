package com.jwcode.core;

import com.jwcode.core.observability.*;
import com.jwcode.core.service.*;

import java.time.Duration;
import java.time.Instant;

/**
 * Quick demo: simulates a tool-chain test with trace, cache, and diagnostics.
 * Run: mvn compile exec:java -pl jwcode-core -Dexec.mainClass=com.jwcode.core.TestHarnessDemo
 */
public class TestHarnessDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("=== JWCode Test Harness Demo ===\n");

        // 1. Create observers
        TracePersistenceObserver trace = new TracePersistenceObserver();
        AnalyticsObserver analytics = new AnalyticsObserver();
        ObservationPipeline pipeline = new DefaultObservationPipeline();
        pipeline.subscribe(trace);
        pipeline.subscribe(analytics);

        // 2. Start a trace run
        String runId = trace.startRun("demo-smoke", "Demo: simulated tool chain smoke test");
        System.out.println("Run started: " + runId);

        // 3. Simulate a realistic event stream
        publish(pipeline, new ObservationEvent.StepStart("init", "Environment check"));

        publish(pipeline, new ObservationEvent.TokenUsage(1200, 350, "claude-sonnet-4-5",
            200, 8000)); // Turn 1: high cache read

        publish(pipeline, new ObservationEvent.ToolCall("Glob", "{pattern: \"**/*.java\"}", "tc_01"));
        publish(pipeline, new ObservationEvent.ToolResult("Glob", "Found 47 files", true,
            Duration.ofMillis(120), "tc_01"));

        publish(pipeline, new ObservationEvent.ToolCall("Read", "{path: \"src/main/App.java\"}", "tc_02"));
        publish(pipeline, new ObservationEvent.ToolResult("Read", "package com.jwcode; ...", true,
            Duration.ofMillis(45), "tc_02"));

        publish(pipeline, new ObservationEvent.TokenUsage(1400, 280, "claude-sonnet-4-5",
            50, 8500)); // Turn 2: still good cache

        publish(pipeline, new ObservationEvent.ToolCall("Edit", "{path: \"src/main/App.java\", ...}", "tc_03"));
        publish(pipeline, new ObservationEvent.ToolResult("Edit", "File updated", true,
            Duration.ofMillis(80), "tc_03"));

        publish(pipeline, new ObservationEvent.TokenUsage(1600, 400, "claude-sonnet-4-5",
            0, 0)); // Turn 3: cache miss! No cache data

        // Simulate potential tool loop
        for (int i = 1; i <= 6; i++) {
            publish(pipeline, new ObservationEvent.ToolCall("Grep", "{pattern: \"missing_class\"}", "tc_loop_" + i));
            publish(pipeline, new ObservationEvent.ToolResult("Grep", "No matches found", true,
                Duration.ofMillis(30), "tc_loop_" + i));
        }

        publish(pipeline, new ObservationEvent.TokenUsage(800, 200, "claude-sonnet-4-5",
            0, 0)); // Turn 4: still cache miss

        publish(pipeline, new ObservationEvent.Checkpoint("Environment check passed"));
        publish(pipeline, new ObservationEvent.StepComplete("init", "all tools available"));

        publish(pipeline, new ObservationEvent.ContextCompressed(15, 8, 12000,
            "Compressed 7 old messages to save tokens"));

        // 4. Publish an Error
        publish(pipeline, new ObservationEvent.Error("ToolExecutor", "Permission denied: /etc/hosts",
            "Check workspace permissions"));

        // 5. End run
        trace.endRun(runId, true,
            "Demo: all simulated checks passed",
            java.util.Map.of("totalCount", 5, "successCount", 5, "failedCount", 0));

        // 6. Show analytics summary
        AnalyticsObserver.ExecutionSummary summary = analytics.getSummary();
        System.out.println("\n--- AnalyticsObserver Summary ---");
        System.out.println("  LLM calls:      " + summary.llmCalls());
        System.out.println("  Tool calls:     " + summary.toolCalls());
        System.out.println("  Errors:         " + summary.errors());
        System.out.println("  Total tokens:   " + summary.totalTokens());
        System.out.println("  Cache creation: " + summary.cacheCreationTokens());
        System.out.println("  Cache read:     " + summary.cacheReadTokens());
        System.out.println("  Cache hit rate: " + String.format("%.1f%%", summary.cacheHitRate() * 100));
        System.out.println("  Elapsed:        " + String.format("%.1fs", summary.elapsed().toMillis() / 1000.0));
        System.out.println();

        // 7. Context dump
        System.out.println("--- ContextDumpTool ---");
        ContextDumpTool dumper = new ContextDumpTool();
        System.out.println(dumper.dumpRun(runId));

        // 8. Cache health diagnostic
        System.out.println("--- CacheHealthDiagnostic ---");
        CacheHealthDiagnostic diag = new CacheHealthDiagnostic();
        System.out.println(diag.analyze(runId));

        // 9. Trend analysis
        System.out.println("--- QualityTrendAnalyzer ---");
        RunHistoryStore store = new RunHistoryStore();
        QualityTrendAnalyzer trend = new QualityTrendAnalyzer(store);
        System.out.println(trend.analyze().toConsoleString());

        System.out.println("\n=== Done. Check .jwcode/test-runs/" + runId + " for trace files ===");
    }

    private static void publish(ObservationPipeline pipeline, ObservationEvent event) {
        pipeline.publish(event);
    }
}
