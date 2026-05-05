package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket Server for real-time task updates
 * 
 * Provides WebSocket endpoint for real-time communication
 * Port: 8081
 */
public class TaskWebSocketServer extends WebSocketServer {
    
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    private final CopyOnWriteArraySet<WebSocket> connections = new CopyOnWriteArraySet<>();
    
    // 心跳定时器
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-heartbeat");
        t.setDaemon(true);
        return t;
    });
    
    // 心跳间隔（秒）
    private static final int HEARTBEAT_INTERVAL = 25;
    
    public TaskWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        // 设置连接超时检测，防止 java-websocket 自动断开空闲连接
        // 0 表示禁用超时检测，由我们自己的心跳机制管理
        this.setConnectionLostTimeout(0);
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
        System.out.println("[TaskWebSocket] New connection: " + conn.getRemoteSocketAddress());
        
        // Send welcome message
        sendMessage(conn, Map.of(
            "type", "connected",
            "message", "Connected to Task WebSocket Server"
        ));
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        System.out.println("[TaskWebSocket] Connection closed: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[TaskWebSocket] Message from " + conn.getRemoteSocketAddress() + ": " + message);
        
        try {
            // Echo back as acknowledgment
            sendMessage(conn, Map.of(
                "type", "ack",
                "original", message
            ));
        } catch (Exception e) {
            System.err.println("[TaskWebSocket] Error processing message: " + e.getMessage());
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[TaskWebSocket] Error: " + ex.getMessage());
        if (conn != null) {
            connections.remove(conn);
        }
    }
    
    @Override
    public void onStart() {
        System.out.println("[TaskWebSocket] Server started on port " + getPort());
        // 启动心跳定时器，定期发送 ping 消息保持连接活跃
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!connections.isEmpty()) {
                    broadcast(Map.of("type", "ping", "data", String.valueOf(System.currentTimeMillis())));
                }
            } catch (Exception e) {
                System.err.println("[TaskWebSocket] Heartbeat error: " + e.getMessage());
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
        System.out.println("[TaskWebSocket] Heartbeat started (interval: " + HEARTBEAT_INTERVAL + "s)");
    }
    
    /**
     * Send message to a specific connection
     */
    private void sendMessage(WebSocket conn, Map<String, Object> data) {
        try {
            String json = MAPPER.writeValueAsString(data);
            conn.send(json);
        } catch (Exception e) {
            System.err.println("[TaskWebSocket] Error sending message: " + e.getMessage());
        }
    }
    
    /**
     * Broadcast message to all connected clients
     */
    public void broadcast(Map<String, Object> data) {
        try {
            String json = MAPPER.writeValueAsString(data);
            for (WebSocket conn : connections) {
                if (conn.isOpen()) {
                    conn.send(json);
                }
            }
        } catch (Exception e) {
            System.err.println("[TaskWebSocket] Error broadcasting: " + e.getMessage());
        }
    }
    
    /**
     * Broadcast task update
     */
    public void broadcastTaskUpdate(String taskId, String action, Object taskData) {
        broadcast(Map.of(
            "type", "task_update",
            "action", action,
            "taskId", taskId,
            "data", taskData
        ));
    }
    
    /**
     * Broadcast log message to all connected clients
     */
    public void broadcastLog(String level, String source, String message) {
        broadcast(Map.of(
            "type", "log",
            "data", Map.of(
                "level", level,
                "source", source,
                "message", message,
                "timestamp", System.currentTimeMillis()
            )
        ));
    }
    
    /**
     * Get number of connected clients
     */
    public int getConnectionCount() {
        return connections.size();
    }
    
    /**
     * Stop server
     */
    @Override
    public void stop() {
        try {
            // 关闭心跳定时器
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            super.stop();
            System.out.println("[TaskWebSocket] Server stopped");
        } catch (Exception e) {
            System.err.println("[TaskWebSocket] Error stopping: " + e.getMessage());
        }
    }
}
