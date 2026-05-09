package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件管理 API 处理器
 * 提供文件列表、读取、创建、写入、删除等接口
 * 
 * 安全设计：所有文件操作限制在 PROJECT_ROOT 目录下，防止目录逃逸攻击。
 */
public class FilesHandler implements HttpHandler {

    /** 项目根目录，所有文件操作必须在此目录下 */
    private static Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

    /**
     * 更新项目根目录
     * @param newRoot 新的项目根目录路径
     */
    public static void setProjectRoot(String newRoot) {
        if (newRoot != null && !newRoot.trim().isEmpty()) {
            Path resolved = Paths.get(newRoot).toAbsolutePath().normalize();
            if (Files.exists(resolved) && Files.isDirectory(resolved)) {
                PROJECT_ROOT = resolved;
            }
        }
    }

    /**
     * 获取当前项目根目录
     */
    public static Path getProjectRoot() {
        return PROJECT_ROOT;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // 设置 CORS 头
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.equals("/api/files/read")) {
                    handleReadFile(exchange);
                } else {
                    handleListFiles(exchange);
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                handleCreateFile(exchange);
            } else if ("PUT".equalsIgnoreCase(method)) {
                if (path.equals("/api/files/write")) {
                    handleWriteFile(exchange);
                } else {
                    sendError(exchange, 405, "不支持的端点");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDeleteFile(exchange);
            } else {
                sendError(exchange, 405, "Method not allowed");
            }
        } catch (SecurityException e) {
            sendError(exchange, 403, "Forbidden: " + e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 校验路径是否在 PROJECT_ROOT 下，防止目录逃逸
     */
    private Path resolveAndValidate(String userPath) {
        Path resolved = PROJECT_ROOT.resolve(userPath).normalize();
        if (!resolved.startsWith(PROJECT_ROOT)) {
            throw new SecurityException("Path escapes project root: " + userPath);
        }
        return resolved;
    }

    private void handleListFiles(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String dirPath = query != null && query.startsWith("path=") ?
                java.net.URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8) : "";

        Path resolved = resolveAndValidate(dirPath);
        List<ObjectNode> files = listFiles(resolved);
        sendSuccess(exchange, 200, files);
    }

    private void handleReadFile(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String filePath = query != null && query.startsWith("path=") ?
                java.net.URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8) : null;

        if (filePath == null) {
            sendError(exchange, 400, "Missing path parameter");
            return;
        }

        Path resolved = resolveAndValidate(filePath);
        String content = readFile(resolved);
        if (content == null) {
            sendError(exchange, 404, "File not found");
        } else {
            sendSuccess(exchange, 200, content);
        }
    }

    private void handleCreateFile(HttpExchange exchange) throws IOException {
        ObjectNode input = parseBody(exchange);
        if (input == null || !input.has("path")) {
            sendError(exchange, 400, "Missing path");
            return;
        }

        String filePath = input.get("path").asText();
        String content = input.has("content") ? input.get("content").asText() : "";

        Path resolved = resolveAndValidate(filePath);
        try {
            Files.createDirectories(resolved.getParent());
            Files.write(resolved, content.getBytes(StandardCharsets.UTF_8));
            sendSuccess(exchange, 201, null);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to create file: " + e.getMessage());
        }
    }

    private void handleWriteFile(HttpExchange exchange) throws IOException {
        ObjectNode input = parseBody(exchange);
        if (input == null || !input.has("path")) {
            sendError(exchange, 400, "Missing path");
            return;
        }

        String filePath = input.get("path").asText();
        String content = input.has("content") ? input.get("content").asText() : "";

        Path resolved = resolveAndValidate(filePath);
        try {
            Files.write(resolved, content.getBytes(StandardCharsets.UTF_8));
            sendSuccess(exchange, 200, null);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to write file: " + e.getMessage());
        }
    }

    private void handleDeleteFile(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.startsWith("path=")) {
            sendError(exchange, 400, "Missing path parameter");
            return;
        }

        String filePath = java.net.URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8);
        Path resolved = resolveAndValidate(filePath);

        try {
            Files.delete(resolved);
            sendSuccess(exchange, 200, null);
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to delete file: " + e.getMessage());
        }
    }

    private List<ObjectNode> listFiles(Path dirPath) {
        List<ObjectNode> files = new ArrayList<>();
        java.io.File dir = dirPath.toFile();

        if (dir.exists() && dir.isDirectory()) {
            java.io.File[] children = dir.listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                });

                for (java.io.File file : children) {
                    String name = file.getName();
                    if (name.startsWith(".")) continue;
                    if (name.equals("node_modules") || name.equals("target") ||
                            name.equals("build") || name.equals("dist")) continue;

                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("id", file.getAbsolutePath());
                    node.put("name", file.getName());
                    node.put("path", file.getAbsolutePath());
                    node.put("type", file.isDirectory() ? "directory" : "file");
                    files.add(node);
                }
            }
        }

        return files;
    }

    private String readFile(Path filePath) {
        try {
            return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private ObjectNode parseBody(HttpExchange exchange) throws IOException {
        try (var is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) return null;
            return objectMapper.readValue(body, ObjectNode.class);
        }
    }

    private void sendSuccess(HttpExchange exchange, int statusCode, Object data) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        if (data != null) {
            response.set("data", objectMapper.valueToTree(data));
        }
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
