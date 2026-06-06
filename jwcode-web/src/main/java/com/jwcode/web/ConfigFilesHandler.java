package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * REST API for browsing and editing config files in ~/.jwcode/.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/config/files              — list config files</li>
 *   <li>GET  /api/config/files/read?file=X  — read a config file</li>
 *   <li>PUT  /api/config/files/write        — write a config file (body: {file, content})</li>
 * </ul>
 *
 * <p>Only known config files are editable (whitelist).
 */
public class ConfigFilesHandler implements HttpHandler {

    private static final Set<String> EDITABLE_FILES = Set.of(
        "config.yaml", "config.yml", "settings.json", "features.json", "hooks.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path jwcodeDir;

    public ConfigFilesHandler() {
        String userHome = System.getProperty("user.home");
        this.jwcodeDir = Paths.get(userHome, ".jwcode");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        if (path.endsWith("/read")) {
            handleRead(exchange);
        } else if (path.endsWith("/write")) {
            handleWrite(exchange, method);
        } else {
            handleList(exchange);
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, createError("Method not allowed"));
            return;
        }

        ArrayNode files = objectMapper.createArrayNode();
        if (Files.isDirectory(jwcodeDir)) {
            try (var stream = Files.list(jwcodeDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(f -> {
                        String name = f.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml")
                            || name.endsWith(".json") || name.endsWith(".properties")
                            || name.endsWith(".md");
                    })
                    .sorted()
                    .forEach(f -> {
                        try {
                            ObjectNode entry = objectMapper.createObjectNode();
                            entry.put("name", f.getFileName().toString());
                            entry.put("size", Files.size(f));
                            entry.put("modified", Files.getLastModifiedTime(f).toMillis());
                            entry.put("editable", EDITABLE_FILES.contains(f.getFileName().toString()));
                            files.add(entry);
                        } catch (IOException ignored) {
                        }
                    });
            }
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", files);
        sendJson(exchange, 200, response);
    }

    private void handleRead(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, createError("Method not allowed"));
            return;
        }

        String fileName = getQueryParam(exchange, "file");
        if (fileName == null || fileName.isBlank()) {
            sendJson(exchange, 400, createError("Missing 'file' query parameter"));
            return;
        }

        Path filePath = resolveSafe(fileName);
        if (filePath == null) {
            sendJson(exchange, 400, createError("Invalid file name"));
            return;
        }

        if (!Files.exists(filePath)) {
            sendJson(exchange, 404, createError("File not found: " + fileName));
            return;
        }

        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = response.putObject("data");
        data.put("name", fileName);
        data.put("content", content);
        data.put("editable", EDITABLE_FILES.contains(fileName));
        sendJson(exchange, 200, response);
    }

    private void handleWrite(HttpExchange exchange, String method) throws IOException {
        if (!"PUT".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            sendJson(exchange, 405, createError("Method not allowed"));
            return;
        }

        ObjectNode request;
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                sendJson(exchange, 400, createError("Request body required"));
                return;
            }
            request = objectMapper.readValue(body, ObjectNode.class);
        }

        if (!request.has("file") || !request.has("content")) {
            sendJson(exchange, 400, createError("Fields 'file' and 'content' are required"));
            return;
        }

        String fileName = request.get("file").asText();
        String content = request.get("content").asText();

        if (!EDITABLE_FILES.contains(fileName)) {
            sendJson(exchange, 403, createError("File is not editable: " + fileName));
            return;
        }

        Path filePath = resolveSafe(fileName);
        if (filePath == null) {
            sendJson(exchange, 400, createError("Invalid file name"));
            return;
        }

        Files.createDirectories(jwcodeDir);
        Files.writeString(filePath, content, StandardCharsets.UTF_8);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "File saved: " + fileName);
        sendJson(exchange, 200, response);
    }

    /**
     * Resolve a file name safely within ~/.jwcode/. Returns null if the name
     * contains path separators or attempts traversal.
     */
    private Path resolveSafe(String fileName) {
        if (fileName.contains("/") || fileName.contains("\\")
            || fileName.contains("..") || fileName.isBlank()) {
            return null;
        }
        return jwcodeDir.resolve(fileName).normalize();
    }

    private String getQueryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                return java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void sendJson(HttpExchange exchange, int statusCode, ObjectNode json) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private ObjectNode createError(String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}
