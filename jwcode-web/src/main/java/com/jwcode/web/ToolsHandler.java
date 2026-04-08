package com.jwcode.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolRegistry;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 工具列表 API
 */
public class ToolsHandler implements HttpHandler {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if ("GET".equalsIgnoreCase(method)) {
            listTools(exchange);
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    private void listTools(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        
        // 从 ToolRegistry 动态获取所有已注册的工具
        ToolRegistry registry = ToolRegistry.createDefault();
        response.put("count", registry.size());  // 显示工具总数
        
        ArrayNode tools = response.putArray("tools");
        
        for (Tool<?, ?, ?> tool : registry.getAllTools()) {
            // 从类名推断分类
            String className = tool.getClass().getSimpleName();
            String category = inferCategory(className);
            addTool(tools, tool.getName(), tool.getDescription(), category);
        }
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 根据类名推断工具分类
     */
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
    
    private void addTool(ArrayNode tools, String name, String description, String category) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("category", category);
        tools.add(tool);
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
