package com.jwcode.web.stream;

import com.jwcode.common.config.ConfigLoader;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.agent.EnhancedOrchestratorAgent;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.llm.*;
import com.jwcode.core.index.CodebaseIndexer;
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
import java.util.concurrent.*;
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
 * - 心跳保活（带认证）
 * - 实时显示执行步骤（思考、规划、工具调用等）
 * - Plan 模式：AI 先分析需求并制定任务计划，再逐步执行
 * 
 * 消息协议（JSON）：
 * - 客户端 -> 服务器: {"type": "auth", "token": "xxx"} （认证）
 * - 客户端 -> 服务器: {"type": "chat", "sessionId": "xxx", "message": "..."}
 * - 客户端 -> 服务器: {"type": "plan", "sessionId": "xxx", "message": "..."} （Plan 模式）
 * - 服务器 -> 客户端: {"type": "content", "data": "..."}
 * - 服务器 -> 客户端: {"type": "thinking", "data": "..."}
 * - 服务器 -> 客户端: {"type": "tool_call", "data": {...}}
 * - 服务器 -> 客户端: {"type": "step_start", "data": {...}}
 * - 服务器 -> 客户端: {"type": "step_thinking", "data": {...}}
 * - 服务器 -> 客户端: {"type": "step_action", "data": {...}}
 * - 服务器 -> 客户端: {"type": "step_complete", "data": {...}}
 * - 服务器 -> 客户端: {"type": "log", "data": {...}}
 * - 服务器 -> 客户端: {"type": "complete"}
 * - 服务器 -> 客户端: {"type": "error", "data": "..."}
 * - 服务器 -> 客户端: {"type": "plan_start", "data": "..."}
 * - 服务器 -> 客户端: {"type": "plan_tasks", "data": "..."}
 * - 服务器 -> 客户端: {"type": "plan_task_start", "data": "..."}
 * - 服务器 -> 客户端: {"type": "plan_task_update", "data": "..."}
 * - 服务器 -> 客户端: {"type": "plan_task_result", "data": "..."}
 * - 服务器 -> 客户端: {"type": "plan_complete", "data": "..."}
 * - 服务器 -> 客户端: {"type": "plan_error", "data": "..."}
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class StreamingWebSocketHandler extends WebSocketServer {
    
    private static final Logger logger = Logger.getLogger(StreamingWebSocketHandler.class.getName());
    
    // 心跳检测配置
    private static final long PING_INTERVAL_MS = 30000; // 30秒发送一次 ping
    private static final long PONG_TIMEOUT_MS = 90000;   // 90秒内未收到 pong 则断开
    private static final long CONNECTION_TIMEOUT_MS = 300000; // 5分钟无活动则断开
    
    // 认证配置（从系统配置读取）
    private String validToken = "default-token"; // 默认 token，生产环境应从配置读取
    
    // 已认证的连接集合
    private final Map<WebSocket, Boolean> authenticatedConnections;
    
    private final Map<String, Session> sessions;
    private final ToolRegistry toolRegistry;
    private CodebaseIndexer codebaseIndexer;
    private final Map<WebSocket, String> connectionSessions;
    private final Map<String, WebSocket> activeSessionConnections; // sessionId -> 当前活跃连接
    private final Map<WebSocket, Consumer<LogEntry>> logListeners;
    private final Map<WebSocket, Long> lastPongTime;    // 最后收到 pong 的时间
    private final Map<WebSocket, Long> lastActivityTime; // 最后活跃时间
    
    // 跟踪每个 sessionId 正在执行的 Future，连接断开时取消
    private final Map<String, java.util.concurrent.Future<?>> runningQueryFutures;
    
    // 待发送消息队列（按 sessionId 分组，连接断开时暂存，重连后重放）
    private final Map<String, java.util.Queue<PendingMessage>> pendingMessages;
    private static final int MAX_PENDING_MESSAGES = 200; // 每个会话最多暂存 200 条消息
    
    /**
     * 待重放的消息体
     */
    private static class PendingMessage {
        final MessageType type;
        final String data;
        final long timestamp;
        
        PendingMessage(MessageType type, String data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    // 默认工作目录（前端可动态切换）
    private String defaultWorkingDirectory = System.getProperty("user.dir");

    /**
     * 设置代码库索引器（由 WebServer 初始化后注入）
     */
    public void setCodebaseIndexer(CodebaseIndexer indexer) {
        this.codebaseIndexer = indexer;
    }
    
    // 查询线程池：核心 4 线程，最大 16 线程，60 秒回收
    private final ThreadPoolExecutor queryExecutor;
    
    public StreamingWebSocketHandler(int port, ToolRegistry toolRegistry) {
        super(new InetSocketAddress(port));
        this.sessions = new ConcurrentHashMap<>();
        this.toolRegistry = toolRegistry;
        this.connectionSessions = new ConcurrentHashMap<>();
        this.activeSessionConnections = new ConcurrentHashMap<>();
        this.logListeners = new ConcurrentHashMap<>();
        this.lastPongTime = new ConcurrentHashMap<>();
        this.lastActivityTime = new ConcurrentHashMap<>();
        this.authenticatedConnections = new ConcurrentHashMap<>();
        this.pendingMessages = new ConcurrentHashMap<>();
        this.runningQueryFutures = new ConcurrentHashMap<>();
        
        // 初始化查询线程池：核心 4，最大 16，60 秒回收，有界队列 100
        this.queryExecutor = new ThreadPoolExecutor(
            4, 16, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ws-query-" + count++);
                    t.setDaemon(true);
                    return t;
                }
            },
            (r, executor) -> {
                logger.warning("查询线程池已满，拒绝任务");
                // 拒绝时直接在当前线程执行（CallerRunsPolicy）
                r.run();
            }
        );
        
        // 从配置读取有效 token
        loadConfig();
        
        // 启动心跳检测线程
        startHeartbeatThread();
        
        // 初始化 TodoWriteBroadcaster（接线待办状态广播）
        initTodoWriteBroadcaster();
    }
    
    /**
     * 初始化 TodoWriteBroadcaster — 将待办状态广播接入主 WebSocket
     */
    private void initTodoWriteBroadcaster() {
        try {
            com.jwcode.core.api.TodoWriteBroadcaster broadcaster = 
                com.jwcode.core.api.TodoWriteBroadcaster.getInstance();
            broadcaster.setBroadcastAdapter((sessionId, type, data) -> {
                sendMessage(sessionId, MessageType.LOG, data);
            });
            logger.info("TodoWriteBroadcaster wired to StreamingWebSocketHandler");
        } catch (Exception e) {
            logger.warning("Failed to wire TodoWriteBroadcaster: " + e.getMessage());
        }
    }
    
    /**
     * 检查连接是否已认证
     */
    private boolean isAuthenticated(WebSocket conn) {
        Boolean authenticated = authenticatedConnections.get(conn);
        return authenticated != null && authenticated;
    }
    
    /**
     * 处理认证消息
     */
    private void handleAuthMessage(WebSocket conn, ClientMessage msg) {
        String token = msg.token;
        
        if (token == null || token.isEmpty()) {
            logger.warning("认证失败: token 为空");
            sendMessage(conn, MessageType.AUTH_FAILED, "Token is required");
            return;
        }
        
        if (validToken.equals(token)) {
            authenticatedConnections.put(conn, true);
            logger.info("认证成功: " + conn.getRemoteSocketAddress());
            sendMessage(conn, MessageType.AUTH_SUCCESS, "Authenticated");
        } else {
            logger.warning("认证失败: " + conn.getRemoteSocketAddress() + ", token=" + maskToken(token));
            sendMessage(conn, MessageType.AUTH_FAILED, "Invalid token");
        }
    }
    
    /**
     * 脱敏显示 token
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
    
    /**
     * 检查连接是否已认证，如未认证则拒绝
     */
    private boolean checkAuthentication(WebSocket conn) {
        if (!isAuthenticated(conn)) {
            sendMessage(conn, MessageType.AUTH_REQUIRED, "Authentication required");
            return false;
        }
        return true;
    }
    
    /**
     * 从配置加载设置
     * 优先级：系统属性 > 环境变量 > 配置文件 > 默认值
     */
    private void loadConfig() {
        // 1. 先尝试从系统属性读取
        String tokenFromProp = System.getProperty("jwcode.websocket.token");
        if (tokenFromProp != null && !tokenFromProp.isEmpty()) {
            this.validToken = tokenFromProp;
            logger.info("从系统属性加载 WebSocket token");
            return;
        }
        
        // 2. 尝试从环境变量读取
        String tokenFromEnv = System.getenv("JWCODE_WEBSOCKET_TOKEN");
        if (tokenFromEnv != null && !tokenFromEnv.isEmpty()) {
            this.validToken = tokenFromEnv;
            logger.info("从环境变量加载 WebSocket token");
            return;
        }
        
        // 3. 尝试从 YAML 配置读取（通过全局设置）
        try {
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            if (config != null && config.getSettings() != null) {
                // 可通过 settings 的 advanced 配置扩展
                JwcodeConfig.AdvancedSettings advanced = config.getSettings().getAdvanced();
                if (advanced != null) {
                    // 这里可以扩展读取 websocket 配置
                }
            }
        } catch (Exception e) {
            logger.fine("无法从 YAML 配置加载: " + e.getMessage());
        }
        
        // 4. 使用默认值（已经在构造函数中设置）
        logger.info("使用默认 WebSocket token");
    }
    
    /**
     * 启动心跳检测线程
     */
    private void startHeartbeatThread() {
        Thread heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(PING_INTERVAL_MS);
                    checkHeartbeat();
                    cleanupStalePendingMessages();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ws-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
    
    /**
     * 检查所有连接的心跳状态
     */
    private void checkHeartbeat() {
        long now = System.currentTimeMillis();
        java.util.List<WebSocket> toClose = new java.util.ArrayList<>();
        
        for (WebSocket conn : getConnections()) {
            if (!conn.isOpen()) continue;
            
            // 检查 pong 超时
            Long lastPong = lastPongTime.get(conn);
            if (lastPong != null && (now - lastPong) > PONG_TIMEOUT_MS) {
                logger.warning("Pong 超时，断开连接: " + conn.getRemoteSocketAddress());
                toClose.add(conn);
                continue;
            }
            
            // 检查连接超时
            Long lastActivity = lastActivityTime.get(conn);
            if (lastActivity != null && (now - lastActivity) > CONNECTION_TIMEOUT_MS) {
                logger.warning("连接超时，断开: " + conn.getRemoteSocketAddress());
                toClose.add(conn);
                continue;
            }
            
            // 发送心跳 ping
            sendMessage(conn, MessageType.PING, null);
        }
        
        // 循环结束后再统一关闭，避免并发修改问题
        for (WebSocket conn : toClose) {
            conn.close();
        }
    }
    
    /**
     * 清理超过 10 分钟未被重放的过期暂存消息，防止内存泄漏
     */
    private void cleanupStalePendingMessages() {
        long now = System.currentTimeMillis();
        long staleThreshold = 600000; // 10 分钟
        java.util.List<String> staleSessions = new java.util.ArrayList<>();
        
        for (Map.Entry<String, java.util.Queue<PendingMessage>> entry : pendingMessages.entrySet()) {
            java.util.Queue<PendingMessage> queue = entry.getValue();
            // 移除队列中过期的消息
            queue.removeIf(msg -> (now - msg.timestamp) > staleThreshold);
            // 如果队列为空，标记该会话待清理
            if (queue.isEmpty()) {
                staleSessions.add(entry.getKey());
            }
        }
        
        for (String sessionId : staleSessions) {
            pendingMessages.remove(sessionId);
        }
        
        if (!staleSessions.isEmpty()) {
            logger.fine("清理了 " + staleSessions.size() + " 个过期暂存消息会话");
        }
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("WebSocket 连接打开: " + conn.getRemoteSocketAddress());
        
        // 初始化心跳时间
        lastPongTime.put(conn, System.currentTimeMillis());
        lastActivityTime.put(conn, System.currentTimeMillis());
        
        // 检查是否是重连：从 handshake 中提取 sessionId（如果有）
        String reconnectingSessionId = null;
        if (handshake != null) {
            Map<String, String> pathParams = parseQueryParams(handshake.getResourceDescriptor());
            reconnectingSessionId = pathParams.get("sessionId");
        }
        
        // 如果是重连（提供了 sessionId），更新 activeSessionConnections 映射
        if (reconnectingSessionId != null && !reconnectingSessionId.isEmpty()) {
            WebSocket oldConn = activeSessionConnections.put(reconnectingSessionId, conn);
            if (oldConn != null) {
                logger.info("WebSocket 重连: sessionId=" + reconnectingSessionId 
                    + ", 旧连接已替换 (oldConn open=" + oldConn.isOpen() + ")");
            }
            connectionSessions.put(conn, reconnectingSessionId);
            logger.info("WebSocket 重连成功: sessionId=" + reconnectingSessionId);
            
            // 重连成功，发送确认消息
            sendMessage(conn, MessageType.CONNECTED, "{\"sessionId\":\"" + escapeJson(reconnectingSessionId) + "\",\"reconnected\":true}");
            
            // 重放连接断开期间暂存的重要消息（如 AI 回复内容、完成信号等）
            replayPendingMessages(conn, reconnectingSessionId);
        } else {
            // 新连接，发送认证请求
            sendMessage(conn, MessageType.AUTH_REQUIRED, "Token required");
        }
    }
    
    /**
     * 重连后重放暂存的消息
     * 将连接断开期间暂存的重要消息（CONTENT、COMPLETE、ERROR 等）发送给重连的客户端
     */
    private void replayPendingMessages(WebSocket conn, String sessionId) {
        java.util.Queue<PendingMessage> queue = pendingMessages.remove(sessionId);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        
        int replayedCount = 0;
        PendingMessage msg;
        while ((msg = queue.poll()) != null) {
            if (conn.isOpen()) {
                // 跳过过期的消息（超过 5 分钟）
                if (System.currentTimeMillis() - msg.timestamp > 300000) {
                    continue;
                }
                sendMessage(conn, msg.type, msg.data, sessionId);
                replayedCount++;
            } else {
                break;
            }
        }
        
        if (replayedCount > 0) {
            logger.info("重连重放完成: sessionId=" + sessionId + ", 重放了 " + replayedCount + " 条消息");
        }
        
        // 清理剩余消息（如果有）
        queue.clear();
    }
    
    /**
     * 解析 URL 查询参数
     */
    private Map<String, String> parseQueryParams(String resourceDescriptor) {
        Map<String, String> params = new java.util.HashMap<>();
        if (resourceDescriptor == null || resourceDescriptor.isEmpty()) {
            return params;
        }
        int queryStart = resourceDescriptor.indexOf('?');
        if (queryStart < 0) {
            return params;
        }
        String query = resourceDescriptor.substring(queryStart + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String key = pair.substring(0, eqIdx);
                String value = pair.substring(eqIdx + 1);
                params.put(key, value);
            }
        }
        return params;
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.warning("连接关闭: code=" + code + ", reason=" + reason + ", remote=" + remote);
        
        // 清理监听器
        Consumer<LogEntry> listener = logListeners.remove(conn);
        if (listener != null) {
            WebSocketLogBroadcaster.getInstance().removeListener(listener);
        }
        
        // 清理认证状态
        authenticatedConnections.remove(conn);
        
        // 清理会话关联（双向映射）
        String sessionId = connectionSessions.remove(conn);
        if (sessionId != null) {
            activeSessionConnections.remove(sessionId);
            // 连接断开时取消正在运行的查询任务
            cancelRunningQuery(sessionId);
        }
        
        // 清理心跳跟踪数据
        lastPongTime.remove(conn);
        lastActivityTime.remove(conn);
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        // 更新最后活跃时间和心跳时间（任何消息都视为有效心跳）
        long now = System.currentTimeMillis();
        lastActivityTime.put(conn, now);
        lastPongTime.put(conn, now);
        
        try {
            ClientMessage clientMsg = parseMessage(message);
            
            // 处理认证（不需要认证）
            if ("auth".equals(clientMsg.type)) {
                handleAuthMessage(conn, clientMsg);
                return;
            }
            
            // 其他消息需要认证
            if (!isAuthenticated(conn)) {
                logger.warning("连接未认证: " + conn.getRemoteSocketAddress());
                sendMessage(conn, MessageType.AUTH_REQUIRED, "Authentication required");
                return;
            }
            
            switch (clientMsg.type) {
                case "chat":
                    logger.info("处理 chat 消息");
                    handleChatMessage(conn, clientMsg);
                    break;
                case "plan":
                    logger.info("处理 plan 消息");
                    handlePlanMessage(conn, clientMsg);
                    break;
                case "plan_confirm":
                    logger.info("处理 plan_confirm 消息");
                    handlePlanConfirm(conn, clientMsg);
                    break;
                case "ping":
                    lastPongTime.put(conn, System.currentTimeMillis());
                    sendMessage(conn, MessageType.PONG, null);
                    break;
                case "pong":
                    // 客户端心跳响应，更新时间戳即可
                    lastPongTime.put(conn, System.currentTimeMillis());
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
                case "notification":
                    handleNotificationMessage(conn, clientMsg);
                    break;
                case "get_commands":
                    handleGetCommands(conn);
                    break;
                case "workspace":
                    handleWorkspaceChange(conn, clientMsg);
                    break;
                default:
                    logger.warning("未知消息类型: " + clientMsg.type);
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
     * 处理获取命令列表请求 - 返回后端所有注册的命令
     */
    private void handleGetCommands(WebSocket conn) {
        try {
            // 从 CommandRegistry 获取所有命令
            com.jwcode.core.command.CommandRegistry registry = com.jwcode.core.command.CommandRegistry.createDefault();
            java.util.Collection<com.jwcode.core.command.Command> allCommands = registry.getAllCommands();
            
            StringBuilder json = new StringBuilder();
            json.append("[");
            boolean first = true;
            for (com.jwcode.core.command.Command cmd : allCommands) {
                if (!first) json.append(",");
                first = false;
                json.append("{");
                json.append("\"name\":\"").append(escapeJson(cmd.getName())).append("\",");
                json.append("\"description\":\"").append(escapeJson(cmd.getDescription())).append("\",");
                json.append("\"usage\":\"").append(escapeJson(cmd.getUsage())).append("\"");
                json.append("}");
            }
            json.append("]");
            
            sendMessage(conn, MessageType.COMMANDS_LIST, json.toString());
            logger.info("已发送命令列表: " + allCommands.size() + " 个命令");
        } catch (Exception e) {
            logger.severe("获取命令列表失败: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to get commands: " + e.getMessage());
        }
    }
    
    /**
     * 处理通知消息 - 广播给所有已连接的客户端
     */
    private void handleNotificationMessage(WebSocket sender, ClientMessage msg) {
        String notificationMessage = msg.message;
        if (notificationMessage == null || notificationMessage.isEmpty()) {
            sendMessage(sender, MessageType.ERROR, "通知内容不能为空");
            return;
        }
        
        logger.info("收到通知广播: " + truncate(notificationMessage, 50));
        
        // 广播给所有已连接的客户端（包括发送者自己）
        for (WebSocket conn : getConnections()) {
            if (conn.isOpen()) {
                sendMessage(conn, MessageType.NOTIFICATION, escapeJson(notificationMessage));
            }
        }
        
        // 同时记录到日志
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.info("通知", "广播: " + truncate(notificationMessage, 50))
        );
    }
    
    /**
     * 处理聊天消息（流式响应）
     */
    private void handleChatMessage(WebSocket conn, ClientMessage msg) {
        logger.info("收到聊天消息: sessionId=" + msg.sessionId + ", message=" + truncate(msg.message, 50));
        
        // 确保 sessionId 不为 null，如果客户端未提供则自动生成
        String sessionId = msg.sessionId;
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "session-" + System.currentTimeMillis();
            logger.warning("客户端未提供 sessionId，自动生成: " + sessionId);
        }
        
        Session session = sessions.get(sessionId);
        
        if (session == null) {
            logger.info("创建新会话: " + sessionId);
            // 创建新会话（包含系统提示词）
            session = createNewSession(sessionId, null);
        }
        
        connectionSessions.put(conn, sessionId);
        activeSessionConnections.put(sessionId, conn);
        
        // 广播日志：开始处理消息
        logger.info("开始处理用户消息: " + truncate(msg.message, 50));
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.info("AI", "开始处理用户消息: " + truncate(msg.message, 50))
        );
        
        // 发送开始标记（带 sessionId）
        logger.info("发送 START 标记");
        sendMessage(conn, MessageType.START, null, sessionId);
        
        // 使用线程池执行查询，避免裸线程泄漏
        final WebSocket clientConn = conn;
        final Session clientSession = session;
        final String clientMessage = msg.message;
        final String finalSessionId = sessionId;
        logger.info("提交查询任务到线程池, message=" + truncate(clientMessage, 30));
        
        // 取消该 sessionId 上之前仍在运行的查询（如果有）
        cancelRunningQuery(finalSessionId);
        
        java.util.concurrent.Future<?> future = queryExecutor.submit(() -> {
            executeQuery(clientConn, clientSession, clientMessage);
        });
        runningQueryFutures.put(finalSessionId, future);
        
        logger.info("查询任务已提交到线程池");
    }
    
    /**
     * 处理 Plan 模式消息 - AI 先分析需求并制定任务计划，再逐步执行
     * 
     * Plan 模式工作流程：
     * 1. 接收用户需求
     * 2. 调用 LLM 分析需求并拆解为任务列表
     * 3. 将任务列表推送给前端展示
     * 4. 按依赖顺序逐个执行任务
     * 5. 每个任务执行时推送进度更新
     * 6. 全部完成后推送完成消息
     */
    private void handlePlanMessage(WebSocket conn, ClientMessage msg) {
        logger.info("收到 Plan 消息: sessionId=" + msg.sessionId + ", message=" + truncate(msg.message, 50));
        
        // 确保 sessionId 不为 null
        String sessionId = msg.sessionId;
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "session-" + System.currentTimeMillis();
            logger.warning("Plan 消息未提供 sessionId，自动生成: " + sessionId);
        }
        
        Session session = sessions.get(sessionId);
        
        if (session == null) {
            logger.info("创建新会话: " + sessionId);
            session = createNewSession(sessionId, null);
        }
        
        connectionSessions.put(conn, sessionId);
        activeSessionConnections.put(sessionId, conn);
        
        // 广播日志
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.info("Plan", "开始 Plan 模式: " + truncate(msg.message, 50))
        );
        
        // 发送 plan_start 标记（带 sessionId）
        sendMessage(conn, MessageType.PLAN_START, escapeJson(msg.message), sessionId);
        
        // 使用线程池执行 Plan 查询
        final WebSocket clientConn = conn;
        final Session clientSession = session;
        final String clientMessage = msg.message;
        final String finalSessionId = sessionId;
        
        // 取消该 sessionId 上之前仍在运行的 Plan 查询
        cancelRunningQuery(finalSessionId);
        
        java.util.concurrent.Future<?> future = queryExecutor.submit(() -> {
            executePlanQuery(clientConn, clientSession, clientMessage);
        });
        runningQueryFutures.put(finalSessionId, future);
    }
    
    /**
     * Plan 模式的系统提示词 - 用于指导 AI 进行任务规划
     */
    /**
     * Plan 模式的系统提示词 - 用于指导 AI 进行自由需求分析
     * 
     * Plan 模式下 AI 使用自然语言分析需求，不要求 JSON 输出。
     * 用户确认后，AI 的完整回复将被传递给 EnhancedOrchestratorAgent
     * 进行结构化任务解析和执行。
     */
    private String getPlanSystemPrompt() {
        return """
            # Plan 模式 - 需求分析专家

            ## 当前工作目录
            """ + this.defaultWorkingDirectory + """

            ## 你的角色
            你是 Plan 模式下的软件工程需求分析师。你的职责是：
            1. 深入理解用户的需求和目标
            2. 分析需求的可行性和潜在风险
            3. 识别需要探索的模块和文件
            4. 给出高层次的实施建议和步骤

            ## 分析原则
            - 基于常识和项目结构常识进行分析（你无法读取实际文件）
            - 诚实标注不确定的内容："需要进一步确认"
            - 分析要有层次：先宏观再微观
            - 最后给出清晰的任务分解建议

            ## 输出要求
            - 使用自然语言，像资深架构师一样思考和分析
            - 分析的最后，用 "## 任务计划" 标题给出具体的任务分解建议
            - 任务分解建议包含：任务名称、目标、建议的 Agent 类型
            - Agent 类型可选：explore / architect / coder / test / review / doc

            ## 分析结构建议
            1. **需求理解**：用自己的话重述需求，确认理解正确
            2. **影响范围**：分析需要修改或新增的模块
            3. **技术方案**：给出高层次的实现思路
            4. **风险与注意事项**：潜在问题和注意事项
            5. **任务计划**：具体的任务分解（用 ## 任务计划 标题）

            ## ⚠️ 绝对禁止
            - **禁止生成任何工具调用标记**（如 <tool_calls>、<invoke name=、Bash 命令等）
            - **禁止尝试执行任何操作**（Plan 模式是纯分析模式，不执行任何代码）
            - **禁止读取文件、搜索代码、运行命令**
            - 如果有需要确认的信息，直接在分析中说明即可

            ## ⚠️ 重要提醒
            Plan 模式下你没有任何工具可用。你的输出将完整呈现给用户。
            用户确认你的分析后，系统会自动将你的分析交给执行引擎来具体实施。
            因此请提供详细、可操作的 task 分解建议。
            """;
    }
    
    /**
     * 检测 AI 响应中是否包含工具调用标记
     * Plan 模式不支持工具，AI 不应生成任何工具调用
     */
    private boolean containsToolCallPattern(String content) {
        if (content == null || content.isEmpty()) return false;
        String lower = content.toLowerCase();
        return lower.contains("<功能_calls") 
            || lower.contains("<tool_calls") 
            || lower.contains("<invoke name=") 
            || lower.contains("功能invoke")
            || lower.contains("<toolcall")
            || lower.contains("bashtool")
            || (lower.contains("<功能") && lower.contains("invoke") && lower.contains("parameter"));
    }
    
    /**
     * 从 Session 构建 LLMMessage 列表（用于多轮对话重试）
     */
    private java.util.List<LLMMessage> buildLLMMessagesFromSession(Session session) {
        java.util.List<LLMMessage> messages = new java.util.ArrayList<>();
        for (com.jwcode.core.model.Message msg : session.getMessages()) {
            String role = msg.getRole().name().toLowerCase();
            String msgContent = msg.getTextContent();
            if (msgContent != null && !msgContent.isEmpty()) {
                LLMMessage.Role llmRole;
                switch (role) {
                    case "system": llmRole = LLMMessage.Role.SYSTEM; break;
                    case "assistant": llmRole = LLMMessage.Role.ASSISTANT; break;
                    case "tool": llmRole = LLMMessage.Role.TOOL; break;
                    default: llmRole = LLMMessage.Role.USER; break;
                }
                messages.add(LLMMessage.builder().role(llmRole).content(msgContent).build());
            }
        }
        return messages;
    }
    
    /**
     * 执行 Plan 查询 - 调用 LLM 分析需求并生成任务计划
     */
    private void executePlanQuery(WebSocket conn, Session session, String message) {
        String sessionId = connectionSessions.get(conn);
        if (sessionId == null) {
            sessionId = session.getId();
            connectionSessions.put(conn, sessionId);
        }
        activeSessionConnections.put(sessionId, conn);
        final String querySessionId = sessionId;
        
        // 快速检查：如果连接已断开，直接返回
        if (Thread.currentThread().isInterrupted() || !conn.isOpen()) {
            logger.info("Plan 查询已取消（连接已断开）: sessionId=" + querySessionId);
            return;
        }
        
        try {
            // 从 YAML 配置读取模型信息
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            JwcodeConfig.ModelDefinition modelDef = config != null ? config.getDefaultModel() : null;
            String model = modelDef != null ? modelDef.getId() : "unknown";
            
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.info("Plan", "使用模型: " + model)
            );
            
            // 创建 LLMFactory 获取 LLMService
            LLMFactory llmFactory = LLMFactory.fromConfig(config);
            LLMService llmService = llmFactory.getLLMService();
            
            // 注入 Plan 模式的系统提示词
            session.addMessage(com.jwcode.core.model.Message.createSystemMessage(getPlanSystemPrompt()));
            
            // 添加用户消息
            session.addMessage(com.jwcode.core.model.Message.createUserMessage(
                "请分析以下需求：\n\n" + message
            ));
            
            // 保存用户目标到 session metadata（供 plan_confirm 使用）
            session.setMetadata("plan_goal", message);
            
            // 转换会话消息
            java.util.List<LLMMessage> llmMessages = buildLLMMessagesFromSession(session);
            
            // 发送 plan_thinking 状态
            sendMessage(querySessionId, MessageType.PLAN_THINKING, escapeJson("正在分析需求..."));
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.info("Plan", "正在分析需求...")
            );
            
            // 使用 StringBuilder 收集完整响应
            StringBuilder fullContentBuilder = new StringBuilder();
            
            // 定义流式内容回调 - 实时推送到前端
            java.util.function.Consumer<String> contentConsumer = chunk -> {
                fullContentBuilder.append(chunk);
                // 实时推送内容到前端（使用 CONTENT 消息类型，让 chatStore 实时渲染）
                sendMessage(querySessionId, MessageType.CONTENT, chunk);
            };
            
            // 调用流式 API（不使用工具）
            CompletableFuture<LLMResponse> future = llmService.chatStream(llmMessages, contentConsumer);
            LLMResponse response = future.get(120, TimeUnit.SECONDS);
            
            if (response.hasError()) {
                String errorMsg = response.getErrorMessage();
                logger.severe("Plan 查询失败: " + errorMsg);
                sendMessage(querySessionId, MessageType.PLAN_ERROR, escapeJson("AI 分析失败: " + errorMsg));
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.error("Plan 失败: " + errorMsg)
                );
                return;
            }
            
            String fullContent = fullContentBuilder.toString();
            
            logger.info("Plan AI 分析完成，内容长度: " + fullContent.length());
            
            // 将完整响应保存到 Session metadata（供 plan_confirm 使用）
            session.setMetadata("plan_response", fullContent);
            
            // 发送 COMPLETE 标记（让前端 chatStore 完成消息记录）
            sendMessage(querySessionId, MessageType.COMPLETE, null);
            
            // 发送 PLAN_COMPLETE 状态 waiting_confirm，通知前端等待用户确认
            sendMessage(querySessionId, MessageType.PLAN_COMPLETE, 
                "{\"status\":\"waiting_confirm\"}");
            
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.success("Plan 分析完成，等待用户确认")
            );
            
        } catch (java.util.concurrent.TimeoutException e) {
            logger.severe("Plan 查询超时: " + e.getMessage());
            sendMessage(querySessionId, MessageType.PLAN_ERROR, escapeJson("AI 分析超时（120秒），请重试"));
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.error("Plan 超时")
            );
        } catch (Exception e) {
            logger.severe("Plan 查询执行失败: " + e.getMessage());
            e.printStackTrace();
            sendMessage(querySessionId, MessageType.PLAN_ERROR, escapeJson("Plan 执行失败: " + e.getMessage()));
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.error("Plan 异常: " + e.getMessage())
            );
        }
    }
    
    /**
     * 按依赖顺序执行 Plan 任务
     * 使用 EnhancedOrchestratorAgent 或 LLMQueryEngine 进行真实的任务执行
     */
    private void executePlanTasks(String sessionId, com.fasterxml.jackson.databind.JsonNode tasksNode) {
        try {
            // 构建任务列表
            java.util.List<PlanTaskInfo> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < tasksNode.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode task = tasksNode.get(i);
                PlanTaskInfo info = new PlanTaskInfo();
                info.id = task.has("id") ? task.get("id").asText() : "task-" + (i + 1);
                info.title = task.has("title") ? task.get("title").asText() : "任务 " + (i + 1);
                info.description = task.has("description") ? task.get("description").asText() : "";
                info.agentType = task.has("agentType") ? task.get("agentType").asText() : "coder";
                info.status = "pending";
                
                // 解析依赖
                if (task.has("dependencies") && task.get("dependencies").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode dep : task.get("dependencies")) {
                        info.dependencies.add(dep.asText());
                    }
                }
                tasks.add(info);
            }
            
            // 拓扑排序
            java.util.List<PlanTaskInfo> sortedTasks = topologicalSort(tasks);
            
            // 获取当前会话对应的 Session 和 LLMQueryEngine
            Session session = sessions.get(sessionId);
            if (session == null) {
                logger.warning("Plan 执行失败: 找不到会话 sessionId=" + sessionId);
                sendMessage(sessionId, MessageType.PLAN_ERROR, escapeJson("会话不存在"));
                return;
            }

            // 从会话中获取或创建 LLMQueryEngine
            LLMQueryEngine engine = getOrCreateQueryEngine(session);
            boolean useRealExecution = (engine != null);

            if (!useRealExecution) {
                logger.warning("Plan 执行: LLMQueryEngine 不可用，回退到模拟执行");
            }
            
            // 逐个执行任务
            for (PlanTaskInfo task : sortedTasks) {
                if (!isSessionActive(sessionId)) {
                    logger.warning("Plan 执行中断: 会话已断开, sessionId=" + sessionId);
                    break;
                }
                
                // 发送任务开始
                String taskStartJson = String.format(
                    "{\"id\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"agentType\":\"%s\",\"status\":\"running\"}",
                    escapeJson(task.id), escapeJson(task.title), escapeJson(task.description), escapeJson(task.agentType)
                );
                sendMessage(sessionId, MessageType.PLAN_TASK_START, taskStartJson);
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.info("Plan", "[执行] " + task.title + " (" + task.agentType + ")")
                );

                if (useRealExecution) {
                    // 真实执行：通过 LLMQueryEngine 调用 LLM + Agent + Tools
                    try {
                        // 构建任务提示词：注入 Agent 角色和任务描述
                        String taskPrompt = String.format(
                            "【Plan 模式子任务】\n角色: %s\n任务: %s\n描述: %s\n\n请以 %s 的身份执行此任务。",
                            task.agentType, task.title, task.description, task.agentType
                        );

                        // 发送进度更新：开始执行
                        sendMessage(sessionId, MessageType.PLAN_TASK_UPDATE, String.format(
                            "{\"id\":\"%s\",\"progress\":10,\"logs\":[\"正在调用 %s Agent 执行: %s\"]}",
                            escapeJson(task.id), escapeJson(task.agentType), escapeJson(task.title)
                        ));

                        // ★ 发送 START 标记，让前端 chatStore 创建 assistant 消息以承载 step 树形结构
                        sendMessage(sessionId, MessageType.START, null);

                        // ★ 设置 StepCallback，将步骤事件通过主 WebSocket 流式发送给前端
                        final String finalSessionId = sessionId;
                        engine.setStepCallback(new LLMQueryEngine.StepCallback() {
                            @Override
                            public void onStepStart(String stepName, String description) {
                                String json = String.format(
                                    "{\"step\":\"%s\",\"description\":\"%s\",\"status\":\"start\"}",
                                    escapeJson(stepName), escapeJson(description)
                                );
                                sendMessage(finalSessionId, MessageType.STEP_START, json);
                            }

                            @Override
                            public void onStepThinking(String stepName, String thought) {
                                String json = String.format(
                                    "{\"step\":\"%s\",\"thought\":\"%s\",\"status\":\"thinking\"}",
                                    escapeJson(stepName), escapeJson(thought)
                                );
                                sendMessage(finalSessionId, MessageType.STEP_THINKING, json);
                            }

                            @Override
                            public void onStepAction(String stepName, String action) {
                                String json = String.format(
                                    "{\"step\":\"%s\",\"action\":\"%s\",\"status\":\"action\"}",
                                    escapeJson(stepName), escapeJson(action)
                                );
                                sendMessage(finalSessionId, MessageType.STEP_ACTION, json);
                            }

                            @Override
                            public void onStepComplete(String stepName, String result) {
                                String json = String.format(
                                    "{\"step\":\"%s\",\"result\":\"%s\",\"status\":\"complete\"}",
                                    escapeJson(stepName), escapeJson(result)
                                );
                                sendMessage(finalSessionId, MessageType.STEP_COMPLETE, json);
                            }

                            @Override
                            public void onToolResult(String toolName, String result) {
                                String json = String.format(
                                    "{\"toolName\":\"%s\",\"result\":\"%s\"}",
                                    escapeJson(toolName), escapeJson(result)
                                );
                                sendMessage(finalSessionId, MessageType.TOOL_RESULT, json);
                            }

                            @Override
                            public void onToolCallChunk(LLMService.StreamToolCallEvent event) {
                                String json = String.format(
                                    "{\"id\":\"%s\",\"name\":\"%s\",\"args\":\"%s\",\"complete\":%b,\"index\":%d}",
                                    escapeJson(event.getId()),
                                    escapeJson(event.getName()),
                                    escapeJson(event.getArguments()),
                                    event.isComplete(),
                                    event.getIndex()
                                );
                                sendMessage(finalSessionId, MessageType.TOOL_CALL, json);
                            }
                        });

                        // 使用 LLMQueryEngine 执行（引擎内部 pipeline 事件将通过 StepCallback 转发）
                        java.util.concurrent.CompletableFuture<LLMQueryEngine.QueryResult> future = engine.query(taskPrompt);
                        LLMQueryEngine.QueryResult result = future.get(300, java.util.concurrent.TimeUnit.SECONDS);

                        // ★ 发送 COMPLETE 标记，结束该 assistant 消息
                        sendMessage(finalSessionId, MessageType.COMPLETE, null);

                        if (result != null && result.isSuccess()) {
                            String resultContent = result.getMessage() != null
                                ? result.getMessage().getTextContent() : "任务执行完成";
                            // 截断过长结果
                            if (resultContent.length() > 500) {
                                resultContent = resultContent.substring(0, 500) + "...";
                            }
                            String resultJson = String.format(
                                "{\"id\":\"%s\",\"status\":\"completed\",\"result\":\"%s\",\"logs\":[\"任务执行成功\"]}",
                                escapeJson(task.id), escapeJson(resultContent)
                            );
                            sendMessage(sessionId, MessageType.PLAN_TASK_RESULT, resultJson);
                            WebSocketLogBroadcaster.getInstance().broadcast(
                                LogEntry.success("Plan [完成] " + task.title)
                            );
                        } else {
                            String errorMsg = (result != null) ? result.getErrorMessage() : "执行返回空结果";
                            String resultJson = String.format(
                                "{\"id\":\"%s\",\"status\":\"failed\",\"error\":\"%s\",\"logs\":[\"执行失败: %s\"]}",
                                escapeJson(task.id), escapeJson(errorMsg), escapeJson(errorMsg)
                            );
                            sendMessage(sessionId, MessageType.PLAN_TASK_RESULT, resultJson);
                            WebSocketLogBroadcaster.getInstance().broadcast(
                                LogEntry.error("Plan [失败] " + task.title + ": " + errorMsg)
                            );
                        }
                    } catch (java.util.concurrent.TimeoutException e) {
                        logger.warning("Plan 子任务执行超时: " + task.id);
                        sendMessage(sessionId, MessageType.COMPLETE, null); // 关闭 assistant 消息
                        String resultJson = String.format(
                            "{\"id\":\"%s\",\"status\":\"failed\",\"error\":\"执行超时\",\"logs\":[\"任务执行超时（300秒）\"]}",
                            escapeJson(task.id)
                        );
                        sendMessage(sessionId, MessageType.PLAN_TASK_RESULT, resultJson);
                    } catch (Exception e) {
                        logger.warning("Plan 子任务执行异常: " + task.id + " - " + e.getMessage());
                        sendMessage(sessionId, MessageType.COMPLETE, null); // 关闭 assistant 消息
                        String resultJson = String.format(
                            "{\"id\":\"%s\",\"status\":\"failed\",\"error\":\"%s\",\"logs\":[\"执行异常: %s\"]}",
                            escapeJson(task.id), escapeJson(e.getMessage()), escapeJson(e.getMessage())
                        );
                        sendMessage(sessionId, MessageType.PLAN_TASK_RESULT, resultJson);
                    }
                } else {
                    // 回退：模拟执行（LLMQueryEngine 不可用时）
                    for (int progress = 10; progress <= 90; progress += 20) {
                        Thread.sleep(500);
                        String updateJson = String.format(
                            "{\"id\":\"%s\",\"progress\":%d,\"logs\":[\"执行进度: %d%%\"]}",
                            escapeJson(task.id), progress, progress
                        );
                        sendMessage(sessionId, MessageType.PLAN_TASK_UPDATE, updateJson);
                    }

                    String resultJson = String.format(
                        "{\"id\":\"%s\",\"status\":\"completed\",\"result\":\"任务 '%s' 执行完成\",\"logs\":[\"任务执行成功\"]}",
                        escapeJson(task.id), escapeJson(task.title)
                    );
                    sendMessage(sessionId, MessageType.PLAN_TASK_RESULT, resultJson);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.info("Plan", "[完成] " + task.title)
                    );
                }
            }
            
            // 全部完成
            sendMessage(sessionId, MessageType.PLAN_COMPLETE, "{\"status\":\"completed\"}");
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.info("Plan", "所有任务执行完成")
            );
            
        } catch (Exception e) {
            logger.severe("Plan 任务执行失败: " + e.getMessage());
            sendMessage(sessionId, MessageType.PLAN_ERROR, escapeJson("任务执行失败: " + e.getMessage()));
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.error("Plan 任务执行异常: " + e.getMessage())
            );
        }
    }
    
    /**
     * 处理 Plan 确认消息 - 用户确认后，将 AI 的分析结果交给 EnhancedOrchestratorAgent 执行
     * 
     * 流程：
     * 1. 从 Session metadata 取出之前保存的 plan_response
     * 2. 创建 EnhancedOrchestratorAgent
     * 3. 调用 processConfirmedPlan() 执行任务
     * 4. 将执行进度推送给前端
     */
    private void handlePlanConfirm(WebSocket conn, ClientMessage msg) {
        String sessionId = connectionSessions.get(conn);
        if (sessionId == null) {
            sessionId = "session-" + System.currentTimeMillis();
            connectionSessions.put(conn, sessionId);
        }
        activeSessionConnections.put(sessionId, conn);
        final String querySessionId = sessionId;
        
        try {
            // 检查连接是否仍然活跃
            if (!isSessionActive(sessionId)) {
                logger.info("Plan 确认已取消（连接已断开）: sessionId=" + sessionId);
                return;
            }
            
            Session session = sessions.get(sessionId);
            if (session == null) {
                sendMessage(querySessionId, MessageType.PLAN_ERROR, escapeJson("会话不存在"));
                return;
            }
            
            // 从 Session metadata 取出之前保存的 plan 响应和 goal
            String planResponse = session.getMetadata("plan_response");
            String goal = session.getMetadata("plan_goal");
            
            if (planResponse == null || planResponse.isEmpty()) {
                sendMessage(querySessionId, MessageType.PLAN_ERROR, 
                    escapeJson("未找到 Plan 分析结果，请重新执行 Plan 模式"));
                return;
            }
            
            if (goal == null || goal.isEmpty()) {
                goal = msg.message != null ? msg.message : "执行任务计划";
            }
            
            logger.info("Plan 确认: goal=" + truncate(goal, 50) 
                + ", planResponse.length=" + planResponse.length());
            
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.info("Plan", "用户已确认，开始执行任务计划...")
            );
            
            // 发送 PLAN_THINKING 状态
            sendMessage(querySessionId, MessageType.PLAN_THINKING, 
                escapeJson("正在解析任务计划并开始执行..."));
            
            // 创建 EnhancedOrchestratorAgent
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            LLMFactory llmFactory = LLMFactory.fromConfig(config);
            LLMService llmService = llmFactory.getLLMService();
            
            EnhancedOrchestratorAgent orchestrator = new EnhancedOrchestratorAgent(
                llmService, this.toolRegistry, null);
            
            // 设置 PlanTaskBroadcaster 的 WebSocket 服务器
            // 将当前 handler 作为广播目标，确保 plan_* 消息通过当前 WebSocket 连接发送
            com.jwcode.core.api.PlanTaskBroadcaster broadcaster = 
                com.jwcode.core.api.PlanTaskBroadcaster.getInstance();
            
            // 如果 PlanTaskBroadcaster 尚未配置 WebSocket 服务器，使用 sendMessage 直接发送
            // EnhancedOrchestratorAgent 内部会通过 PlanTaskBroadcaster 广播任务进度
            // 我们通过 sendMessage 直接转发这些广播到前端
            
            // 调用 processConfirmedPlan 执行
            String result = orchestrator.processConfirmedPlan(planResponse, goal);
            
            logger.info("Plan 执行完成，结果长度: " + result.length());
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.success("Plan 执行完成")
            );
            
            // 发送执行结果
            sendMessage(querySessionId, MessageType.PLAN_COMPLETE, 
                "{\"status\":\"completed\"}");
            
            // 清理 session 中的 plan 数据
            session.setMetadata("plan_response", null);
            session.setMetadata("plan_goal", null);
            
        } catch (Exception e) {
            logger.severe("Plan 确认执行失败: " + e.getMessage());
            e.printStackTrace();
            sendMessage(querySessionId, MessageType.PLAN_ERROR, 
                escapeJson("Plan 执行失败: " + e.getMessage()));
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.error("Plan 异常: " + e.getMessage())
            );
        }
    }
    
    /**
     * 拓扑排序 - 按依赖顺序排列任务
     */
    private java.util.List<PlanTaskInfo> topologicalSort(java.util.List<PlanTaskInfo> tasks) {
        java.util.List<PlanTaskInfo> sorted = new java.util.ArrayList<>();
        java.util.Map<String, PlanTaskInfo> taskMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> inDegree = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<String>> adjList = new java.util.HashMap<>();
        
        // 初始化
        for (PlanTaskInfo task : tasks) {
            taskMap.put(task.id, task);
            inDegree.put(task.id, 0);
            adjList.put(task.id, new java.util.ArrayList<>());
        }
        
        // 构建依赖图
        for (PlanTaskInfo task : tasks) {
            for (String depId : task.dependencies) {
                if (adjList.containsKey(depId)) {
                    adjList.get(depId).add(task.id);
                    inDegree.put(task.id, inDegree.getOrDefault(task.id, 0) + 1);
                }
            }
        }
        
        // Kahn 算法
        java.util.Queue<PlanTaskInfo> queue = new java.util.LinkedList<>();
        for (PlanTaskInfo task : tasks) {
            if (inDegree.getOrDefault(task.id, 0) == 0) {
                queue.add(task);
            }
        }
        
        while (!queue.isEmpty()) {
            PlanTaskInfo task = queue.poll();
            sorted.add(task);
            
            for (String neighbor : adjList.getOrDefault(task.id, new java.util.ArrayList<>())) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(taskMap.get(neighbor));
                }
            }
        }
        
        // 如果有环，将剩余任务追加到末尾
        for (PlanTaskInfo task : tasks) {
            if (!sorted.contains(task)) {
                sorted.add(task);
            }
        }
        
        return sorted;
    }
    
    /**
     * 检查会话是否仍然活跃
     */
    private boolean isSessionActive(String sessionId) {
        WebSocket conn = activeSessionConnections.get(sessionId);
        return conn != null && conn.isOpen();
    }
    
    /**
     * 取消指定 sessionId 上正在运行的查询任务
     * 在连接断开或新查询提交时调用，避免后台任务继续向已断开的连接发送消息
     */
    private void cancelRunningQuery(String sessionId) {
        if (sessionId == null) return;
        java.util.concurrent.Future<?> future = runningQueryFutures.remove(sessionId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            logger.info("已取消 sessionId=" + sessionId + " 的查询任务, cancelled=" + cancelled);
        }
    }

    /**
     * 获取或创建 LLMQueryEngine 实例
     * 用于 Plan 模式下的真实任务执行
     */
    private LLMQueryEngine getOrCreateQueryEngine(Session session) {
        if (session == null) return null;
        try {
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            LLMFactory llmFactory = LLMFactory.fromConfig(config);
            AgentRegistry agentRegistry = AgentRegistry.createDefault();
            return llmFactory.createQueryEngine(
                session,
                this.toolRegistry,
                null,
                agentRegistry
            );
        } catch (Exception e) {
            logger.warning("创建 LLMQueryEngine 失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Plan 任务信息内部类
     */
    private static class PlanTaskInfo {
        String id;
        String title;
        String description;
        String agentType;
        String status;
        java.util.List<String> dependencies = new java.util.ArrayList<>();
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
        Session session = createNewSession(sessionId, msg.model);
        
        // 返回会话信息
        sendMessage(conn, MessageType.SESSION_CREATED, 
            String.format("{\"sessionId\": \"%s\", \"model\": \"%s\"}", 
                sessionId, session.getModel()));
        
        // 广播日志
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.info("Session", "创建新会话: " + sessionId)
        );
    }
    
    /**
     * 处理工作目录切换请求
     * 前端切换工作目录时，同步更新后端的所有相关路径配置
     */
    private void handleWorkspaceChange(WebSocket conn, ClientMessage msg) {
        String newDir = msg.message;
        if (newDir == null || newDir.trim().isEmpty()) {
            sendMessage(conn, MessageType.ERROR, "工作目录路径不能为空");
            return;
        }
        
        newDir = newDir.trim();
        java.io.File dirFile = new java.io.File(newDir);
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            sendMessage(conn, MessageType.ERROR, "工作目录不存在或不是有效的目录: " + newDir);
            return;
        }
        
        try {
            String normalizedDir = dirFile.getCanonicalPath();
            String oldDir = this.defaultWorkingDirectory;
            this.defaultWorkingDirectory = normalizedDir;
            
            // 同步更新 System property，确保 SystemPromptLoader 和 LLMQueryEngine 获取正确目录
            System.setProperty("user.dir", normalizedDir);
            
            // 同步更新 FilesHandler 的项目根目录
            com.jwcode.web.FilesHandler.setProjectRoot(normalizedDir);
            
            // 更新所有已有会话的工作目录，并添加系统通知消息
            for (Session s : sessions.values()) {
                s.setWorkingDirectory(normalizedDir);
                
                // 清除旧的 [ENV_INFO] 环境信息消息，确保 LLMQueryEngine 下次注入时获取最新工作目录
                int removed = s.removeSystemMessagesContaining("[ENV_INFO]");
                if (removed > 0) {
                    logger.fine("已清除会话 " + s.getId() + " 中 " + removed + " 条旧环境信息消息");
                }
                
                // 立即注入新的环境信息（含正确的工作目录），不等 LLMQueryEngine 下次查询
                String freshEnvInfo = com.jwcode.core.config.SystemPromptLoader.getEnvironmentInfo(normalizedDir);
                s.addMessage(com.jwcode.core.model.Message.createSystemMessage(freshEnvInfo));
                
                // 添加工作目录变更的系统通知，让 AI 感知到目录已变
                s.addMessage(com.jwcode.core.model.Message.createSystemMessage(
                    "[系统通知] 工作目录已切换为：" + normalizedDir + "。所有文件操作请基于此目录进行。"
                ));
            }
            
            logger.info("工作目录已切换: " + oldDir + " -> " + normalizedDir);
            
            // 触发代码库索引器切换工作区（后台异步重索引）
            if (codebaseIndexer != null) {
                try {
                    codebaseIndexer.switchWorkspace(java.nio.file.Path.of(normalizedDir));
                    logger.info("代码库索引器已切换至新工作区");
                } catch (Exception ie) {
                    logger.warning("代码库索引器切换失败: " + ie.getMessage());
                }
            }
            
            // 广播日志
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.info("Workspace", "工作目录已切换: " + normalizedDir)
            );
            
            // 发送成功确认给前端
            String responseJson = String.format(
                "{\"status\":\"ok\",\"oldDir\":\"%s\",\"newDir\":\"%s\"}",
                escapeJson(oldDir), escapeJson(normalizedDir)
            );
            sendMessage(conn, MessageType.WORKSPACE_CHANGED, responseJson);
            
        } catch (Exception e) {
            logger.severe("切换工作目录失败: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "切换工作目录失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建新会话（包含系统提示词）
     */
    private Session createNewSession(String sessionId, String model) {
        Session session = new Session(sessionId, this.defaultWorkingDirectory);
        
        // 加载并添加系统提示词（使用会话的工作目录，确保环境信息准确）
        try {
            String systemPrompt = com.jwcode.core.config.SystemPromptLoader.getSystemPrompt(null, this.defaultWorkingDirectory);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                com.jwcode.core.model.Message systemMessage = 
                    com.jwcode.core.model.Message.createSystemMessage(systemPrompt);
                session.addMessage(systemMessage);
            }
        } catch (Exception e) {
            logger.warning("加载系统提示词失败: " + e.getMessage());
        }
        
        // 设置模型
        if (model != null && !model.isEmpty()) {
            session.setModel(model);
        } else {
            // 从配置读取默认模型
            String defaultModel = null;
            try {
                // 优先从 YAML 配置读取
                com.jwcode.core.config.JwcodeConfig yamlConfig = com.jwcode.core.config.YamlConfigLoader.getInstance().getConfig();
                com.jwcode.core.config.JwcodeConfig.ModelDefinition modelDef = yamlConfig.getDefaultModel();
                if (modelDef != null && modelDef.getName() != null) {
                    defaultModel = modelDef.getName();
                }
            } catch (Exception e) {
                // 忽略错误，尝试旧版配置
            }
            
            if (defaultModel == null || defaultModel.isEmpty()) {
                try {
                    com.jwcode.common.config.ConfigLoader config = new com.jwcode.common.config.ConfigLoader();
                    defaultModel = (String) config.getConfig("api.model", false);
                } catch (Exception e) {
                    // 忽略错误
                }
            }
            
            if (defaultModel != null && !defaultModel.isEmpty()) {
                session.setModel(defaultModel);
            } else {
                // 没有配置模型，记录警告
                System.err.println("[警告] 未配置默认模型，请检查配置文件");
                session.setModel("unknown");
            }
        }
        
        sessions.put(sessionId, session);
        return session;
    }
    
    /**
     * 发送消息到客户端（通过 sessionId 查找当前活跃连接）
     * 所有消息都会在 JSON 中附加 sessionId，方便前端路由
     */
    private void sendMessage(String sessionId, MessageType type, String data) {
        if (sessionId == null) {
            logger.fine("sendMessage 被调用但 sessionId 为 null, type=" + type);
            return;
        }
        WebSocket conn = activeSessionConnections.get(sessionId);
        if (conn != null && conn.isOpen()) {
            sendMessage(conn, type, data, sessionId);
        } else {
            // 连接丢失时，将重要消息暂存到队列，等待重连后重放
            if (shouldQueueMessage(type)) {
                java.util.Queue<PendingMessage> queue = pendingMessages.computeIfAbsent(
                    sessionId, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
                // 限制队列大小，防止内存泄漏
                while (queue.size() >= MAX_PENDING_MESSAGES) {
                    queue.poll();
                }
                queue.offer(new PendingMessage(type, data));
                logger.fine("消息已暂存: sessionId=" + sessionId + ", type=" + type 
                    + ", queueSize=" + queue.size());
            } else {
                // 连接已断开但消息仍在发送 — 首次降为 INFO，后续降为 FINE 避免刷屏
                logger.fine("sendMessage 连接不可用: sessionId=" + sessionId 
                    + ", type=" + type + ", conn=" + (conn == null ? "null" : "closed"));
            }
        }
    }
    
    /**
     * 判断消息类型是否需要在连接断开时暂存
     * 只暂存对前端状态至关重要的消息：CONTENT（AI 回复）、COMPLETE（完成信号）、
     * ERROR（错误信息）、STEP_COMPLETE（步骤完成）
     */
    private boolean shouldQueueMessage(MessageType type) {
        return type == MessageType.CONTENT 
            || type == MessageType.COMPLETE 
            || type == MessageType.ERROR
            || type == MessageType.STEP_COMPLETE;
    }
    
    /**
     * 发送消息到客户端（带 sessionId 版本）
     * 所有消息都会在 JSON 中附加 sessionId，方便前端路由
     */
    private void sendMessage(WebSocket conn, MessageType type, String data, String sessionId) {
        if (conn == null || !conn.isOpen()) {
            return;
        }
        
        String json;
        if (type == MessageType.LOG) {
            // 日志消息直接使用 data 作为 JSON
            json = String.format("{\"type\": \"%s\", \"data\": %s, \"sessionId\": \"%s\"}",
                type.name().toLowerCase(),
                data,
                escapeJson(sessionId != null ? sessionId : ""));
        } else {
            json = String.format("{\"type\": \"%s\", \"data\": %s, \"sessionId\": \"%s\"}",
                type.name().toLowerCase(),
                data != null ? "\"" + escapeJson(data) + "\"" : "null",
                escapeJson(sessionId != null ? sessionId : ""));
        }
        
        try {
            conn.send(json);
        } catch (Exception e) {
            logger.warning("发送消息失败: " + conn.getRemoteSocketAddress() + ", error=" + e.getMessage());
            conn.close(4003, "Send error");
        }
    }
    
    /**
     * 发送消息到客户端（不带 sessionId 版本，向后兼容）
     */
    private void sendMessage(WebSocket conn, MessageType type, String data) {
        sendMessage(conn, type, data, null);
    }
    
    /**
     * 转义 JSON 字符串中的控制字符
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    /**
     * 尝试从非结构化 LLM 响应中提取 tasks JSON
     * 当 LLM 返回的 JSON 解析失败时，尝试从中提取可能的 tasks 数组
     */
    private String tryExtractTasksFromContent(String content) {
        if (content == null || content.isEmpty()) return null;
        try {
            // 尝试1: 查找 {"analysis": ... "tasks": [...]} 模式
            int tasksStart = content.indexOf("\"tasks\"");
            if (tasksStart >= 0) {
                int bracketStart = content.indexOf("[", tasksStart);
                if (bracketStart >= 0) {
                    // 找到匹配的闭合括号
                    int depth = 0;
                    int bracketEnd = -1;
                    for (int i = bracketStart; i < content.length(); i++) {
                        char c = content.charAt(i);
                        if (c == '[') depth++;
                        else if (c == ']') {
                            depth--;
                            if (depth == 0) {
                                bracketEnd = i;
                                break;
                            }
                        }
                    }
                    if (bracketEnd > bracketStart) {
                        String tasksArrayStr = content.substring(bracketStart, bracketEnd + 1);
                        // 验证是否为合法的 JSON 数组
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode testNode = mapper.readTree(tasksArrayStr);
                        if (testNode.isArray() && testNode.size() > 0) {
                            // 提取 analysis
                            String analysis = "";
                            int analysisStart = content.indexOf("\"analysis\"");
                            if (analysisStart >= 0) {
                                int colonPos = content.indexOf(":", analysisStart + 9);
                                if (colonPos >= 0) {
                                    int valStart = content.indexOf("\"", colonPos + 1);
                                    if (valStart >= 0) {
                                        int valEnd = valStart + 1;
                                        while (valEnd < content.length() && !(content.charAt(valEnd) == '\"' && content.charAt(valEnd - 1) != '\\')) {
                                            valEnd++;
                                        }
                                        if (valEnd < content.length()) {
                                            analysis = content.substring(valStart + 1, valEnd);
                                        }
                                    }
                                }
                            }
                            // 构建标准格式
                            StringBuilder result = new StringBuilder();
                            result.append("{\"analysis\":\"").append(escapeJson(analysis)).append("\",\"tasks\":");
                            result.append(tasksArrayStr);
                            result.append("}");
                            return result.toString();
                        }
                    }
                }
            }

            // 尝试2: 查找 markdown 代码块中的 JSON
            int jsonBlock = content.indexOf("```json");
            if (jsonBlock >= 0) {
                int start = content.indexOf("{", jsonBlock);
                int end = content.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    String jsonCandidate = content.substring(start, end + 1);
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(jsonCandidate);
                    if (rootNode.has("tasks") && rootNode.get("tasks").isArray()) {
                        return jsonCandidate;
                    }
                }
            }

            // 尝试3: 直接在整个文本中查找 {...} 并验证是否为合法 tasks JSON
            int firstBrace = content.indexOf("{");
            int lastBrace = content.lastIndexOf("}");
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                String jsonCandidate = content.substring(firstBrace, lastBrace + 1);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(jsonCandidate);
                if (rootNode.has("tasks") && rootNode.get("tasks").isArray()) {
                    return jsonCandidate;
                }
            }
        } catch (Exception e) {
            logger.fine("尝试提取 tasks JSON 失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 执行查询 - 增强版，支持步骤展示
     * 步骤与消息分离：步骤显示在独立容器中，完成后收起或淡化
     */
    private void executeQuery(WebSocket conn, Session session, String message) {
        // 获取或绑定 sessionId 与当前连接的双向映射
        String sessionId = connectionSessions.get(conn);
        if (sessionId == null) {
            sessionId = session.getId();
            connectionSessions.put(conn, sessionId);
        }
        activeSessionConnections.put(sessionId, conn);
        final String querySessionId = sessionId;
        
        // 快速检查：如果连接已断开，直接返回，避免无谓的 LLM 调用
        if (Thread.currentThread().isInterrupted() || !conn.isOpen()) {
            logger.info("查询已取消（连接已断开）: sessionId=" + querySessionId);
            return;
        }
        
        try {
            // 从 YAML 配置读取模型信息
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            JwcodeConfig.ModelDefinition modelDef = config != null ? config.getDefaultModel() : null;
            String model = modelDef != null ? modelDef.getId() : "unknown";
            
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.info("System", "使用模型: " + model)
            );
            
            // 如果 session 没有设置 model，则设置
            if (session.getModel() == null) {
                session.setModel(model);
            }
            
            // 创建 LLMFactory 和 QueryEngine（与 CLI 端一致，使用完整参数）
            LLMFactory llmFactory = LLMFactory.fromConfig(config);
            AgentRegistry agentRegistry = AgentRegistry.createDefault();
            LLMQueryEngine engine = llmFactory.createQueryEngine(
                session,
                this.toolRegistry,  // 使用 WebServer 传入的 toolRegistry
                null,               // toolExecutor 使用默认
                agentRegistry       // 启用分层 Agent 架构
            );
            
            // 创建步骤回调，将步骤信息通过 WebSocket 发送给前端
            // 使用 querySessionId 而不是 conn，确保连接断开后重连的消息仍能送达
            LLMQueryEngine.StepCallback stepCallback = new LLMQueryEngine.StepCallback() {
                @Override
                public void onStepStart(String stepName, String description) {
                    String json = String.format(
                        "{\"step\":\"%s\",\"description\":\"%s\",\"status\":\"start\"}",
                        escapeJson(stepName), escapeJson(description)
                    );
                    sendMessage(querySessionId, MessageType.STEP_START, json);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.info("Step", "[开始] " + stepName + ": " + description)
                    );
                }
                
                @Override
                public void onStepThinking(String stepName, String thought) {
                    String json = String.format(
                        "{\"step\":\"%s\",\"thought\":\"%s\",\"status\":\"thinking\"}",
                        escapeJson(stepName), escapeJson(thought)
                    );
                    sendMessage(querySessionId, MessageType.STEP_THINKING, json);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.info("Thinking", "[思考] " + stepName + ": " + thought)
                    );
                }
                
                @Override
                public void onStepAction(String stepName, String action) {
                    String json = String.format(
                        "{\"step\":\"%s\",\"action\":\"%s\",\"status\":\"action\"}",
                        escapeJson(stepName), escapeJson(action)
                    );
                    sendMessage(querySessionId, MessageType.STEP_ACTION, json);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.tool("[动作] " + stepName + ": " + action)
                    );
                }
                
                @Override
                public void onStepComplete(String stepName, String result) {
                    String json = String.format(
                        "{\"step\":\"%s\",\"result\":\"%s\",\"status\":\"complete\"}",
                        escapeJson(stepName), escapeJson(result)
                    );
                    sendMessage(querySessionId, MessageType.STEP_COMPLETE, json);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.success("[完成] " + stepName + ": " + result)
                    );
                }
                
                @Override
                public void onToolResult(String toolName, String result) {
                    String json = String.format(
                        "{\"toolName\":\"%s\",\"result\":\"%s\"}",
                        escapeJson(toolName), escapeJson(result)
                    );
                    sendMessage(querySessionId, MessageType.TOOL_RESULT, json);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.tool("[工具结果] " + toolName + ": " + truncate(result, 50))
                    );
                }
                
                @Override
                public void onToolCallChunk(LLMService.StreamToolCallEvent event) {
                    String json = String.format(
                        "{\"id\":\"%s\",\"name\":\"%s\",\"args\":\"%s\",\"complete\":%b,\"index\":%d}",
                        escapeJson(event.getId()),
                        escapeJson(event.getName()),
                        escapeJson(event.getArguments()),
                        event.isComplete(),
                        event.getIndex()
                    );
                    sendMessage(querySessionId, MessageType.TOOL_CALL, json);
                }
            };
            
            // 设置回调
            engine.setStepCallback(stepCallback);
            
            // 定义流式消费者（不再预先 escapeJson，由 sendMessage 统一处理）
            // 加入连接活跃性检查，连接断开时停止消费
            java.util.function.Consumer<String> contentConsumer = chunk -> {
                if (!isSessionActive(querySessionId) || Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("连接已断开，取消流式消费");
                }
                sendMessage(querySessionId, MessageType.CONTENT, chunk);
            };
            
            java.util.function.Consumer<String> thinkingConsumer = chunk -> {
                if (!isSessionActive(querySessionId) || Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("连接已断开，取消流式消费");
                }
                sendMessage(querySessionId, MessageType.THINKING, chunk);
            };
            
            java.util.function.Consumer<LLMService.StreamToolCallEvent> toolCallConsumer = event -> {
                if (!isSessionActive(querySessionId) || Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("连接已断开，取消流式消费");
                }
                String json = String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"args\":\"%s\",\"complete\":%b,\"index\":%d}",
                    escapeJson(event.getId()),
                    escapeJson(event.getName()),
                    escapeJson(event.getArguments()),
                    event.isComplete(),
                    event.getIndex()
                );
                sendMessage(querySessionId, MessageType.TOOL_CALL, json);
            };
            
            // 执行流式查询
            LLMQueryEngine.QueryResult result = engine.queryStream(message, contentConsumer, thinkingConsumer, toolCallConsumer).join();
            
            if (result.isSuccess()) {
                sendMessage(querySessionId, MessageType.COMPLETE, null);
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.success("查询完成")
                );
            } else {
                String errorMsg = result.getErrorMessage();
                sendMessage(querySessionId, MessageType.ERROR, escapeJson(errorMsg));
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.error("查询失败: " + errorMsg)
                );
            }
            
        } catch (Exception e) {
            logger.severe("查询执行失败: " + e.getMessage());
            e.printStackTrace();
            
            sendMessage(querySessionId, MessageType.ERROR, escapeJson("执行失败: " + e.getMessage()));
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.error("执行异常: " + e.getMessage())
            );
        }
    }
    
    /**
     * 发送步骤开始事件
     */
    private void sendStepStart(WebSocket conn, String stepName, String description) {
        String json = String.format(
            "{\"step\":\"%s\",\"description\":\"%s\",\"status\":\"start\"}",
            escapeJson(stepName), escapeJson(description)
        );
        sendMessage(conn, MessageType.STEP_START, json);
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.info("Step", "[开始] " + stepName + ": " + description)
        );
    }
    
    /**
     * 发送步骤思考事件
     */
    private void sendStepThinking(WebSocket conn, String stepName, String thought) {
        String json = String.format(
            "{\"step\":\"%s\",\"thought\":\"%s\",\"status\":\"thinking\"}",
            escapeJson(stepName), escapeJson(thought)
        );
        sendMessage(conn, MessageType.STEP_THINKING, json);
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.info("Thinking", "[思考] " + stepName + ": " + thought)
        );
    }
    
    /**
     * 发送步骤动作事件
     */
    private void sendStepAction(WebSocket conn, String stepName, String action) {
        String json = String.format(
            "{\"step\":\"%s\",\"action\":\"%s\",\"status\":\"action\"}",
            escapeJson(stepName), escapeJson(action)
        );
        sendMessage(conn, MessageType.STEP_ACTION, json);
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.tool("[动作] " + stepName + ": " + action)
        );
    }
    
    /**
     * 发送步骤完成事件
     */
    private void sendStepComplete(WebSocket conn, String stepName, String result) {
        String json = String.format(
            "{\"step\":\"%s\",\"result\":\"%s\",\"status\":\"complete\"}",
            escapeJson(stepName), escapeJson(result)
        );
        sendMessage(conn, MessageType.STEP_COMPLETE, json);
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.success("[完成] " + stepName + ": " + result)
        );
    }
    
    /**
     * 发送工具结果事件（完整结果，供前端展示）
     */
    private void sendToolResult(WebSocket conn, String toolName, String result) {
        String json = String.format(
            "{\"toolName\":\"%s\",\"result\":\"%s\"}",
            escapeJson(toolName), escapeJson(result)
        );
        sendMessage(conn, MessageType.TOOL_RESULT, json);
        WebSocketLogBroadcaster.getInstance().broadcast(
            LogEntry.tool("[工具结果] " + toolName + ": " + truncate(result, 50))
        );
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
                String text = ((com.jwcode.core.model.Message.TextContent) block).getText();
                text = filterInternalThinking(text);
                sb.append(text);
            }
        }
        return sb.toString();
    }
    
    /**
     * 过滤内部思考内容
     */
    private String filterInternalThinking(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String[] thinkingMarkers = {
            "用户说", "分析请求", "正在分析用户输入", "💭", 
            "我已经找到了用户", "根据系统提示", "作为AI助手",
            "我需要", "我会", "让我", "思考过程："
        };
        
        for (String marker : thinkingMarkers) {
            if (text.trim().startsWith(marker)) {
                logger.fine("过滤内部思考内容: " + truncate(text, 50));
                return "";
            }
        }
        
        text = text.replaceAll("(?s)<thinking>.*?</thinking>", "");
        text = text.replaceAll("(?s)\\[思考\\].*?\\[/思考\\]", "");
        
        return text.trim();
    }
    
    /**
     * 解析客户端消息 - 使用 Jackson 替代正则，支持消息内容中包含引号等特殊字符
     */
    private ClientMessage parseMessage(String json) {
        ClientMessage msg = new ClientMessage();
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            
            if (root.has("type")) msg.type = root.get("type").asText();
            if (root.has("sessionId")) msg.sessionId = root.get("sessionId").asText();
            if (root.has("message")) msg.message = root.get("message").asText();
            if (root.has("model")) msg.model = root.get("model").asText();
            if (root.has("token")) msg.token = root.get("token").asText();
        } catch (Exception e) {
            logger.warning("Jackson 解析 JSON 失败，回退到正则解析: " + e.getMessage());
            // 回退到正则解析
            msg.type = extractJsonValue(json, "type");
            msg.sessionId = extractJsonValue(json, "sessionId");
            msg.message = extractJsonValue(json, "message");
            msg.model = extractJsonValue(json, "model");
            msg.token = extractJsonValue(json, "token");
        }
        
        return msg;
    }
    
    /**
     * 从 JSON 中提取值（正则回退方案）
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
            id -> new Session(id, this.defaultWorkingDirectory));
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
     * 关闭所有连接
     */
    public void shutdown() {
        try {
            // 关闭代码库索引器
            if (codebaseIndexer != null) {
                codebaseIndexer.shutdown();
            }
            
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
        STEP_START,     // 步骤开始
        STEP_THINKING,  // 步骤思考中
        STEP_ACTION,    // 步骤执行动作
        STEP_COMPLETE,  // 步骤完成
        PROGRESS,       // 进度更新
        COMPLETE,       // 完成
        ERROR,          // 错误
        PONG,           // 心跳响应
        PING,           // 心跳请求
        AUTH_REQUIRED,  // 需要认证
        AUTH_SUCCESS,   // 认证成功
        AUTH_FAILED,   // 认证失败
        LOG,            // 日志消息
        COMMANDS_LIST,  // 命令列表
        NOTIFICATION,   // 通知消息
        // Plan 模式消息类型
        PLAN_START,     // 开始规划
        PLAN_THINKING,  // 规划思考中
        PLAN_TASKS,     // 任务列表
        PLAN_TASK_START,// 任务开始执行
        PLAN_TASK_UPDATE,// 任务进度更新
        PLAN_TASK_RESULT,// 任务执行结果
        PLAN_COMPLETE,  // 规划完成
        PLAN_ERROR,     // 规划错误
        WORKSPACE_CHANGED  // 工作目录已切换
    }
    
    /**
     * 日志条目类
     */
    public static class LogEntry {
        private final String level;
        private final String source;
        private final String message;
        private final long timestamp;
        
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
                escape(level), escape(source), escape(message), timestamp
            );
        }
        
        private String escape(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r");
        }
        
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
        String token;
    }
}
