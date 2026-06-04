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
        ObjectNode r = objectMapper.createObjectNode();
        r.put("success", false);
        r.put("error", "Chat requires WebSocket upgrade");
        r.put("hint", "Use WebSocket ws://host:8081");
        sendJsonResponse(exchange, 426, r);
    }
    
    private void handleGetHistory(HttpExchange exchange) throws IOException {
        ObjectNode r = objectMapper.createObjectNode();
        r.put("success", true);
        r.putArray("messages");
        r.put("hint", "Session history via WebSocket");
        sendJsonResponse(exchange, 200, r);
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
