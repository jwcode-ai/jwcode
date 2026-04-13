package com.jwcode.core.lsp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LspMessageHandler - LSP 消息处理器
 * 
 * 功能说明：
 * 处理语言服务器发送的消息，包括通知、响应和请求。
 * 支持消息分发、诊断信息收集和自定义处理器注册。
 * 
 * 核心特性：
 * - 服务器消息处理
 * - 通知分发机制
 * - 诊断信息收集
 * - 进度报告处理
 * - 可扩展的处理器注册
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspMessageHandler {
    
    private static final Logger LOGGER = Logger.getLogger(LspMessageHandler.class.getName());
    
    /**
     * 通知处理器映射
     */
    private final Map<String, List<Consumer<JsonNode>>> notificationHandlers;
    
    /**
     * 请求处理器映射
     */
    private final Map<String, BiConsumer<JsonNode, CompletableFuture<JsonNode>>> requestHandlers;
    
    /**
     * 全局消息监听器
     */
    private final List<MessageListener> messageListeners;
    
    /**
     * 诊断收集器
     */
    private final DiagnosticCollector diagnosticCollector;
    
    /**
     * 进度报告处理器
     */
    private final Map<String, Consumer<ProgressReport>> progressHandlers;
    
    /**
     * 日志消息处理器
     */
    private Consumer<LogMessage> logMessageHandler;
    
    /**
     * 显示消息处理器
     */
    private Consumer<ShowMessage> showMessageHandler;
    
    /**
     * 消息监听器接口
     */
    @FunctionalInterface
    public interface MessageListener {
        void onMessage(MessageType type, String method, JsonNode payload);
    }
    
    /**
     * 消息类型枚举
     */
    public enum MessageType {
        NOTIFICATION,
        REQUEST,
        RESPONSE,
        ERROR
    }
    
    /**
     * 诊断收集器接口
     */
    @FunctionalInterface
    public interface DiagnosticCollector {
        void collectDiagnostics(String uri, List<LspDiagnostic> diagnostics);
    }
    
    /**
     * LSP 诊断（内部表示）
     */
    public static class LspDiagnostic {
        private final LspRange range;
        private final String message;
        private final int severity;
        private final String source;
        private final String code;
        private final List<LspCodeAction> quickFixes;
        
        public LspDiagnostic(LspRange range, String message, int severity, 
                            String source, String code) {
            this.range = range;
            this.message = message;
            this.severity = severity;
            this.source = source;
            this.code = code;
            this.quickFixes = new ArrayList<>();
        }
        
        public LspRange getRange() { return range; }
        public String getMessage() { return message; }
        public int getSeverity() { return severity; }
        public String getSource() { return source; }
        public String getCode() { return code; }
        public List<LspCodeAction> getQuickFixes() { return quickFixes; }
        
        public void addQuickFix(LspCodeAction action) {
            quickFixes.add(action);
        }
    }
    
    /**
     * 进度报告
     */
    public static class ProgressReport {
        private final String token;
        private final String kind;  // begin, report, end
        private final String message;
        private final Integer percentage;
        private final boolean cancellable;
        
        public ProgressReport(String token, String kind, String message, 
                             Integer percentage, boolean cancellable) {
            this.token = token;
            this.kind = kind;
            this.message = message;
            this.percentage = percentage;
            this.cancellable = cancellable;
        }
        
        public String getToken() { return token; }
        public String getKind() { return kind; }
        public String getMessage() { return message; }
        public Integer getPercentage() { return percentage; }
        public boolean isCancellable() { return cancellable; }
    }
    
    /**
     * 日志消息
     */
    public static class LogMessage {
        private final int type;  // 1=Error, 2=Warning, 3=Info, 4=Log
        private final String message;
        
        public LogMessage(int type, String message) {
            this.type = type;
            this.message = message;
        }
        
        public int getType() { return type; }
        public String getMessage() { return message; }
        
        public String getTypeName() {
            return switch (type) {
                case 1 -> "ERROR";
                case 2 -> "WARNING";
                case 3 -> "INFO";
                case 4 -> "LOG";
                default -> "UNKNOWN";
            };
        }
    }
    
    /**
     * 显示消息
     */
    public static class ShowMessage {
        private final int type;
        private final String message;
        
        public ShowMessage(int type, String message) {
            this.type = type;
            this.message = message;
        }
        
        public int getType() { return type; }
        public String getMessage() { return message; }
    }
    
    public LspMessageHandler() {
        this.notificationHandlers = new HashMap<>();
        this.requestHandlers = new HashMap<>();
        this.messageListeners = new CopyOnWriteArrayList<>();
        this.diagnosticCollector = null;
        this.progressHandlers = new HashMap<>();
        this.logMessageHandler = null;
        this.showMessageHandler = null;
        
        // 注册默认处理器
        registerDefaultHandlers();
    }
    
    public LspMessageHandler(DiagnosticCollector diagnosticCollector) {
        this.notificationHandlers = new HashMap<>();
        this.requestHandlers = new HashMap<>();
        this.messageListeners = new CopyOnWriteArrayList<>();
        this.diagnosticCollector = diagnosticCollector;
        this.progressHandlers = new HashMap<>();
        this.logMessageHandler = null;
        this.showMessageHandler = null;
        
        registerDefaultHandlers();
    }
    
    /**
     * 注册通知处理器
     */
    public void registerNotificationHandler(String method, Consumer<JsonNode> handler) {
        notificationHandlers.computeIfAbsent(method, k -> new CopyOnWriteArrayList<>())
                           .add(handler);
    }
    
    /**
     * 移除通知处理器
     */
    public void removeNotificationHandler(String method, Consumer<JsonNode> handler) {
        List<Consumer<JsonNode>> handlers = notificationHandlers.get(method);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }
    
    /**
     * 注册请求处理器
     */
    public void registerRequestHandler(String method, 
        BiConsumer<JsonNode, CompletableFuture<JsonNode>> handler) {
        requestHandlers.put(method, handler);
    }
    
    /**
     * 添加消息监听器
     */
    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }
    
    /**
     * 移除消息监听器
     */
    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }
    
    /**
     * 设置日志消息处理器
     */
    public void setLogMessageHandler(Consumer<LogMessage> handler) {
        this.logMessageHandler = handler;
    }
    
    /**
     * 设置显示消息处理器
     */
    public void setShowMessageHandler(Consumer<ShowMessage> handler) {
        this.showMessageHandler = handler;
    }
    
    /**
     * 注册进度报告处理器
     */
    public void registerProgressHandler(String token, Consumer<ProgressReport> handler) {
        progressHandlers.put(token, handler);
    }
    
    /**
     * 处理通知消息
     */
    public void handleNotification(String method, JsonNode params) {
        // 通知监听器
        notifyListeners(MessageType.NOTIFICATION, method, params);
        
        // 调用特定处理器
        List<Consumer<JsonNode>> handlers = notificationHandlers.get(method);
        if (handlers != null) {
            for (Consumer<JsonNode> handler : handlers) {
                try {
                    handler.accept(params);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Notification handler error for " + method, e);
                }
            }
        }
        
        // 处理特殊通知
        handleSpecialNotifications(method, params);
    }
    
    /**
     * 处理请求消息（服务器向客户端发送的请求）
     */
    public CompletableFuture<JsonNode> handleRequest(String method, JsonNode params) {
        notifyListeners(MessageType.REQUEST, method, params);
        
        BiConsumer<JsonNode, CompletableFuture<JsonNode>> handler = requestHandlers.get(method);
        if (handler != null) {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            try {
                handler.accept(params, future);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }
        
        // 默认返回空结果
        LOGGER.warning("No handler for request: " + method);
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 处理响应消息
     */
    public void handleResponse(Integer id, JsonNode result, JsonNode error) {
        if (error != null) {
            notifyListeners(MessageType.ERROR, "response", error);
        } else {
            notifyListeners(MessageType.RESPONSE, "response", result);
        }
    }
    
    /**
     * 注册默认处理器
     */
    private void registerDefaultHandlers() {
        // 诊断通知处理器
        registerNotificationHandler("textDocument/publishDiagnostics", this::handleDiagnostics);
        
        // 日志消息处理器
        registerNotificationHandler("window/logMessage", this::handleLogMessage);
        
        // 显示消息处理器
        registerNotificationHandler("window/showMessage", this::handleShowMessage);
        
        // 进度报告处理器
        registerNotificationHandler("$/progress", this::handleProgress);
        
        // 工作区配置处理器
        registerRequestHandler("workspace/configuration", this::handleConfigurationRequest);
        
        // 工作区文件夹处理器
        registerRequestHandler("workspace/workspaceFolders", this::handleWorkspaceFoldersRequest);
    }
    
    /**
     * 处理特殊通知
     */
    private void handleSpecialNotifications(String method, JsonNode params) {
        switch (method) {
            case "textDocument/publishDiagnostics":
                // 已在默认处理器中处理
                break;
            case "window/showMessage":
                // 已在默认处理器中处理
                break;
            case "window/logMessage":
                // 已在默认处理器中处理
                break;
            case "$/progress":
                // 已在默认处理器中处理
                break;
            case "workspace/applyEdit":
                handleApplyEdit(params);
                break;
            case "client/registerCapability":
                handleRegisterCapability(params);
                break;
            case "client/unregisterCapability":
                handleUnregisterCapability(params);
                break;
            default:
                LOGGER.fine("Unhandled notification: " + method);
        }
    }
    
    /**
     * 处理诊断信息
     */
    private void handleDiagnostics(JsonNode params) {
        if (params == null || !params.has("uri")) {
            return;
        }
        
        String uri = params.get("uri").asText();
        JsonNode diagnosticsNode = params.get("diagnostics");
        
        List<LspDiagnostic> diagnostics = new ArrayList<>();
        if (diagnosticsNode != null && diagnosticsNode.isArray()) {
            for (JsonNode diag : diagnosticsNode) {
                LspDiagnostic diagnostic = parseDiagnostic(diag);
                if (diagnostic != null) {
                    diagnostics.add(diagnostic);
                }
            }
        }
        
        LOGGER.fine(String.format("Received %d diagnostics for %s", diagnostics.size(), uri));
        
        if (diagnosticCollector != null) {
            diagnosticCollector.collectDiagnostics(uri, diagnostics);
        }
    }
    
    /**
     * 解析诊断信息
     */
    private LspDiagnostic parseDiagnostic(JsonNode diag) {
        if (diag == null) {
            return null;
        }
        
        LspRange range = parseRange(diag.get("range"));
        String message = diag.has("message") ? diag.get("message").asText() : "";
        int severity = diag.has("severity") ? diag.get("severity").asInt() : 3;
        String source = diag.has("source") ? diag.get("source").asText() : null;
        String code = diag.has("code") ? diag.get("code").asText() : null;
        
        return new LspDiagnostic(range, message, severity, source, code);
    }
    
    /**
     * 解析范围
     */
    private LspRange parseRange(JsonNode rangeNode) {
        if (rangeNode == null || !rangeNode.has("start") || !rangeNode.has("end")) {
            return null;
        }
        
        JsonNode start = rangeNode.get("start");
        JsonNode end = rangeNode.get("end");
        
        return new LspRange(
            new LspPosition(
                start.get("line").asInt(),
                start.get("character").asInt()
            ),
            new LspPosition(
                end.get("line").asInt(),
                end.get("character").asInt()
            )
        );
    }
    
    /**
     * 处理日志消息
     */
    private void handleLogMessage(JsonNode params) {
        if (params == null) return;
        
        int type = params.has("type") ? params.get("type").asInt() : 4;
        String message = params.has("message") ? params.get("message").asText() : "";
        
        LogMessage logMessage = new LogMessage(type, message);
        
        if (logMessageHandler != null) {
            logMessageHandler.accept(logMessage);
        } else {
            // 默认记录到日志
            Level level = switch (type) {
                case 1 -> Level.SEVERE;
                case 2 -> Level.WARNING;
                case 3 -> Level.INFO;
                default -> Level.FINE;
            };
            LOGGER.log(level, "[LSP] " + message);
        }
    }
    
    /**
     * 处理显示消息
     */
    private void handleShowMessage(JsonNode params) {
        if (params == null) return;
        
        int type = params.has("type") ? params.get("type").asInt() : 3;
        String message = params.has("message") ? params.get("message").asText() : "";
        
        ShowMessage showMessage = new ShowMessage(type, message);
        
        if (showMessageHandler != null) {
            showMessageHandler.accept(showMessage);
        } else {
            LOGGER.info("[LSP ShowMessage] " + message);
        }
    }
    
    /**
     * 处理进度报告
     */
    private void handleProgress(JsonNode params) {
        if (params == null || !params.has("token")) return;
        
        String token = params.get("token").asText();
        JsonNode value = params.get("value");
        
        if (value == null) return;
        
        String kind = value.has("kind") ? value.get("kind").asText() : "report";
        String message = value.has("message") ? value.get("message").asText() : null;
        Integer percentage = value.has("percentage") ? value.get("percentage").asInt() : null;
        boolean cancellable = value.has("cancellable") && value.get("cancellable").asBoolean();
        
        ProgressReport report = new ProgressReport(token, kind, message, percentage, cancellable);
        
        Consumer<ProgressReport> handler = progressHandlers.get(token);
        if (handler != null) {
            handler.accept(report);
        }
        
        // 如果是结束，移除处理器
        if ("end".equals(kind)) {
            progressHandlers.remove(token);
        }
    }
    
    /**
     * 处理配置请求
     */
    private void handleConfigurationRequest(JsonNode params, CompletableFuture<JsonNode> future) {
        // 返回默认配置
        future.complete(null);
    }
    
    /**
     * 处理工作区文件夹请求
     */
    private void handleWorkspaceFoldersRequest(JsonNode params, CompletableFuture<JsonNode> future) {
        // 返回默认工作区文件夹
        future.complete(null);
    }
    
    /**
     * 处理应用编辑
     */
    private void handleApplyEdit(JsonNode params) {
        LOGGER.info("Server requested apply edit: " + params);
        // 实际应用中应该将编辑应用到工作区
    }
    
    /**
     * 处理注册能力
     */
    private void handleRegisterCapability(JsonNode params) {
        LOGGER.fine("Server registered capability: " + params);
    }
    
    /**
     * 处理注销能力
     */
    private void handleUnregisterCapability(JsonNode params) {
        LOGGER.fine("Server unregistered capability: " + params);
    }
    
    /**
     * 通知所有监听器
     */
    private void notifyListeners(MessageType type, String method, JsonNode payload) {
        for (MessageListener listener : messageListeners) {
            try {
                listener.onMessage(type, method, payload);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Message listener error", e);
            }
        }
    }
    
    /**
     * 清除所有处理器
     */
    public void clearHandlers() {
        notificationHandlers.clear();
        requestHandlers.clear();
        messageListeners.clear();
        progressHandlers.clear();
    }
}
