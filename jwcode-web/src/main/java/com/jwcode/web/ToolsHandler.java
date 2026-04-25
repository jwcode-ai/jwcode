package com.jwcode.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * 工具列表 API
 */
public class ToolsHandler implements HttpHandler {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolRegistry toolRegistry;
    
    public ToolsHandler(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
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
        
        if ("GET".equalsIgnoreCase(method)) {
            if (path.matches("/api/tools/[^/]+")) {
                String toolId = path.substring(path.lastIndexOf('/') + 1);
                getToolDetail(exchange, toolId);
            } else {
                listTools(exchange);
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            if (path.matches("/api/tools/[^/]+/toggle")) {
                String toolId = path.substring("/api/tools/".length(), path.lastIndexOf("/toggle"));
                toggleTool(exchange, toolId);
            } else {
                sendJsonResponse(exchange, 405, createError("不支持的端点"));
            }
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    private void listTools(HttpExchange exchange) throws IOException {
        ArrayNode data = objectMapper.createArrayNode();
        
        for (Tool<?, ?, ?> tool : toolRegistry.getAllTools()) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("id", tool.getName());
            toolNode.put("name", tool.getName());
            toolNode.put("description", tool.getDescription());
            toolNode.put("category", inferCategory(tool.getClass().getSimpleName()));
            toolNode.put("enabled", tool.isEnabled());
            
            // 提取参数 schema
            ArrayNode params = toolNode.putArray("params");
            JsonNode schema = tool.getInputSchema();
            if (schema != null && schema.has("properties")) {
                JsonNode properties = schema.get("properties");
                JsonNode required = schema.has("required") ? schema.get("required") : null;
                Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    ObjectNode param = params.addObject();
                    param.put("name", entry.getKey());
                    param.put("type", entry.getValue().has("type") ? entry.getValue().get("type").asText() : "string");
                    param.put("description", entry.getValue().has("description") ? entry.getValue().get("description").asText() : "");
                    param.put("required", isRequired(entry.getKey(), required));
                }
            }
            
            data.add(toolNode);
        }
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", data);
        sendJsonResponse(exchange, 200, response);
    }
    
    private void getToolDetail(HttpExchange exchange, String toolId) throws IOException {
        Tool<?, ?, ?> tool = toolRegistry.findByName(toolId).orElse(null);
        
        ObjectNode response = objectMapper.createObjectNode();
        if (tool == null) {
            response.put("success", false);
            response.put("error", "工具不存在: " + toolId);
            sendJsonResponse(exchange, 404, response);
            return;
        }
        
        ObjectNode toolNode = objectMapper.createObjectNode();
        toolNode.put("id", tool.getName());
        toolNode.put("name", tool.getName());
        toolNode.put("description", tool.getDescription());
        toolNode.put("category", inferCategory(tool.getClass().getSimpleName()));
        toolNode.put("enabled", tool.isEnabled());
        
        ArrayNode params = toolNode.putArray("params");
        JsonNode schema = tool.getInputSchema();
        if (schema != null && schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            JsonNode required = schema.has("required") ? schema.get("required") : null;
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                ObjectNode param = params.addObject();
                param.put("name", entry.getKey());
                param.put("type", entry.getValue().has("type") ? entry.getValue().get("type").asText() : "string");
                param.put("description", entry.getValue().has("description") ? entry.getValue().get("description").asText() : "");
                param.put("required", isRequired(entry.getKey(), required));
            }
        }
        
        response.put("success", true);
        response.set("data", toolNode);
        sendJsonResponse(exchange, 200, response);
    }
    
    private void toggleTool(HttpExchange exchange, String toolId) throws IOException {
        // 工具启用状态由 Tool 接口决定，暂不支持动态切换
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "工具状态切换请求已接收（当前版本工具始终启用）");
        sendJsonResponse(exchange, 200, response);
    }
    
    private boolean isRequired(String paramName, JsonNode requiredArray) {
        if (requiredArray == null || !requiredArray.isArray()) {
            return false;
        }
        for (JsonNode node : requiredArray) {
            if (node.asText().equals(paramName)) {
                return true;
            }
        }
        return false;
    }
    
    private String inferCategory(String className) {
        String lower = className.toLowerCase();
        if (lower.contains("file")) return "file";
        if (lower.contains("web") || lower.contains("search") || lower.contains("fetch")) return "web";
        if (lower.contains("bash") || lower.contains("shell") || lower.contains("powershell")) return "shell";
        if (lower.contains("task") || lower.contains("todo")) return "task";
        if (lower.contains("git")) return "git";
        if (lower.contains("agent")) return "agent";
        if (lower.contains("mcp") || lower.contains("lsp")) return "system";
        return "utility";
    }
    
    private void sendJsonResponse(HttpExchange exchange, int statusCode, ObjectNode json) throws IOException {
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
