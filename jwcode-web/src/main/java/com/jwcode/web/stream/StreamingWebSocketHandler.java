package com.jwcode.web.stream;

import com.jwcode.common.config.ConfigLoader;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;
import com.jwcode.core.llm.*;
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
 * - 实时显示执行步骤（思考、规划、工具调用等）
 * 
 * 消息协议（JSON）：
 * - 客户端 -> 服务器: {"type": "chat", "sessionId": "xxx", "message": "..."}
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
            // 创建新会话（包含系统提示词）
            session = createNewSession(sessionId, null);
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
     * 创建新会话（包含系统提示词）
     */
    private Session createNewSession(String sessionId, String model) {
        Session session = new Session(sessionId, System.getProperty("user.dir"));
        
        // 加载并添加系统提示词
        try {
            String systemPrompt = com.jwcode.core.config.SystemPromptLoader.getSystemPrompt();
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
            
            // 创建 LLMFactory 和 QueryEngine
            LLMFactory llmFactory = LLMFactory.fromConfig(config);
            LLMQueryEngine engine = llmFactory.createQueryEngine(session);
            
            // 创建步骤回调，将步骤信息通过 WebSocket 发送给前端
            LLMQueryEngine.StepCallback stepCallback = new LLMQueryEngine.StepCallback() {
                @Override
                public void onStepStart(String stepName, String description) {
                    sendStepStart(conn, stepName, description);
                }
                
                @Override
                public void onStepThinking(String stepName, String thought) {
                    sendStepThinking(conn, stepName, thought);
                }
                
                @Override
                public void onStepAction(String stepName, String action) {
                    sendStepAction(conn, stepName, action);
                }
                
                @Override
                public void onStepComplete(String stepName, String result) {
                    sendStepComplete(conn, stepName, result);
                }
                
                @Override
                public void onToolResult(String toolName, String result) {
                    sendToolResult(conn, toolName, result);
                }
                
                @Override
                public void onContentChunk(String chunk) {
                    sendMessage(conn, MessageType.CONTENT, escapeJson(chunk));
                }
                
                @Override
                public void onThinkingChunk(String chunk) {
                    sendMessage(conn, MessageType.THINKING, escapeJson(chunk));
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
                    sendMessage(conn, MessageType.TOOL_CALL, json);
                }
            };
            
            // 设置回调
            engine.setStepCallback(stepCallback);
            
            // 定义流式消费者
            java.util.function.Consumer<String> contentConsumer = chunk -> {
                sendMessage(conn, MessageType.CONTENT, escapeJson(chunk));
            };
            
            java.util.function.Consumer<String> thinkingConsumer = chunk -> {
                sendMessage(conn, MessageType.THINKING, escapeJson(chunk));
            };
            
            java.util.function.Consumer<LLMService.StreamToolCallEvent> toolCallConsumer = event -> {
                String json = String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"args\":\"%s\",\"complete\":%b,\"index\":%d}",
                    escapeJson(event.getId()),
                    escapeJson(event.getName()),
                    escapeJson(event.getArguments()),
                    event.isComplete(),
                    event.getIndex()
                );
                sendMessage(conn, MessageType.TOOL_CALL, json);
            };
            
            // 执行流式查询
            LLMQueryEngine.QueryResult result = engine.queryStream(message, contentConsumer, thinkingConsumer, toolCallConsumer).join();
            
            if (result.isSuccess()) {
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
     * 解析客户端消息
     */
    private ClientMessage parseMessage(String json) {
        ClientMessage msg = new ClientMessage();
        
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
        LOG             // 日志消息
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
