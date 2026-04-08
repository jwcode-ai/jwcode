package com.jwcode.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

/**
 * 会话管理 API
 */
public class SessionsHandler implements HttpHandler {
    
    private final WebSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public SessionsHandler(WebSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if ("GET".equalsIgnoreCase(method)) {
            listSessions(exchange);
        } else if ("POST".equalsIgnoreCase(method)) {
            createSession(exchange);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            deleteSession(exchange);
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    private void listSessions(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        
        ArrayNode sessions = response.putArray("sessions");
        
        // 添加示例会话
        ObjectNode session1 = objectMapper.createObjectNode();
        session1.put("id", "session_001");
        session1.put("title", "分析项目架构");
        session1.put("createdAt", "2026-04-05T10:00:00Z");
        sessions.add(session1);
        
        ObjectNode session2 = objectMapper.createObjectNode();
        session2.put("id", "session_002");
        session2.put("title", "修复 Bug");
        session2.put("createdAt", "2026-04-05T09:30:00Z");
        sessions.add(session2);
        
        sendJsonResponse(exchange, 200, response);
    }
    
    private void createSession(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("sessionId", "session_" + System.currentTimeMillis());
        response.put("message", "会话创建成功");
        sendJsonResponse(exchange, 201, response);
    }
    
    private void deleteSession(HttpExchange exchange) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "会话已删除");
        sendJsonResponse(exchange, 200, response);
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
