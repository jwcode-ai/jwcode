package com.jwcode.web.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class TerminalHandler implements HttpHandler {
    private static final Logger logger = Logger.getLogger(TerminalHandler.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TTYD_BASE_PORT = 8090;

    private final String ttydPath;
    private final String tsCliPath;
    private TerminalSession currentSession;

    public TerminalHandler(String ttydPath, String tsCliPath) {
        this.ttydPath = ttydPath;
        this.tsCliPath = tsCliPath;
    }

    public boolean isTtydAvailable() {
        return ttydPath != null;
    }

    public void shutdown() {
        if (currentSession != null) {
            currentSession.kill();
            currentSession = null;
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        try {
            if ("POST".equalsIgnoreCase(method) && path.equals("/api/terminal/start")) {
                handleStart(exchange);
            } else if ("POST".equalsIgnoreCase(method) && path.equals("/api/terminal/stop")) {
                handleStop(exchange);
            } else if ("GET".equalsIgnoreCase(method) && path.equals("/api/terminal/status")) {
                handleStatus(exchange);
            } else {
                sendJson(exchange, 404, errorJson("Not found"));
            }
        } catch (Exception e) {
            logger.severe("Terminal handler error: " + e.getMessage());
            sendJson(exchange, 500, errorJson(e.getMessage()));
        }
    }

    private void handleStart(HttpExchange exchange) throws IOException {
        if (!isTtydAvailable()) {
            sendJson(exchange, 400, errorJson(
                "ttyd not found. Install from https://github.com/tsl0922/ttyd/releases " +
                "(winget install ttyd / brew install ttyd / apt install ttyd)"));
            return;
        }

        // Read workspaceDir from request body
        String workspaceDir = System.getProperty("user.dir");
        try {
            String body = readBody(exchange);
            if (body != null && !body.isEmpty()) {
                ObjectNode json = (ObjectNode) MAPPER.readTree(body);
                if (json.has("workspaceDir") && !json.get("workspaceDir").asText().isEmpty()) {
                    workspaceDir = json.get("workspaceDir").asText();
                }
            }
        } catch (Exception ignored) {}

        // Kill existing session
        if (currentSession != null && currentSession.isRunning()) {
            currentSession.kill();
        }

        try {
            int port = TerminalSession.findFreePort(TTYD_BASE_PORT);
            currentSession = new TerminalSession(ttydPath, tsCliPath, workspaceDir, port);

            // Brief wait for ttyd to start listening
            Thread.sleep(300);

            if (!currentSession.isRunning()) {
                sendJson(exchange, 500, errorJson("ttyd process failed to start"));
                return;
            }

            ObjectNode data = MAPPER.createObjectNode();
            data.put("ttydPort", port);
            data.put("wsUrl", "ws://127.0.0.1:" + port + "/ws");
            sendJson(exchange, 200, successJson(data));

            logger.info("Terminal session started on port " + port + " for " + workspaceDir);
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson("Failed to start terminal: " + e.getMessage()));
        }
    }

    private void handleStop(HttpExchange exchange) throws IOException {
        if (currentSession != null) {
            currentSession.kill();
            currentSession = null;
        }
        sendJson(exchange, 200, successJson(null));
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("running", currentSession != null && currentSession.isRunning());
        data.put("ttydAvailable", isTtydAvailable());
        if (currentSession != null && currentSession.isRunning()) {
            data.put("port", currentSession.getPort());
            data.put("uptime", currentSession.getUptime());
            data.put("workspaceDir", currentSession.getWorkspaceDir());
        }
        sendJson(exchange, 200, successJson(data));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange exchange, int status, ObjectNode json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private ObjectNode successJson(ObjectNode data) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("success", true);
        if (data != null) node.set("data", data);
        return node;
    }

    private ObjectNode errorJson(String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("success", false);
        node.put("error", message);
        return node;
    }
}
