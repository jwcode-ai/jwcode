package com.jwcode.core;

import com.jwcode.core.coordinator.ToolTestConfig;
import com.jwcode.core.coordinator.ToolTestCoordinator;
import com.jwcode.core.observability.TracePersistenceObserver;
import com.jwcode.core.report.TestReport;
import com.jwcode.core.service.CacheHealthDiagnostic;
import com.jwcode.core.service.ContextDumpTool;
import com.jwcode.core.service.QualityTrendAnalyzer;
import com.jwcode.core.service.RunHistoryStore;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.core.tool.ToolExecutor;

/**
 * Real test: runs ToolTestCoordinator with actual tools and full trace pipeline.
 */
public class RealTestHarness {
    public static void main(String[] args) throws Exception {
        System.out.println("=== JWCode Real Tool Test ===\n");

        TracePersistenceObserver trace = new TracePersistenceObserver();
        RunHistoryStore store = new RunHistoryStore();
        store.init();

        ToolRegistry registry = ToolRegistry.createDefault();
        ToolExecutor executor = new ToolExecutor(registry);
        ToolTestConfig config = ToolTestConfig.builder()
            .testSuiteName("JWCode Real Tool Chain Test")
            .environmentCheckEnabled(true)
            .strictMode(false)
            .timeoutSeconds(30)
            .maxConsecutiveFailures(5)
            .includeToolsRequiringExternalDeps(false)
            .build();

        ToolTestCoordinator coordinator = new ToolTestCoordinator(registry, executor, config);

        System.out.println("Running tool tests with trace persistence...\n");
        TestReport report = coordinator.runAllTestsWithPersistence(
            progress -> System.out.printf("\r  [%d/%d] %s",
                progress.current(), progress.total(), progress.currentTool()),
            trace, store);

        System.out.println("\n");

        // Report summary
        System.out.println("--- Test Report ---");
        System.out.println("  Total:   " + report.getTotalCount());
        System.out.println("  Passed:  " + report.getSuccessCount());
        System.out.println("  Failed:  " + report.getFailedCount());
        System.out.println("  Skipped: " + report.getSkippedCount());
        System.out.println("  Errors:  " + report.getErrorCount());
        System.out.println("  Rate:    " + String.format("%.1f%%", report.getSuccessRate()));
        System.out.println();

        // Find the run directory
        String runId = null;
        var runs = store.listRuns();
        if (!runs.isEmpty()) {
            runId = runs.get(0).getFileName().toString();
            System.out.println("Run ID: " + runId + "\n");
        }

        if (runId != null) {
            // Context dump
            System.out.println("--- ContextDumpTool ---");
            ContextDumpTool dumper = new ContextDumpTool();
            System.out.println(dumper.dumpRun(runId));

            // Cache health
            System.out.println("--- CacheHealthDiagnostic ---");
            CacheHealthDiagnostic diag = new CacheHealthDiagnostic();
            System.out.println(diag.analyze(runId));

            // Trend
            System.out.println("--- QualityTrendAnalyzer ---");
            QualityTrendAnalyzer trend = new QualityTrendAnalyzer(store);
            System.out.println(trend.analyze().toConsoleString());
        }

        System.out.println("Done. Trace at: .jwcode/test-runs/");
    }
}
