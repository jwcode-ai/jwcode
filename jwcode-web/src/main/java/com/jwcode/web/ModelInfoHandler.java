package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ModelInfoHandler - 模型信息 API 处理器
 * 
 * 提供模型配置信息、健康检查、统计信息等 REST API
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ModelInfoHandler implements HttpHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        // 处理 CORS 预检请求
        if ("OPTIONS".equals(method)) {
            handleOptions(exchange);
            return;
        }
        
        try {
            if ("GET".equals(method)) {
                if (path.equals("/api/models")) {
                    handleGetModels(exchange);
                } else if (path.equals("/api/models/status")) {
                    handleGetStatus(exchange);
                } else {
                    sendError(exchange, 404, "Not found: " + path);
                }
            } else {
                sendError(exchange, 405, "Method not allowed: " + method);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal error: " + e.getMessage());
        }
    }
    
    /**
     * 处理 CORS 预检请求
     */
    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }
    
    /**
     * 获取模型列表
     */
    private void handleGetModels(HttpExchange exchange) throws IOException {
        JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
        
        List<Map<String, Object>> models = new ArrayList<>();
        
        if (config != null && config.getDefaultProvider() != null) {
            JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
            JwcodeConfig.ModelDefinition defaultModel = config.getDefaultModel();
            
            if (provider.getModels() != null) {
                for (JwcodeConfig.ModelDefinition model : provider.getModels()) {
                    Map<String, Object> modelInfo = new HashMap<>();
                    modelInfo.put("id", model.getId());
                    modelInfo.put("name", model.getName());
                    modelInfo.put("enabled", model.isEnabled());
                    modelInfo.put("maxTokens", model.getMaxTokens());
                    modelInfo.put("temperature", model.getTemperature());
                    modelInfo.put("contextWindow", model.getContextWindow());
                    modelInfo.put("isDefault", defaultModel != null && 
                        model.getId().equals(defaultModel.getId()));
                    models.add(modelInfo);
                }
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("models", models);
        response.put("defaultProvider", config != null ? config.getDefaultProvider() : null);
        
        sendJson(exchange, 200, response);
    }
    
    /**
     * 获取状态
     */
    private void handleGetStatus(HttpExchange exchange) throws IOException {
        JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
        
        Map<String, Object> status = new HashMap<>();
        status.put("status", config != null ? "configured" : "not_configured");
        
        if (config != null) {
            JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
            if (provider != null) {
                status.put("provider", config.getDefaultProvider());
                status.put("baseUrl", provider.getBaseUrl());
                status.put("modelCount", provider.getModels() != null ? provider.getModels().size() : 0);
                status.put("hasApiKey", provider.getApiKeys() != null && !provider.getApiKeys().isEmpty());
            }
        }
        
        sendJson(exchange, 200, status);
    }
    
    private void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = mapper.writeValueAsString(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        
        // 添加 CORS 头部，允许浏览器访问
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }
}
