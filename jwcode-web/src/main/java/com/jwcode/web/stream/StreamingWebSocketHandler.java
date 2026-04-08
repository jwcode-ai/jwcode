package com.jwcode.web.stream;

import com.jwcode.common.config.ConfigLoader;
import com.jwcode.core.query.QueryEngine;
import com.jwcode.core.query.QueryResult;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * StreamingWebSocketHandler - WebSocket 流式响应处理器
 * 
 * 功能说明：
 * 通过 WebSocket 提供实时的 AI 响应流，支持：
 * - 实时显示 AI 生成的内容
 * - 实时显示思考过程
 * - 实时显示工具调用
 * - 实时显示后台日志/活动
 * - 心跳保活
 * 
 * 消息协议（JSON）：
 * - 客户端 -> 服务器: {"type": "chat", "sessionId": "xxx", "message": "..."}
 * - 服务器 -> 客户端: {"type": "content", "data": "..."}
 * - 服务器 -> 客户端: {"type": "thinking", "data": "..."}
 * - 服务器 -> 客户端: {"type": "tool_call", "data": {...}}
 * - 服务器 -> 客户端: {"type": "log", "data": {...}}  ← 新增：日志消息
 * - 服务器 -> 客户端: {"type": "complete"}
 * - 服务器 -> 客户端: {"type": "error", "data": "..."}
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class StreamingWebSocketHandler extends WebSocketServer {
    
    private static final Logger logger = Logger.getLogger(StreamingWebSocketHandler.class.getName());
    
    private final Map<String, Session> sessions;
    private final ToolRegistry toolRegistry;
    private final Map<WebSocket, String> connectionSessions;
    private final Map<WebSocket, Consumer<LogEntry>> logListeners;
    
    public StreamingWebSocketHandler(int port, ToolRegistry toolRegistry) {
        super(new InetSocketAddress(port));
        this.sessions = new ConcurrentHashMap<>();
        this.toolRegistry = toolRegistry;
        this.connectionSessions = new ConcurrentHashMap<>();
        this.logListeners = new ConcurrentHashMap<>();
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("WebSocket 连接打开: " + conn.getRemoteSocketAddress());
        
        // 发送欢迎消息
        sendMessage(conn, MessageType.CONNECTED, "Connected to JwCode Streaming Server");
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("WebSocket 连接关闭: " + conn.getRemoteSocketAddress() + ", reason: " + reason);
        
        // 清理监听器
        Consumer<LogEntry> listener = logListeners.remove(conn);
        if (listener != null) {
            WebSocketLogBroadcaster.getInstance().removeListener(listener);
        }
        
        connectionSessions.remove(conn);
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            ClientMessage clientMsg = parseMessage(message);
            
            switch (clientMsg.type) {
                case "chat":
                    handleChatMessage(conn, clientMsg);
                    break;
                case "ping":
                    sendMessage(conn, MessageType.PONG, null);
                    break;
                case "create_session":
                    handleCreateSession(conn, clientMsg);
                    break;
                case "subscribe_logs":
                    handleSubscribeLogs(conn, clientMsg);
                    break;
                case "unsubscribe_logs":
                    handleUnsubscribeLogs(conn);
                    break;
                default:
                    sendMessage(conn, MessageType.ERROR, "Unknown message type: " + clientMsg.type);
            }
        } catch (Exception e) {
            logger.severe("处理消息失败: " + e.getMessage());
            e.printStackTrace();
            sendMessage(conn, MessageType.ERROR, "Invalid message format: " + e.getMessage());
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.severe("WebSocket 错误: " + ex.getMessage());
        if (conn != null) {
            sendMessage(conn, MessageType.ERROR, ex.getMessage());
        }
    }
    
    @Override
    public void onStart() {
        logger.info("WebSocket 服务器启动在端口: " + getPort());
    }
    
    /**
     * 处理日志订阅请求
     */
    private void handleSubscribeLogs(WebSocket conn, ClientMessage msg) {
        // 创建日志监听器
        Consumer<LogEntry> listener = logEntry -> {
            if (conn.isOpen()) {
                sendMessage(conn, MessageType.LOG, logEntry.toJson());
            }
        };
        
        logListeners.put(conn, listener);
        WebSocketLogBroadcaster.getInstance().addListener(listener);
        
        // 发送确认
        sendMessage(conn, MessageType.LOG, "{\"level\":\"info\",\"message\":\"已订阅日志\"}");
        logger.info("客户端订阅日志: " + conn.getRemoteSocketAddress());
    }
    
    /**
     * 处理取消日志订阅
     */
    private void handleUnsubscribeLogs(WebSocket conn) {
        Consumer<LogEntry> listener = logListeners.remove(conn);
        if (listener != null) {
            WebSocketLogBroadcaster.getInstance().removeListener(listener);
            logger.info("客户端取消订阅日志: " + conn.getRemoteSocketAddress());
        }
    }
    
    /**
     * 处理聊天消息（流式响应）
     */
    private void handleChatMessage(WebSocket conn, ClientMessage msg) {
        String sessionId = msg.sessionId;
        Session session = sessions.get(sessionId);
        
        if (session == null) {
            // 创建新会话
            session = new Session(sessionId, System.getProperty("user.dir"));
            sessions.put(sessionId, session);
        }
        
        connectionSessions.put(conn, sessionId);
        
        // 广播日志：开始处理消息
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.info("AI", "开始处理用户消息: " + truncate(msg.message, 50))
        );
        
        // 发送开始标记
        sendMessage(conn, MessageType.START, null);
        
        // 使用 QueryEngine 执行真实查询
        final WebSocket clientConn = conn;
        final Session clientSession = session;
        final String clientMessage = msg.message;
        new Thread(() -> {
            executeQuery(clientConn, clientSession, clientMessage);
        }).start();
    }
    
    /**
     * 模拟流式输出效果
     */
    private void simulateStreamOutput(WebSocket conn, String content) {
        // 将内容分成较小的块来模拟流式效果
        int chunkSize = 5; // 每5个字符发送一次
        
        for (int i = 0; i < content.length(); i += chunkSize) {
            if (!conn.isOpen()) {
                break;
            }
            
            int end = Math.min(i + chunkSize, content.length());
            String chunk = content.substring(i, end);
            
            sendMessage(conn, MessageType.CONTENT, escapeJson(chunk));
            
            // 添加小延迟以模拟流式效果
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 处理创建会话请求
     */
    private void handleCreateSession(WebSocket conn, ClientMessage msg) {
        String sessionId = "session-" + System.currentTimeMillis();
        Session session = new Session(sessionId, System.getProperty("user.dir"));
        
        if (msg.model != null) {
            session.setModel(msg.model);
        }
        
        sessions.put(sessionId, session);
        
        // 返回会话信息
        sendMessage(conn, MessageType.SESSION_CREATED, 
            String.format("{\"sessionId\": \"%s\", \"model\": \"%s\"}", 
                sessionId, session.getModel()));
    }
    
    /**
     * 发送消息到客户端
     */
    private void sendMessage(WebSocket conn, MessageType type, String data) {
        if (conn == null || !conn.isOpen()) {
            return;
        }
        
        String json;
        if (type == MessageType.LOG) {
            // 日志消息直接使用 data 作为 JSON
            json = String.format("{\"type\": \"%s\", \"data\": %s}",
                type.name().toLowerCase(),
                data);
        } else {
            json = String.format("{\"type\": \"%s\", \"data\": %s}",
                type.name().toLowerCase(),
                data != null ? "\"" + escapeJson(data) + "\"" : "null");
        }
        
        conn.send(json);
    }
    
    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 执行查询
     */
    private void executeQuery(WebSocket conn, Session session, String message) {
        try {
            // 从配置文件读取 model
            ConfigLoader config = new ConfigLoader();
            String model = (String) config.getConfig("api.model", false);
            
            // 如果配置文件没有设置，使用默认值
            if (model == null || model.isEmpty()) {
                model = "sonnet"; // 默认模型
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.warn("System", "未配置 api.model，使用默认值: sonnet")
                );
            } else {
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.info("System", "使用模型: " + model)
                );
            }
            
            // 如果 session 没有设置 model，则设置
            if (session.getModel() == null) {
                session.setModel(model);
            }
            
            // 创建 QueryEngine
            QueryEngine engine = QueryEngine.builder()
                .session(session)
                .model(model)
                .toolRegistry(toolRegistry)
                .build();
            
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.info("System", "QueryEngine 已创建，开始查询...")
            );
            
            // 执行查询
            QueryResult result = engine.query(message).join();
            
            if (result.isSuccess()) {
                // 获取 AI 的完整响应
                com.jwcode.core.model.Message aiMessage = result.getMessage();
                String response = extractMessageContent(aiMessage);
                
                if (response != null && !response.isEmpty()) {
                    // 模拟流式输出（逐字符发送以显示流式效果）
                    simulateStreamOutput(conn, response);
                }
                
                sendMessage(conn, MessageType.COMPLETE, null);
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.success("查询完成")
                );
            } else {
                String errorMsg = result.getErrorMessage();
                sendMessage(conn, MessageType.ERROR, escapeJson(errorMsg));
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.error("查询失败: " + errorMsg)
                );
            }
            
        } catch (Exception e) {
            logger.severe("查询执行失败: " + e.getMessage());
            e.printStackTrace();
            sendMessage(conn, MessageType.ERROR, escapeJson("执行失败: " + e.getMessage()));
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.error("执行异常: " + e.getMessage())
            );
        }
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 从 Message 对象中提取文本内容
     */
    private String extractMessageContent(com.jwcode.core.model.Message message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (com.jwcode.core.model.Message.ContentBlock block : message.getContent()) {
            if (block instanceof com.jwcode.core.model.Message.TextContent) {
                sb.append(((com.jwcode.core.model.Message.TextContent) block).getText());
            }
        }
        return sb.toString();
    }
    
    /**
     * 解析客户端消息
     */
    private ClientMessage parseMessage(String json) {
        ClientMessage msg = new ClientMessage();
        
        // 简单解析（实际应该使用 JSON 库）
        msg.type = extractJsonValue(json, "type");
        msg.sessionId = extractJsonValue(json, "sessionId");
        msg.message = extractJsonValue(json, "message");
        msg.model = extractJsonValue(json, "model");
        
        return msg;
    }
    
    /**
     * 从 JSON 中提取值
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
    
    /**
     * 获取或创建会话
     */
    public Session getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, 
            id -> new Session(id, System.getProperty("user.dir")));
    }
    
    /**
     * 关闭所有连接
     */
    public void shutdown() {
        try {
            // 移除所有日志监听器
            for (Consumer<LogEntry> listener : logListeners.values()) {
                WebSocketLogBroadcaster.getInstance().removeListener(listener);
            }
            logListeners.clear();
            
            for (WebSocket conn : getConnections()) {
                conn.close();
            }
            stop();
        } catch (Exception e) {
            logger.severe("关闭 WebSocket 服务器失败: " + e.getMessage());
        }
    }
    
    /**
     * 消息类型枚举
     */
    public enum MessageType {
        CONNECTED,      // 连接成功
        SESSION_CREATED,// 会话创建成功
        START,          // 开始生成
        CONTENT,        // 内容块
        THINKING,       // 思考过程
        TOOL_CALL,      // 工具调用
        TOOL_RESULT,    // 工具结果
        COMPLETE,       // 完成
        ERROR,          // 错误
        PONG,           // 心跳响应
        LOG             // 日志消息（新增）
    }
    
    /**
     * 日志条目类
     */
    public static class LogEntry {
        private final String level;   // info, warn, error, success, tool
        private final String source;  // 日志来源
        private final String message; // 消息内容
        private final long timestamp; // 时间戳
        
        public LogEntry(String level, String source, String message) {
            this.level = level;
            this.source = source;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
        
        public static LogEntry info(String source, String message) {
            return new LogEntry("info", source, message);
        }
        
        public static LogEntry warn(String source, String message) {
            return new LogEntry("warn", source, message);
        }
        
        public static LogEntry error(String message) {
            return new LogEntry("error", "System", message);
        }
        
        public static LogEntry success(String message) {
            return new LogEntry("success", "System", message);
        }
        
        public static LogEntry tool(String message) {
            return new LogEntry("tool", "Tool", message);
        }
        
        public String toJson() {
            return String.format(
                "{\"level\":\"%s\",\"source\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                escape(message), escape(source), escape(message), timestamp
            );
        }
        
        private String escape(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r");
        }
        
        // Getters
        public String getLevel() { return level; }
        public String getSource() { return source; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * WebSocket 日志广播器 - 单例模式
     */
    public static class WebSocketLogBroadcaster {
        private static WebSocketLogBroadcaster instance;
        private final CopyOnWriteArrayList<Consumer<LogEntry>> listeners = new CopyOnWriteArrayList<>();
        
        private WebSocketLogBroadcaster() {}
        
        public static synchronized WebSocketLogBroadcaster getInstance() {
            if (instance == null) {
                instance = new WebSocketLogBroadcaster();
            }
            return instance;
        }
        
        public void addListener(Consumer<LogEntry> listener) {
            listeners.add(listener);
        }
        
        public void removeListener(Consumer<LogEntry> listener) {
            listeners.remove(listener);
        }
        
        public void broadcast(LogEntry entry) {
            for (Consumer<LogEntry> listener : listeners) {
                try {
                    listener.accept(entry);
                } catch (Exception e) {
                    // 忽略监听器错误
                }
            }
        }
    }
    
    /**
     * 客户端消息结构
     */
    private static class ClientMessage {
        String type;
        String sessionId;
        String message;
        String model;
    }
}
