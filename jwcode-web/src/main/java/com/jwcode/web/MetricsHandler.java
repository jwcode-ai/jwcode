package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.observability.ObservabilityService;
import com.jwcode.core.service.CostTrackerService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Metrics handler — Prometheus-compatible / OpenTelemetry-aligned metrics.
 *
 * Endpoints:
 *   GET /api/metrics           — Prometheus text format
 *   GET /api/metrics/json      — JSON format
 *   GET /api/metrics/health    — Health check
 *
 * Exported metrics (对标 Codex otel crate):
 *   jwcode_llm_calls_total      — Total LLM API calls
 *   jwcode_llm_latency_ms       — LLM call latency histogram
 *   jwcode_tool_exec_total      — Total tool executions
 *   jwcode_ws_connections       — Active WebSocket connections
 *   jwcode_sessions_active      — Active sessions
 *   jwcode_memory_heap_bytes    — JVM heap memory usage
 *   jwcode_errors_total         — Total errors
 */
public class MetricsHandler implements HttpHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BASE_PATH = "/api/metrics";

    private final ObservabilityService observabilityService;
    private int activeWsConnections = 0;
    private int activeSessions = 0;

    public MetricsHandler() {
        this.observabilityService = ObservabilityService.getInstance();
    }

    // Called by WebSocket handler to update connection count
    public void setActiveWsConnections(int count) {
        this.activeWsConnections = count;
    }

    public void setActiveSessions(int count) {
        this.activeSessions = count;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        try {
            if (path.equals(BASE_PATH + "/health")) {
                handleHealth(exchange);
            } else if (path.equals(BASE_PATH + "/json")) {
                handleJson(exchange);
            } else {
                handlePrometheus(exchange);
            }
        } catch (Exception e) {
            sendText(exchange, 500, "Internal error: " + e.getMessage());
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "ok");
        response.put("uptime_seconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
        response.put("ws_connections", activeWsConnections);
        response.put("active_sessions", activeSessions);

        byte[] bytes = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleJson(HttpExchange exchange) throws IOException {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // LLM metrics
        Map<String, Object> obsSummary = observabilityService.getSummary();
        metrics.put("llm", obsSummary);

        // Tool metrics
        Map<String, Object> costs = observabilityService.getCosts();
        metrics.put("costs", costs);

        // JVM metrics
        Map<String, Object> jvm = getJvmMetrics();
        metrics.put("jvm", jvm);

        // Connection metrics
        Map<String, Object> connections = new LinkedHashMap<>();
        connections.put("ws_active", activeWsConnections);
        connections.put("sessions_active", activeSessions);
        metrics.put("connections", connections);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", objectMapper.valueToTree(metrics));

        byte[] bytes = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handlePrometheus(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();

        // JVM metrics
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        sb.append("# HELP jwcode_memory_heap_bytes JVM heap memory usage\n");
        sb.append("# TYPE jwcode_memory_heap_bytes gauge\n");
        sb.append("jwcode_memory_heap_bytes{area=\"used\"} ").append(heap.getUsed()).append("\n");
        sb.append("jwcode_memory_heap_bytes{area=\"max\"} ").append(heap.getMax()).append("\n");
        sb.append("jwcode_memory_heap_bytes{area=\"committed\"} ").append(heap.getCommitted()).append("\n\n");

        // GC metrics
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gc.getCollectionCount();
            gcTime += gc.getCollectionTime();
        }
        sb.append("# HELP jwcode_gc_collections_total Total GC collections\n");
        sb.append("# TYPE jwcode_gc_collections_total counter\n");
        sb.append("jwcode_gc_collections_total ").append(gcCount).append("\n\n");
        sb.append("# HELP jwcode_gc_time_ms_total Total GC time in ms\n");
        sb.append("# TYPE jwcode_gc_time_ms_total counter\n");
        sb.append("jwcode_gc_time_ms_total ").append(gcTime).append("\n\n");

        // Thread metrics
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        sb.append("# HELP jwcode_threads_current Current thread count\n");
        sb.append("# TYPE jwcode_threads_current gauge\n");
        sb.append("jwcode_threads_current ").append(threadBean.getThreadCount()).append("\n\n");

        // Application metrics
        Map<String, Object> summary = observabilityService.getSummary();
        sb.append("# HELP jwcode_llm_calls_total Total LLM API calls\n");
        sb.append("# TYPE jwcode_llm_calls_total counter\n");
        sb.append("jwcode_llm_calls_total ").append(summary.getOrDefault("totalCalls", 0)).append("\n\n");

        sb.append("# HELP jwcode_ws_connections_active Active WebSocket connections\n");
        sb.append("# TYPE jwcode_ws_connections_active gauge\n");
        sb.append("jwcode_ws_connections_active ").append(activeWsConnections).append("\n\n");

        sb.append("# HELP jwcode_sessions_active Active sessions\n");
        sb.append("# TYPE jwcode_sessions_active gauge\n");
        sb.append("jwcode_sessions_active ").append(activeSessions).append("\n\n");

        sb.append("# HELP jwcode_uptime_seconds Process uptime in seconds\n");
        sb.append("# TYPE jwcode_uptime_seconds gauge\n");
        sb.append("jwcode_uptime_seconds ").append(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0).append("\n");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, Object> getJvmMetrics() {
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("uptime_seconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
        jvm.put("available_processors", Runtime.getRuntime().availableProcessors());
        jvm.put("java_version", System.getProperty("java.version"));
        jvm.put("os_name", System.getProperty("os.name"));
        jvm.put("os_arch", System.getProperty("os.arch"));

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> heap = new LinkedHashMap<>();
        heap.put("used_mb", memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        heap.put("max_mb", memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        heap.put("committed_mb", memoryBean.getHeapMemoryUsage().getCommitted() / (1024 * 1024));
        jvm.put("heap", heap);

        return jvm;
    }

    private void sendText(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
