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
     * MCP 连接内部类 — 实现真实传输层连接
     */
    private static class McpConnection {
        private final String serverName;
        private final McpConfig.McpServerConfig config;
        private volatile boolean connected;
        private volatile boolean connecting;
        private Process stdioProcess;
        private java.net.HttpURLConnection httpConnection;

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
                    boolean success = doConnect();
                    connected = success;
                    connecting = false;
                    return success;
                } catch (Exception e) {
                    connecting = false;
                    return false;
                }
            });
        }

        private boolean doConnect() {
            String type = config.getType();
            if ("sse".equalsIgnoreCase(type) || "http".equalsIgnoreCase(type)) {
                return connectSse();
            } else if ("stdio".equalsIgnoreCase(type)) {
                return connectStdio();
            } else if ("websocket".equalsIgnoreCase(type) || "ws".equalsIgnoreCase(type)) {
                return connectWebSocket();
            }
            // 未知类型，默认尝试 HTTP
            return connectSse();
        }

        private boolean connectSse() {
            try {
                java.net.URL url = new java.net.URL(config.getCommand());
                httpConnection = (java.net.HttpURLConnection) url.openConnection();
                httpConnection.setRequestMethod("GET");
                httpConnection.setConnectTimeout(5000);
                httpConnection.setReadTimeout(5000);
                httpConnection.setRequestProperty("Accept", "text/event-stream");
                int responseCode = httpConnection.getResponseCode();
                return responseCode >= 200 && responseCode < 300;
            } catch (Exception e) {
                return false;
            }
        }

        private boolean connectStdio() {
            try {
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(config.getCommand());
                pb.inheritIO();
                if (!config.getArgs().isEmpty()) {
                    pb.command().addAll(config.getArgs());
                }
                pb.environment().putAll(config.getEnv());
                stdioProcess = pb.start();
                // 等待进程启动
                Thread.sleep(500);
                return stdioProcess.isAlive();
            } catch (Exception e) {
                return false;
            }
        }

        private boolean connectWebSocket() {
            try {
                String url = config.getCommand();
                java.net.URI uri = new java.net.URI(url.replace("ws://", "http://").replace("wss://", "https://"));
                java.net.Socket socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 80), 5000);
                boolean reachable = socket.isConnected();
                socket.close();
                return reachable;
            } catch (Exception e) {
                return false;
            }
        }

        CompletableFuture<Void> disconnect() {
            return CompletableFuture.runAsync(() -> {
                connected = false;
                connecting = false;
                if (stdioProcess != null && stdioProcess.isAlive()) {
                    stdioProcess.destroy();
                }
                if (httpConnection != null) {
                    httpConnection.disconnect();
                }
            });
        }

        boolean isConnected() {
            if (connected && stdioProcess != null) {
                return stdioProcess.isAlive();
            }
            return connected;
        }

        boolean isConnecting() {
            return connecting;
        }
    }
}