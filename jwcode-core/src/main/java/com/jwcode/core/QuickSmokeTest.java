package com.jwcode.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.observability.DefaultObservationPipeline;
import com.jwcode.core.observability.ObservationEvent;
import com.jwcode.core.observability.ObservationPipeline;
import com.jwcode.core.observability.TracePersistenceObserver;
import com.jwcode.core.service.CacheHealthDiagnostic;
import com.jwcode.core.service.ContextDumpTool;
import com.jwcode.core.service.QualityTrendAnalyzer;
import com.jwcode.core.service.RunHistoryStore;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.core.tool.context.ToolExecutionContext;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

public class QuickSmokeTest {
    public static void main(String[] args) throws Exception {
        ObservationPipeline pipeline = new DefaultObservationPipeline();
        TracePersistenceObserver trace = new TracePersistenceObserver();
        pipeline.subscribe(trace);
        RunHistoryStore store = new RunHistoryStore(); store.init();
        ToolRegistry registry = ToolRegistry.createDefault();
        ToolExecutor executor = new ToolExecutor(registry);
        String runId = trace.startRun("quick-smoke", "Smoke test: fast read-only tools");
        System.out.println("Run: " + runId);
        int passed = 0, failed = 0, total = 0;
        for (String name : List.of("FileReadTool", "GrepTool", "GlobTool")) {
            total++;
            var opt = registry.findByName(name);
            if (opt.isEmpty()) { System.out.println("  SKIP " + name); continue; }
            Tool t = opt.get();
            JsonNode input = buildInput(name);
            if (input == null) { System.out.println("  SKIP " + name); continue; }
            System.out.print("  TEST " + name + "... ");
            try {
                pipeline.publish(new ObservationEvent.StepStart(name, "Testing " + name));
                long t0 = System.currentTimeMillis();
                var result = executor.execute(name, input, ToolExecutionContext.builder().build()).get(10, TimeUnit.SECONDS);
                long dt = System.currentTimeMillis() - t0;
                pipeline.publish(new ObservationEvent.ToolResult(name, result.isSuccess() ? "OK" : result.getErrorMessage(), result.isSuccess(), Duration.ofMillis(dt), "tc_" + name));
                if (result.isSuccess()) { System.out.println("PASS (" + dt + "ms)"); passed++; }
                else { System.out.println("FAIL: " + result.getErrorMessage()); failed++; }
                pipeline.publish(new ObservationEvent.StepComplete(name, result.isSuccess() ? "passed" : "failed"));
            } catch (TimeoutException e) {
                System.out.println("TIMEOUT (>10s)"); failed++;
                pipeline.publish(new ObservationEvent.Error(name, "Timeout", "Increase timeout or check tool"));
            }
            catch (Exception e) {
                String m = e.getMessage();
                if (m != null && m.contains("validation")) { System.out.println("SKIP: " + m); total--; }
                else { System.out.println("FAIL: " + (m != null ? m : e.getClass().getSimpleName())); failed++; }
            }
        }
        pipeline.publish(new ObservationEvent.Checkpoint(String.format("%d/%d passed", passed, total)));
        trace.endRun(runId, failed == 0, String.format("%d/%d passed", passed, total), java.util.Map.of());
        System.out.println("Results: " + passed + "/" + total + " passed, " + failed + " failed\n");
        System.out.println(new ContextDumpTool().dumpRun(runId));
        System.out.println(new CacheHealthDiagnostic().analyze(runId));
        System.out.println(new QualityTrendAnalyzer(store).analyze().toConsoleString());
    }
    private static JsonNode buildInput(String name) {
        ObjectMapper m = new ObjectMapper();
        if ("FileReadTool".equals(name)) { ObjectNode n = m.createObjectNode(); n.put("file_path", "pom.xml"); return n; }
        if ("GrepTool".equals(name)) { ObjectNode n = m.createObjectNode(); n.put("pattern", "public class"); n.put("path", "."); n.put("file_pattern", "*.java"); return n; }
        if ("GlobTool".equals(name)) { ObjectNode n = m.createObjectNode(); n.put("pattern", "pom.xml"); n.put("max_results", 5); return n; }
        return null;
    }
}
