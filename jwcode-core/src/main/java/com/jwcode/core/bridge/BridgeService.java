package com.jwcode.core.bridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BridgeService - Bridge 远程服务
 * 
 * 功能说明：
 * 提供与远程 Bridge API 的通信能力，支持远程会话管理、文件传输、命令执行等功能。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class BridgeService {
    
    private static final String DEFAULT_BRIDGE_URL = "http://localhost:8080/bridge";
    
    private final ExecutorService executor;
    private final Map<String, BridgeSession> activeSessions;
    private String bridgeUrl;
    private volatile boolean connected;
    private String authToken;
    
    public BridgeService() {
        this(DEFAULT_BRIDGE_URL);
    }
    
    public BridgeService(String bridgeUrl) {
        this.executor = Executors.newFixedThreadPool(4);
        this.bridgeUrl = bridgeUrl;
        this.activeSessions = new ConcurrentHashMap<>();
        this.connected = false;
    }
    
    /**
     * 连接到 Bridge 服务器
     */
    public CompletableFuture<BridgeResult> connect(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.authToken = token;
                BridgeRequest request = new BridgeRequest("connect");
                request.params = Map.of("token", token, "client", "jwcode", "version", "1.0.0");
                
                BridgeResponse response = sendRequest(request);
                
                if (response.success) {
                    this.connected = true;
                    return new BridgeResult(true, "已连接到 Bridge 服务器");
                } else {
                    return new BridgeResult(false, "连接失败：" + response.error);
                }
            } catch (Exception e) {
                return new BridgeResult(false, "连接异常：" + e.getMessage());
            }
        }, executor);
    }
    
    /**
     * 断开连接
     */
    public CompletableFuture<BridgeResult> disconnect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BridgeRequest request = new BridgeRequest("disconnect");
                sendRequest(request);
                
                this.connected = false;
                this.authToken = null;
                
                // 关闭所有活动会话
                for (BridgeSession session : activeSessions.values()) {
                    session.close();
                }
                activeSessions.clear();
                
                return new BridgeResult(true, "已断开连接");
            } catch (Exception e) {
                return new BridgeResult(false, "断开连接异常：" + e.getMessage());
            }
        }, executor);
    }
    
    /**
     * 创建远程会话
     */
    public CompletableFuture<BridgeSession> createSession(String targetHost, int targetPort) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BridgeRequest request = new BridgeRequest("create_session");
                request.params = Map.of(
                        "host", targetHost,
                        "port", String.valueOf(targetPort)
                );
                
                BridgeResponse response = sendRequest(request);
                
                if (response.success) {
                    String sessionId = (String) response.data.get("sessionId");
                    BridgeSession session = new BridgeSession(sessionId, targetHost, targetPort);
                    activeSessions.put(sessionId, session);
                    return session;
                } else {
                    throw new RuntimeException("创建会话失败：" + response.error);
                }
            } catch (Exception e) {
                throw new RuntimeException("创建会话异常：" + e.getMessage());
            }
        }, executor);
    }
    
    /**
     * 执行远程命令
     */
    public CompletableFuture<BridgeResult> executeCommand(String sessionId, String command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BridgeSession session = activeSessions.get(sessionId);
                if (session == null) {
                    return new BridgeResult(false, "会话不存在：" + sessionId);
                }
                
                BridgeRequest request = new BridgeRequest("execute");
                request.params = Map.of(
                        "sessionId", sessionId,
                        "command", command
                );
                
                BridgeResponse response = sendRequest(request);
                
                if (response.success) {
                    String output = (String) response.data.getOrDefault("output", "");
                    session.lastOutput = output;
                    return new BridgeResult(true, output);
                } else {
                    return new BridgeResult(false, response.error);
                }
            } catch (Exception e) {
                return new BridgeResult(false, "执行命令异常：" + e.getMessage());
            }
        }, executor);
    }
    
    /**
     * 传输文件到远程
     */
    public CompletableFuture<BridgeResult> transferFile(String sessionId, String localPath, String remotePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BridgeRequest request = new BridgeRequest("transfer_file");
                request.params = Map.of(
                        "sessionId", sessionId,
                        "localPath", localPath,
                        "remotePath", remotePath
                );
                
                BridgeResponse response = sendRequest(request);
                
                if (response.success) {
                    return new BridgeResult(true, "文件传输完成");
                } else {
                    return new BridgeResult(false, response.error);
                }
            } catch (Exception e) {
                return new BridgeResult(false, "文件传输异常：" + e.getMessage());
            }
        }, executor);
    }
    
    /**
     * 获取会话状态
     */
    public CompletableFuture<BridgeSessionInfo> getSessionStatus(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BridgeSession session = activeSessions.get(sessionId);
                if (session == null) {
                    return null;
                }
                
                BridgeRequest request = new BridgeRequest("session_status");
                request.params = Map.of("sessionId", sessionId);
                
                BridgeResponse response = sendRequest(request);
                
                if (response.success) {
                    BridgeSessionInfo info = new BridgeSessionInfo();
                    info.sessionId = sessionId;
                    info.host = session.host;
                    info.port = session.port;
                    info.connected = Boolean.TRUE.equals(response.data.get("connected"));
                    info.lastActivity = (String) response.data.getOrDefault("lastActivity", "");
                    return info;
                } else {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }, executor);
    }
    
    /**
     * 关闭会话
     */
    public CompletableFuture<BridgeResult> closeSession(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BridgeSession session = activeSessions.remove(sessionId);
                if (session != null) {
                    session.close();
                }
                
                BridgeRequest request = new BridgeRequest("close_session");
                request.params = Map.of("sessionId", sessionId);
                
                BridgeResponse response = sendRequest(request);
                
                if (response.success) {
                    return new BridgeResult(true, "会话已关闭");
                } else {
                    return new BridgeResult(false, response.error);
                }
            } catch (Exception e) {
                return new BridgeResult(false, "关闭会话异常：" + e.getMessage());
            }
        }, executor);
    }
    
    /**
     * 发送请求
     */
    private BridgeResponse sendRequest(BridgeRequest request) throws IOException {
        URL url = new URL(bridgeUrl + "/api/v1/" + request.action);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        
        String jsonBody = request.toJson();
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        String responseBody;
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            responseBody = response.toString();
        }
        
        conn.disconnect();
        
        return BridgeResponse.fromJson(responseBody);
    }
    
    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * 获取活动会话数量
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        disconnect().join();
        executor.shutdown();
    }
    
    /**
     * Bridge 请求类
     */
    public static class BridgeRequest {
        public String action;
        public Map<String, String> params;
        public long timestamp;
        
        public BridgeRequest(String action) {
            this.action = action;
            this.params = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\"action\":\"").append(action).append("\"");
            json.append(",\"timestamp\":").append(timestamp);
            json.append(",\"params\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                first = false;
            }
            json.append("}}");
            return json.toString();
        }
    }
    
    /**
     * Bridge 响应类
     */
    public static class BridgeResponse {
        public boolean success;
        public String error;
        public Map<String, Object> data;
        
        public static BridgeResponse fromJson(String json) {
            BridgeResponse response = new BridgeResponse();
            response.data = new HashMap<>();
            
            // 简单解析 JSON
            if (json.contains("\"success\":true")) {
                response.success = true;
            } else if (json.contains("\"success\":false")) {
                response.success = false;
            } else {
                response.success = false;
            }
            
            // 解析错误信息
            int errorStart = json.indexOf("\"error\":");
            if (errorStart != -1) {
                int valueStart = json.indexOf("\"", errorStart + 8) + 1;
                int valueEnd = json.indexOf("\"", valueStart);
                if (valueEnd != -1) {
                    response.error = json.substring(valueStart, valueEnd);
                }
            }
            
            return response;
        }
    }
    
    /**
     * Bridge 会话类
     */
    public static class BridgeSession {
        public final String sessionId;
        public final String host;
        public final int port;
        public String lastOutput;
        public boolean active;
        
        public BridgeSession(String sessionId, String host, int port) {
            this.sessionId = sessionId;
            this.host = host;
            this.port = port;
            this.active = true;
        }
        
        public void close() {
            this.active = false;
        }
    }
    
    /**
     * Bridge 会话信息
     */
    public static class BridgeSessionInfo {
        public String sessionId;
        public String host;
        public int port;
        public boolean connected;
        public String lastActivity;
    }
    
    /**
     * Bridge 结果类
     */
    public static class BridgeResult {
        public final boolean success;
        public final String message;
        
        public BridgeResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}