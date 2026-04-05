package com.jwcode.core.bridge;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.query.QueryEngine;
import com.jwcode.core.session.Session;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Bridge Server - 桥接模式服务器
 * 
 * 允许远程客户端通过 HTTP/WebSocket 连接到本地 JwCode 实例
 * 参照 Claude Code 的桥接模式
 */
public class BridgeServer {
    
    private static final Logger logger = Logger.getLogger(BridgeServer.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private HttpServer server;
    private final int port;
    private final Map<String, BridgeSession> sessions = new ConcurrentHashMap<>();
    private QueryEngine queryEngine;
    
    public BridgeServer(int port) {
        this.port = port;
    }
    
    /**
     * 启动桥接服务器
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 注册路由
        server.createContext("/bridge/connect", new ConnectHandler());
        server.createContext("/bridge/message", new MessageHandler());
        server.createContext("/bridge/stream", new StreamHandler());
        server.createContext("/bridge/status", new StatusHandler());
        
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        
        logger.info("Bridge Server 启动: http://localhost:" + port);
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            // 关闭所有会话
            sessions.values().forEach(BridgeSession::close);
            sessions.clear();
            
            server.stop(0);
            logger.info("Bridge Server 已停止");
        }
    }
    
    /**
     * 设置 QueryEngine
     */
    public void setQueryEngine(QueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }
    
    // ============ HTTP 处理器 ============
    
    /**
     * 连接处理器
     */
    class ConnectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            try {
                // 创建新会话
                String sessionId = generateSessionId();
                BridgeSession session = new BridgeSession(sessionId);
                sessions.put(sessionId, session);
                
                ObjectNode response = objectMapper.createObjectNode();
                response.put("success", true);
                response.put("sessionId", sessionId);
                response.put("message", "连接成功");
                
                sendJson(exchange, 200, response);
                logger.info("新会话连接: " + sessionId);
                
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    /**
     * 消息处理器
     */
    class MessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            try {
                String body = readBody(exchange);
                ObjectNode request = objectMapper.readValue(body, ObjectNode.class);
                
                String sessionId = request.get("sessionId").asText();
                String message = request.get("message").asText();
                
                BridgeSession session = sessions.get(sessionId);
                if (session == null) {
                    sendError(exchange, 404, "会话不存在");
                    return;
                }
                
                // 处理消息
                String response = processMessage(session, message);
                
                ObjectNode result = objectMapper.createObjectNode();
                result.put("success", true);
                result.put("response", response);
                
                sendJson(exchange, 200, result);
                
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        }
    }
    
    /**
     * 流式响应处理器 (SSE)
     */
    class StreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);
            
            try (OutputStream os = exchange.getResponseBody()) {
                // 发送 SSE 头部
                String sessionId = generateSessionId();
                sendSSE(os, "connected", "{\"sessionId\": \"" + sessionId + "\"}");
                
                // 保持连接，定期发送心跳
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(1000);
                    sendSSE(os, "heartbeat", "{\"time\": " + System.currentTimeMillis() + "}");
                }
                
                sendSSE(os, "completed", "{}");
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 状态处理器
     */
    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ObjectNode status = objectMapper.createObjectNode();
            status.put("status", "running");
            status.put("activeSessions", sessions.size());
            status.put("port", port);
            
            sendJson(exchange, 200, status);
        }
    }
    
    // ============ 辅助方法 ============
    
    private String processMessage(BridgeSession session, String message) {
        // 简化实现，实际应调用 QueryEngine
        return "收到消息: " + message;
    }
    
    private void sendSSE(OutputStream os, String event, String data) throws IOException {
        String sse = "event: " + event + "\ndata: " + data + "\n\n";
        os.write(sse.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }
    
    private void sendJson(HttpExchange exchange, int statusCode, ObjectNode json) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(json);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }
    
    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    private String generateSessionId() {
        return "bridge_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
}
