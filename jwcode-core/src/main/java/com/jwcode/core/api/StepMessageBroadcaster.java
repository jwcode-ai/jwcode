package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Step 消息广播器 - 将子Agent执行过程通过WebSocket发送到前端
 * 
 * 支持发送的消息类型：
 * - step_start: 子Agent任务开始
 * - step_thinking: 子Agent思考过程
 * - step_action: 子Agent执行动作
 * - step_complete: 子Agent任务完成
 */
public class StepMessageBroadcaster {
    
    private static final Logger logger = Logger.getLogger(StepMessageBroadcaster.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    // WebSocket服务器实例
    private static volatile TaskWebSocketServer wsServer;
    
    // 广播器单例
    private static volatile StepMessageBroadcaster instance;
    
    // 是否启用广播
    private boolean enabled = true;
    
    // 私有构造函数
    private StepMessageBroadcaster() {
    }
    
    /**
     * 获取单例实例
     */
    public static StepMessageBroadcaster getInstance() {
        if (instance == null) {
            synchronized (StepMessageBroadcaster.class) {
                if (instance == null) {
                    instance = new StepMessageBroadcaster();
                }
            }
        }
        return instance;
    }
    
    /**
     * 设置WebSocket服务器
     */
    public static void setWebSocketServer(TaskWebSocketServer server) {
        wsServer = server;
        logger.info("StepMessageBroadcaster: WebSocket server configured");
    }
    
    /**
     * 启用/禁用广播
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("StepMessageBroadcaster: enabled=" + enabled);
    }
    
    /**
     * 发送 step_start 消息
     */
    public void broadcastStepStart(String taskId, String stepName, String description) {
        if (!enabled || wsServer == null) {
            return;
        }
        
        try {
            Map<String, Object> data = Map.of(
                "step", stepName,
                "description", description != null ? description : "",
                "taskId", taskId,
                "status", "running"
            );
            
            broadcast("step_start", data);
            logger.fine("[StepBroadcaster] step_start: " + stepName);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast step_start", e);
        }
    }
    
    /**
     * 发送 step_thinking 消息
     */
    public void broadcastStepThinking(String taskId, String thought) {
        if (!enabled || wsServer == null) {
            return;
        }
        
        try {
            Map<String, Object> data = Map.of(
                "thought", thought != null ? thought : "",
                "taskId", taskId
            );
            
            broadcast("step_thinking", data);
            logger.fine("[StepBroadcaster] step_thinking: " + (thought != null && thought.length() > 50 ? thought.substring(0, 50) + "..." : thought));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast step_thinking", e);
        }
    }
    
    /**
     * 发送 step_action 消息
     */
    public void broadcastStepAction(String taskId, String action) {
        if (!enabled || wsServer == null) {
            return;
        }
        
        try {
            Map<String, Object> data = Map.of(
                "action", action != null ? action : "",
                "taskId", taskId
            );
            
            broadcast("step_action", data);
            logger.fine("[StepBroadcaster] step_action: " + action);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast step_action", e);
        }
    }
    
    /**
     * 发送 step_complete 消息
     */
    public void broadcastStepComplete(String taskId, String result, boolean success) {
        if (!enabled || wsServer == null) {
            return;
        }
        
        try {
            Map<String, Object> data = Map.of(
                "result", result != null ? result : "",
                "taskId", taskId,
                "status", success ? "success" : "error"
            );
            
            broadcast("step_complete", data);
            logger.fine("[StepBroadcaster] step_complete: " + taskId + ", success=" + success);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast step_complete", e);
        }
    }
    
    /**
     * 广播消息到WebSocket
     */
    private void broadcast(String type, Map<String, Object> data) {
        if (wsServer == null) {
            logger.warning("[StepBroadcaster] WebSocket server not configured");
            return;
        }
        
        try {
            String json = MAPPER.writeValueAsString(data);
            wsServer.broadcast(Map.of(
                "type", type,
                "data", json
            ));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast message: " + type, e);
        }
    }
    
    /**
     * 检查是否已配置
     */
    public static boolean isConfigured() {
        return wsServer != null;
    }
}