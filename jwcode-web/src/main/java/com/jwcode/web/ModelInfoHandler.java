package com.jwcode.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ModelInfoHandler - 模型信息 API 处理器
 */
public class ModelInfoHandler implements HttpHandler {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        if ("OPTIONS".equals(method)) {
            handleOptions(exchange);
            return;
        }
        
        try {
            if ("GET".equals(method)) {
                if ("/api/models".equals(path)) {
                    handleGetModels(exchange);
                } else if ("/api/models/status".equals(path)) {
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
    
    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }
    
    private void handleGetModels(HttpExchange exchange) throws IOException {
        JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
        List<Map<String, Object>> models = new ArrayList<>();
        
        if (config != null && config.getDefaultProvider() != null) {
            JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
            JwcodeConfig.ModelDefinition defaultModel = config.getDefaultModel();
            String providerName = config.getDefaultProviderName();
            
            if (provider.getModels() != null) {
                for (JwcodeConfig.ModelDefinition model : provider.getModels()) {
                    Map<String, Object> modelInfo = new HashMap<>();
                    modelInfo.put("id", model.getId());
                    modelInfo.put("name", model.getName());
                    modelInfo.put("enabled", model.isEnabled());
                    modelInfo.put("maxTokens", model.getMaxTokens());
                    modelInfo.put("temperature", model.getTemperature());
                    modelInfo.put("contextWindow", model.getContextWindow());
                    modelInfo.put("isDefault", defaultModel != null && model.getId().equals(defaultModel.getId()));
                    modelInfo.put("provider", providerName);
                    modelInfo.put("status", model.isEnabled() ? "online" : "offline");
                    
                    if (model.getCost() != null) {
                        Map<String, Object> costInfo = new HashMap<>();
                        costInfo.put("input", model.getCost().getInput());
                        costInfo.put("output", model.getCost().getOutput());
                        costInfo.put("cacheRead", model.getCost().getCacheRead());
                        costInfo.put("cacheWrite", model.getCost().getCacheWrite());
                        modelInfo.put("cost", costInfo);
                        modelInfo.put("price", costInfo);
                    }
                    
                    modelInfo.put("load", 0);
                    modelInfo.put("maxLoad", 100);
                    modelInfo.put("tokens", 0);
                    
                    models.add(modelInfo);
                }
            }
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("models", models);
        data.put("defaultProvider", config != null ? config.getDefaultProvider() : null);
        
        sendSuccess(exchange, 200, data);
    }
    
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
        
        sendSuccess(exchange, 200, status);
    }
    
    private void sendSuccess(HttpExchange exchange, int statusCode, Object data) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        sendJson(exchange, statusCode, response);
    }
    
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }
    
    private void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = mapper.writeValueAsString(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
