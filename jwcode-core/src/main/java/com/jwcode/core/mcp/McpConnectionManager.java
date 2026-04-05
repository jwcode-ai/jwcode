package com.jwcode.core.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * McpConnectionManager - MCP 连接管理
 * 
 * 功能说明：
 * 管理 MCP 服务器的连接状态，包括连接、断开、重连等操作。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpConnectionManager {
    
    private final Map<String, McpConnection> connections;
    private final Map<String, McpConfig.McpServerConfig> serverConfigs;
    private final ConnectionListener listener;
    
    public McpConnectionManager() {
        this.connections = new ConcurrentHashMap<>();
        this.serverConfigs = new HashMap<>();
        this.listener = null;
    }
    
    public McpConnectionManager(ConnectionListener listener) {
        this.connections = new ConcurrentHashMap<>();
        this.serverConfigs = new HashMap<>();
        this.listener = listener;
    }
    
    /**
     * 注册服务器配置
     */
    public void registerServer(String name, McpConfig.McpServerConfig config) {
        serverConfigs.put(name, config);
        if (config.isAutoConnect()) {
            connect(name);
        }
    }
    
    /**
     * 连接到服务器
     */
    public CompletableFuture<Boolean> connect(String serverName) {
        McpConfig.McpServerConfig config = serverConfigs.get(serverName);
        if (config == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        if (connections.containsKey(serverName)) {
            McpConnection existing = connections.get(serverName);
            if (existing.isConnected()) {
                return CompletableFuture.completedFuture(true);
            }
        }
        
        McpConnection connection = new McpConnection(serverName, config);
        connections.put(serverName, connection);
        
        return connection.connect()
            .thenApply(success -> {
                if (success && listener != null) {
                    listener.onConnected(serverName);
                }
                return success;
            });
    }
    
    /**
     * 断开与服务器的连接
     */
    public CompletableFuture<Void> disconnect(String serverName) {
        McpConnection connection = connections.get(serverName);
        if (connection != null) {
            return connection.disconnect()
                .thenRun(() -> {
                    connections.remove(serverName);
                    if (listener != null) {
                        listener.onDisconnected(serverName);
                    }
                });
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 重连服务器
     */
    public CompletableFuture<Boolean> reconnect(String serverName) {
        return disconnect(serverName)
            .thenCompose(v -> connect(serverName));
    }
    
    /**
     * 断开所有连接
     */
    public CompletableFuture<Void> disconnectAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String serverName : connections.keySet()) {
            futures.add(disconnect(serverName));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * 获取连接状态
     */
    public ConnectionStatus getConnectionStatus(String serverName) {
        McpConnection connection = connections.get(serverName);
        if (connection == null) {
            return ConnectionStatus.DISCONNECTED;
        }
        return connection.isConnected() ? ConnectionStatus.CONNECTED : ConnectionStatus.CONNECTING;
    }
    
    /**
     * 获取所有连接状态
     */
    public Map<String, ConnectionStatus> getAllConnectionStatuses() {
        Map<String, ConnectionStatus> statuses = new HashMap<>();
        for (String name : serverConfigs.keySet()) {
            statuses.put(name, getConnectionStatus(name));
        }
        return statuses;
    }
    
    /**
     * 获取已连接的服务器列表
     */
    public List<String> getConnectedServers() {
        List<String> connected = new ArrayList<>();
        for (Map.Entry<String, McpConnection> entry : connections.entrySet()) {
            if (entry.getValue().isConnected()) {
                connected.add(entry.getKey());
            }
        }
        return connected;
    }
    
    /**
     * 连接状态枚举
     */
    public enum ConnectionStatus {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
        ERROR
    }
    
    /**
     * 连接监听器接口
     */
    public interface ConnectionListener {
        void onConnected(String serverName);
        void onDisconnected(String serverName);
        void onError(String serverName, Throwable error);
    }
    
    /**
     * MCP 连接内部类
     */
    private static class McpConnection {
        private final String serverName;
        private final McpConfig.McpServerConfig config;
        private volatile boolean connected;
        private volatile boolean connecting;
        
        McpConnection(String serverName, McpConfig.McpServerConfig config) {
            this.serverName = serverName;
            this.config = config;
            this.connected = false;
            this.connecting = false;
        }
        
        CompletableFuture<Boolean> connect() {
            connecting = true;
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // 模拟连接过程
                    Thread.sleep(1000);
                    connected = true;
                    connecting = false;
                    return true;
                } catch (Exception e) {
                    connecting = false;
                    return false;
                }
            });
        }
        
        CompletableFuture<Void> disconnect() {
            return CompletableFuture.runAsync(() -> {
                connected = false;
                connecting = false;
            });
        }
        
        boolean isConnected() {
            return connected;
        }
        
        boolean isConnecting() {
            return connecting;
        }
    }
}