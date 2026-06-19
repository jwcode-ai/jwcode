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
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

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

    // ==== auth ====

    private static String getServerToken() {
        String token = System.getProperty("jwcode.websocket.token");
        if (token != null && !token.isEmpty()) return token;
        token = System.getenv("JWCODE_WEBSOCKET_TOKEN");
        if (token != null && !token.isEmpty()) return token;
        try {
            java.nio.file.Path tokenFile = java.nio.file.Paths.get(
                System.getProperty("user.home"), ".jwcode", ".websocket_token");
            if (java.nio.file.Files.exists(tokenFile)) {
                return java.nio.file.Files.readString(tokenFile).trim();
            }
        } catch (Exception e) {
        }
        return null;
    }

    private boolean authenticateRequest(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if (token == null || token.isEmpty()) {
            token = exchange.getRequestHeaders().getFirst("X-API-Key");
        }
        if (token == null || token.isEmpty()) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    int eq = param.indexOf("=");
                    if (eq > 0 && "token".equals(param.substring(0, eq))) {
                        token = java.net.URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
        }
        String validToken = getServerToken();
        if (validToken == null || validToken.isEmpty()) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 401, "Authentication required: server token not configured");
                return false;
            }
            return true;
        }
        if (token == null || token.isEmpty() || !validToken.equals(token)) {
            sendError(exchange, 401, "Invalid or missing API token");
            return false;
        }
        return true;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // 设置 CORS 头
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && (origin.equals("http://localhost:8080")
            || origin.startsWith("http://localhost") || origin.startsWith("https://localhost")
            || origin.startsWith("http://127.0.0.1") || origin.startsWith("https://127.0.0.1"))) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        } else if (origin != null && !origin.isEmpty()) {
            Logger.getLogger(FilesHandler.class.getName()).warning("FilesHandler: cors reject origin=" + origin);
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().set("Vary", "Origin");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        if (!authenticateRequest(exchange)) {
            return;
        }

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.equals("/api/files/read")) {
                    handleReadFile(exchange);
                } else if (path.equals("/api/files/download")) {
                    handleDownload(exchange);
                } else {
                    handleListFiles(exchange);
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.equals("/api/files/upload")) {
                    handleUpload(exchange);
                } else {
                    handleCreateFile(exchange);
                }
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
        try {
            if (java.nio.file.Files.exists(resolved)) {
                resolved = resolved.toRealPath();
            }
        } catch (IOException e) {
            Logger.getLogger(FilesHandler.class.getName()).warning("FilesHandler: toRealPath failed " + resolved);
        }
        Path rootReal;
        try {
            rootReal = PROJECT_ROOT.toRealPath();
        } catch (IOException e) {
            rootReal = PROJECT_ROOT;
        }
        if (!resolved.startsWith(rootReal)) {
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

    /**
     * 文件下载：以二进制流返回指定文件，浏览器触发下载。
     * 支持任意二进制文件（图片、压缩包等）。
     */
    private void handleDownload(HttpExchange exchange) throws IOException {
        String filePath = getQueryParam(exchange, "path");
        if (filePath == null || filePath.isEmpty()) {
            sendError(exchange, 400, "Missing path parameter");
            return;
        }

        Path resolved = resolveAndValidate(filePath);
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            sendError(exchange, 404, "File not found");
            return;
        }

        byte[] bytes = Files.readAllBytes(resolved);
        String fileName = resolved.getFileName().toString();
        String contentType = Files.probeContentType(resolved);
        if (contentType == null) contentType = "application/octet-stream";

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition",
            "attachment; filename=\"" + fileName.replace("\"", "") + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * 文件上传：接收 JSON {path, content, base64}，写入工作区。
     * base64=true 时 content 为 base64 编码（支持二进制文件），否则按 UTF-8 文本处理。
     */
    private void handleUpload(HttpExchange exchange) throws IOException {
        ObjectNode input = parseBody(exchange);
        if (input == null || !input.hasNonNull("path")) {
            sendError(exchange, 400, "Missing path");
            return;
        }

        String filePath = input.get("path").asText();
        String content = input.hasNonNull("content") ? input.get("content").asText("") : "";
        boolean base64 = input.hasNonNull("base64") && input.get("base64").asBoolean(false);

        Path resolved = resolveAndValidate(filePath);
        try {
            Files.createDirectories(resolved.getParent());
            byte[] data = base64
                ? Base64.getDecoder().decode(content)
                : content.getBytes(StandardCharsets.UTF_8);
            Files.write(resolved, data);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("path", resolved.toString());
            payload.put("size", data.length);
            sendSuccess(exchange, 201, payload);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Invalid base64 content: " + e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Failed to upload file: " + e.getMessage());
        }
    }

    /** 从 query string 中提取指定参数（URL 解码），找不到返回 null。 */
    private String getQueryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0 && key.equals(part.substring(0, idx))) {
                return java.net.URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
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
