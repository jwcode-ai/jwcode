package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.Skill;
import com.jwcode.core.a2a.registry.A2ARegistry;
import com.jwcode.core.a2a.registry.AgentSession;
import com.jwcode.core.task.TaskStore;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket Server for real-time task updates
 * 
 * Provides WebSocket endpoint for real-time communication
 * Port: 8081
 * 
 * <p>增强功能：
 * <ul>
 *   <li>支持 Agent 注册（通过 URL 参数 agentId/agentType）</li>
 *   <li>支持按 agentId 定向推送消息</li>
 *   <li>支持按 sessionId 定向推送消息</li>
 *   <li>集成 A2ARegistry 管理 Agent 会话</li>
 *   <li>心跳检测与过期清理</li>
 * </ul>
 * </p>
 */
public class TaskWebSocketServer extends WebSocketServer {
    
    private static final Logger logger = Logger.getLogger(TaskWebSocketServer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    /** 所有 WebSocket 连接集合 */
    private final CopyOnWriteArraySet<WebSocket> connections = new CopyOnWriteArraySet<>();
    
    /** 按 connectionId 索引的 WebSocket 连接 */
    private final ConcurrentHashMap<String, WebSocket> connectionsById = new ConcurrentHashMap<>();
    
    /** 按 sessionId 索引的 WebSocket 连接 */
    private final ConcurrentHashMap<String, WebSocket> connectionsBySession = new ConcurrentHashMap<>();
    
    /** 按 agentId 索引的 WebSocket 连接 */
    private final ConcurrentHashMap<String, WebSocket> connectionsByAgent = new ConcurrentHashMap<>();
    
    /** WebSocket 连接的反向索引：conn -> connectionId */
    private final ConcurrentHashMap<WebSocket, String> connectionIds = new ConcurrentHashMap<>();
    
    /** 连接 ID 生成器 */
    private final AtomicLong idGenerator = new AtomicLong(0);
    
    // 心跳定时器
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-heartbeat");
        t.setDaemon(true);
        return t;
    });
    
    // 过期清理定时器
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-cleanup");
        t.setDaemon(true);
        return t;
    });
    
    // 心跳间隔（秒）
    private static final int HEARTBEAT_INTERVAL = 25;
    
    // 过期清理间隔（秒）
    private static final int CLEANUP_INTERVAL = 60;
    
    // A2A Registry
    private final A2ARegistry registry = A2ARegistry.getInstance();
    
    // WebSocket 消息处理器（用于处理 chat/plan 等业务消息）
    private WebSocketMessageHandler messageHandler;
    
    public TaskWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        // 设置连接超时检测，防止 java-websocket 自动断开空闲连接
        // 0 表示禁用超时检测，由我们自己的心跳机制管理
        this.setConnectionLostTimeout(0);
    }
    
    /**
     * 设置消息处理器
     */
    public void setMessageHandler(WebSocketMessageHandler handler) {
        this.messageHandler = handler;
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // 生成唯一连接 ID
        String connectionId = "conn-" + idGenerator.incrementAndGet();
        
        connections.add(conn);
        connectionIds.put(conn, connectionId);
        connectionsById.put(connectionId, conn);
        
        // 解析 URL 参数
        String query = handshake.getResourceDescriptor();
        Map<String, String> params = parseQueryParams(query);
        
        String agentId = params.get("agentId");
        String agentType = params.get("agentType");
        String sessionId = params.get("sessionId");
        String agentName = params.get("agentName");
        
        // 如果携带 sessionId，建立 session 映射
        if (sessionId != null && !sessionId.isEmpty()) {
            connectionsBySession.put(sessionId, conn);
            logger.info("[TaskWebSocket] Session mapped: " + sessionId + " -> " + connectionId);
        }
        
        // 如果携带 agentId，注册到 A2A Registry
        if (agentId != null && !agentId.isEmpty()) {
            connectionsByAgent.put(agentId, conn);
            
            // 构建 AgentCard 并注册到 Registry
            String name = agentName != null ? agentName : agentId;
            String type = agentType != null ? agentType : "generic";
            
            AgentCard card = AgentCard.builder()
                .name(name)
                .description("Agent connected via WebSocket: " + agentId)
                .agentType(type)
                .skills(parseSkills(params))
                .build();
            
            AgentSession session = AgentSession.builder()
                .agentName(name)
                .agentType(type)
                .agentCard(card)
                .connectionId(connectionId)
                .maxLoad(parseIntParam(params, "maxLoad", 3))
                .build();
            
            registry.register(session);
            logger.info("[TaskWebSocket] Agent registered: " + name + " (type=" + type + ")");
        }
        
        logger.info("[TaskWebSocket] New connection: " + conn.getRemoteSocketAddress() 
            + " id=" + connectionId
            + (agentId != null ? " agent=" + agentId : "")
            + (sessionId != null ? " session=" + sessionId : ""));
        
        // Send welcome message with connectionId
        sendMessage(conn, Map.of(
            "type", "connected",
            "connectionId", connectionId,
            "message", "Connected to Task WebSocket Server"
        ));
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String connectionId = connectionIds.remove(conn);
        connections.remove(conn);
        connectionsById.remove(connectionId);
        
        // 从 session 映射中移除
        connectionsBySession.values().remove(conn);
        
        // 从 agent 映射中移除并注销 Registry
        connectionsByAgent.values().remove(conn);
        if (connectionId != null) {
            registry.unregister(connectionId);
        }
        
        logger.info("[TaskWebSocket] Connection closed: " + conn.getRemoteSocketAddress() 
            + " id=" + connectionId);
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        String connId = connectionIds.get(conn);
        logger.fine("[TaskWebSocket] Message from " + connId + ": " + message);
        
        try {
            // 解析 JSON 消息
            @SuppressWarnings("unchecked")
            Map<String, Object> msgMap = MAPPER.readValue(message, Map.class);
            
            String type = (String) msgMap.getOrDefault("type", "");
            String sessionId = (String) msgMap.getOrDefault("sessionId", "");
            String msgContent = (String) msgMap.getOrDefault("message", "");
            
            // 处理心跳
            if ("pong".equals(type)) {
                if (connId != null) {
                    registry.heartbeat(connId);
                }
                return;
            }
            
            // 先回复 ack 确认收到
            sendMessage(conn, Map.of(
                "type", "ack",
                "original", message
            ));
            
            // 如果有消息处理器，将消息交给处理器处理
            if (messageHandler != null && !type.isEmpty()) {
                String finalType = type;
                String finalSessionId = sessionId;
                String finalMsg = msgContent;
                
                // 异步处理消息，避免阻塞 WebSocket 线程
                messageHandler.handleMessage(finalSessionId, finalType, finalMsg)
                    .thenAccept(result -> {
                        logger.fine("[TaskWebSocket] Message processed: type=" + finalType 
                            + ", sessionId=" + finalSessionId);
                    })
                    .exceptionally(e -> {
                        logger.warning("[TaskWebSocket] Message processing error: " + e.getMessage());
                        return null;
                    });
            } else if (messageHandler == null && !type.equals("ping")) {
                logger.fine("[TaskWebSocket] No message handler configured for type: " + type);
            }
        } catch (Exception e) {
            logger.warning("[TaskWebSocket] Error processing message: " + e.getMessage());
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.warning("[TaskWebSocket] Error: " + ex.getMessage());
        if (conn != null) {
            String connId = connectionIds.get(conn);
            connections.remove(conn);
            connectionIds.remove(conn);
            connectionsById.remove(connId);
            connectionsBySession.values().remove(conn);
            connectionsByAgent.values().remove(conn);
            if (connId != null) {
                registry.unregister(connId);
            }
        }
    }
    
    @Override
    public void onStart() {
        logger.info("[TaskWebSocket] Server started on port " + getPort());
        
        // 启动心跳定时器，定期发送 ping 消息保持连接活跃
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!connections.isEmpty()) {
                    broadcast(Map.of("type", "ping", "data", String.valueOf(System.currentTimeMillis())));
                }
            } catch (Exception e) {
                logger.warning("[TaskWebSocket] Heartbeat error: " + e.getMessage());
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
        
        // 启动过期清理定时器
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                int purged = registry.purgeExpiredSessions();
                if (purged > 0) {
                    logger.info("[TaskWebSocket] Cleanup: purged " + purged + " expired sessions");
                }
            } catch (Exception e) {
                logger.warning("[TaskWebSocket] Cleanup error: " + e.getMessage());
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.SECONDS);
        
        logger.info("[TaskWebSocket] Heartbeat started (interval: " + HEARTBEAT_INTERVAL + "s)");
        logger.info("[TaskWebSocket] Cleanup started (interval: " + CLEANUP_INTERVAL + "s)");
        
        // 注册 TaskStore 监听器 — 任务变更时自动广播到所有 WebSocket 客户端
        TaskStore.getInstance().addTaskListener(event -> {
            try {
                String action = event.getAction();
                com.jwcode.core.task.Task task = event.getTask();
                if (task != null) {
                    broadcastTaskUpdate(task.getId(), action, task);
                    logger.fine("[TaskWebSocket] Task broadcast: " + action + " " + task.getId());
                }
            } catch (Exception e) {
                logger.warning("[TaskWebSocket] Task broadcast error: " + e.getMessage());
            }
        });
        logger.info("[TaskWebSocket] TaskStore listener registered for real-time task push");
    }
    
    // ==================== 定向推送方法 ====================
    
    /**
     * 向指定 agentId 推送消息
     */
    public boolean sendToAgent(String agentId, Map<String, Object> data) {
        WebSocket conn = connectionsByAgent.get(agentId);
        if (conn != null && conn.isOpen()) {
            sendMessage(conn, data);
            return true;
        }
        logger.fine("[TaskWebSocket] Agent not found or disconnected: " + agentId);
        return false;
    }
    
    /**
     * 向指定 sessionId 推送消息
     */
    public boolean sendToSession(String sessionId, Map<String, Object> data) {
        WebSocket conn = connectionsBySession.get(sessionId);
        if (conn != null && conn.isOpen()) {
            sendMessage(conn, data);
            return true;
        }
        logger.fine("[TaskWebSocket] Session not found or disconnected: " + sessionId);
        return false;
    }
    
    /**
     * 向指定 connectionId 推送消息
     */
    public boolean sendToConnection(String connectionId, Map<String, Object> data) {
        WebSocket conn = connectionsById.get(connectionId);
        if (conn != null && conn.isOpen()) {
            sendMessage(conn, data);
            return true;
        }
        logger.fine("[TaskWebSocket] Connection not found: " + connectionId);
        return false;
    }
    
    /**
     * 向指定 WebSocket 连接推送消息
     */
    private void sendMessage(WebSocket conn, Map<String, Object> data) {
        try {
            String json = MAPPER.writeValueAsString(data);
            conn.send(json);
        } catch (Exception e) {
            String connId = connectionIds.get(conn);
            logger.log(Level.WARNING, "[TaskWebSocket] Error sending message to " +
                    (connId != null ? connId : conn.getRemoteSocketAddress()) +
                    ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast message to all connected clients
     * 
     * <p>每个连接单独 try/catch，防止单个连接异常中断整个广播循环。
     * 同时记录每个失败连接的 connectionId 便于诊断。</p>
     */
    public void broadcast(Map<String, Object> data) {
        String json;
        try {
            json = MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            logger.warning("[TaskWebSocket] Error serializing broadcast data: " + e.getMessage());
            return;
        }
        for (WebSocket conn : connections) {
            if (!conn.isOpen()) continue;
            try {
                conn.send(json);
            } catch (Exception e) {
                String connId = connectionIds.get(conn);
                logger.warning("[TaskWebSocket] Broadcast send failed to " +
                        (connId != null ? connId : conn.getRemoteSocketAddress()) +
                        ": " + e.getMessage());
            }
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
    
    // ==================== 查询方法 ====================
    
    /**
     * 获取 A2A Registry 实例
     */
    public A2ARegistry getRegistry() {
        return registry;
    }
    
    /**
     * 获取连接数量
     */
    public int getConnectionCount() {
        return connections.size();
    }
    
    /**
     * 获取所有连接的 connectionId
     */
    public Set<String> getAllConnectionIds() {
        return new HashSet<>(connectionsById.keySet());
    }
    
    /**
     * 获取所有已注册的 agentId
     */
    public Set<String> getAllAgentIds() {
        return new HashSet<>(connectionsByAgent.keySet());
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 解析 URL 查询参数
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        
        // 去掉路径部分，只保留查询参数
        int qIndex = query.indexOf('?');
        if (qIndex < 0) return params;
        
        String qs = query.substring(qIndex + 1);
        for (String pair : qs.split("&")) {
            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0) {
                String key = pair.substring(0, eqIndex);
                String value = pair.substring(eqIndex + 1);
                params.put(key, value);
            }
        }
        return params;
    }
    
    /**
     * 解析技能参数（格式：skills=code,debug,test）
     */
    private List<Skill> parseSkills(Map<String, String> params) {
        String skillsStr = params.get("skills");
        if (skillsStr == null || skillsStr.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Skill> skills = new ArrayList<>();
        for (String skillId : skillsStr.split(",")) {
            skillId = skillId.trim();
            if (!skillId.isEmpty()) {
                skills.add(new Skill(skillId, skillId, "Skill: " + skillId, null, null));
            }
        }
        return skills;
    }
    
    /**
     * 解析整数参数
     */
    private int parseIntParam(Map<String, String> params, String key, int defaultValue) {
        String val = params.get(key);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return defaultValue;
    }
    
    /**
     * Stop server
     */
    @Override
    public void stop() {
        try {
            // 关闭心跳定时器
            heartbeatExecutor.shutdown();
            cleanupExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
                if (!cleanupExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            super.stop();
            logger.info("[TaskWebSocket] Server stopped");
        } catch (Exception e) {
            logger.warning("[TaskWebSocket] Error stopping: " + e.getMessage());
        }
    }
}
