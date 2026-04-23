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
    
    public TaskWebSocketServer(int port) {
        super(new InetSocketAddress(port));
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
            super.stop();
            System.out.println("[TaskWebSocket] Server stopped");
        } catch (Exception e) {
            System.err.println("[TaskWebSocket] Error stopping: " + e.getMessage());
        }
    }
}
