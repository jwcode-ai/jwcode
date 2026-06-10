package com.jwcode.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * 会话管理 API - 从 WebSessionManager 动态获取真实会话数据
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
        String path = exchange.getRequestURI().getPath();

        // 设置 CORS 头
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        // 路由: /api/sessions/{id}/messages
        if ("GET".equalsIgnoreCase(method) && path.matches("/api/sessions/[^/]+/messages")) {
            String sessionId = path.replaceAll("/api/sessions/([^/]+)/messages", "$1");
            getSessionMessages(exchange, sessionId);
        } else if ("GET".equalsIgnoreCase(method) && !path.equals("/api/sessions")) {
            getSession(exchange, path);
        } else if ("GET".equalsIgnoreCase(method)) {
            listSessions(exchange);
        } else if ("POST".equalsIgnoreCase(method)) {
            createSession(exchange);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            deleteSession(exchange, path);
        } else {
            sendJsonResponse(exchange, 405, createError("Method not allowed"));
        }
    }
    
    /**
     * 列出所有会话 - 从 WebSessionManager 动态获取
     */
    private void listSessions(HttpExchange exchange) throws IOException {
        ArrayNode sessions = objectMapper.createArrayNode();

        for (WebSessionManager.WebSession session : sessionManager.getAllSessions()) {
            ObjectNode sessionNode = objectMapper.createObjectNode();
            sessionNode.put("id", session.getId());
            sessionNode.put("title", session.getTitle());
            sessionNode.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(
                Instant.ofEpochMilli(session.getCreatedAt())
            ));
            sessionNode.put("updatedAt", DateTimeFormatter.ISO_INSTANT.format(
                Instant.ofEpochMilli(session.getUpdatedAt())
            ));
            sessionNode.put("messageCount", session.getMessageCount());
            sessions.add(sessionNode);
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", sessions);

        sendJsonResponse(exchange, 200, response);
    }

    /**
     * 获取单个会话详情。
     */
    private void getSession(HttpExchange exchange, String path) throws IOException {
        String sessionId = path.substring(path.lastIndexOf('/') + 1);
        WebSessionManager.WebSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            sendJsonResponse(exchange, 404, createError("Session not found: " + sessionId));
            return;
        }
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", session.getId());
        data.put("title", session.getTitle());
        data.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(session.getCreatedAt())
        ));
        data.put("updatedAt", DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(session.getUpdatedAt())
        ));
        data.put("messageCount", session.getMessageCount());

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", data);
        sendJsonResponse(exchange, 200, response);
    }

    /**
     * 获取会话消息列表 — GET /api/sessions/{id}/messages
     */
    private void getSessionMessages(HttpExchange exchange, String sessionId) throws IOException {
        WebSessionManager.WebSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            sendJsonResponse(exchange, 404, createError("Session not found: " + sessionId));
            return;
        }

        ArrayNode messages = objectMapper.createArrayNode();
        for (String msgJson : session.getMessages()) {
            try {
                messages.add(objectMapper.readTree(msgJson));
            } catch (Exception e) {
                messages.add(msgJson);
            }
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", messages);
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * 创建新会话
     */
    private void createSession(HttpExchange exchange) throws IOException {
        String sessionId = sessionManager.generateSessionId();
        WebSessionManager.WebSession session = sessionManager.getOrCreateSession(sessionId);
        session.setTitle("新会话 " + sessionId);
        
        ObjectNode data = objectMapper.createObjectNode();
        data.put("sessionId", sessionId);
        data.put("title", session.getTitle());
        data.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(session.getCreatedAt())
        ));
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", data);
        response.put("message", "会话创建成功");
        
        sendJsonResponse(exchange, 201, response);
    }
    
    /**
     * 删除会话
     */
    private void deleteSession(HttpExchange exchange, String path) throws IOException {
        // 从路径中提取 sessionId，如 /api/sessions/session_001
        String sessionId = path.substring(path.lastIndexOf('/') + 1);
        
        if (sessionId.isEmpty() || "sessions".equals(sessionId)) {
            ObjectNode errResponse = objectMapper.createObjectNode();
            errResponse.put("success", false);
            errResponse.put("error", "需要提供 sessionId");
            sendJsonResponse(exchange, 400, errResponse);
            return;
        }
        
        sessionManager.removeSession(sessionId);
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("message", "会话已删除");
        response.put("sessionId", sessionId);
        
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
