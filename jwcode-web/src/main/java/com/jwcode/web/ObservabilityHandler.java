package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.observability.ObservabilityService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Observability API handler — serves session stats, cost data, and historical trace runs.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/observability/summary — current session aggregate stats</li>
 *   <li>GET /api/observability/costs — cost breakdown by model + history</li>
 *   <li>GET /api/observability/runs — list historical trace runs</li>
 *   <li>GET /api/observability/runs/{runId} — single run metadata</li>
 *   <li>GET /api/observability/runs/{runId}/events?page=0&amp;size=50 — paginated events</li>
 * </ul>
 */
public class ObservabilityHandler implements HttpHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BASE_PATH = "/api/observability";

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
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        try {
            if (path.equals(BASE_PATH + "/summary")) {
                handleSummary(exchange);
            } else if (path.equals(BASE_PATH + "/costs")) {
                handleCosts(exchange);
            } else if (path.equals(BASE_PATH + "/runs")) {
                handleListRuns(exchange);
            } else if (path.startsWith(BASE_PATH + "/runs/")) {
                String subPath = path.substring((BASE_PATH + "/runs/").length());
                if (subPath.contains("/events")) {
                    // /api/observability/runs/{runId}/events
                    String runId = subPath.substring(0, subPath.indexOf("/events"));
                    handleRunEvents(exchange, runId);
                } else {
                    // /api/observability/runs/{runId}
                    handleGetRun(exchange, subPath);
                }
            } else {
                sendError(exchange, 404, "Not found: " + path);
            }
        } catch (Exception e) {
            sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }

    private void handleSummary(HttpExchange exchange) throws IOException {
        ObservabilityService svc = ObservabilityService.getInstance();
        Map<String, Object> summary = svc.getSummary();
        sendSuccess(exchange, 200, summary);
    }

    private void handleCosts(HttpExchange exchange) throws IOException {
        ObservabilityService svc = ObservabilityService.getInstance();
        Map<String, Object> costs = svc.getCosts();
        sendSuccess(exchange, 200, costs);
    }

    private void handleListRuns(HttpExchange exchange) throws IOException {
        ObservabilityService svc = ObservabilityService.getInstance();
        List<Map<String, Object>> runs = svc.listRuns();
        sendSuccess(exchange, 200, runs);
    }

    private void handleGetRun(HttpExchange exchange, String runId) throws IOException {
        ObservabilityService svc = ObservabilityService.getInstance();
        Map<String, Object> run = svc.getRun(runId);
        if (run == null) {
            sendError(exchange, 404, "Run not found: " + runId);
        } else {
            sendSuccess(exchange, 200, run);
        }
    }

    private void handleRunEvents(HttpExchange exchange, String runId) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        int page = 0;
        int size = 50;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    try {
                        if ("page".equals(kv[0])) page = Integer.parseInt(kv[1]);
                        if ("size".equals(kv[0])) size = Math.min(Integer.parseInt(kv[1]), 200);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        ObservabilityService svc = ObservabilityService.getInstance();
        Map<String, Object> events = svc.getRunEvents(runId, page, size);
        sendSuccess(exchange, 200, events);
    }

    private void sendSuccess(HttpExchange exchange, int statusCode, Object data) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", objectMapper.valueToTree(data));
        sendJson(exchange, statusCode, response);
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", false);
        response.put("error", message);
        sendJson(exchange, statusCode, response);
    }

    private void sendJson(HttpExchange exchange, int statusCode, ObjectNode json) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
