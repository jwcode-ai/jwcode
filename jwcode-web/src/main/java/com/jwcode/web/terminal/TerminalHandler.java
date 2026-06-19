package com.jwcode.web.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.logging.Logger;

public class TerminalHandler implements HttpHandler {
    private static final Logger logger = Logger.getLogger(TerminalHandler.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BASE_PORT = 8090;

    private TerminalSession currentSession;
    private Path workspaceRoot;

    public TerminalHandler() {
        this.workspaceRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public TerminalHandler(String ignoredTtydPath, String ignoredTsCliPath) {
        this();
    }

    public boolean isAvailable() {
        return true;
    }

    public void setWorkspaceRoot(String workspaceDir) {
        if (workspaceDir != null && !workspaceDir.isBlank()) {
            workspaceRoot = Paths.get(workspaceDir).toAbsolutePath().normalize();
        }
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

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
            } else if ("POST".equalsIgnoreCase(method) && path.equals("/api/terminal/upload")) {
                handleUpload(exchange);
            } else if ("GET".equalsIgnoreCase(method) && path.equals("/api/terminal/download")) {
                handleDownload(exchange);
            } else {
                sendJson(exchange, 404, errorJson("Not found"));
            }
        } catch (Exception e) {
            logger.severe("Terminal handler error: " + e.getMessage());
            sendJson(exchange, 500, errorJson(e.getMessage()));
        }
    }

    private void handleStart(HttpExchange exchange) throws IOException {
        String workspaceDir = workspaceRoot.toString();
        try {
            String body = readBody(exchange);
            if (body != null && !body.isBlank()) {
                JsonNode json = MAPPER.readTree(body);
                if (json.hasNonNull("workspaceDir") && !json.get("workspaceDir").asText().isBlank()) {
                    workspaceDir = json.get("workspaceDir").asText();
                }
            }
        } catch (Exception ignored) {
        }

        setWorkspaceRoot(workspaceDir);

        if (currentSession != null && currentSession.isRunning()) {
            currentSession.kill();
        }

        try {
            int port = TerminalSession.findFreePort(BASE_PORT);
            currentSession = new TerminalSession(workspaceRoot.toString(), port);

            ObjectNode data = MAPPER.createObjectNode();
            data.put("port", port);
            data.put("ttydPort", port);
            data.put("wsUrl", currentSession.getWsUrl());
            data.put("workspaceDir", workspaceRoot.toString());
            data.put("shell", TerminalSession.findShellExecutable() != null ? TerminalSession.findShellExecutable() : "");
            data.put("ttydAvailable", true);
            sendJson(exchange, 200, successJson(data));
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
        data.put("terminalAvailable", TerminalSession.findShellExecutable() != null);
        data.put("ttydAvailable", TerminalSession.findShellExecutable() != null);
        data.put("workspaceDir", workspaceRoot.toString());
        if (currentSession != null && currentSession.isRunning()) {
            data.put("port", currentSession.getPort());
            data.put("ttydPort", currentSession.getPort());
            data.put("uptime", currentSession.getUptime());
            data.put("wsUrl", currentSession.getWsUrl());
        }
        sendJson(exchange, 200, successJson(data));
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        if (!ensureWorkspaceWithinRoot()) {
            sendJson(exchange, 400, errorJson("Workspace root is invalid"));
            return;
        }

        JsonNode input = MAPPER.readTree(readBody(exchange));
        String path = requiredText(input, "path");
        String content = input.hasNonNull("content") ? input.get("content").asText("") : "";
        boolean base64 = input.hasNonNull("base64") && input.get("base64").asBoolean(false);

        Path resolved = resolveAndValidate(path);
        Files.createDirectories(resolved.getParent());
        byte[] data = base64 ? Base64.getDecoder().decode(content) : content.getBytes(StandardCharsets.UTF_8);
        Files.write(resolved, data);

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("path", resolved.toString());
        payload.put("size", data.length);
        sendJson(exchange, 200, successJson(payload));
    }

    private void handleDownload(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String path = null;
        if (query != null) {
            for (String part : query.split("&")) {
                int idx = part.indexOf('=');
                if (idx > 0 && "path".equals(part.substring(0, idx))) {
                    path = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        if (path == null || path.isBlank()) {
            sendJson(exchange, 400, errorJson("Missing path parameter"));
            return;
        }

        Path resolved = resolveAndValidate(path);
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            sendJson(exchange, 404, errorJson("File not found"));
            return;
        }

        byte[] bytes = Files.readAllBytes(resolved);
        String fileName = resolved.getFileName().toString();
        String contentType = Files.probeContentType(resolved);
        if (contentType == null) contentType = "application/octet-stream";

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition",
            "attachment; filename=\"" + fileName.replace("\"", "") + "\"");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean ensureWorkspaceWithinRoot() {
        return workspaceRoot != null && Files.exists(workspaceRoot) && Files.isDirectory(workspaceRoot);
    }

    private Path resolveAndValidate(String userPath) {
        Path resolved = workspaceRoot.resolve(userPath).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("Path escapes workspace root: " + userPath);
        }
        return resolved;
    }

    private String requiredText(JsonNode input, String key) {
        if (input == null || !input.hasNonNull(key) || input.get(key).asText().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return input.get(key).asText();
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
