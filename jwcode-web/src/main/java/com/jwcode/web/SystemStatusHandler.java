package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 系统状态 API 处理器
 * 提供系统运行状态、内存、模型统计等聚合信息
 */
public class SystemStatusHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(SystemStatusHandler.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static WebServer webServer;

    /**
     * Set the WebServer reference for restart support.
     */
    public static void setWebServer(WebServer server) {
        webServer = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // 设置 CORS 头
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        // POST /api/system/shutdown
        if (path.endsWith("/shutdown") && "POST".equalsIgnoreCase(method)) {
            handleShutdown(exchange);
            return;
        }

        // POST /api/system/restart
        if (path.endsWith("/restart") && "POST".equalsIgnoreCase(method)) {
            handleRestart(exchange);
            return;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("status", "running");
            
            // 运行时间（毫秒）
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            data.put("uptime", uptimeMs);
            
            // 内存信息
            ObjectNode memory = objectMapper.createObjectNode();
            Runtime runtime = Runtime.getRuntime();
            memory.put("used", runtime.totalMemory() - runtime.freeMemory());
            memory.put("total", runtime.totalMemory());
            memory.put("max", runtime.maxMemory());
            data.set("memory", memory);
            
            // 模型统计
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            int modelCount = 0;
            int onlineModels = 0;
            if (config != null && config.getDefaultProvider() != null && config.getDefaultProvider().getModels() != null) {
                var models = config.getDefaultProvider().getModels();
                modelCount = models.size();
                for (var m : models) {
                    if (m.isEnabled()) onlineModels++;
                }
            }
            
            ObjectNode modelsStatus = objectMapper.createObjectNode();
            modelsStatus.put("total", modelCount);
            modelsStatus.put("online", onlineModels);
            modelsStatus.put("offline", modelCount - onlineModels);
            data.set("models", modelsStatus);
            
            data.put("timestamp", System.currentTimeMillis());
            
            sendSuccess(exchange, 200, data);
        } catch (Exception e) {
            sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }
    
    private void handleRestart(HttpExchange exchange) throws IOException {
        if (webServer == null) {
            sendError(exchange, 500, "WebServer reference not available");
            return;
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "Server restarting...");

        byte[] bytes = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }

        // Restart in a new thread so the HTTP response is sent first
        new Thread(() -> {
            try {
                Thread.sleep(500);
                webServer.restart();
            } catch (Exception e) {
                LOGGER.warning("Restart failed: " + e.getMessage());
            }
        }, "server-restart").start();
    }

    private void handleShutdown(HttpExchange exchange) throws IOException {
        if (webServer == null) {
            sendError(exchange, 500, "WebServer reference not available");
            return;
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "Server shutting down...");

        byte[] bytes = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }

        // Shutdown in a new thread so the HTTP response is sent first
        new Thread(() -> {
            try {
                Thread.sleep(500);
                webServer.stop();
                LOGGER.info("Server stopped. Exiting JVM...");
            } catch (Exception e) {
                LOGGER.warning("Shutdown failed: " + e.getMessage());
            } finally {
                System.exit(0);
            }
        }, "server-shutdown").start();
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
