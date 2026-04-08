package com.jwcode.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 聊天 API 处理器
 */
public class ChatHandler implements HttpHandler {
    
    private final WebSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ChatHandler(WebSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if ("POST".equalsIgnoreCase(method)) {
            handleChat(exchange);
        } else if ("GET".equalsIgnoreCase(method)) {
            handleGetHistory(exchange);
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    private void handleChat(HttpExchange exchange) throws IOException {
        try {
            // 读取请求体
            String body = readRequestBody(exchange);
            ObjectNode json = objectMapper.readValue(body, ObjectNode.class);
            
            String message = json.get("message").asText();
            String sessionId = json.has("sessionId") ? json.get("sessionId").asText() : "default";
            
            // 处理消息（简化实现）
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("message", "收到: " + message);
            response.put("sessionId", sessionId);
            
            sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            sendJsonResponse(exchange, 500, createError(e.getMessage()));
        }
    }
    
    private void handleGetHistory(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        
        ObjectNode message1 = objectMapper.createObjectNode();
        message1.put("role", "user");
        message1.put("content", "你好");
        
        ObjectNode message2 = objectMapper.createObjectNode();
        message2.put("role", "assistant");
        message2.put("content", "你好！我是 JwCode Web。");
        
        response.putArray("messages").add(message1).add(message2);
        
        sendJsonResponse(exchange, 200, response);
    }
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
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
