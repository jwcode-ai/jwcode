package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 日志文件管理 API 处理器
 * 提供日志文件列表、内容预览和下载功能
 *
 * 安全设计：所有操作限制在 {workspaceDir}/logs/ 目录下，防止目录逃逸攻击。
 */
public class LogsHandler implements HttpHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 日志目录相对于 workspace 的位置 */
    private static final String LOGS_DIR = "logs";

    /** 允许直接预览的日志文件扩展名 */
    private static final List<String> PREVIEW_EXTS = List.of(".log", ".txt", ".out", ".err");

    /** 文件读取大小限制（50MB） */
    private static final long MAX_READ_SIZE = 50L * 1024 * 1024;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            if (path.equals("/api/logs/download")) {
                handleDownload(exchange);
            } else if (path.equals("/api/logs/read")) {
                handleRead(exchange);
            } else {
                handleList(exchange);
            }
        } catch (SecurityException e) {
            sendError(exchange, 403, "Forbidden: " + e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 获取日志目录路径（相对于 workspace）
     */
    private Path getLogsDir() {
        String userDir = System.getProperty("user.dir");
        return Paths.get(userDir).toAbsolutePath().normalize().resolve(LOGS_DIR);
    }

    /**
     * 校验路径是否在日志目录下，防止目录逃逸
     */
    private Path resolveAndValidate(String fileName) {
        Path logsDir = getLogsDir();
        // 确保返回的是相对路径，防止绝对路径逃逸
        Path resolved = logsDir.resolve(fileName).normalize();
        if (!resolved.startsWith(logsDir)) {
            throw new SecurityException("Path escapes logs directory: " + fileName);
        }
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new SecurityException("File not found or not a regular file: " + fileName);
        }
        return resolved;
    }

    /**
     * GET /api/logs — 列出所有日志文件
     */
    private void handleList(HttpExchange exchange) throws IOException {
        Path logsDir = getLogsDir();
        List<ObjectNode> files = new ArrayList<>();

        if (Files.exists(logsDir) && Files.isDirectory(logsDir)) {
            try (Stream<Path> stream = Files.list(logsDir)) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> !p.getFileName().toString().startsWith("."))
                      .sorted(Comparator.comparingLong(p -> {
                          try { return Files.getLastModifiedTime((Path) p).toMillis(); } catch (IOException e) { return 0L; }
                      }).reversed())
                      .forEach(p -> {
                          try {
                              ObjectNode node = objectMapper.createObjectNode();
                              String name = p.getFileName().toString();
                              node.put("name", name);
                              node.put("size", Files.size(p));
                              node.put("modified", Files.getLastModifiedTime(p).toMillis());
                              node.put("previewable", isPreviewable(name));
                              files.add(node);
                          } catch (IOException ignored) {}
                      });
            }
        }

        sendSuccess(exchange, 200, files);
    }

    /**
     * GET /api/logs/read?file=xxx — 读取日志文件内容（支持分页，默认返回最新 1000 行）
     */
    private void handleRead(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String fileName = getQueryParam(query, "file");
        String maxLinesStr = getQueryParam(query, "maxLines");

        if (fileName == null || fileName.isEmpty()) {
            sendError(exchange, 400, "Missing file parameter");
            return;
        }

        Path resolved = resolveAndValidate(fileName);
        long fileSize = Files.size(resolved);
        if (fileSize > MAX_READ_SIZE) {
            sendError(exchange, 413, "File too large to preview (" + (fileSize / 1024 / 1024) + "MB, max 50MB)");
            return;
        }
        int maxLines = 1000;
        if (maxLinesStr != null && !maxLinesStr.isEmpty()) {
            try {
                maxLines = Math.max(1, Math.min(100000, Integer.parseInt(maxLinesStr)));
            } catch (NumberFormatException ignored) {}
        }

        try {
            byte[] rawBytes = Files.readAllBytes(resolved);
            Charset charset = detectCharset(rawBytes);
            String fullContent = new String(rawBytes, charset);
            String[] allLines = fullContent.split("\n", -1);
            int totalLines = allLines.length;
            String[] lines;
            int startLine;

            if (totalLines <= maxLines) {
                lines = allLines;
                startLine = 1;
            } else {
                lines = java.util.Arrays.copyOfRange(allLines, totalLines - maxLines, totalLines);
                startLine = totalLines - maxLines + 1;
            }

            ObjectNode data = objectMapper.createObjectNode();
            data.put("name", fileName);
            data.put("size", Files.size(resolved));
            data.put("modified", Files.getLastModifiedTime(resolved).toMillis());
            data.put("totalLines", totalLines);
            data.put("startLine", startLine);
            data.put("content", String.join("\n", lines));

            sendSuccess(exchange, 200, data);
        } catch (IOException e) {
            sendError(exchange, 500, "Failed to read file: " + e.getMessage());
        }
    }

    /**
     * GET /api/logs/download?file=xxx — 下载日志文件
     */
    private void handleDownload(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String fileName = getQueryParam(query, "file");

        if (fileName == null || fileName.isEmpty()) {
            sendError(exchange, 400, "Missing file parameter");
            return;
        }

        Path resolved = resolveAndValidate(fileName);

        byte[] data = Files.readAllBytes(resolved);
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition",
            "attachment; filename=\"" + fileName + "\"");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * 判断文件扩展名是否支持直接预览
     */
    private boolean isPreviewable(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = fileName.substring(dot).toLowerCase();
        return PREVIEW_EXTS.contains(ext);
    }

    /**
     * 检测文件字节流的字符编码。
     * 优先尝试 UTF-8（严格模式），如果解码失败则回退到系统默认编码。
     * 在中文 Windows 上，系统默认编码为 GBK，可以正确显示 GBK 编码的日志文件。
     */
    private Charset detectCharset(byte[] bytes) {
        int sampleLen = Math.min(bytes.length, 8192);
        byte[] sample = java.util.Arrays.copyOf(bytes, sampleLen);
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(sample));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return Charset.defaultCharset();
        }
    }

    /**
     * 从查询字符串中解析参数值
     */
    private String getQueryParam(String query, String param) {
        if (query == null) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = pair.substring(0, idx);
                if (key.equals(param)) {
                    return java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
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

