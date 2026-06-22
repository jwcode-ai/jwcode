package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * <p>澧炲己鍔熻兘锛?
 * <ul>
 *   <li>鏀寔 Agent 娉ㄥ唽锛堥€氳繃 URL 鍙傛暟 agentId/agentType锛?/li>
 *   <li>鏀寔鎸?agentId 瀹氬悜鎺ㄩ€佹秷鎭?/li>
 *   <li>鏀寔鎸?sessionId 瀹氬悜鎺ㄩ€佹秷鎭?/li>
 *   <li>闆嗘垚 A2ARegistry 绠＄悊 Agent 浼氳瘽</li>
 *   <li>蹇冭烦妫€娴嬩笌杩囨湡娓呯悊</li>
 * </ul>
 * </p>
 */
public class TaskWebSocketServer extends WebSocketServer {
    
    private static final Logger logger = Logger.getLogger(TaskWebSocketServer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    /** 鎵€鏈?WebSocket 杩炴帴闆嗗悎 */
    private final CopyOnWriteArraySet<WebSocket> connections = new CopyOnWriteArraySet<>();
    
    /** 鎸?connectionId 绱㈠紩鐨?WebSocket 杩炴帴 */
    private final ConcurrentHashMap<String, WebSocket> connectionsById = new ConcurrentHashMap<>();
    
    /** 鎸?sessionId 绱㈠紩鐨?WebSocket 杩炴帴 */
    private final ConcurrentHashMap<String, WebSocket> connectionsBySession = new ConcurrentHashMap<>();
    
    /** 鎸?agentId 绱㈠紩鐨?WebSocket 杩炴帴 */
    private final ConcurrentHashMap<String, WebSocket> connectionsByAgent = new ConcurrentHashMap<>();
    
    /** WebSocket 杩炴帴鐨勫弽鍚戠储寮曪細conn -> connectionId */
    private final ConcurrentHashMap<WebSocket, String> connectionIds = new ConcurrentHashMap<>();
    
    /** 杩炴帴 ID 鐢熸垚鍣?*/
    private final AtomicLong idGenerator = new AtomicLong(0);
    
    // 蹇冭烦瀹氭椂鍣?
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-heartbeat");
        t.setDaemon(true);
        return t;
    });
    
    // 杩囨湡娓呯悊瀹氭椂鍣?
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-cleanup");
        t.setDaemon(true);
        return t;
    });
    
    // 蹇冭烦闂撮殧锛堢锛?
    private static final int HEARTBEAT_INTERVAL = 25;
    
    // 杩囨湡娓呯悊闂撮殧锛堢锛?
    private static final int CLEANUP_INTERVAL = 60;
    
    // WebSocket 娑堟伅澶勭悊鍣紙鐢ㄤ簬澶勭悊 chat/plan 绛変笟鍔℃秷鎭級
    private WebSocketMessageHandler messageHandler;
    
    public TaskWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        // 璁剧疆杩炴帴瓒呮椂妫€娴嬶紝闃叉 java-websocket 鑷姩鏂紑绌洪棽杩炴帴
        // 0 琛ㄧず绂佺敤瓒呮椂妫€娴嬶紝鐢辨垜浠嚜宸辩殑蹇冭烦鏈哄埗绠＄悊
        this.setConnectionLostTimeout(0);
    }
    
    /**
     * 璁剧疆娑堟伅澶勭悊鍣?
     */
    public void setMessageHandler(WebSocketMessageHandler handler) {
        this.messageHandler = handler;
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // 鐢熸垚鍞竴杩炴帴 ID
        String connectionId = "conn-" + idGenerator.incrementAndGet();
        
        connections.add(conn);
        connectionIds.put(conn, connectionId);
        connectionsById.put(connectionId, conn);
        
        // 瑙ｆ瀽 URL 鍙傛暟
        String query = handshake.getResourceDescriptor();
        Map<String, String> params = parseQueryParams(query);
        
        String agentId = params.get("agentId");
        String agentType = params.get("agentType");
        String sessionId = params.get("sessionId");
        String agentName = params.get("agentName");
        
        // 濡傛灉鎼哄甫 sessionId锛屽缓绔?session 鏄犲皠
        if (sessionId != null && !sessionId.isEmpty()) {
            connectionsBySession.put(sessionId, conn);
            logger.info("[TaskWebSocket] Session mapped: " + sessionId + " -> " + connectionId);
        }
        
        // 濡傛灉鎼哄甫 agentId锛屾敞鍐屽埌 A2A Registry
        if (agentId != null && !agentId.isEmpty()) {
            connectionsByAgent.put(agentId, conn);
            String name = agentName != null ? agentName : agentId;
            String type = agentType != null ? agentType : "generic";
            logger.info("[TaskWebSocket] Agent connection mapped: " + name + " (type=" + type + ")");
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
        
        // 浠?session 鏄犲皠涓Щ闄?
        connectionsBySession.values().remove(conn);
        
        // 浠?agent 鏄犲皠涓Щ闄ゅ苟娉ㄩ攢 Registry
        connectionsByAgent.values().remove(conn);
        
        logger.info("[TaskWebSocket] Connection closed: " + conn.getRemoteSocketAddress() 
            + " id=" + connectionId);
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        String connId = connectionIds.get(conn);
        logger.fine("[TaskWebSocket] Message from " + connId + ": " + message);
        
        try {
            // 瑙ｆ瀽 JSON 娑堟伅
            @SuppressWarnings("unchecked")
            Map<String, Object> msgMap = MAPPER.readValue(message, Map.class);
            
            String type = (String) msgMap.getOrDefault("type", "");
            String sessionId = (String) msgMap.getOrDefault("sessionId", "");
            String msgContent = (String) msgMap.getOrDefault("message", "");
            if (msgContent == null || msgContent.isEmpty()) {
                msgContent = (String) msgMap.getOrDefault("data", "");
            }

            // 澶勭悊蹇冭烦
            if ("pong".equals(type)) {
                return;
            }
            
            // 鍏堝洖澶?ack 纭鏀跺埌
            sendMessage(conn, Map.of(
                "type", "ack",
                "original", message
            ));
            
            // 濡傛灉鏈夋秷鎭鐞嗗櫒锛屽皢娑堟伅浜ょ粰澶勭悊鍣ㄥ鐞?
            if (messageHandler != null && !type.isEmpty()) {
                String finalType = type;
                String finalSessionId = sessionId;
                String finalMsg = msgContent;
                
                // 寮傛澶勭悊娑堟伅锛岄伩鍏嶉樆濉?WebSocket 绾跨▼
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
        }
    }
    
    @Override
    public void onStart() {
        logger.info("[TaskWebSocket] Server started on port " + getPort());
        
        // 鍚姩蹇冭烦瀹氭椂鍣紝瀹氭湡鍙戦€?ping 娑堟伅淇濇寔杩炴帴娲昏穬
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (!connections.isEmpty()) {
                    broadcast(Map.of("type", "ping", "data", String.valueOf(System.currentTimeMillis())));
                }
            } catch (Exception e) {
                logger.warning("[TaskWebSocket] Heartbeat error: " + e.getMessage());
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
        
        // 鍚姩杩囨湡娓呯悊瀹氭椂鍣?
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                // Connection cleanup is handled by WebSocket close events.
            } catch (Exception e) {
                logger.warning("[TaskWebSocket] Cleanup error: " + e.getMessage());
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.SECONDS);
        
        logger.info("[TaskWebSocket] Heartbeat started (interval: " + HEARTBEAT_INTERVAL + "s)");
        logger.info("[TaskWebSocket] Cleanup started (interval: " + CLEANUP_INTERVAL + "s)");
        
        // 娉ㄥ唽 TaskStore 鐩戝惉鍣?鈥?浠诲姟鍙樻洿鏃惰嚜鍔ㄥ箍鎾埌鎵€鏈?WebSocket 瀹㈡埛绔?
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
    
    // ==================== 瀹氬悜鎺ㄩ€佹柟娉?====================
    
    /**
     * 鍚戞寚瀹?agentId 鎺ㄩ€佹秷鎭?
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
     * 鍚戞寚瀹?sessionId 鎺ㄩ€佹秷鎭?
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
     * 鍚戞寚瀹?connectionId 鎺ㄩ€佹秷鎭?
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
     * 鍚戞寚瀹?WebSocket 杩炴帴鎺ㄩ€佹秷鎭?
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
     * <p>姣忎釜杩炴帴鍗曠嫭 try/catch锛岄槻姝㈠崟涓繛鎺ュ紓甯镐腑鏂暣涓箍鎾惊鐜€?
     * 鍚屾椂璁板綍姣忎釜澶辫触杩炴帴鐨?connectionId 渚夸簬璇婃柇銆?/p>
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
    
    // ==================== 鏌ヨ鏂规硶 ====================
    
    
    /**
     * 鑾峰彇杩炴帴鏁伴噺
     */
    public int getConnectionCount() {
        return connections.size();
    }
    
    /**
     * 鑾峰彇鎵€鏈夎繛鎺ョ殑 connectionId
     */
    public Set<String> getAllConnectionIds() {
        return new HashSet<>(connectionsById.keySet());
    }
    
    /**
     * 鑾峰彇鎵€鏈夊凡娉ㄥ唽鐨?agentId
     */
    public Set<String> getAllAgentIds() {
        return new HashSet<>(connectionsByAgent.keySet());
    }
    
    // ==================== 宸ュ叿鏂规硶 ====================
    
    /**
     * 瑙ｆ瀽 URL 鏌ヨ鍙傛暟
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        
        // 鍘绘帀璺緞閮ㄥ垎锛屽彧淇濈暀鏌ヨ鍙傛暟
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
     * 瑙ｆ瀽鎶€鑳藉弬鏁帮紙鏍煎紡锛歴kills=code,debug,test锛?
     */
    
    /**
     * 瑙ｆ瀽鏁存暟鍙傛暟
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
            // 鍏抽棴蹇冭烦瀹氭椂鍣?
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
