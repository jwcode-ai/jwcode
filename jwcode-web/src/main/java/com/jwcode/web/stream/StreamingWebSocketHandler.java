package com.jwcode.web.stream;

import com.jwcode.common.config.ConfigLoader;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.agent.EnhancedOrchestratorAgent;
import com.jwcode.core.api.AgentFlowBroadcaster;
import com.jwcode.core.hook.HookApprovalManager;
import com.jwcode.core.hook.HookChain;
import com.jwcode.core.hook.HookContext;
import com.jwcode.core.hook.HookEventType;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.llm.*;
import com.jwcode.core.index.CodebaseIndexer;
import com.jwcode.core.memory.FileMemoryLayer;
import com.jwcode.core.permission.PermissionManager;
import com.jwcode.core.plan.PlanModeManager;
import com.jwcode.core.session.Session;
import com.jwcode.core.service.ContextWindowManager;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.hands.ToolHand;
import com.jwcode.core.workflow.EffectVM;
import com.jwcode.core.workflow.WorkflowArtifactStore;
import com.jwcode.core.workflow.WorkflowCompiler;
import com.jwcode.core.workflow.WorkflowEvent;
import com.jwcode.core.workflow.WorkflowEventBus;
import com.jwcode.core.workflow.WorkflowIR;
import com.jwcode.core.workflow.WorkflowInput;
import com.jwcode.core.workflow.WorkflowLedger;
import com.jwcode.core.workflow.WorkflowResult;
import com.jwcode.core.workflow.WorkflowState;
import com.jwcode.core.workflow.WorkflowStatus;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.ErrorMode;
import com.jwcode.core.workflow.ir.PhaseNode;
import com.jwcode.core.workflow.ir.PipelineNode;
import com.jwcode.web.WebSessionManager;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final ObjectMapper WORKFLOW_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final WorkflowCompiler WORKFLOW_COMPILER = new WorkflowCompiler();
    private static final String PLAN_SYSTEM_PROMPT_MARKER = "[JWCODE_PLAN_SYSTEM_PROMPT]";
    
    // 心跳检测配置
    private static final long PING_INTERVAL_MS = 30000; // 30秒发送一次 ping
    private static final long PONG_TIMEOUT_MS = 90000;   // 90秒内未收到 pong 则断开
    private static final long CONNECTION_TIMEOUT_MS = 300000; // 5分钟无活动则断开
    
    // 认证配置（从系统配置读取）
    private final com.jwcode.web.auth.WebSocketAuthenticator authenticator;
    
    private final Map<String, Session> sessions;
    private final ToolRegistry toolRegistry;
    private CodebaseIndexer codebaseIndexer;
    private volatile com.jwcode.core.hook.HookChain hookChain;
    private final Map<WebSocket, String> connectionSessions;
    private final Map<String, WebSocket> activeSessionConnections; // sessionId -> 当前活跃连接
    private final Map<WebSocket, Consumer<LogEntry>> logListeners;
    private final Map<WebSocket, Long> lastPongTime;    // 最后收到 pong 的时间
    private final Map<WebSocket, Long> lastActivityTime; // 最后活跃时间
    
    // 跟踪每个 sessionId 正在执行的 Future，连接断开时取消
    private final Map<String, java.util.concurrent.Future<?>> runningQueryFutures;
    private final Map<String, java.util.concurrent.Future<?>> runningWorkflowFutures;
    
    // 跟踪被暂停的查询（sessionId -> 暂停锁对象）
    private final Set<String> pausedQuerySessions;
    
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
        final long seq;

        PendingMessage(MessageType type, String data, long seq) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.seq = seq;
        }
    }

    // 每个会话的消息序号生成器
    private final Map<String, java.util.concurrent.atomic.AtomicLong> sessionSeqCounters = new ConcurrentHashMap<>();
    
    // 默认工作目录（前端可动态切换）
    private String defaultWorkingDirectory = System.getProperty("user.dir");

    private WebSessionManager sessionStore;

    /**
     * 设置代码库索引器（由 WebServer 初始化后注入）
     */
    public void setCodebaseIndexer(CodebaseIndexer indexer) {
        this.codebaseIndexer = indexer;
    }

    public void setHookChain(com.jwcode.core.hook.HookChain hookChain) {
        this.hookChain = hookChain;
    }

    private com.jwcode.core.command.CommandRegistry commandRegistry;

    public void setCommandRegistry(com.jwcode.core.command.CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    /** Inject session manager for message persistence. */
    public void setSessionManager(WebSessionManager mgr) {
        this.sessionStore = mgr;
    }
    
    // 查询线程池：核心 4 线程，最大 16 线程，60 秒回收
    private final ThreadPoolExecutor queryExecutor;

    // 心跳和清理的后台调度器（替代手工守护线程）
    private final ScheduledExecutorService heartbeatScheduler;
    
    public StreamingWebSocketHandler(int port, ToolRegistry toolRegistry) {
        super(new InetSocketAddress(port));
        // 启用内置连接丢失检测（30s ping），
        // ws 库客户端自动响应 WebSocket 协议层 PONG 帧，保持 TCP 连接存活。
        // 注意：应用层 checkHeartbeat() 是独立的，处理的是应用层 pong 超时（90s）。
        this.setConnectionLostTimeout(30);
        this.sessions = new ConcurrentHashMap<>();
        this.toolRegistry = toolRegistry;
        this.connectionSessions = new ConcurrentHashMap<>();
        this.activeSessionConnections = new ConcurrentHashMap<>();
        this.logListeners = new ConcurrentHashMap<>();
        this.lastPongTime = new ConcurrentHashMap<>();
        this.lastActivityTime = new ConcurrentHashMap<>();
        this.authenticator = new com.jwcode.web.auth.WebSocketAuthenticator(
            (conn, type, payload) -> sendMessage(conn, MessageType.valueOf(type.toUpperCase()), payload));
        this.pendingMessages = new ConcurrentHashMap<>();
        this.runningQueryFutures = new ConcurrentHashMap<>();
        this.runningWorkflowFutures = new ConcurrentHashMap<>();
        this.pausedQuerySessions = ConcurrentHashMap.newKeySet();
        
        // 初始化查询线程池：核心 4，最大 16，60 秒回收，有界队列 100
        this.queryExecutor = new ThreadPoolExecutor(
            4, 16, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ws-query-" + count.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            (r, executor) -> {
                // 拒绝策略：记录告警，返回429错误给客户端，不阻塞WebSocket IO线程
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
                logger.severe("[WebSocket] 查询线程池已满! active=" + tpe.getActiveCount()
                    + ", pool=" + tpe.getPoolSize() + "/" + tpe.getMaximumPoolSize()
                    + ", queue=" + tpe.getQueue().size() + "/100"
                    + " — 拒绝任务（客户端应稍后重试）");
                // 不执行 r.run()——避免阻塞WebSocket IO线程
                // 调用方会在 sendMessage 时检测连接状态自行处理
            }
        );

        // 初始化心跳调度器（替代手工守护线程）
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-heartbeat");
            t.setDaemon(true);
            return t;
        });
        
        // 从配置读取有效 token
        loadPermissionConfig();

        // 初始化 Hook 审批系统 — Bash/FileWrite 操作需要用户确认
        try {
            var registry = new com.jwcode.core.hook.HookRegistry();
            // 注册内置安全钩子
            registry.register(new com.jwcode.core.hook.BashSafetyHook());
            registry.register(new com.jwcode.core.hook.FileWriteAuditHook());
            var auditLogger = new com.jwcode.core.hook.HookAuditLogger();
            this.hookChain = new com.jwcode.core.hook.HookChain(registry, auditLogger);
            logger.info("Hook system initialized: BashSafety + FileWriteAudit");
        } catch (Exception e) {
            logger.warning("Hook init failed: " + e.getMessage());
        }

        // 启动心跳检测（ScheduledExecutorService 替代手工线程）
        heartbeatScheduler.scheduleWithFixedDelay(
            this::checkHeartbeat, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
        heartbeatScheduler.scheduleWithFixedDelay(
            this::cleanupStalePendingMessages, 600_000, 600_000, TimeUnit.MILLISECONDS);
        
        // 初始化 TodoWriteBroadcaster（接线待办状态广播）
        initTodoWriteBroadcaster();
        initHookApprovalBroadcaster();
    }
    
    /**
     * 初始化 TodoWriteBroadcaster — 将待办状态广播接入主 WebSocket
     * 
     * 注意：todo_update/todo_item_done/todo_progress 的 data 是 JSON 原始数据（数组或对象），
     * 不能通过 sendMessage(MessageType.LOG) 发送（LOG 会特殊处理 data 格式）。
     * 需要直接构造正确的 JSON 消息发送。
     */
    private void initTodoWriteBroadcaster() {
        try {
            com.jwcode.core.api.TodoWriteBroadcaster broadcaster = 
                com.jwcode.core.api.TodoWriteBroadcaster.getInstance();
            broadcaster.setBroadcastAdapter((sessionId, type, data) -> {
                if (sessionId == null) return;
                WebSocket conn = activeSessionConnections.get(sessionId);
                if (conn == null || !conn.isOpen()) return;
                // 直接构造 JSON 消息，data 已经是 JSON 原始数据（数组或对象）
                String json = String.format(
                    "{\"type\": \"%s\", \"data\": %s, \"sessionId\": \"%s\"}",
                    type,  // type 来自 broadcaster: "todo_update" / "todo_item_done" / "todo_progress"
                    data,
                    escapeJson(sessionId)
                );
                try {
                    conn.send(json);
                } catch (Exception ex) {
                    logger.warning("发送 todo 消息失败: " + ex.getMessage());
                }
            });
            logger.info("TodoWriteBroadcaster wired to StreamingWebSocketHandler (direct JSON)");
        } catch (Exception e) {
            logger.warning("Failed to wire TodoWriteBroadcaster: " + e.getMessage());
        }
    }
    
    /**
     * 从配置加载权限设置（token 由 WebSocketAuthenticator 管理）。
     */
    private void loadPermissionConfig() {
        // 0. 传播权限配置到 PermissionManager（必须在早期返回之前执行）
        try {
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            if (config != null && config.getSettings() != null && config.getSettings().getPermissions() != null) {
                JwcodeConfig.PermissionSettings perm = config.getSettings().getPermissions();
                PermissionManager pm = PermissionManager.getInstance();
                pm.setAutoApproveRead(perm.isAutoApproveRead());
                pm.setAutoApproveWrite(perm.isAutoApproveWrite());
                pm.setAutoApproveDelete(perm.isAutoApproveDelete());
                pm.setAutoApproveDestructive(perm.isAutoApproveDestructive());
                logger.info("权限配置已传播: read=" + perm.isAutoApproveRead()
                    + " write=" + perm.isAutoApproveWrite()
                    + " delete=" + perm.isAutoApproveDelete()
                    + " destructive=" + perm.isAutoApproveDestructive());
            }
        } catch (Exception e) {
            logger.fine("无法传播权限配置: " + e.getMessage());
        }

    }
    
    /**
     * 关闭 WebSocket 服务器，包括心跳调度器和查询线程池。
     */
    @Override
    public void stop() throws InterruptedException {
        heartbeatScheduler.shutdownNow();
        queryExecutor.shutdown();
        try {
            if (!queryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                queryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            queryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            throw e;
        }
        super.stop();
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
            try {
                sendMessage(conn, MessageType.PING, null);
            } catch (Exception e) {
                logger.warning("发送心跳 ping 失败: " + e.getMessage());
            }
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

        // 检查 Origin 头，防止跨站 WebSocket 劫持（CSRF）
        if (handshake != null) {
            String origin = handshake.getFieldValue("Origin");
            String host = handshake.getFieldValue("Host");
            if (origin != null && !origin.isEmpty() && !origin.equals("null")) {
                boolean allowed = false;
                if (host != null && !host.isEmpty()) {
                    String expectedOrigin = "http://" + host;
                    String expectedOriginHttps = "https://" + host;
                    if (origin.equals(expectedOrigin) || origin.equals(expectedOriginHttps)
                        || origin.startsWith("http://localhost") || origin.startsWith("https://localhost")
                        || origin.startsWith("http://127.0.0.1") || origin.startsWith("https://127.0.0.1")) {
                        allowed = true;
                    }
                } else {
                    if (origin.startsWith("http://localhost") || origin.startsWith("https://localhost")
                        || origin.startsWith("http://127.0.0.1") || origin.startsWith("https://127.0.0.1")) {
                        allowed = true;
                    }
                }
                if (!allowed) {
                    logger.warning("WebSocket 连接被拒绝: Origin=" + origin + " 不被允许 (Host=" + host + ")");
                    conn.close(4003, "Origin not allowed");
                    return;
                }
            }
        }

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
        if (code == 1006) {
            // 1006 表示异常断开（网络层问题），记录更多上下文便于定位
            String sessionId = connectionSessions.get(conn);
            Long lastPong = lastPongTime.get(conn);
            Long lastActivity = lastActivityTime.get(conn);
            long now = System.currentTimeMillis();
            logger.warning("WebSocket 异常断开(code=1006)" 
                + ", sessionId=" + sessionId
                + ", lastPong=" + (lastPong != null ? ((now - lastPong) / 1000) + "s ago" : "N/A")
                + ", lastActivity=" + (lastActivity != null ? ((now - lastActivity) / 1000) + "s ago" : "N/A")
                + ", remote=" + remote);
        } else {
            logger.warning("连接关闭: code=" + code + ", reason=" + reason + ", remote=" + remote);
        }
        
        // 清理监听器
        Consumer<LogEntry> listener = logListeners.remove(conn);
        if (listener != null) {
            WebSocketLogBroadcaster.getInstance().removeListener(listener);
        }
        
        // 清理认证状态
        authenticator.removeConnection(conn);
        
        // 清理会话关联（双向映射）
        String sessionId = connectionSessions.remove(conn);
        if (sessionId != null) {
            activeSessionConnections.remove(sessionId);
            // 连接断开时取消正在运行的查询任务
            cancelRunningQuery(sessionId);
            // 连接断开时拒绝该 session 的所有待审批 Hook 请求
            HookApprovalManager.getInstance().denyAllForSession(sessionId);
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
                authenticator.handleAuth(conn, clientMsg.token);
                return;
            }
            
            // 其他消息需要认证
            if (!authenticator.isAuthenticated(conn)) {
                logger.warning("连接未认证: " + conn.getRemoteSocketAddress());
                sendMessage(conn, MessageType.AUTH_REQUIRED, "Authentication required");
                return;
            }
            
            // UserPromptSubmit Hook — 用户提交提示时触发
            if ("chat".equals(clientMsg.type) || "plan".equals(clientMsg.type)) {
                triggerLifecycleHook(HookEventType.USER_PROMPT_SUBMIT, conn, clientMsg);
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
                case "workflow_start":
                    logger.info("handle workflow_start message");
                    handleWorkflowStart(conn, clientMsg);
                    break;
                case "workflow_resume":
                    logger.info("handle workflow_resume message");
                    handleWorkflowResume(conn, clientMsg);
                    break;
                case "workflow_status":
                    handleWorkflowStatus(conn, clientMsg);
                    break;
                case "workflow_cancel":
                    handleWorkflowCancel(conn, clientMsg);
                    break;
                case "workflow_pause":
                    handleWorkflowPause(conn, clientMsg);
                    break;
                case "ping":
                    lastPongTime.put(conn, System.currentTimeMillis());
                    sendMessage(conn, MessageType.PONG, null);
                    break;
                case "pong":
                    // 客户端心跳响应，更新时间戳即可
                    lastPongTime.put(conn, System.currentTimeMillis());
                    break;
                case "message_ack":
                    // 客户端确认收到消息，标记已送达
                    handleMessageAck(conn, clientMsg);
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
                case "stop":
                    logger.info("收到 stop 消息, sessionId=" + clientMsg.sessionId);
                    cancelRunningQuery(clientMsg.sessionId);
                    sendMessage(conn, MessageType.COMPLETE, null, clientMsg.sessionId);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.info("System", "用户已终止生成"));
                    break;
                case "pause":
                    logger.info("收到 pause 消息, sessionId=" + clientMsg.sessionId);
                    pauseRunningQuery(clientMsg.sessionId);
                    sendMessage(conn, MessageType.GENERATION_PAUSED, null, clientMsg.sessionId);
                    break;
                case "resume":
                    logger.info("收到 resume 消息, sessionId=" + clientMsg.sessionId);
                    resumeRunningQuery(clientMsg.sessionId);
                    sendMessage(conn, MessageType.GENERATION_RESUMED, null, clientMsg.sessionId);
                    break;
                case "plan_mode_change":
                    logger.info("收到 plan_mode_change 消息, sessionId=" + clientMsg.sessionId);
                    handlePlanModeChange(conn, clientMsg);
                    break;
                case "init":
                    handleInit(conn, clientMsg);
                    break;
                case "effort":
                    handleEffort(conn, clientMsg);
                    break;
                case "branch":
                    handleBranch(conn, clientMsg);
                    break;
                case "mcp":
                    handleMcpCommand(conn, clientMsg);
                    break;
                case "skills":
                    handleSkillsCommand(conn, clientMsg);
                    break;
                case "agents":
                    handleAgentsCommand(conn, clientMsg);
                    break;
                case "model_change":
                    handleModelChange(conn, clientMsg);
                    break;
                case "config":
                    handleConfigCommand(conn, clientMsg);
                    break;
                case "plugin":
                    handlePluginCommand(conn, clientMsg);
                    break;
                case "compact":
                    handleCompact(conn, clientMsg);
                    break;
                case "toggle_workspace_guard":
                    handleToggleWorkspaceGuard(conn, clientMsg);
                    break;
                case "toggle_yolo":
                    handleToggleYolo(conn, clientMsg);
                    break;
                case "toggle_auto_swarm":
                    handleToggleAutoSwarm(conn, clientMsg);
                    break;
                case "hook_allow":
                    handleHookApprovalResponse(clientMsg, true);
                    break;
                case "hook_deny":
                    handleHookApprovalResponse(clientMsg, false);
                    break;
                case "plan_confirm":
                    logger.info("处理 plan_confirm 消息, sessionId=" + clientMsg.sessionId);
                    handlePlanConfirm(conn, clientMsg);
                    break;
                case "plan_refine":
                    logger.info("处理 plan_refine 消息, sessionId=" + clientMsg.sessionId);
                    handlePlanRefine(conn, clientMsg);
                    break;
                case "doctor":
                    logger.info("处理 doctor 消息, sessionId=" + clientMsg.sessionId);
                    handleDoctorCommand(conn, clientMsg);
                    break;
                case "rewind":
                    logger.info("处理 rewind 消息, sessionId=" + clientMsg.sessionId);
                    handleRewindCommand(conn, clientMsg);
                    break;
                case "update_docs":
                case "project":
                    logger.info("处理 " + clientMsg.type + " 消息, sessionId=" + clientMsg.sessionId);
                    handleUpdateDocsCommand(conn, clientMsg);
                    break;
                case "tokens":
                    handleTokensCommand(conn, clientMsg);
                    break;
                // Phase 3 — graceful "not yet implemented" notifications
                case "export":
                case "checkpoint":
                case "test":
                case "lint":
                case "search":
                    logger.info("收到未实现命令: " + clientMsg.type);
                    sendMessage(conn, MessageType.NOTIFICATION,
                        escapeJson("Command '" + clientMsg.type + "' is not yet implemented via WebSocket."));
                    break;
                case "command_execute":
                    handleCommandExecute(conn, clientMsg);
                    break;
                case "exit":
                    logger.info("收到 exit 消息，正在关闭后端服务...");
                    sendMessage(conn, MessageType.EXIT, "Server shutting down...");
                    // 异步关闭，先让响应发出去
                    new Thread(() -> {
                        try { Thread.sleep(500); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.exit(0);
                        }
                    }).start();
                    break;
                default:
                    logger.warning("未知消息类型: " + clientMsg.type);
                    sendMessage(conn, MessageType.ERROR, "Unknown message type: " + clientMsg.type);
            }
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "处理消息失败", e);
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
        
        // 注册 PlanModeManager 模式切换监听器 — 模式切换时广播到前端
        try {
            PlanModeManager modeManager = PlanModeManager.getInstance();
            modeManager.addListener(event -> {
                // Determine transition direction from the event
                boolean enteringPlan = event.newMode() == PlanModeManager.Mode.PLAN;
                boolean exitingPlan = event.previousMode() == PlanModeManager.Mode.PLAN;

                // Broadcast mode change to all connected clients
                String modeEventJson = String.format(
                    "{\"previousMode\":\"%s\",\"newMode\":\"%s\",\"description\":\"%s\"}",
                    event.previousMode().getValue(),
                    event.newMode().getValue(),
                    escapeJson(event.description())
                );
                for (WebSocket conn : getConnections()) {
                    if (conn.isOpen()) {
                        sendMessage(conn, MessageType.PLAN_MODE_CHANGE, modeEventJson);
                        // Send separate enter/exit events for CLI reactive state
                        if (enteringPlan) {
                            sendMessage(conn, MessageType.PLAN_MODE_ENTER, modeEventJson);
                        } else if (exitingPlan) {
                            sendMessage(conn, MessageType.PLAN_MODE_EXIT, modeEventJson);
                        }
                    }
                }
                logger.info("PlanMode 切换广播: " + event.previousMode() + " → " + event.newMode());
            });
            logger.info("PlanModeManager 监听器已注册");
        } catch (Exception e) {
            logger.warning("注册 PlanModeManager 监听器失败: " + e.getMessage());
        }
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

    private void handleWorkflowStart(WebSocket conn, ClientMessage msg) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        Session session = sessions.get(sessionId);
        if (session == null) {
            session = createNewSession(sessionId, msg.model);
        }
        connectionSessions.put(conn, sessionId);
        activeSessionConnections.put(sessionId, conn);

        String runId = msg.runId != null && !msg.runId.isBlank()
            ? msg.runId
            : "wf-" + UUID.randomUUID();

        try {
            WorkflowIR ir = resolveWorkflowIR(msg, runId);
            WorkflowInput input = buildWorkflowInput(sessionId, msg);
            saveWorkflowIR(runId, ir);
            sendMessage(conn, MessageType.WORKFLOW_STARTED, workflowRunJson(runId, sessionId, "RUNNING"), sessionId);
            sendMessage(conn, MessageType.START, "", sessionId);
            java.util.concurrent.Future<?> future = queryExecutor.submit(() -> executeWorkflowRun(sessionId, runId, ir, input));
            runningWorkflowFutures.put(runId, future);
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "workflow_start failed", e);
            sendMessage(conn, MessageType.WORKFLOW_ERROR, workflowErrorJson(runId, e.getMessage()), sessionId);
        }
    }

    private void handleWorkflowResume(WebSocket conn, ClientMessage msg) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        connectionSessions.put(conn, sessionId);
        activeSessionConnections.put(sessionId, conn);

        if (msg.runId == null || msg.runId.isBlank()) {
            sendMessage(conn, MessageType.WORKFLOW_ERROR, workflowErrorJson("", "workflow_resume requires runId"), sessionId);
            return;
        }

        try {
            WorkflowLedger existingLedger = new WorkflowLedger(msg.runId, workflowRunDirectory(msg.runId));
            if (existingLedger.replayState().status() == com.jwcode.core.workflow.WorkflowStatus.CANCELLED && !msg.forceResume) {
                sendMessage(conn, MessageType.WORKFLOW_ERROR,
                    workflowErrorJson(msg.runId, "Cannot resume cancelled workflow without forceResume=true"), sessionId);
                return;
            }
            WorkflowIR ir = hasWorkflowIR(msg) ? resolveWorkflowIR(msg, msg.runId) : readWorkflowIR(msg.runId);
            WorkflowInput input = buildWorkflowInput(sessionId, msg);
            saveWorkflowIR(msg.runId, ir);
            sendMessage(conn, MessageType.WORKFLOW_STARTED, workflowRunJson(msg.runId, sessionId, "RESUMING"), sessionId);
            java.util.concurrent.Future<?> future = queryExecutor.submit(() -> executeWorkflowRun(sessionId, msg.runId, ir, input));
            runningWorkflowFutures.put(msg.runId, future);
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "workflow_resume failed", e);
            sendMessage(conn, MessageType.WORKFLOW_ERROR, workflowErrorJson(msg.runId, e.getMessage()), sessionId);
        }
    }

    private void handleWorkflowStatus(WebSocket conn, ClientMessage msg) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        if (msg.runId == null || msg.runId.isBlank()) {
            sendMessage(conn, MessageType.WORKFLOW_ERROR, workflowErrorJson("", "workflow_status requires runId"), sessionId);
            return;
        }
        try {
            Path runDir = workflowRunDirectory(msg.runId);
            WorkflowState state = Files.exists(runDir.resolve("events.jsonl"))
                ? new WorkflowLedger(msg.runId, runDir).replayState()
                : new WorkflowState(msg.runId);
            ObjectNode json = WORKFLOW_MAPPER.createObjectNode();
            json.put("runId", msg.runId);
            json.put("sessionId", sessionId);
            json.put("status", state.status().name());
            json.put("completedEffects", state.completedEffectsCount());
            json.put("completedPhases", state.completedPhasesCount());
            json.put("tokensUsed", state.tokensUsed());
            sendMessage(conn, MessageType.WORKFLOW_PROGRESS, json.toString(), sessionId);
        } catch (Exception e) {
            sendMessage(conn, MessageType.WORKFLOW_ERROR, workflowErrorJson(msg.runId, e.getMessage()), sessionId);
        }
    }

    private void handleWorkflowCancel(WebSocket conn, ClientMessage msg) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        if (msg.runId == null || msg.runId.isBlank()) {
            sendMessage(conn, MessageType.WORKFLOW_ERROR, workflowErrorJson("", "workflow_cancel requires runId"), sessionId);
            return;
        }
        java.util.concurrent.Future<?> future = runningWorkflowFutures.remove(msg.runId);
        if (future != null) {
            future.cancel(true);
        }
        try {
            new WorkflowLedger(msg.runId, workflowRunDirectory(msg.runId)).append("run.cancelled", Map.of());
        } catch (Exception e) {
            logger.fine("Failed to append workflow cancellation: " + e.getMessage());
        }
        sendMessage(conn, MessageType.WORKFLOW_FINISHED, workflowRunJson(msg.runId, sessionId, "CANCELLED"), sessionId);
    }

    private void handleWorkflowPause(WebSocket conn, ClientMessage msg) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        if (msg.runId == null || msg.runId.isBlank()) {
            sendMessage(conn, MessageType.WORKFLOW_ERROR, workflowErrorJson("", "workflow_pause requires runId"), sessionId);
            return;
        }
        try {
            new WorkflowLedger(msg.runId, workflowRunDirectory(msg.runId)).append("run.paused", Map.of());
        } catch (Exception e) {
            logger.fine("Failed to append workflow pause: " + e.getMessage());
        }
        sendMessage(conn, MessageType.WORKFLOW_PROGRESS, workflowRunJson(msg.runId, sessionId, "PAUSED"), sessionId);
    }

    private void executeWorkflowRun(String sessionId, String runId, WorkflowIR ir, WorkflowInput input) {
        Path runDir = workflowRunDirectory(runId);
        WorkflowEventBus eventBus = new WorkflowEventBus();
        eventBus.subscribe(event -> sendWorkflowEvent(sessionId, event));
        try {
            WorkflowLedger ledger = new WorkflowLedger(runId, runDir, eventBus);
            EffectVM vm = new EffectVM(
                ledger,
                new WorkflowArtifactStore(runDir),
                createWorkflowAgentHand(sessionId),
                createWorkflowToolHand(),
                createWorkflowMemoryLayer());
            WorkflowResult result = vm.execute(runId, ir, input);
            if (result.status() == WorkflowStatus.COMPLETED) {
                ObjectNode done = WORKFLOW_MAPPER.createObjectNode();
                done.put("runId", runId);
                done.put("sessionId", sessionId);
                done.put("status", result.status().name());
                done.set("output", result.output());
                sendMessage(sessionId, MessageType.WORKFLOW_FINISHED, done.toString());
                sendMessage(sessionId, MessageType.COMPLETE, "");
            } else if (result.status() == WorkflowStatus.PAUSED) {
                sendMessage(sessionId, MessageType.WORKFLOW_PROGRESS, workflowRunJson(runId, sessionId, "PAUSED"));
            } else if (result.status() == WorkflowStatus.CANCELLED) {
                sendMessage(sessionId, MessageType.WORKFLOW_FINISHED, workflowRunJson(runId, sessionId, "CANCELLED"));
                sendMessage(sessionId, MessageType.COMPLETE, "");
            } else {
                sendMessage(sessionId, MessageType.WORKFLOW_ERROR, workflowErrorJson(runId, result.errorMessage()));
                sendMessage(sessionId, MessageType.ERROR, result.errorMessage() == null ? "Workflow failed" : result.errorMessage());
            }
        } catch (Exception e) {
            logger.log(java.util.logging.Level.SEVERE, "workflow execution failed", e);
            sendMessage(sessionId, MessageType.WORKFLOW_ERROR, workflowErrorJson(runId, e.getMessage()));
            sendMessage(sessionId, MessageType.ERROR, e.getMessage() == null ? "Workflow failed" : e.getMessage());
        } finally {
            runningWorkflowFutures.remove(runId);
        }
    }

    private LLMQueryEngine.StepCallback createWorkflowStepCallback(String sessionId) {
        return new LLMQueryEngine.StepCallback() {
            @Override
            public void onStepStart(String stepName, String description) {
                String json = String.format(
                    "{\"step\":\"%s\",\"description\":\"%s\",\"status\":\"start\"}",
                    escapeJson(stepName), escapeJson(description));
                sendMessage(sessionId, MessageType.STEP_START, json);
            }

            @Override
            public void onStepThinking(String stepName, String thought) {
                String json = String.format(
                    "{\"step\":\"%s\",\"thought\":\"%s\",\"status\":\"thinking\"}",
                    escapeJson(stepName), escapeJson(thought));
                sendMessage(sessionId, MessageType.STEP_THINKING, json);
            }

            @Override
            public void onStepAction(String stepName, String action) {
                String json = String.format(
                    "{\"step\":\"%s\",\"action\":\"%s\",\"status\":\"action\"}",
                    escapeJson(stepName), escapeJson(action));
                sendMessage(sessionId, MessageType.STEP_ACTION, json);
            }

            @Override
            public void onStepComplete(String stepName, String result, boolean success) {
                String json = String.format(
                    "{\"step\":\"%s\",\"result\":\"%s\",\"status\":\"%s\"}",
                    escapeJson(stepName), escapeJson(result), success ? "success" : "error");
                sendMessage(sessionId, MessageType.STEP_COMPLETE, json);
            }

            @Override
            public void onToolResult(String toolName, String result, String toolCallId) {
                String json = String.format(
                    "{\"id\":\"%s\",\"toolName\":\"%s\",\"result\":\"%s\"}",
                    escapeJson(toolCallId), escapeJson(toolName), escapeJson(result));
                sendMessage(sessionId, MessageType.TOOL_RESULT, json);
            }

            @Override
            public void onToolCallChunk(LLMService.StreamToolCallEvent event) {
                String json = String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"args\":\"%s\",\"complete\":%b,\"index\":%d}",
                    escapeJson(event.getId()),
                    escapeJson(event.getName()),
                    escapeJson(event.getArguments()),
                    event.isComplete(),
                    event.getIndex());
                sendMessage(sessionId, MessageType.TOOL_CALL, json);
            }
        };
    }

    private LocalAgentHand createWorkflowAgentHand(String sessionId) {
        ToolExecutor toolExecutor = createWorkflowToolExecutor();
        try {
            JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
            LLMService llmService = LLMFactory.fromConfig(config).getLLMService();
            return new LocalAgentHand(
                llmService,
                toolExecutor,
                Path.of(defaultWorkingDirectory),
                null,
                createWorkflowMemoryLayer(),
                createWorkflowStepCallback(sessionId));
        } catch (Exception e) {
            logger.warning("Workflow LocalAgentHand falling back to echo mode: " + e.getMessage());
            return new LocalAgentHand();
        }
    }

    private ToolHand createWorkflowToolHand() {
        return new ToolHand(createWorkflowToolExecutor(), Path.of(defaultWorkingDirectory));
    }

    private ToolExecutor createWorkflowToolExecutor() {
        return new ToolExecutor(
            this.toolRegistry,
            new com.jwcode.core.permission.PermissionManagerChecker(),
            null,
            this.hookChain);
    }

    private WorkflowIR resolveWorkflowIR(ClientMessage msg, String runId) {
        JsonNode irNode = firstWorkflowIRNode(msg);
        if (irNode != null) {
            return WORKFLOW_COMPILER.fromJson(irNode.toString());
        }
        if (msg.data != null && msg.data.trim().startsWith("{")) {
            try {
                return WORKFLOW_COMPILER.fromJson(msg.data);
            } catch (Exception ignored) {
                // data may be an arbitrary payload, not an IR.
            }
        }
        return defaultWorkflowIR(runId, msg.message != null ? msg.message : msg.data);
    }

    private boolean hasWorkflowIR(ClientMessage msg) {
        if (firstWorkflowIRNode(msg) != null) {
            return true;
        }
        if (msg.data == null || !msg.data.trim().startsWith("{")) {
            return false;
        }
        try {
            WORKFLOW_COMPILER.fromJson(msg.data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode firstWorkflowIRNode(ClientMessage msg) {
        if (isWorkflowIRNode(msg.workflow)) return msg.workflow;
        if (isWorkflowIRNode(msg.ir)) return msg.ir;
        if (isWorkflowIRNode(msg.dataNode)) return msg.dataNode;
        return null;
    }

    private boolean isWorkflowIRNode(JsonNode node) {
        return node != null && node.isObject() && node.has("root");
    }

    private WorkflowInput buildWorkflowInput(String sessionId, ClientMessage msg) {
        JsonNode payload;
        if (msg.input != null && !msg.input.isNull()) {
            payload = msg.input;
        } else {
            ObjectNode object = WORKFLOW_MAPPER.createObjectNode();
            if (msg.message != null) object.put("message", msg.message);
            if (msg.dataNode != null && !msg.dataNode.isNull()) object.set("data", msg.dataNode);
            else if (msg.data != null) object.put("data", msg.data);
            object.put("workingDirectory", defaultWorkingDirectory);
            payload = object;
        }
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("transport", "websocket");
        metadata.put("projectId", msg.projectId != null && !msg.projectId.isBlank()
            ? msg.projectId
            : Path.of(defaultWorkingDirectory).toAbsolutePath().normalize().toString());
        metadata.put("memoryEnabled", msg.memoryEnabled == null || msg.memoryEnabled);
        metadata.put("checkpointPolicy", msg.checkpointPolicy == null || msg.checkpointPolicy.isBlank()
            ? "phase-and-token"
            : msg.checkpointPolicy);
        if (msg.forceResume) {
            metadata.put("forceResume", true);
        }
        return new WorkflowInput(sessionId, payload, metadata);
    }

    private WorkflowIR defaultWorkflowIR(String runId, String message) {
        String request = message == null || message.isBlank() ? "Execute the requested workflow." : message;
        List<String> readTools = List.of("FileReadTool", "BatchReadTool", "GrepTool", "GlobTool", "ToolSearch", "SmartAnalyzeTool");
        List<String> codeTools = List.of("FileReadTool", "BatchReadTool", "GrepTool", "GlobTool", "FileEditTool",
            "FileWriteTool", "BashTool", "PowerShell", "TodoWrite");
        List<String> verifyTools = List.of("FileReadTool", "BatchReadTool", "GrepTool", "GlobTool", "BashTool",
            "PowerShell", "GitTool", "LSPTool");
        return new WorkflowIR(
            "websocket-" + runId,
            new PipelineNode("root-pipeline", List.of(
                new PhaseNode("p1-explore", "explore", List.of(
                    new AgentNode("e1-explore", "explorer",
                        "Explore the repository and clarify implementation constraints for this request:\n\n" + request,
                        readTools, null, 0, 0))),
                new PhaseNode("p2-code", "code", List.of(
                    new AgentNode("e2-code", "coder",
                        "Implement the requested change using the exploration result as context:\n\n" + request,
                        codeTools, null, 0, 0))),
                new PhaseNode("p3-verify", "verify", List.of(
                    new AgentNode("e3-verify", "verifier",
                        "Verify the implementation, run appropriate checks, and summarize remaining risk for:\n\n" + request,
                        verifyTools, null, 0, 0)))
            ), ErrorMode.FAIL_FAST),
            null,
            "workflow-ir.v1");
    }

    private void sendWorkflowEvent(String sessionId, WorkflowEvent event) {
        String json = workflowEventJson(event);
        sendMessage(sessionId, MessageType.WORKFLOW_EVENT, json);
        if (event.totalEffects() > 0 || event.totalPhases() > 0) {
            sendMessage(sessionId, MessageType.WORKFLOW_PROGRESS, json);
        }
    }

    private String workflowEventJson(WorkflowEvent event) {
        ObjectNode json = WORKFLOW_MAPPER.createObjectNode();
        json.put("eventId", event.eventId());
        json.put("runId", event.runId());
        json.put("type", event.type());
        json.put("timestamp", event.timestamp().toString());
        json.put("sequence", event.sequence());
        if (event.phaseId() != null) json.put("phaseId", event.phaseId());
        if (event.effectId() != null) json.put("effectId", event.effectId());
        json.put("completedEffects", event.completedEffects());
        json.put("totalEffects", event.totalEffects());
        json.put("completedPhases", event.completedPhases());
        json.put("totalPhases", event.totalPhases());
        json.put("tokensUsed", event.tokensUsed());
        json.put("tokensRemaining", event.tokensRemaining());
        json.set("data", WORKFLOW_MAPPER.valueToTree(event.data()));
        return json.toString();
    }

    private String workflowRunJson(String runId, String sessionId, String status) {
        ObjectNode json = WORKFLOW_MAPPER.createObjectNode();
        json.put("runId", runId);
        json.put("sessionId", sessionId);
        json.put("status", status);
        json.put("workflowRoot", workflowRunDirectory(runId).toString());
        return json.toString();
    }

    private String workflowErrorJson(String runId, String error) {
        ObjectNode json = WORKFLOW_MAPPER.createObjectNode();
        json.put("runId", runId == null ? "" : runId);
        json.put("error", error == null ? "Workflow failed" : error);
        return json.toString();
    }

    private Path workflowRootDirectory() {
        return Path.of(System.getProperty(
            "jwcode.workflow.root",
            Path.of(System.getProperty("user.home"), ".jwcode", "workflows").toString()));
    }

    private Path workflowRunDirectory(String runId) {
        return workflowRootDirectory().resolve(runId);
    }

    private FileMemoryLayer createWorkflowMemoryLayer() {
        return new FileMemoryLayer(Path.of(System.getProperty(
            "jwcode.memory.root",
            Path.of(System.getProperty("user.home"), ".jwcode").toString())));
    }

    private void saveWorkflowIR(String runId, WorkflowIR ir) throws Exception {
        Path runDir = workflowRunDirectory(runId);
        Files.createDirectories(runDir);
        Files.writeString(runDir.resolve("ir.json"), WORKFLOW_COMPILER.toJson(ir), StandardCharsets.UTF_8);
    }

    private WorkflowIR readWorkflowIR(String runId) throws Exception {
        Path irFile = workflowRunDirectory(runId).resolve("ir.json");
        if (!Files.exists(irFile)) {
            throw new IllegalArgumentException("Workflow IR not found for runId: " + runId);
        }
        return WORKFLOW_COMPILER.fromJson(Files.readString(irFile, StandardCharsets.UTF_8));
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
     * 
     * Plan 模式下 AI 可以使用只读工具（SmartAnalyzeTool、GlobTool、GrepTool、
     * FileReadTool 等）来探索项目结构、读取文件内容，以做出更准确的分析。
     */
    private String getPlanSystemPrompt() {
        return PLAN_SYSTEM_PROMPT_MARKER + "\n" + """
            # Plan 模式 — 只读需求分析专家

            ## 当前工作目录
            """ + this.defaultWorkingDirectory + """

            ## 核心约束
            1. 你当前处于 **Plan 模式**，职责仅限于：分析需求、探索代码、输出自然语言分析报告
            2. **绝对不允许**修改任何文件、创建/删除文件、执行 Shell 命令
            3. **绝对不允许**在分析过程中直接实现功能或编写业务代码
            4. 即使你认为某个修改"很小"，也只能在分析报告中说明，不能实际执行

            ## 你的角色
            你是 Plan 模式下的资深软件工程需求分析师。你的职责是：
            1. 深入理解用户的需求和目标
            2. 使用只读工具探索项目结构和相关文件
            3. 分析需求的可行性和潜在风险
            4. 给出高层次的实施建议和步骤
            5. 输出清晰的自然语言分析报告

            ## 分析原则
            - 基于实际项目结构进行分析，使用工具探索代码
            - 诚实标注不确定的内容："需要进一步确认"
            - 分析要有层次：先宏观再微观

            ## 输出要求
            - 使用自然语言，像资深架构师一样思考和分析
            - **不需要**输出 JSON 或结构化任务列表
            - 给出清晰的分析和建议即可
            - 分析完成后，提醒用户：**切换到 Act 模式即可开始执行**

            ## 分析结构建议
            1. **需求理解**：用自己的话重述需求，确认理解正确
            2. **影响范围**：分析需要修改或新增的模块和文件
            3. **技术方案**：给出高层次的实现思路
            4. **风险与注意事项**：潜在问题和注意事项
            5. **实施建议**：建议的执行步骤（自然语言即可）

            ## 深度分析清单（根据需求类型选择性覆盖）
            在分析过程中，请根据需求类型选择性地覆盖以下维度：

            ### 架构分析
            - 模块依赖关系：核心模块之间的依赖方向是否合理？是否存在循环依赖？
            - 接口与抽象：关键模块是否通过接口解耦？实现类是否符合单一职责？
            - 数据流：数据在模块间的流转路径是否清晰？是否存在跨层数据泄露？
            - 扩展点：哪些部分是硬编码的、需要重构才能扩展？

            ### 安全审查
            - 输入验证：用户输入、文件路径、命令参数是否经过校验？是否存在注入风险？
            - 认证与会话：Token 管理是否安全？会话标识是否符合安全最佳实践？
            - 文件权限：文件操作是否有路径穿越防护？工作区隔离是否生效？
            - 敏感信息：日志/错误消息中是否可能泄露 Token、密钥或内部路径？

            ### 性能考量
            - 资源使用：是否存在潜在的内存泄漏或无限增长的数据结构？
            - I/O 模式：文件读写是否频繁？是否需要批量操作或缓存？
            - 并发模型：线程池配置是否合理？是否存在锁竞争或死锁风险？

            ### 代码质量
            - 错误处理：异常路径是否都得到了处理？是否存在静默吞异常的模式？
            - 日志级别：重要操作是否有日志？调试信息是否使用了合适的日志级别？
            - 向后兼容：改动是否会影响现有 API 或持久化数据格式？

            ## 可用工具（仅只读工具）
            - **SmartAnalyzeTool**: 智能分析项目整体结构（Plan 模式首选）
            - **GlobTool**: 按模式搜索文件路径
            - **GrepTool**: 在文件内容中搜索关键词
            - **FileReadTool**: 读取文件内容
            - **BatchReadTool**: 批量读取多个文件
            - **AskUserQuestion**: 向用户提问以获取更多信息

            ## 使用建议
            - 分析开始时，先用 SmartAnalyzeTool 了解项目整体结构
            - 然后用 GlobTool/GrepTool 定位相关文件
            - 用 FileReadTool 读取关键文件的具体内容
            - 基于实际代码内容做出准确的分析和判断
            """;
    }

    private int clearPlanSystemPrompt(Session session) {
        if (session == null) {
            return 0;
        }
        return session.removeSystemMessagesContaining(PLAN_SYSTEM_PROMPT_MARKER);
    }
    
    /**
     * 检测 AI 响应中是否包含模拟工具调用标记（非标准 Function Calling 格式）
     * 用于检测 AI 是否在文本中生成了伪工具调用（如 <tool_calls> 等标记）
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
     * 从 Session 构建 LLMMessage 列表（支持工具调用消息）
     * 
     * 正确处理以下消息类型：
     * - SYSTEM / USER / ASSISTANT: 普通文本消息
     * - ASSISTANT + tool_calls: 带工具调用的助手消息
     * - TOOL: 工具执行结果消息
     */
    private java.util.List<LLMMessage> buildLLMMessagesFromSession(Session session) {
        java.util.List<LLMMessage> messages = new java.util.ArrayList<>();
        for (com.jwcode.core.model.Message msg : session.getMessages()) {
            String role = msg.getRole().name().toLowerCase();
            
            switch (role) {
                case "system": {
                    String content = msg.getTextContent();
                    if (content != null && !content.isEmpty()) {
                        messages.add(LLMMessage.builder().role(LLMMessage.Role.SYSTEM).content(content).build());
                    }
                    break;
                }
                case "user": {
                    String content = msg.getTextContent();
                    if (content != null && !content.isEmpty()) {
                        messages.add(LLMMessage.builder().role(LLMMessage.Role.USER).content(content).build());
                    }
                    break;
                }
                case "assistant": {
                    String content = msg.getTextContent();
                    if (content == null) content = "";
                    
                    if (msg.hasToolCalls()) {
                        // 带工具调用的 assistant 消息
                        List<LLMMessage.ToolCall> toolCalls = new ArrayList<>();
                        for (com.jwcode.core.model.Message.ToolCallInfo tci : msg.getToolCalls()) {
                            toolCalls.add(LLMMessage.ToolCall.builder()
                                .id(tci.getId())
                                .function(tci.getName(), tci.getArguments())
                                .build());
                        }
                        messages.add(LLMMessage.assistantWithTools(content, toolCalls, msg.getReasoningContent()));
                    } else {
                        // 普通 assistant 消息
                        messages.add(LLMMessage.builder()
                            .role(LLMMessage.Role.ASSISTANT)
                            .content(content)
                            .reasoningContent(msg.getReasoningContent())
                            .build());
                    }
                    break;
                }
                case "tool": {
                    // 工具结果消息 — 提取 toolCallId 和结果内容
                    String toolCallId = extractToolCallIdFromMsg(msg);
                    String resultContent = extractToolResultContentFromMsg(msg);
                    if (toolCallId != null && !toolCallId.isEmpty()) {
                        messages.add(LLMMessage.tool(toolCallId, resultContent));
                    }
                    break;
                }
            }
        }
        return messages;
    }
    
    /**
     * 从 TOOL 消息中提取 toolCallId
     */
    private String extractToolCallIdFromMsg(com.jwcode.core.model.Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return null;
        }
        for (com.jwcode.core.model.Message.ContentBlock block : msg.getContent()) {
            if (block instanceof com.jwcode.core.model.Message.ToolResultContent) {
                return ((com.jwcode.core.model.Message.ToolResultContent) block).getToolUseId();
            }
        }
        return null;
    }
    
    /**
     * 从 TOOL 消息中提取结果内容
     */
    private String extractToolResultContentFromMsg(com.jwcode.core.model.Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return "";
        }
        for (com.jwcode.core.model.Message.ContentBlock block : msg.getContent()) {
            if (block instanceof com.jwcode.core.model.Message.ToolResultContent) {
                Object result = ((com.jwcode.core.model.Message.ToolResultContent) block).getResult();
                if (result != null) {
                    String resultStr = result.toString();
                    // 截断过长结果以节省 token
                    if (resultStr.length() > 1000) {
                        return resultStr.substring(0, 1000) + "\n...[结果已截断]";
                    }
                    return resultStr;
                }
                return "";
            }
        }
        return msg.getTextContent();
    }
    
    /**
     * 执行 Plan 查询 - 调用 LLM 分析需求并生成任务计划
     * 
     * 支持工具调用循环：AI 可以使用只读工具（SmartAnalyzeTool、GlobTool、
     * FileReadTool 等）来探索项目结构，做出更准确的分析。
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
        
        // ========== 超时自动重试机制 ==========
        // 从 YAML 配置读取模型信息（只需读取一次）
        JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
        String defaultProviderName = config != null ? config.getDefaultProviderName() : "null";
        JwcodeConfig.ModelDefinition modelDef = config != null ? config.getDefaultModel() : null;
        String model = modelDef != null ? modelDef.getId() : "unknown";
        
        // === 模型加载诊断日志 (Plan 模式) ===
        logger.info("[ModelDebug][Plan] defaultProviderName=" + defaultProviderName
            + ", modelId=" + model
            + ", configLoaded=" + (config != null));
        if (config != null) {
            logger.info("[ModelDebug][Plan] providers keys: " + config.getProviders().keySet());
            JwcodeConfig.ProviderConfig dp = config.getDefaultProvider();
            if (dp != null) {
                logger.info("[ModelDebug][Plan] defaultProvider baseUrl=" + dp.getBaseUrl()
                    + ", apiType=" + dp.getApiType()
                    + ", modelCount=" + (dp.getModels() != null ? dp.getModels().size() : 0));
            } else {
                logger.warning("[ModelDebug][Plan] defaultProvider is NULL! providerName=" + defaultProviderName);
            }
        }
        
        // 根据模型类型动态调整超时
        int maxRetries = 2;
        int retryCount = 0;
        int baseTimeout = 300;
        // DeepSeek 等推理模型需要更长超时
        if (model != null && (model.toLowerCase().contains("deepseek") || model.toLowerCase().contains("r1"))) {
            baseTimeout = 600;
        }
        boolean systemMessagesInjected = false;
        CompletableFuture<LLMResponse> future = null;
        
        while (retryCount <= maxRetries) {
            try {
                if (retryCount > 0) {
                    int timeoutForThisAttempt = baseTimeout * (retryCount + 1);
                    logger.info("Plan 查询重试第 " + retryCount + " 次，超时=" + timeoutForThisAttempt + "s");
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.info("Plan", "重试中...（第 " + retryCount + " 次，超时=" + timeoutForThisAttempt + "s）")
                    );
                    sendMessage(querySessionId, MessageType.PLAN_THINKING, 
                        escapeJson("重试中...（第 " + retryCount + " 次）"));
                }
                
                // 仅在首次注入 system/user 消息（重试时保留已有 session 消息）
                if (!systemMessagesInjected) {
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.info("Plan", "使用模型: " + model)
                    );
                    
                    // 注入 Plan 模式的系统提示词
                    session.addMessage(com.jwcode.core.model.Message.createSystemMessage(getPlanSystemPrompt()));
                    
                    // 添加用户消息
                    session.addMessage(com.jwcode.core.model.Message.createUserMessage(
                        "请分析以下需求：\n\n" + message
                    ));
                    
                    systemMessagesInjected = true;
                }
                
                // 创建 LLMFactory 获取 LLMService
                LLMFactory llmFactory = LLMFactory.fromConfig(config);
                LLMService llmService = llmFactory.getLLMService();
                
                // 创建 ToolExecutor（使用已有的 toolRegistry）
                ToolExecutor toolExecutor = new ToolExecutor(this.toolRegistry,
                    null, null, this.hookChain);
                
                // 发送 plan_thinking 状态
                sendMessage(querySessionId, MessageType.PLAN_THINKING, escapeJson("正在分析需求..."));
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.info("Plan", "正在分析需求...")
                );
                
                // 构建 Plan 模式下允许的工具列表
                List<LLMTool> planTools = buildPlanModeTools(toolExecutor);
                
                // 使用 StringBuilder 收集完整响应
                StringBuilder fullContentBuilder = new StringBuilder();
                
                // 最大工具调用轮次（对齐 claude-code maxTurns=200，Plan 模式给 100 足够）
                int maxToolIterations = (config != null && config.getSettings() != null && config.getSettings().getEngine() != null)
                    ? config.getSettings().getEngine().getMaxIterations()
                    : 50;
                int toolIteration = 0;
                boolean finished = false;
                // 连续失败计数器 — 防止 LLM 幻觉出不存在的工具导致死循环
                int consecutiveUnknownTools = 0;
                int consecutiveToolErrors = 0;

                while (!finished && (maxToolIterations <= 0 || toolIteration < maxToolIterations)) {
                    toolIteration++;

                    // Plan 轮次限制：超过最大轮次强制总结
                    if (PlanModeManager.getInstance().isPlanRoundsExceeded()) {
                        logger.warning("Plan 查询: 已达最大分析轮次(" + PlanModeManager.getInstance().getMaxPlanRounds()
                            + ")，强制总结。当前轮次=" + PlanModeManager.getInstance().getPlanRounds());
                        session.addMessage(com.jwcode.core.model.Message.createSystemMessage(
                            "你已达到最大分析轮次限制（" + PlanModeManager.getInstance().getMaxPlanRounds()
                            + "轮）。请基于已有信息直接给出分析总结和计划，不要继续使用工具探索。"));
                        java.util.List<LLMMessage> finalMessages = buildLLMMessagesFromSession(session);
                        CompletableFuture<LLMResponse> finalFuture = llmService.chatStreamWithTools(
                            finalMessages, java.util.List.of(), fullContentBuilder::append, null, null);
                        try {
                            LLMResponse finalResponse = finalFuture.get(baseTimeout, TimeUnit.SECONDS);
                            if (!finalResponse.hasError()) {
                                fullContentBuilder.append(finalResponse.getContent());
                            }
                        } catch (Exception e) {
                            logger.warning("Plan final response (max rounds) failed: " + e.getMessage());
                        }
                        finished = true;
                        break;
                    }

                    // 熔断：连续 5 轮工具都失败/未知，停止循环给出已有结果
                    if (consecutiveToolErrors >= 5) {
                        logger.warning("Plan 查询: 连续 " + consecutiveToolErrors + " 轮工具执行失败，停止工具调用循环");
                        session.addMessage(com.jwcode.core.model.Message.createSystemMessage(
                            "工具调用连续失败，请基于已有信息直接给出分析结果。"));
                        // 最后一轮不带工具调用，直接获取最终回答
                        java.util.List<LLMMessage> finalMessages = buildLLMMessagesFromSession(session);
                        CompletableFuture<LLMResponse> finalFuture = llmService.chatStreamWithTools(
                            finalMessages, java.util.List.of(), fullContentBuilder::append, null, null);
                        try {
                            LLMResponse finalResponse = finalFuture.get(baseTimeout, TimeUnit.SECONDS);
                            if (!finalResponse.hasError()) {
                                fullContentBuilder.append(finalResponse.getContent());
                            }
                        } catch (Exception e) {
                            logger.warning("Plan final response (tool errors) failed: " + e.getMessage());
                        }
                        finished = true;
                        break;
                    }

                    // 转换会话消息
                    java.util.List<LLMMessage> llmMessages = buildLLMMessagesFromSession(session);

                    // 定义流式内容回调 - 实时推送到前端
                    java.util.function.Consumer<String> contentConsumer = chunk -> {
                        fullContentBuilder.append(chunk);
                        sendMessage(querySessionId, MessageType.CONTENT, chunk);
                    };

                    // 调用流式 API（带工具）
                    future = llmService.chatStreamWithTools(
                        llmMessages, planTools, contentConsumer, null, null);
                    // 动态超时：重试时使用更长的超时
                    int currentTimeout = retryCount > 0 ? baseTimeout * (retryCount + 1) : baseTimeout;
                    LLMResponse response = future.get(currentTimeout, TimeUnit.SECONDS);

                    if (response.hasError()) {
                        String errorMsg = response.getErrorMessage();
                        logger.severe("Plan 查询失败: " + errorMsg);
                        sendMessage(querySessionId, MessageType.PLAN_ERROR, escapeJson("AI 分析失败: " + errorMsg));
                        WebSocketLogBroadcaster.getInstance().broadcast(
                            LogEntry.error("Plan 失败: " + errorMsg)
                        );
                        return;
                    }

                    // 检查是否有工具调用
                    if (response.hasToolCalls()) {
                        // 将 AI 的 tool_calls 消息加入 session
                        List<com.jwcode.core.model.Message.ToolCallInfo> toolCallInfos = new ArrayList<>();
                        for (LLMMessage.ToolCall tc : response.getToolCalls()) {
                            toolCallInfos.add(new com.jwcode.core.model.Message.ToolCallInfo(
                                tc.getId(),
                                tc.getFunction().getName(),
                                tc.getFunction().getArguments()
                            ));
                        }
                        session.addMessage(com.jwcode.core.model.Message.createAssistantMessageWithToolCalls(
                            response.getContent(), toolCallInfos));

                        // 检查是否有已知工具调用
                        boolean anyKnownTool = false;
                        boolean anySuccess = false;

                        // 广播工具调用日志
                        int toolIndex = 0;
                        for (LLMMessage.ToolCall tc : response.getToolCalls()) {
                            String toolName = tc.getFunction().getName();
                            String args = tc.getFunction().getArguments();
                            String toolCallId = tc.getId();
                            boolean isKnown = toolExecutor.getToolRegistry().getTool(toolName) != null;
                            if (isKnown) anyKnownTool = true;
                            logger.info("Plan 工具调用: " + toolName
                                + (isKnown ? "" : " [未知工具!]")
                                + " args=" + (args.length() > 200 ? args.substring(0, 200) + "..." : args));
                            WebSocketLogBroadcaster.getInstance().broadcast(
                                LogEntry.info("Plan", (isKnown ? "调用工具: " : "⚠ 未知工具: ") + toolName)
                            );
                            // 发送工具调用事件到前端（带 id + index 用于后续 TOOL_RESULT 匹配）
                            String toolCallJson = String.format(
                                "{\"name\":\"%s\",\"args\":%s,\"id\":\"%s\",\"index\":%d}",
                                escapeJson(toolName), args, escapeJson(toolCallId), toolIndex
                            );
                            sendMessage(querySessionId, MessageType.TOOL_CALL, toolCallJson);
                            toolIndex++;
                        }

                        // 如果本轮所有工具都不存在，注入提示让 LLM 知道可用工具
                        if (!anyKnownTool) {
                            consecutiveUnknownTools++;
                            List<String> availableNames = toolExecutor.getEnabledTools().stream()
                                .map(t -> t.getName())
                                .toList();
                            String hint = "以下工具调用不存在: " + toolCallInfos.stream()
                                .map(tc -> tc.getName()).toList()
                                + "。当前可用的工具只有: " + availableNames
                                + "。请使用这些实际存在的工具重新尝试。";
                            session.addMessage(com.jwcode.core.model.Message.createSystemMessage(hint));
                            logger.warning("Plan 查询: LLM 尝试调用不存在的工具 (" + consecutiveUnknownTools + " 次累计)");
                        }

                        // 执行每个工具调用
                        for (LLMMessage.ToolCall tc : response.getToolCalls()) {
                            String toolName = tc.getFunction().getName();
                            String args = tc.getFunction().getArguments();
                            String toolCallId = tc.getId();

                            // 执行工具
                            String result = executePlanTool(toolExecutor, toolName, args, toolCallId, session);

                            // 检查结果是否为错误
                            boolean isError = result.startsWith("Error:");
                            if (isError) {
                                consecutiveToolErrors++;
                            } else {
                                anySuccess = true;
                            }

                            // 将工具结果加入 session
                            session.addMessage(com.jwcode.core.model.Message.createToolResultMessage(
                                toolCallId, toolName, args, result));

                            // 发送工具结果到前端（带 id 用于精确匹配）
                            String resultJson = String.format(
                                "{\"toolName\":\"%s\",\"id\":\"%s\",\"result\":\"%s\"}",
                                escapeJson(toolName), escapeJson(toolCallId), escapeJson(result)
                            );
                            sendMessage(querySessionId, MessageType.TOOL_RESULT, resultJson);

                            // 广播工具结果
                            String resultPreview = result.length() > 200 ? result.substring(0, 200) + "..." : result;
                            logger.info("Plan 工具结果: " + toolName + " -> " + resultPreview);
                            WebSocketLogBroadcaster.getInstance().broadcast(
                                LogEntry.info("Plan", "工具 " + toolName + " 执行完成")
                            );
                        }

                        // 任一工具成功就重置连续错误计数
                        if (anySuccess) {
                            consecutiveToolErrors = 0;
                        }

                        // 递增 Plan 轮次计数（用于上限检查）
                        PlanModeManager.getInstance().incrementPlanRound();

                        // 继续循环，让 AI 基于工具结果继续分析
                        WebSocketLogBroadcaster.getInstance().broadcast(
                            LogEntry.info("Plan", "继续分析中...（第 " + toolIteration + " 轮 / 上限 " + PlanModeManager.getInstance().getMaxPlanRounds() + " 轮）")
                        );
                    } else {
                        // 没有工具调用，分析完成
                        finished = true;
                    }
                }
                
                String fullContent = fullContentBuilder.toString();
                
                logger.info("Plan AI 分析完成，内容长度: " + fullContent.length() +
                    "，工具调用轮次: " + toolIteration);

                // 发送 COMPLETE 标记（让前端 chatStore 完成消息记录）
                sendMessage(querySessionId, MessageType.COMPLETE, null);

                // 发送 PLAN_COMPLETE，通知前端分析完成，提示切换到 Act 模式
                sendMessage(querySessionId, MessageType.PLAN_COMPLETE,
                    "{\"status\":\"analysis_complete\",\"message\":\"分析完成，请切换到 Act 模式执行\"}");

                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.success("Plan 分析完成")
                );
                
                return; // 成功完成，退出
                
            } catch (java.util.concurrent.TimeoutException e) {
                retryCount++;
                logger.severe("Plan 查询超时（第 " + retryCount + " 次）: " + e.getMessage());
                // 主动取消 future，释放底层 HTTP 连接
                if (future != null && !future.isDone()) {
                    future.cancel(true);
                }
                
                if (retryCount <= maxRetries) {
                    // 指数退避：每次重试增加超时时间
                    int nextTimeout = baseTimeout * (retryCount + 1);
                    logger.info("Plan 将在 " + nextTimeout + "s 超时下重试（第 " + retryCount + " 次）");
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.warn("Plan", "超时，正在重试...（第 " + retryCount + " 次，超时=" + nextTimeout + "s）")
                    );
                    // 清理可能导致超时的上下文（移除最后的 assistant 消息，避免重复）
                    continue;
                }
                
                // 重试耗尽，返回错误
                sendMessage(querySessionId, MessageType.PLAN_ERROR, 
                    escapeJson("AI 分析超时，已自动重试 " + maxRetries + " 次仍未成功，请稍后重试"));
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.error("Plan 超时（已重试 " + maxRetries + " 次）")
                );
                return;
                
            } catch (Exception e) {
                logger.log(java.util.logging.Level.SEVERE, "Plan 查询执行失败", e);
                sendMessage(querySessionId, MessageType.PLAN_ERROR, escapeJson("Plan 执行失败: " + e.getMessage()));
                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.error("Plan 异常: " + e.getMessage())
                );
                return;
            }
        }
    }
    
    /**
     * 构建 Plan 模式下允许的工具列表（LLMTool 格式）
     * 利用 PlanModeManager 过滤出只读/分析类工具
     */
    private List<LLMTool> buildPlanModeTools(ToolExecutor toolExecutor) {
        List<Tool<?, ?, ?>> allTools = toolExecutor.getEnabledTools();
        
        // 使用 PlanModeManager 过滤出 Plan 模式下允许的工具
        PlanModeManager modeManager = PlanModeManager.getInstance();
        List<Tool<?, ?, ?>> allowedTools;
        if (modeManager != null) {
            allowedTools = modeManager.filterPlanModeTools(allTools);
        } else {
            // 降级：只保留只读工具
            allowedTools = allTools.stream()
                .filter(t -> t.isReadOnly(null))
                .toList();
        }
        
        logger.info("Plan 模式可用工具: " + allowedTools.size() + "/" + allTools.size() +
            " (" + allowedTools.stream().map(Tool::getName).toList() + ")");
        
        // 转换为 LLMTool 格式
        List<LLMTool> result = new ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (Tool<?, ?, ?> tool : allowedTools) {
            LLMTool llmTool = new LLMTool();
            llmTool.setType("function");
            
            LLMTool.Function func = new LLMTool.Function();
            func.setName(tool.getName());
            func.setDescription(tool.getDescription());
            
            com.fasterxml.jackson.databind.JsonNode inputSchema = tool.getInputSchema();
            if (inputSchema != null) {
                Map<String, Object> params = mapper.convertValue(inputSchema, Map.class);
                func.setParameters(params);
            }
            
            llmTool.setFunction(func);
            result.add(llmTool);
        }
        return result;
    }
    
    /**
     * 执行单个 Plan 工具调用，返回结果字符串
     */
    private String executePlanTool(ToolExecutor toolExecutor, String toolName, String args, String toolCallId, Session session) {
        try {
            // Windows 路径转义清洗：修复 LLM 生成的 JSON 中无效转义序列（如 \_、\t 等）
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                args = args.replaceAll("\\\\_", "_")
                           .replaceAll("\\\\t([^\\\"\\\\])", "t$1")  // 非转义意义的 \t
                           .replaceAll("\\\\n([^\\\"\\\\])", "n$1"); // 非转义意义的 \n
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode argsNode = mapper.readTree(args);

            // 使用 ToolExecutionContext 执行工具（带工作区守卫绕过检查）
            String sessionWd = session.getWorkingDirectory();
            java.nio.file.Path wd = (sessionWd != null && !sessionWd.isEmpty())
                ? java.nio.file.Path.of(sessionWd)
                : java.nio.file.Path.of(System.getProperty("user.dir"));
            com.jwcode.core.tool.context.ToolExecutionContext context =
                com.jwcode.core.tool.context.ToolExecutionContext.builder()
                    .session(session)
                    .workingDirectory(wd)
                    .build();
            // Sync workspace guard bypass from session metadata (default: bypass)
            Boolean bypass = session.getMetadata("workspaceGuardBypass");
            context.setBypassWorkspaceGuard(!Boolean.FALSE.equals(bypass));

            java.util.concurrent.CompletableFuture<com.jwcode.core.tool.ToolExecutor.ToolExecutionResult> future =
                toolExecutor.execute(toolName, argsNode, context);
            com.jwcode.core.tool.ToolExecutor.ToolExecutionResult execResult = future.get();
            
            if (execResult != null && execResult.isSuccess()) {
                com.jwcode.core.tool.ToolResult<?> toolResult = execResult.getResult();
                if (toolResult != null && toolResult.isSuccess()) {
                    Object data = toolResult.getData();
                    if (data != null) {
                        return mapper.valueToTree(data).toString();
                    } else {
                        return "Success (no data)";
                    }
                } else {
                    String error = toolResult != null ? toolResult.getContent() : "Unknown error";
                    return "Error: " + error;
                }
            } else {
                String errorMsg = execResult != null ? execResult.getErrorMessage() : "Execution failed";
                return "Error: " + errorMsg;
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * 检查会话是否仍然活跃
     */
    private boolean isSessionActive(String sessionId) {
        WebSocket conn = activeSessionConnections.get(sessionId);
        return conn != null && conn.isOpen();
    }
    
    /**
     * 处理 plan_mode_change 消息 — 前端 Plan/Act 模式切换同步到后端。
     * 
     * <p>前端点击 Plan/Act 切换按钮时，通过 WebSocket 发送 plan_mode_change 消息。
     * 此方法解析消息中的 newMode，调用 PlanModeManager 进行模式切换，
     * 并广播切换结果到所有已连接的客户端。</p>
     */
    private void handlePlanModeChange(WebSocket conn, ClientMessage msg) {
        try {
            String rawData = msg.data;
            if (rawData == null || rawData.isEmpty()) {
                logger.warning("plan_mode_change data 为空，尝试从 message 字段解析");
                rawData = msg.message;
            }
            if (rawData == null || rawData.isEmpty()) {
                logger.warning("plan_mode_change: 无法获取模式数据");
                sendMessage(conn, MessageType.ERROR, "Invalid plan_mode_change: missing data");
                return;
            }
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode dataNode = mapper.readTree(rawData);
            
            String newModeStr = dataNode.has("newMode") ? dataNode.get("newMode").asText() : "";
            String previousModeStr = dataNode.has("previousMode") ? dataNode.get("previousMode").asText() : "unknown";
            
            if (newModeStr.isEmpty()) {
                logger.warning("plan_mode_change: newMode 为空");
                sendMessage(conn, MessageType.ERROR, "Invalid plan_mode_change: missing newMode");
                return;
            }
            
            PlanModeManager modeManager = PlanModeManager.getInstance();
            boolean success;
            
            switch (newModeStr) {
                case "plan":
                    success = modeManager.enterPlanMode("User requested Plan mode via WebSocket");
                    break;
                case "act":
                    if (modeManager.isPlanMode()) {
                        success = modeManager.exitPlanMode("User requested Act mode via WebSocket");
                    } else {
                        success = modeManager.enterActMode();
                    }
                    sessions.values().forEach(this::clearPlanSystemPrompt);
                    break;
                default:
                    logger.warning("plan_mode_change: 未知模式 " + newModeStr);
                    sendMessage(conn, MessageType.ERROR, "Unknown mode: " + newModeStr);
                    return;
            }
            
            logger.info("PlanMode 切换: " + previousModeStr + " → " + newModeStr + ", success=" + success);
            
            // 广播模式切换事件到所有客户端（保持与 PlanModeManager 监听器一致的广播方式）
            String resolvedSessionId = getOrGenerateSessionId(conn, msg);
            String modeEventJson = String.format(
                "{\"sessionId\":\"%s\",\"previousMode\":\"%s\",\"newMode\":\"%s\",\"success\":%b,\"timestamp\":%d}",
                escapeJson(resolvedSessionId),
                escapeJson(previousModeStr),
                escapeJson(newModeStr),
                success,
                System.currentTimeMillis()
            );
            for (WebSocket client : getConnections()) {
                if (client.isOpen()) {
                    sendMessage(client, MessageType.PLAN_MODE_CHANGE, modeEventJson);
                }
            }
            
            // 发送 ack 给请求客户端
            sendMessage(conn, MessageType.PLAN_MODE_CHANGE, 
                "{\"ack\":true,\"sessionId\":\"" + escapeJson(resolvedSessionId)
                + "\",\"previousMode\":\"" + escapeJson(previousModeStr) 
                + "\",\"newMode\":\"" + escapeJson(newModeStr) + "\",\"success\":" + success + "}");
            
        } catch (Exception e) {
            logger.severe("处理 plan_mode_change 失败: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to process plan_mode_change: " + e.getMessage());
        }
    }
    
    /**
     * 取消指定 sessionId 上正在运行的查询任务
     * 在连接断开或新查询提交时调用，避免后台任务继续向已断开的连接发送消息
     */
    private void cancelRunningQuery(String sessionId) {
        if (sessionId == null) return;
        pausedQuerySessions.remove(sessionId);
        java.util.concurrent.Future<?> future = runningQueryFutures.remove(sessionId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            logger.info("已取消 sessionId=" + sessionId + " 的查询任务, cancelled=" + cancelled);
        }
    }
    
    /**
     * 暂停指定 sessionId 上正在运行的查询任务
     * 将 session 标记为暂停状态，执行线程中的内容消费者会检查并等待
     */
    private void pauseRunningQuery(String sessionId) {
        if (sessionId == null) return;
        pausedQuerySessions.add(sessionId);
        logger.info("已暂停 sessionId=" + sessionId + " 的查询任务");
    }
    
    /**
     * 恢复指定 sessionId 上被暂停的查询任务
     */
    private void resumeRunningQuery(String sessionId) {
        if (sessionId == null) return;
        pausedQuerySessions.remove(sessionId);
        logger.info("已恢复 sessionId=" + sessionId + " 的查询任务");
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
     * Handle /init command — analyze project and generate JWCODE.md
     */
    private void handleInit(WebSocket conn, ClientMessage msg) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        Session session = sessions.get(sessionId);
        if (session == null) {
            session = createNewSession(sessionId, msg.model);
        }
        String initPrompt = "[System /init] Analyze the current project structure, tech stack, build system, " +
            "coding conventions, and key files. Generate a JWCODE.md file in the project root containing:\n" +
            "1. Project overview and tech stack\n" +
            "2. Build and run commands\n" +
            "3. Directory structure and key paths\n" +
            "4. Coding conventions and standards\n" +
            "5. Architecture design highlights\n" +
            "Use Read, Glob, Grep tools to fully understand the project before generating the file.";
        msg.message = initPrompt;
        handleChatMessage(conn, msg);
    }

    /**
     * Handle /effort command — set task effort level
     */
    private void handleEffort(WebSocket conn, ClientMessage msg) {
        try {
            String dataStr = msg.data;
            String level = "medium";
            if (dataStr != null && !dataStr.isEmpty()) {
                // Simple key:value or plain string
                String raw = dataStr.trim();
                if (raw.contains(":")) {
                    String[] parts = raw.split(":", 2);
                    if (parts.length > 1) level = parts[1].trim().replaceAll("[\"{}]", "").trim();
                } else {
                    level = raw.replaceAll("[\"{}]", "").trim();
                }
            }
            level = level.toLowerCase().trim();
            if (!level.equals("low") && !level.equals("medium") && !level.equals("high")) {
                sendMessage(conn, MessageType.ERROR, "effort must be low, medium, or high, got: " + level);
                return;
            }
            String sessionId = getOrGenerateSessionId(conn, msg);
            Session session = sessions.get(sessionId);
            if (session != null) {
                session.setMetadata("effort", level);
            }
            sendMessage(conn, MessageType.NOTIFICATION, "Effort level set to: " + level);
            logger.info("Session " + sessionId + " effort set to: " + level);
        } catch (Exception e) {
            logger.warning("handleEffort error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to set effort: " + e.getMessage());
        }
    }

    /**
     * Handle /model command — switch the active LLM model.
     * Message format: {type: "model_change", data: {"model":"gpt-4o"}}
     */
    private void handleModelChange(WebSocket conn, ClientMessage msg) {
        try {
            String dataStr = msg.data;
            String modelId = null;
            if (dataStr != null && !dataStr.isEmpty()) {
                dataStr = dataStr.trim();
                // Parse JSON: {"model":"gpt-4o"} or plain string
                if (dataStr.startsWith("{")) {
                    var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(dataStr);
                    if (node.has("model")) modelId = node.get("model").asText();
                } else {
                    modelId = dataStr.replaceAll("[\"']", "").trim();
                }
            }
            if (modelId == null || modelId.isEmpty()) {
                sendMessage(conn, MessageType.ERROR, "Usage: /model <model-id>");
                return;
            }

            // Reload config and switch model
            var loader = com.jwcode.core.config.YamlConfigLoader.getInstance();
            var config = loader.getConfig();
            LLMFactory factory = LLMFactory.fromConfig(config);
            factory.switchModel(modelId);

            // Store factory for subsequent queries
            String sessionId = getOrGenerateSessionId(conn, msg);
            Session session = sessions.get(sessionId);
            if (session != null) {
                session.setMetadata("modelId", modelId);

                // Emit tombstone for current assistant messages — model switch means context may differ
                java.util.List<String> orphanedIds = session.getMessages().stream()
                    .filter(m -> m.getRole() == com.jwcode.core.model.Message.Role.ASSISTANT)
                    .map(com.jwcode.core.model.Message::getId)
                    .collect(java.util.stream.Collectors.toList());
                if (!orphanedIds.isEmpty()) {
                    emitTombstone(sessionId, orphanedIds, "model_switch");
                    logger.info("Tombstone emitted for " + orphanedIds.size() + " messages (model switch)");
                }
            }

            sendMessage(conn, MessageType.NOTIFICATION, "Model switched to: " + modelId);
            logger.info("Model switched to: " + modelId + " (session=" + sessionId + ")");
        } catch (Exception e) {
            logger.warning("handleModelChange error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to switch model: " + e.getMessage());
        }
    }

    /**
     * Handle workspace guard bypass toggle.
     * Message format: {type: "toggle_workspace_guard", data: "true"|"false"}
     */
    private void handleToggleWorkspaceGuard(WebSocket conn, ClientMessage msg) {
        try {
            String dataStr = msg.data;
            boolean bypass = "true".equalsIgnoreCase(dataStr) || "1".equals(dataStr);
            String sessionId = getOrGenerateSessionId(conn, msg);
            Session session = sessions.get(sessionId);
            if (session != null) {
                session.setMetadata("workspaceGuardBypass", bypass);
            }
            String state = bypass ? "已绕过（允许访问工作目录外的路径）" : "已恢复（仅限工作目录内）";
            sendMessage(conn, MessageType.NOTIFICATION, "工作区守卫: " + state);
            logger.info("Session " + sessionId + " workspace guard bypass: " + bypass);
        } catch (Exception e) {
            logger.warning("handleToggleWorkspaceGuard error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to toggle workspace guard: " + e.getMessage());
        }
    }

    /**
     * Handle YOLO mode toggle.
     * Message format: {type: "toggle_yolo", data: "true"|"false"}
     */
    private void handleToggleYolo(WebSocket conn, ClientMessage msg) {
        try {
            String dataStr = msg.data;
            boolean enabled = "true".equalsIgnoreCase(dataStr) || "1".equals(dataStr);
            String sessionId = getOrGenerateSessionId(conn, msg);
            Session session = sessions.get(sessionId);
            if (session != null) {
                session.setMetadata("yoloEnabled", enabled);
            }
            com.jwcode.core.config.ConfigManager.getInstance().set("yolo.enabled", String.valueOf(enabled));
            String state = enabled ? "已开启（全自动模式，无需确认）" : "已关闭（恢复权限检查）";
            sendMessage(conn, MessageType.NOTIFICATION, "YOLO Mode: " + state);
            logger.info("Session " + sessionId + " YOLO mode: " + enabled);
        } catch (Exception e) {
            logger.warning("handleToggleYolo error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to toggle YOLO: " + e.getMessage());
        }
    }

    /**
     * Handle Auto Swarm mode toggle.
     * Message format: {type: "toggle_auto_swarm", data: "true"|"false"}
     */
    private void handleToggleAutoSwarm(WebSocket conn, ClientMessage msg) {
        try {
            String dataStr = msg.data;
            boolean enabled = "true".equalsIgnoreCase(dataStr) || "1".equals(dataStr);
            com.jwcode.core.config.ConfigManager.getInstance().set("autoSwarm.enabled", String.valueOf(enabled));
            String sessionId = getOrGenerateSessionId(conn, msg);
            Session session = sessions.get(sessionId);
            if (session != null) {
                session.setMetadata("autoSwarmEnabled", enabled);
            }
            String state = enabled ? "已开启（自动 Agent Swarm 模式）" : "已关闭";
            sendMessage(conn, MessageType.NOTIFICATION, "Auto Swarm: " + state);
            logger.info("Auto Swarm mode toggled: " + enabled);
        } catch (Exception e) {
            logger.warning("handleToggleAutoSwarm error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to toggle Auto Swarm: " + e.getMessage());
        }
    }

    /**
     * Handle manual compact request from frontend.
     * Sends compaction_progress messages during stages, then context_compressed on completion.
     */
    private void handleCompact(WebSocket conn, ClientMessage msg) {
        try {
            String sessionId = getOrGenerateSessionId(conn, msg);
            Session session = sessions.get(sessionId);
            if (session == null || session.getMessages().isEmpty()) {
                sendMessage(conn, MessageType.NOTIFICATION, "无需压缩：会话为空");
                return;
            }

            int beforeCount = session.getMessageCount();

            // Stage 1: analyzing
            String strategy = "normal";
            String dataStr = msg.data;
            if (dataStr != null && !dataStr.isEmpty()) {
                strategy = dataStr.trim();
            }
            sendCompactionProgress(conn, "analyzing", 15, "分析上下文... (" + beforeCount + " 条消息)");

            // Stage 2: compacting
            sendCompactionProgress(conn, "compact", 50, "压缩中... (策略: " + strategy + ")");

            // Collect original message IDs before compaction (for tombstone detection)
            java.util.List<com.jwcode.core.model.Message> originalMessages = new java.util.ArrayList<>(session.getMessages());
            java.util.Set<String> beforeIds = originalMessages.stream()
                .map(com.jwcode.core.model.Message::getId)
                .collect(java.util.stream.Collectors.toSet());

            List<com.jwcode.core.model.Message> compacted;

            // "pipeline" strategy uses the 5-stage CompactionPipeline
            if ("pipeline".equals(strategy)) {
                com.jwcode.core.context.CompactionPipeline pipeline = new com.jwcode.core.context.CompactionPipeline(
                    null, // no LLM strategy for manual compact
                    (stage, percent, progressMsg) -> sendCompactionProgress(conn, stage.getId(), percent, progressMsg)
                );
                var result = pipeline.execute(session, true);
                compacted = session.getMessages();
                sendCompactionProgress(conn, "pipeline", 100,
                    "管线压缩完成: " + result.getBeforeCount() + " → " + result.getAfterCount() + " 条");
            } else {
                // Traditional strategy using ContextWindowManager
                ContextWindowManager windowManager;
                switch (strategy) {
                    case "aggressive" -> windowManager = new ContextWindowManager(
                        ContextWindowManager.DEFAULT_CONTEXT_LIMIT, 10, 2);
                    case "summary" -> windowManager = new ContextWindowManager(
                        ContextWindowManager.DEFAULT_CONTEXT_LIMIT, 20, 4);
                    default -> windowManager = new ContextWindowManager(
                        ContextWindowManager.DEFAULT_CONTEXT_LIMIT, 30, 4);
                }

                compacted = windowManager.prepareMessages(
                    session.getMessages(), "aggressive".equals(strategy));
                session.setMessages(compacted);
            }
            int afterCount = compacted != null ? compacted.size() : beforeCount;

            if (compacted != null && afterCount < beforeCount) {
                // Compute removed assistant messages for tombstone (using pre-compaction snapshot)
                java.util.Set<String> afterIds = compacted.stream()
                    .map(com.jwcode.core.model.Message::getId)
                    .collect(java.util.stream.Collectors.toSet());
                java.util.List<String> removedAssistantIds = originalMessages.stream()
                    .filter(m -> m.getRole() == com.jwcode.core.model.Message.Role.ASSISTANT)
                    .map(com.jwcode.core.model.Message::getId)
                    .filter(id -> !afterIds.contains(id))
                    .collect(java.util.stream.Collectors.toList());

                session.markCompacted();
                int removed = beforeCount - afterCount;
                long tokensSaved = removed * 120L; // rough estimate

                // Emit tombstone for removed assistant messages
                if (!removedAssistantIds.isEmpty()) {
                    emitTombstone(sessionId, removedAssistantIds, "compaction");
                    logger.info("Tombstone emitted for " + removedAssistantIds.size() + " assistant messages (compaction)");
                }

                // Stage 3: finalizing
                sendCompactionProgress(conn, "finalize", 90, "完成中...");

                // Final result
                String summary = "手动压缩完成 (" + strategy + "): " + beforeCount + " → " + afterCount + " 条消息";
                String json = String.format(
                    "{\"originalCount\":%d,\"compressedCount\":%d,\"tokensSaved\":%d,\"summary\":\"%s\"}",
                    beforeCount, afterCount, tokensSaved, escapeJson(summary));
                sendMessage(conn, MessageType.CONTEXT_COMPRESSED, json);

                sendCompactionProgress(conn, "done", 100, "压缩完成: " + beforeCount + " → " + afterCount + " 条消息");

                WebSocketLogBroadcaster.getInstance().broadcast(
                    LogEntry.info("Compact", String.format(
                        "上下文压缩完成 (%s): %d → %d 条消息", strategy, beforeCount, afterCount)));
            } else {
                sendCompactionProgress(conn, "done", 100, "无需压缩：消息数量已在安全范围内");
                sendMessage(conn, MessageType.NOTIFICATION, "无需压缩：消息数量已在安全范围内");
            }
        } catch (Exception e) {
            logger.warning("handleCompact error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "压缩失败: " + e.getMessage());
        }
    }

    /**
     * Send a compaction progress update to the client.
     */
    private void sendCompactionProgress(WebSocket conn, String stage, int percent, String message) {
        String json = String.format(
            "{\"stage\":\"%s\",\"percent\":%d,\"message\":\"%s\"}",
            escapeJson(stage), percent, escapeJson(message));
        sendMessage(conn, MessageType.COMPACTION_PROGRESS, json);
    }

    /**
     * Emit a tombstone message to the frontend, instructing it to mark specific messages as deleted.
     * Used when model switches or context compaction creates orphaned assistant messages.
     */
    private void emitTombstone(String sessionId, java.util.List<String> messageIds, String reason) {
        if (messageIds == null || messageIds.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"messageIds\":[");
        for (int i = 0; i < messageIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(messageIds.get(i))).append("\"");
        }
        sb.append("],");
        sb.append("\"reason\":\"").append(escapeJson(reason)).append("\"");
        sb.append(",\"ts\":").append(System.currentTimeMillis());
        sb.append("}");
        sendMessage(sessionId, MessageType.TOMBSTONE, sb.toString());
    }

    /**
     * Handle hook approval response (hook_allow / hook_deny) from frontend.
     */
    private void handleHookApprovalResponse(ClientMessage msg, boolean approved) {
        try {
            String dataStr = msg.data;
            if (dataStr == null || dataStr.isEmpty()) {
                logger.warning("[Hook] Empty hook response data");
                return;
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(dataStr);
            String approvalId = node.has("approvalId") ? node.get("approvalId").asText() : null;
            if (approvalId == null || approvalId.isEmpty()) {
                logger.warning("[Hook] Missing approvalId in hook response");
                return;
            }
            if (approved) {
                HookApprovalManager.getInstance().approve(approvalId);
            } else {
                HookApprovalManager.getInstance().deny(approvalId);
            }
            logger.info("[Hook] Approval " + (approved ? "allowed" : "denied") + " for " + approvalId);
        } catch (Exception e) {
            logger.warning("[Hook] Error processing hook approval: " + e.getMessage());
        }
    }

    /**
     * Handle /branch command — create named branch session
     */
    private void handleBranch(WebSocket conn, ClientMessage msg) {
        try {
            String dataStr = msg.data;
            String branchName = "branch";
            if (dataStr != null && !dataStr.isEmpty()) {
                String raw = dataStr.trim();
                if (raw.contains(":")) {
                    String[] parts = raw.split(":", 2);
                    if (parts.length > 1) branchName = parts[1].trim().replaceAll("[\"{}]", "").trim();
                } else {
                    branchName = raw.replaceAll("[\"{}]", "").trim();
                }
            }
            String newSessionId = "session_" + branchName + "_" + System.currentTimeMillis();
            Session newSession = createNewSession(newSessionId, msg.model);
            sessions.put(newSessionId, newSession);
            connectionSessions.put(conn, newSessionId);
            activeSessionConnections.put(newSessionId, conn);
            String oldSessionId = connectionSessions.get(conn);
            if (oldSessionId != null) {
                Session oldSession = sessions.get(oldSessionId);
                if (oldSession != null) {
                    for (com.jwcode.core.model.Message m : oldSession.getMessages()) {
                        newSession.addMessage(m);
                    }
                }
            }
            String resp = String.format("{\"sessionId\":\"%s\",\"branchName\":\"%s\"}",
                escapeJson(newSessionId), escapeJson(branchName));
            sendMessage(conn, MessageType.SESSION_CREATED, resp);
            logger.info("Created branch session: " + newSessionId);
        } catch (Exception e) {
            logger.warning("handleBranch error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to create branch: " + e.getMessage());
        }
    }

    /**
     * Handle /mcp command — MCP server status
     */
    private void handleMcpCommand(WebSocket conn, ClientMessage msg) {
        try {
            com.jwcode.core.mcp.McpConnectionManager mcpManager = new com.jwcode.core.mcp.McpConnectionManager();
            var statuses = mcpManager.getAllConnectionStatuses();
            StringBuilder sb = new StringBuilder();
            sb.append("MCP Server Status:\n");
            for (var entry : statuses.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            if (statuses.isEmpty()) {
                sb.append("  (no MCP servers configured)\n");
            }
            sendMessage(conn, MessageType.NOTIFICATION, sb.toString());
        } catch (Exception e) {
            logger.warning("handleMcpCommand error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "MCP operation failed: " + e.getMessage());
        }
    }

    /**
     * Handle /skills command — list available Skills
     */
    private void handleSkillsCommand(WebSocket conn, ClientMessage msg) {
        try {
            com.jwcode.core.skill.SkillRegistry registry = new com.jwcode.core.skill.SkillRegistry();
            var skills = registry.getAll();
            StringBuilder sb = new StringBuilder();
            sb.append("Available Skills (").append(skills.size()).append("):\n");
            for (var skill : skills) {
                sb.append("  ").append(skill.getId())
                  .append(" - ").append(skill.getDescription()).append("\n");
            }
            sendMessage(conn, MessageType.NOTIFICATION, sb.toString());
        } catch (Exception e) {
            logger.warning("handleSkillsCommand error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to get skills: " + e.getMessage());
        }
    }

    /**
     * Handle /agents command — list available Agents
     */
    private void handleAgentsCommand(WebSocket conn, ClientMessage msg) {
        try {
            // AgentRegistry requires ToolRegistry — use ToolRegistry.getInstance() or new
            com.jwcode.core.agent.AgentRegistry registry = new com.jwcode.core.agent.AgentRegistry(toolRegistry);
            var agents = registry.getAll();
            StringBuilder sb = new StringBuilder();
            sb.append("Available Agents (").append(agents.size()).append("):\n");
            for (var agent : agents) {
                sb.append("  ").append(agent.getName())
                  .append(" - ").append(agent.getDescription()).append("\n");
            }
            sendMessage(conn, MessageType.NOTIFICATION, sb.toString());
        } catch (Exception e) {
            logger.warning("handleAgentsCommand error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Failed to get agents: " + e.getMessage());
        }
    }

    /**
     * Handle /config command — manage configuration via YamlConfigLoader
     */
    private void handleConfigCommand(WebSocket conn, ClientMessage msg) {
        try {
            String dataStr = msg.data;
            if (dataStr == null || dataStr.isEmpty()) {
                dataStr = "list";
            }

            // Parse JSON data if present
            String action = dataStr;
            JsonNode dataNode = null;
            if (dataStr.startsWith("{")) {
                try {
                    dataNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(dataStr);
                    if (dataNode.has("action")) action = dataNode.get("action").asText();
                } catch (Exception ignored) { /* plain text */ }
            }

            switch (action) {
                case "list":
                case "status": {
                    var loader = com.jwcode.core.config.YamlConfigLoader.getInstance();
                    var summary = loader.getProviderSummary();
                    boolean configured = loader.isProviderConfigured();
                    var config = loader.getConfig();
                    StringBuilder sb = new StringBuilder();
                    sb.append("Configuration Status:\n");
                    sb.append("  Configured: ").append(configured ? "yes" : "no (run setup wizard)").append("\n");
                    sb.append("  Default provider: ").append(config.getDefaultProviderName() != null ? config.getDefaultProviderName() : "(none)").append("\n");
                    sb.append("  Providers: ");
                    if (config.getProviders().isEmpty()) {
                        sb.append("(none configured)\n");
                    } else {
                        sb.append("\n");
                        for (var entry : config.getProviders().entrySet()) {
                            sb.append("    - ").append(entry.getKey());
                            sb.append(" (").append(entry.getValue().getModels().size()).append(" models");
                            boolean hasKey = entry.getValue().getApiKeys().stream().anyMatch(k -> k != null && !k.isBlank() && k.length() >= 20 && !k.contains("your-api-key"));
                            sb.append(", API key: ").append(hasKey ? "configured" : "missing").append(")\n");
                        }
                    }
                    sb.append("  Models: ").append(config.getProviders().values().stream().mapToInt(p -> (int) p.getModels().stream().filter(m -> m.isEnabled()).count()).sum()).append(" enabled\n");
                    sb.append("\nUse /config provider to configure, or open web UI Settings.");
                    sendMessage(conn, MessageType.NOTIFICATION, sb.toString());
                    break;
                }
                case "provider":
                case "providers": {
                    var loader = com.jwcode.core.config.YamlConfigLoader.getInstance();
                    var summary = loader.getProviderSummary();
                    String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(summary);
                    sendMessage(conn, MessageType.NOTIFICATION, json);
                    break;
                }
                default:
                    sendMessage(conn, MessageType.NOTIFICATION,
                        "Config: " + dataStr + " — use /config to see status, or /config provider for details.");
                    break;
            }
        } catch (Exception e) {
            logger.warning("handleConfigCommand error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Config operation failed: " + e.getMessage());
        }
    }

    /**
     * Handle /plugin command — plugin management (placeholder)
     */
    private void handlePluginCommand(WebSocket conn, ClientMessage msg) {
        sendMessage(conn, MessageType.NOTIFICATION,
            "Plugin management: use /plugin <install|list|remove>. Plugin system is under development.");
    }

    // ── Phase 1: New command handlers ──

    /**
     * Handle plan_confirm — execute the plan that was generated in Plan mode.
     * Reuses executePlanQuery flow but marks the session as confirmed.
     */
    private void handlePlanConfirm(WebSocket conn, ClientMessage msg) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        Session session = sessions.get(sessionId);
        if (session == null) {
            session = createNewSession(sessionId, msg.model);
        }
        // Mark session as confirmed for execution
        session.setMetadata("planConfirmed", true);
        // Reuse the plan execution flow — LLM generates the final implementation
        connectionSessions.put(conn, sessionId);
        activeSessionConnections.put(sessionId, conn);
        final WebSocket clientConn = conn;
        final Session clientSession = session;
        final String finalSessionId = sessionId;
        cancelRunningQuery(finalSessionId);
        java.util.concurrent.Future<?> future = queryExecutor.submit(() -> {
            executeQuery(clientConn, clientSession,
                "The user has approved the plan. Execute it step by step in Act mode. " +
                "Use the existing plan and task list as the implementation guide." +
                (msg.message != null && !msg.message.isEmpty() ? "\n\nApproved plan:\n" + msg.message : ""));
        });
        runningQueryFutures.put(finalSessionId, future);
    }

    /**
     * Handle plan_refine — refine the plan based on user feedback, then re-plan.
     */
    private void handlePlanRefine(WebSocket conn, ClientMessage msg) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        Session session = sessions.get(sessionId);
        if (session == null) {
            session = createNewSession(sessionId, msg.model);
        }
        connectionSessions.put(conn, sessionId);
        activeSessionConnections.put(sessionId, conn);
        sendMessage(conn, MessageType.PLAN_START, escapeJson("Refining plan based on feedback..."));
        final WebSocket clientConn = conn;
        final Session clientSession = session;
        final String finalSessionId = sessionId;
        cancelRunningQuery(finalSessionId);
        String feedback = msg.message != null && !msg.message.isEmpty() ? msg.message : "Please refine the plan.";
        java.util.concurrent.Future<?> future = queryExecutor.submit(() -> {
            executePlanQuery(clientConn, clientSession,
                "The user has provided feedback on the plan. Please refine it accordingly.\n\n" +
                "User feedback: " + feedback);
        });
        runningQueryFutures.put(finalSessionId, future);
    }

    /**
     * Handle /doctor command — run system diagnostics.
     */
    private void handleDoctorCommand(WebSocket conn, ClientMessage msg) {
        try {
            sendMessage(conn, MessageType.NOTIFICATION, "Running diagnostics...");
            StringBuilder report = new StringBuilder();
            report.append("=== System Diagnostics ===\n");
            report.append("Java version: ").append(System.getProperty("java.version")).append("\n");
            report.append("OS: ").append(System.getProperty("os.name"))
                   .append(" ").append(System.getProperty("os.version")).append("\n");
            report.append("Working directory: ").append(defaultWorkingDirectory).append("\n");
            report.append("Active sessions: ").append(sessions.size()).append("\n");
            report.append("Connected clients: ").append(getConnections().size()).append("\n");
            report.append("Thread pool: active=").append(queryExecutor.getActiveCount())
                   .append("/").append(queryExecutor.getMaximumPoolSize())
                   .append(", queue=").append(queryExecutor.getQueue().size()).append("\n");
            // Try to load config
            try {
                var config = com.jwcode.core.config.YamlConfigLoader.getInstance().getConfig();
                report.append("Config: loaded (default provider=")
                       .append(config.getDefaultProviderName()).append(")\n");
                report.append("Models: ").append(config.getProviders().values().stream()
                    .mapToInt(p -> (int) p.getModels().stream().filter(m -> m.isEnabled()).count()).sum())
                    .append(" enabled\n");
            } catch (Exception e) {
                report.append("Config: load failed — ").append(e.getMessage()).append("\n");
            }
            // Docker check
            try {
                Process p = new ProcessBuilder("docker", "--version").start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                report.append("Docker: ").append(out).append("\n");
            } catch (Exception e) {
                report.append("Docker: not available (").append(e.getMessage()).append(")\n");
            }
            report.append("\nDiagnostics complete.");
            String text = report.toString();
            sendMessage(conn, MessageType.NOTIFICATION, escapeJson(text));
            // Also send as doctor_result for frontend handlers
            sendMessage(conn, MessageType.DOCTOR_RESULT, escapeJson(text));
            logger.info("Doctor diagnostics complete");
        } catch (Exception e) {
            logger.warning("handleDoctorCommand error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Doctor failed: " + e.getMessage());
        }
    }

    /**
     * Handle /rewind command — rewind session to a previous state.
     */
    private void handleRewindCommand(WebSocket conn, ClientMessage msg) {
        try {
            String sessionId = getOrGenerateSessionId(conn, msg);
            Session session = sessions.get(sessionId);
            if (session == null || session.getMessages().isEmpty()) {
                sendMessage(conn, MessageType.NOTIFICATION, "Nothing to rewind — session is empty.");
                return;
            }
            // Parse steps count from data (default: 1)
            int steps = 1;
            if (msg.data != null && !msg.data.isEmpty()) {
                try { steps = Integer.parseInt(msg.data.trim()); } catch (NumberFormatException ignored) {
                    // Use default steps=1
                }
            }
            if (steps < 1) steps = 1;
            // Remove the last N assistant + user message pairs
            List<com.jwcode.core.model.Message> msgs = new ArrayList<>(session.getMessages());
            int removed = 0;
            for (int i = msgs.size() - 1; i >= 0 && removed < steps * 2; i--) {
                String role = msgs.get(i).getRole().name();
                if ("assistant".equalsIgnoreCase(role) || "user".equalsIgnoreCase(role)) {
                    msgs.remove(i);
                    removed++;
                }
            }
            session.setMessages(msgs);
            session.markCompacted(); // mark as modified
            String result = String.format("{\"messages\":[],\"rewound\":%d,\"remaining\":%d}",
                removed, msgs.size());
            sendMessage(conn, MessageType.REWIND_RESULT, result);
            sendMessage(conn, MessageType.NOTIFICATION,
                escapeJson("Rewound " + removed + " messages. " + msgs.size() + " remaining."));
            logger.info("Session " + sessionId + " rewound " + removed + " messages");
        } catch (Exception e) {
            logger.warning("handleRewindCommand error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Rewind failed: " + e.getMessage());
        }
    }

    /**
     * Handle /update_docs or /project command — generate project documentation.
     */
    private void handleUpdateDocsCommand(WebSocket conn, ClientMessage msg) {
        try {
            String sessionId = getOrGenerateSessionId(conn, msg);
            Session session = sessions.get(sessionId);
            if (session == null) {
                session = createNewSession(sessionId, msg.model);
            }
            sendMessage(conn, MessageType.NOTIFICATION, "Generating project documentation...");
            String docPrompt = "Analyze the current project structure, tech stack, build system, " +
                "coding conventions, and key files. Generate or update project documentation covering:\n" +
                "1. Project overview and architecture\n" +
                "2. Build and run instructions\n" +
                "3. Key directories and their purposes\n" +
                "4. Technology stack\n" +
                "5. Development conventions\n" +
                "Use Read, Glob, Grep tools to understand the project first.";
            connectionSessions.put(conn, sessionId);
            activeSessionConnections.put(sessionId, conn);
            final WebSocket clientConn = conn;
            final Session clientSession = session;
            final String finalSessionId = sessionId;
            cancelRunningQuery(finalSessionId);
            java.util.concurrent.Future<?> future = queryExecutor.submit(() -> {
                executeQuery(clientConn, clientSession, docPrompt);
            });
            runningQueryFutures.put(finalSessionId, future);
            // After completion, send docs_updated event
            sendMessage(conn, MessageType.DOCS_UPDATED,
                escapeJson("Documentation generated for " + defaultWorkingDirectory));
        } catch (Exception e) {
            logger.warning("handleUpdateDocsCommand error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Documentation generation failed: " + e.getMessage());
        }
    }

    /**
     * Handle /tokens command — report token usage for the session.
     */
    private void handleTokensCommand(WebSocket conn, ClientMessage msg) {
        try {
            String sessionId = getOrGenerateSessionId(conn, msg);
            // Collect usage data from session
            Session session = sessions.get(sessionId);
            int msgCount = (session != null) ? session.getMessageCount() : 0;
            StringBuilder report = new StringBuilder();
            report.append("Token Usage:\n");
            report.append("  Messages in session: ").append(msgCount).append("\n");
            report.append("  Model: ").append(session != null ? session.getModel() : "unknown").append("\n");
            report.append("  Active sessions: ").append(sessions.size()).append("\n");
            // Try to get detailed token info from config/engine
            try {
                var config = com.jwcode.core.config.YamlConfigLoader.getInstance().getConfig();
                var modelDef = config.getDefaultModel();
                report.append("  Max tokens: ").append(modelDef != null ? modelDef.getMaxTokens() : "unknown").append("\n");
            } catch (Exception e) {
                logger.fine("Failed to get model config for token report: " + e.getMessage());
            }
            sendMessage(conn, MessageType.NOTIFICATION, escapeJson(report.toString()));
        } catch (Exception e) {
            logger.warning("handleTokensCommand error: " + e.getMessage());
            sendMessage(conn, MessageType.ERROR, "Token query failed: " + e.getMessage());
        }
    }

    /**
    /**
     * Get or generate a session ID for the connection.
     */
    /**
     * Handle the unified command_execute WebSocket protocol.
     *
     * <p>Parses {command, args} from msg.data, emits COMMAND_START, then runs the
     * command on the query executor (without blocking the WS IO thread).
     * Orchestrated commands (init/branch/compact/doctor/rewind/...) delegate to
     * the existing per-command handlers to preserve full behavior; pure commands
     * (export/search/test/lint/...) run via Command.execute. Results are sent as
     * a NOTIFICATION (so existing clients render them) plus a COMMAND_COMPLETE
     * event. AI-prompt commands (init/project) forward to executeQuery.
     */
    private void handleCommandExecute(WebSocket conn, ClientMessage msg) {
        String command = null;
        String args = "";
        try {
            String dataStr = msg.data;
            if (dataStr != null && !dataStr.isEmpty()) {
                String trimmed = dataStr.trim();
                if (trimmed.startsWith("{")) {
                    JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(trimmed);
                    if (node.has("command")) command = node.get("command").asText();
                    if (node.has("args")) args = node.get("args").asText();
                } else {
                    int sp = trimmed.indexOf(' ');
                    if (sp > 0) {
                        command = trimmed.substring(0, sp);
                        args = trimmed.substring(sp + 1).trim();
                    } else {
                        command = trimmed;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("command_execute parse error: " + e.getMessage());
        }
        if (command == null || command.isEmpty()) {
            sendMessage(conn, MessageType.COMMAND_ERROR, escapeJson("{\"error\":\"missing command\"}"), msg.sessionId);
            return;
        }
        String name = command;
        if (name.startsWith("/")) name = name.substring(1);
        name = name.trim();
        final String cmdName = name;
        final String cmdArgs = args;
        String startJson = String.format("{\"command\":\"/%s\",\"args\":\"%s\"}",
            escapeJson(cmdName), escapeJson(cmdArgs));
        sendMessage(conn, MessageType.COMMAND_START, startJson, msg.sessionId);

        queryExecutor.submit(() -> {
            try {
                runCommandExecute(conn, msg, cmdName, cmdArgs);
            } catch (Exception e) {
                logger.warning("command_execute error: " + e.getMessage());
                String errJson = String.format("{\"command\":\"/%s\",\"error\":\"%s\"}",
                    escapeJson(cmdName), escapeJson(e.getMessage() == null ? "error" : e.getMessage()));
                sendMessage(conn, MessageType.COMMAND_ERROR, errJson, msg.sessionId);
            }
        });
    }

    private void runCommandExecute(WebSocket conn, ClientMessage msg, String name, String args) {
        String sessionId = getOrGenerateSessionId(conn, msg);
        Session session = sessions.get(sessionId);

        // Orchestrated commands delegate to existing handlers (preserve behavior).
        switch (name) {
            case "init":
                handleInit(conn, msg);
                sendCommandComplete(conn, msg, name, "init started");
                return;
            case "effort":
                msg.data = args;
                handleEffort(conn, msg);
                sendCommandComplete(conn, msg, name, "effort applied");
                return;
            case "branch":
                msg.data = args;
                handleBranch(conn, msg);
                sendCommandComplete(conn, msg, name, "branch created");
                return;
            case "mcp":
                handleMcpCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "mcp done");
                return;
            case "agents":
                handleAgentsCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "agents listed");
                return;
            case "model":
                msg.data = args;
                handleModelChange(conn, msg);
                sendCommandComplete(conn, msg, name, "model set");
                return;
            case "config":
                msg.data = args;
                handleConfigCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "config done");
                return;
            case "plugin":
                handlePluginCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "plugin done");
                return;
            case "compact":
                msg.data = args;
                handleCompact(conn, msg);
                sendCommandComplete(conn, msg, name, "compact done");
                return;
            case "doctor":
                handleDoctorCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "doctor done");
                return;
            case "rewind":
                msg.data = args;
                handleRewindCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "rewind done");
                return;
            case "project":
            case "update_docs":
                handleUpdateDocsCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "project started");
                return;
            case "tokens":
                handleTokensCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "tokens reported");
                return;
            case "skills":
                handleSkillsCommand(conn, msg);
                sendCommandComplete(conn, msg, name, "skills listed");
                return;
            default:
                break;
        }

        // Pure commands: run via Command.execute().
        com.jwcode.core.command.CommandRegistry registry = commandRegistry != null
            ? commandRegistry : com.jwcode.core.command.CommandRegistry.getInstance();
        if (registry == null) {
            sendMessage(conn, MessageType.COMMAND_ERROR,
                escapeJson("{\"command\":\"/" + name + "\",\"error\":\"command registry unavailable\"}"), msg.sessionId);
            return;
        }
        com.jwcode.core.command.Command cmd = registry.getCommand(name);
        if (cmd == null) {
            sendMessage(conn, MessageType.COMMAND_ERROR,
                escapeJson("{\"command\":\"/" + name + "\",\"error\":\"unknown command: " + name + "\"}"), msg.sessionId);
            return;
        }
        String[] argArr = (args == null || args.trim().isEmpty())
            ? new String[0] : args.trim().split("\\s+");
        com.jwcode.core.command.CommandResult result = cmd.execute(argArr, session);

        if (result.isExit()) {
            sendCommandComplete(conn, msg, name, result.getMessage());
            sendMessage(conn, MessageType.EXIT, "Server shutting down...");
            new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.exit(0);
            }).start();
            return;
        }
        if ("AI_PROMPT".equals(result.getData())) {
            if (session == null) {
                session = createNewSession(sessionId, msg.model);
            }
            final Session sess = session;
            final String prompt = result.getMessage();
            cancelRunningQuery(sessionId);
            java.util.concurrent.Future<?> future = queryExecutor.submit(() -> executeQuery(conn, sess, prompt));
            runningQueryFutures.put(sessionId, future);
            sendCommandComplete(conn, msg, name, "started");
            return;
        }
        if (result.isSuccess()) {
            sendMessage(conn, MessageType.NOTIFICATION, escapeJson(result.getMessage()), msg.sessionId);
            sendCommandComplete(conn, msg, name, result.getMessage());
        } else {
            String errJson = String.format("{\"command\":\"/%s\",\"error\":\"%s\"}",
                escapeJson(name), escapeJson(result.getMessage()));
            sendMessage(conn, MessageType.COMMAND_ERROR, errJson, msg.sessionId);
        }
    }

    private void sendCommandComplete(WebSocket conn, ClientMessage msg, String name, String result) {
        String json = String.format("{\"command\":\"/%s\",\"exitCode\":0,\"result\":\"%s\"}",
            escapeJson(name), escapeJson(result == null ? "" : result));
        sendMessage(conn, MessageType.COMMAND_COMPLETE, json, msg.sessionId);
    }

    private String getOrGenerateSessionId(WebSocket conn, ClientMessage msg) {
        String sessionId = msg.sessionId;
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = connectionSessions.get(conn);
        }
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "session-" + System.currentTimeMillis();
        }
        return sessionId;
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
                logger.warning("No default model configured, check config file");
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
    public void sendMessage(String sessionId, MessageType type, String data) {
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
                long seq = sessionSeqCounters
                    .computeIfAbsent(sessionId, k -> new java.util.concurrent.atomic.AtomicLong(0))
                    .incrementAndGet();
                queue.offer(new PendingMessage(type, data, seq));
                logger.fine("消息已暂存: sessionId=" + sessionId + ", type=" + type
                    + ", seq=" + seq + ", queueSize=" + queue.size());
            } else {
                // 连接已断开但消息仍在发送 — 首次降为 INFO，后续降为 FINE 避免刷屏
                logger.fine("sendMessage 连接不可用: sessionId=" + sessionId 
                    + ", type=" + type + ", conn=" + (conn == null ? "null" : "closed"));
            }
        }
    }

    /**
     * 发送实时 Token 用量更新到前端。
     */
    /** 触发生命周期 Hook (UserPromptSubmit, SessionStart, etc.) */
    private void triggerLifecycleHook(HookEventType eventType, WebSocket conn, ClientMessage msg) {
        if (hookChain == null) return;
        try {
            HookContext ctx = new HookContext.Builder(eventType)
                .sessionId(msg.sessionId)
                .build();
            hookChain.execute(ctx);
        } catch (Exception e) {
            logger.fine("[Hook] Lifecycle hook error: " + e.getMessage());
        }
    }

    private void sendTokenUpdate(String sessionId, LLMQueryEngine engine) {
        if (engine == null || sessionId == null) return;
        try {
            TokenBudget budget = engine.getTokenBudget();
            if (budget == null) return;
            String model = escapeJson(engine.getModelName());
            String json = String.format(
                "{\"promptTokens\":%d,\"completionTokens\":%d,\"totalTokens\":%d,\"usageRatio\":%.3f,\"model\":\"%s\"}",
                budget.getUsedPromptTokens(), budget.getUsedCompletionTokens(),
                budget.getUsedTotal(), budget.usageRatio(), model);
            sendMessage(sessionId, MessageType.TOKEN_UPDATE, json);
        } catch (Exception e) {
            logger.fine("Token update failed: " + e.getMessage());
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
            || type == MessageType.STEP_COMPLETE
            || type == MessageType.TOOL_RESULT
            || type == MessageType.WORKFLOW_STARTED
            || type == MessageType.WORKFLOW_EVENT
            || type == MessageType.WORKFLOW_PROGRESS
            || type == MessageType.WORKFLOW_FINISHED
            || type == MessageType.WORKFLOW_ERROR;
    }
    
    /**
     * 发送消息到客户端（带 sessionId 版本）
     * 所有消息都会在 JSON 中附加 sessionId，方便前端路由
     */
    private void sendMessage(WebSocket conn, MessageType type, String data, String sessionId) {
        sendMessage(conn, type, data, sessionId, -1);
    }

    /**
     * 发送消息到客户端（带 sessionId 和序号）
     */
    private void sendMessage(WebSocket conn, MessageType type, String data, String sessionId, long seq) {
        if (conn == null || !conn.isOpen()) {
            return;
        }

        // 生成消息序号（用于 ACK 确认和丢包检测）
        long msgSeq = (seq >= 0) ? seq
            : (sessionId != null) ? sessionSeqCounters
                .computeIfAbsent(sessionId, k -> new java.util.concurrent.atomic.AtomicLong(0))
                .incrementAndGet()
            : -1;

        String json;
        if (type == MessageType.LOG) {
            json = String.format("{\"type\": \"%s\", \"data\": %s, \"sessionId\": \"%s\", \"seq\": %d}",
                type.name().toLowerCase(),
                data,
                escapeJson(sessionId != null ? sessionId : ""),
                msgSeq);
        } else {
            json = String.format("{\"type\": \"%s\", \"data\": %s, \"sessionId\": \"%s\", \"seq\": %d}",
                type.name().toLowerCase(),
                data != null ? "\"" + escapeJson(data) + "\"" : "null",
                escapeJson(sessionId != null ? sessionId : ""),
                msgSeq);
        }

        try {
            conn.send(json);
        } catch (Exception e) {
            logger.warning("发送消息失败: " + conn.getRemoteSocketAddress() + ", error=" + e.getMessage());
        }

        // 会话消息持久化：存储有意义的对话消息
        if (sessionStore != null && sessionId != null) {
            storeMessageIfRelevant(sessionId, type, data);
        }
    }

    /** 仅持久化对会话回放有意义的消息类型。 */
    private void storeMessageIfRelevant(String sessionId, MessageType type, String data) {
        switch (type) {
            case START:
            case CONTENT:
            case THINKING:
            case TOOL_CALL:
            case TOOL_RESULT:
            case COMPLETE:
            case ERROR:
            case PLAN_START:
            case PLAN_THINKING:
            case PLAN_TASKS:
            case PLAN_TASK_START:
            case PLAN_TASK_UPDATE:
            case PLAN_TASK_RESULT:
            case PLAN_COMPLETE:
            case PLAN_ERROR:
            case STEP_START:
            case STEP_THINKING:
            case STEP_ACTION:
            case STEP_COMPLETE:
            case NOTIFICATION:
            case LOG:
            case WORKFLOW_STARTED:
            case WORKFLOW_EVENT:
            case WORKFLOW_PROGRESS:
            case WORKFLOW_FINISHED:
            case WORKFLOW_ERROR:
                sessionStore.appendMessage(sessionId, type.name().toLowerCase(), data);
                break;
            default:
                break; // PING, PONG, AUTH_* 等不持久化
        }
    }

    /**
     * 发送消息到客户端（不带 sessionId 版本，向后兼容）
     */
    private void sendMessage(WebSocket conn, MessageType type, String data) {
        sendMessage(conn, type, data, null);
    }

    /**
     * 处理客户端的消息确认（ACK）
     */
    private void handleMessageAck(WebSocket conn, ClientMessage clientMsg) {
        try {
            if (clientMsg.data != null && !clientMsg.data.isEmpty()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode ackData = mapper.readTree(clientMsg.data);
                long ackSeq = ackData.has("seq") ? ackData.get("seq").asLong() : -1;
                if (ackSeq >= 0) {
                    // 确认消息已送达前端的消息，可用于未来清理持久化消息
                    logger.fine("收到消息 ACK: sessionId=" + clientMsg.sessionId + ", seq=" + ackSeq);
                }
            }
        } catch (Exception e) {
            logger.fine("解析 message_ack 失败: " + e.getMessage());
        }
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
            String defaultProviderName = config != null ? config.getDefaultProviderName() : "null";
            JwcodeConfig.ModelDefinition modelDef = config != null ? config.getDefaultModel() : null;
            String model = modelDef != null ? modelDef.getId() : "unknown";

            // === 模型加载诊断日志 ===
            logger.info("[ModelDebug] defaultProviderName=" + defaultProviderName
                + ", modelId=" + model
                + ", configLoaded=" + (config != null));
            if (config != null) {
                logger.info("[ModelDebug] providers keys: " + config.getProviders().keySet());
                JwcodeConfig.ProviderConfig dp = config.getDefaultProvider();
                if (dp != null) {
                    logger.info("[ModelDebug] defaultProvider baseUrl=" + dp.getBaseUrl()
                        + ", apiType=" + dp.getApiType()
                        + ", modelCount=" + (dp.getModels() != null ? dp.getModels().size() : 0));
                } else {
                    logger.warning("[ModelDebug] defaultProvider is NULL! providerName=" + defaultProviderName);
                }
            }

            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.info("System", "使用模型: " + model + " (provider: " + defaultProviderName + ")")
            );

            // 如果 session 没有设置 model，则设置
            if (session.getModel() == null) {
                session.setModel(model);
            }

            // 创建 LLMFactory 和 QueryEngine（与 CLI 端一致，使用完整参数）
            LLMFactory llmFactory = LLMFactory.fromConfig(config);
            AgentRegistry agentRegistry = AgentRegistry.createDefault();
            // 创建带 HookChain 的 ToolExecutor — shell/文件写入操作需用户确认
            ToolExecutor toolExecutor = new ToolExecutor(
                this.toolRegistry,
                new com.jwcode.core.permission.PermissionManagerChecker(),
                null,
                this.hookChain);
            LLMQueryEngine engine = llmFactory.createQueryEngine(
                session,
                this.toolRegistry,
                toolExecutor,
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
                    // 广播 Agent 信息流：工具调用 -> 对应 Agent 处理中
                    String agent = mapToolToAgent(stepName);
                    String taskId = stepName + "-" + System.currentTimeMillis();
                    AgentFlowBroadcaster.getInstance().broadcastDispatch(
                        "Orchestrator", agent, taskId, stepName, querySessionId);
                }
                
                @Override
                public void onStepComplete(String stepName, String result, boolean success) {
                    String json = String.format(
                        "{\"step\":\"%s\",\"result\":\"%s\",\"status\":\"%s\"}",
                        escapeJson(stepName), escapeJson(result), success ? "success" : "error"
                    );
                    sendMessage(querySessionId, MessageType.STEP_COMPLETE, json);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.success("[完成] " + stepName + ": " + result)
                    );
                }
                
                @Override
                public void onToolResult(String toolName, String result, String toolCallId) {
                    String json = String.format(
                        "{\"id\":\"%s\",\"toolName\":\"%s\",\"result\":\"%s\"}",
                        escapeJson(toolCallId), escapeJson(toolName), escapeJson(result)
                    );
                    sendMessage(querySessionId, MessageType.TOOL_RESULT, json);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.tool("[工具结果] " + toolName + ": " + truncate(result, 50))
                    );
                    // 广播 Agent 信息流：工具完成 -> 对应 Agent 完成
                    String agent = mapToolToAgent(toolName);
                    AgentFlowBroadcaster.getInstance().broadcastComplete(
                        agent, "Orchestrator", toolCallId, "completed", querySessionId);
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
                
                @Override
                public void onTokenUpdate(long promptTokens, long completionTokens, long totalBudget, double usageRatio) {
                    String json = String.format(
                        "{\"promptTokens\":%d,\"completionTokens\":%d,\"totalTokens\":%d,\"usageRatio\":%.3f,\"model\":\"%s\"}",
                        promptTokens, completionTokens, promptTokens + completionTokens, usageRatio, engine.getModelName());
                    sendMessage(querySessionId, MessageType.TOKEN_UPDATE, json);
                }

                public void onSwarmEvent(String eventType, String eventData) {
                    // 将 Swarm 事件转换为 AgentFlowView 识别的 dispatch/complete 格式
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode data = mapper.readTree(eventData);
                        String type = data.has("type") ? data.get("type").asText() : "";
                        String taskId = data.has("taskId") ? data.get("taskId").asText() : "";
                        String desc = data.has("description") ? data.get("description").asText() : "";
                        // Map Swarm task type to display agent name
                        String agent = mapSwarmTypeToAgent(type);

                        if ("task_start".equals(eventType)) {
                            String dispatchJson = String.format(
                                "{\"eventType\":\"dispatch\",\"fromAgent\":\"Orchestrator\",\"toAgent\":\"%s\",\"taskId\":\"%s\",\"description\":\"%s\",\"status\":\"running\",\"sessionId\":\"%s\",\"timestamp\":%d}",
                                escapeJson(agent), escapeJson(taskId), escapeJson(desc),
                                escapeJson(querySessionId), System.currentTimeMillis());
                            sendMessage(querySessionId, MessageType.AGENT_FLOW_EVENT, dispatchJson);
                        } else if ("task_complete".equals(eventType)) {
                            boolean success = data.has("success") && data.get("success").asBoolean();
                            String status = success ? "completed" : "failed";
                            String completeJson = String.format(
                                "{\"eventType\":\"complete\",\"fromAgent\":\"%s\",\"toAgent\":\"Orchestrator\",\"taskId\":\"%s\",\"description\":\"\",\"status\":\"%s\",\"sessionId\":\"%s\",\"timestamp\":%d}",
                                escapeJson(agent), escapeJson(taskId), status,
                                escapeJson(querySessionId), System.currentTimeMillis());
                            sendMessage(querySessionId, MessageType.AGENT_FLOW_EVENT, completeJson);
                        } else {
                            // progress or other events — send as-is
                            sendMessage(querySessionId, MessageType.AGENT_FLOW_EVENT,
                                "{\"eventType\":\"" + escapeJson(eventType) + "\",\"data\":" + eventData + "}");
                        }
                    } catch (Exception e) {
                        logger.fine("onSwarmEvent parse error: " + e.getMessage());
                    }
                }

                @Override
                public void onContextCompressed(int originalCount, int compressedCount,
                                                 long tokensSaved, String summary) {
                    String json = String.format(
                        "{\"originalCount\":%d,\"compressedCount\":%d,\"tokensSaved\":%d,\"summary\":\"%s\"}",
                        originalCount, compressedCount, tokensSaved, escapeJson(summary));
                    sendMessage(querySessionId, MessageType.CONTEXT_COMPRESSED, json);
                    WebSocketLogBroadcaster.getInstance().broadcast(
                        LogEntry.info("Compact", String.format(
                            "上下文压缩: %d → %d 条消息, 释放 %,d tokens", originalCount, compressedCount, tokensSaved)));
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
            int removedPlanPrompts = clearPlanSystemPrompt(session);
            if (removedPlanPrompts > 0) {
                logger.info("Cleared " + removedPlanPrompts
                    + " stale Plan system prompt(s) before Act query, sessionId=" + querySessionId);
            }

            LLMQueryEngine.QueryResult result = engine.queryStream(message, contentConsumer, thinkingConsumer, toolCallConsumer).join();
            
            // 发送实时 Token 用量（修复: Act 模式缺少 Token 更新推送）
            sendTokenUpdate(querySessionId, engine);
            
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
            logger.log(java.util.logging.Level.SEVERE, "查询执行失败", e);

            sendMessage(querySessionId, MessageType.ERROR, escapeJson("执行失败: " + e.getMessage()));
            sendMessage(querySessionId, MessageType.COMPLETE, null);
            WebSocketLogBroadcaster.getInstance().broadcast(
                LogEntry.error("执行异常: " + e.getMessage())
            );
        } finally {
            // 清理序号生成器，避免内存泄漏
            runningQueryFutures.remove(querySessionId);
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
            "{\"step\":\"%s\",\"result\":\"%s\"}",
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
            if (root.has("data")) msg.data = root.get("data").asText();
            if (root.has("model")) msg.model = root.get("model").asText();
            if (root.has("token")) msg.token = root.get("token").asText();
            if (root.has("runId")) msg.runId = root.get("runId").asText();
            if (root.has("workflow")) msg.workflow = root.get("workflow");
            if (root.has("ir")) msg.ir = root.get("ir");
            if (root.has("input")) msg.input = root.get("input");
            if (root.has("data")) msg.dataNode = root.get("data");
            if (root.has("projectId")) msg.projectId = root.get("projectId").asText();
            if (root.has("memoryEnabled")) msg.memoryEnabled = root.get("memoryEnabled").asBoolean();
            if (root.has("checkpointPolicy")) msg.checkpointPolicy = root.get("checkpointPolicy").asText();
            if (root.has("forceResume")) msg.forceResume = root.get("forceResume").asBoolean(false);
        } catch (Exception e) {
            logger.warning("Jackson 解析 JSON 失败，回退到正则解析: " + e.getMessage());
            // 回退到正则解析
            msg.type = extractJsonValue(json, "type");
            msg.sessionId = extractJsonValue(json, "sessionId");
            msg.message = extractJsonValue(json, "message");
            msg.data = extractJsonValue(json, "data");
            msg.model = extractJsonValue(json, "model");
            msg.token = extractJsonValue(json, "token");
            msg.runId = extractJsonValue(json, "runId");
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

    // ==================== Phase 2 辅助方法 ====================

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
     * Map a Swarm task type to a display agent name.
     */
    private static String mapSwarmTypeToAgent(String type) {
        if (type == null) return "explorer";
        return switch (type.toUpperCase()) {
            case "ANALYSIS" -> "explorer";
            case "PLANNING" -> "architect";
            case "EXECUTION" -> "coder";
            case "VERIFICATION" -> "tester";
            default -> "explorer";
        };
    }

    /**
     * Map a tool name to a display agent name for AgentFlowView.
     */
    private static String mapToolToAgent(String toolName) {
        if (toolName == null) return "explorer";
        String name = toolName.toLowerCase();
        if (name.contains("read") || name.contains("glob") || name.contains("grep")) return "explorer";
        if (name.contains("edit") || name.contains("write")) return "coder";
        if (name.contains("bash")) {
            if (name.contains("test") || name.contains("npm test") || name.contains("mvn test")
                || name.contains("pytest") || name.contains("jest")) return "tester";
            return "debug";
        }
        if (name.contains("websearch") || name.contains("webfetch")) return "explorer";
        if (name.contains("taskcreate") || name.contains("taskupdate")) return "architect";
        if (name.contains("agent")) return "architect";
        if (name.contains("review") || name.contains("security")) return "reviewer";
        if (name.contains("test")) return "tester";
        if (name.contains("doc")) return "documenter";
        if (name.contains("eval")) return "evaluator";
        if (name.contains("plan")) return "architect";
        if (name.contains("debug") || name.contains("fix")) return "debug";
        return "explorer";
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
        PLAN_MODE_CHANGE, // Plan/Act 模式切换事件（后端广播到前端）
        PLAN_MODE_ENTER,  // Plan 模式已进入
        PLAN_MODE_EXIT,   // Plan 模式已退出
        STEP_PROMPT,    // 步骤上下文提示（Plan 模式执行阶段）
        WORKSPACE_CHANGED,  // 工作目录已切换
        // 生成控制消息
        GENERATION_PAUSED,  // 生成已暂停
        GENERATION_RESUMED, // 生成已恢复
        TOKEN_UPDATE,       // Token 用量更新（实时）
        CONTEXT_COMPRESSED, // 上下文压缩通知（自动压缩时广播到前端）
        COMPACTION_PROGRESS, // 压缩进度更新（阶段 + 百分比）
        AGENT_FLOW_EVENT,   // Agent 流程事件（Swarm 等）
        MESSAGE_ACK,        // 消息确认（前端收到消息后回执）
        DOCTOR_RESULT,      // 诊断结果
        REWIND_RESULT,      // 回退结果
        DOCS_UPDATED,       // 文档更新完成
        TOMBSTONE,          // Tombstone 消息：通知前端清理孤立 assistant 消息
        EXIT,                // 退出后端服务
        WORKFLOW_STARTED,
        WORKFLOW_EVENT,
        WORKFLOW_PROGRESS,
        WORKFLOW_FINISHED,
        WORKFLOW_ERROR,
        COMMAND_START,      // command_start event
        COMMAND_OUTPUT,     // command_output event (v2 streaming, unused in v1)
        COMMAND_COMPLETE,   // command_complete event
        COMMAND_ERROR       // command_error event
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

    private void initHookApprovalBroadcaster() {
        try {
            com.jwcode.core.hook.HookApprovalManager.getInstance().setWebSocketBroadcaster(request -> {
                String sid = request.sessionId;
                if (sid != null) {
                    WebSocket conn = activeSessionConnections.get(sid);
                    if (conn != null && conn.isOpen()) sendHookApproval(conn, request);
                    return;
                }
                for (var entry : activeSessionConnections.entrySet()) {
                    WebSocket conn = entry.getValue();
                    if (conn != null && conn.isOpen()) sendHookApproval(conn, request);
                }
            });
            logger.info("HookApproval broadcaster wired to WebSocket");
        } catch (Exception e) {
            logger.warning("Failed to wire HookApproval broadcaster: " + e.getMessage());
        }
    }

    private void sendHookApproval(WebSocket conn, com.jwcode.core.hook.HookApprovalManager.ApprovalRequest req) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var dataNode = mapper.createObjectNode();
            dataNode.put("approvalId", req.id);
            dataNode.put("toolName", req.toolName);
            dataNode.put("askPayload", req.askPayload != null ? req.askPayload : "");
            dataNode.put("createdAt", req.createdAt);
            var msgNode = mapper.createObjectNode();
            msgNode.put("type", "hook_ask");
            msgNode.set("data", dataNode);
            conn.send(mapper.writeValueAsString(msgNode));
        } catch (Exception e) {
            logger.warning("Failed to send hook approval: " + e.getMessage());
        }
    }

    private static class ClientMessage {
        String type;
        String sessionId;
        String message;
        String data;
        String model;
        String token;
        String runId;
        JsonNode workflow;
        JsonNode ir;
        JsonNode input;
        JsonNode dataNode;
        String projectId;
        Boolean memoryEnabled;
        String checkpointPolicy;
        boolean forceResume;
    }
}
