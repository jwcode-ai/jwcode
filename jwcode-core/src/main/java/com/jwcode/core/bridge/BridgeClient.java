package com.jwcode.core.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Bridge Client - 远程桥接客户端
 * 
 * 用于连接到远程 JwCode Bridge 服务器
 */
public class BridgeClient {
    
    private static final Logger logger = Logger.getLogger(BridgeClient.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String serverUrl;
    private String sessionId;
    private boolean connected = false;
    
    public BridgeClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
    }
    
    /**
     * 连接到服务器
     */
    public boolean connect() {
        try {
            URL url = new URL(serverUrl + "bridge/connect");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    String response = scanner.useDelimiter("\\A").next();
                    ObjectNode json = objectMapper.readValue(response, ObjectNode.class);
                    
                    if (json.get("success").asBoolean()) {
                        sessionId = json.get("sessionId").asText();
                        connected = true;
                        logger.info("连接到 Bridge Server: " + sessionId);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("连接失败: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * 发送消息
     */
    public String sendMessage(String message) {
        if (!connected) {
            return "未连接";
        }
        
        try {
            URL url = new URL(serverUrl + "bridge/message");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            
            ObjectNode request = objectMapper.createObjectNode();
            request.put("sessionId", sessionId);
            request.put("message", message);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(request));
            }
            
            try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                String response = scanner.useDelimiter("\\A").next();
                ObjectNode json = objectMapper.readValue(response, ObjectNode.class);
                
                if (json.get("success").asBoolean()) {
                    return json.get("response").asText();
                } else {
                    return "错误: " + json.get("error").asText();
                }
            }
            
        } catch (Exception e) {
            logger.severe("发送消息失败: " + e.getMessage());
            return "错误: " + e.getMessage();
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        connected = false;
        sessionId = null;
        logger.info("断开连接");
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public String getSessionId() {
        return sessionId;
    }
}
