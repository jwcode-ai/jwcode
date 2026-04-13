package com.jwcode.core.lsp.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.lsp.*;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LspClientImpl - LSP 客户端实现
 * 
 * 功能说明：
 * 实现 LspService 接口，提供完整的 LSP 协议支持。
 * 使用 JSON-RPC 2.0 进行通信，管理输入输出流，处理异步响应。
 * 
 * 核心特性：
 * - JSON-RPC 2.0 通信协议
 * - 异步请求/响应处理
 * - 通知消息分发
 * - 连接状态管理
 * - 文档同步支持
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspClientImpl implements LspService {
    
    private static final Logger LOGGER = Logger.getLogger(LspClientImpl.class.getName());
    private static final String JSON_RPC_VERSION = "2.0";
    private static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final int REQUEST_TIMEOUT_MS = 30000;
    
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestIdCounter;
    private final Map<Integer, PendingRequest> pendingRequests;
    private final List<Consumer<LspMessage>> notificationHandlers;
    private final LspDiagnosticRegistry diagnosticRegistry;
    private final LspServerManager.LspServerInstance serverInstance;
    private final String languageId;
    
    private volatile boolean connected;
    private volatile Process serverProcess;
    private volatile BufferedReader inputReader;
    private volatile OutputStreamWriter outputWriter;
    private volatile CompletableFuture<Void> messageReaderFuture;
    private volatile ExecutorService executorService;
    private volatile String workspaceRoot;
    private volatile InitializeResult serverCapabilities;
    
    /**
     * 待处理请求内部类
     */
    private static class PendingRequest {
        final CompletableFuture<JsonNode> future;
        final long timestamp;
        final String method;
        
        PendingRequest(CompletableFuture<JsonNode> future, String method) {
            this.future = future;
            this.timestamp = System.currentTimeMillis();
            this.method = method;
        }
    }
    
    /**
     * LSP 消息类
     */
    public static class LspMessage {
        private final String method;
        private final JsonNode params;
        private final JsonNode result;
        private final JsonNode error;
        private final Integer id;
        
        public LspMessage(String method, JsonNode params, JsonNode result, JsonNode error, Integer id) {
            this.method = method;
            this.params = params;
            this.result = result;
            this.error = error;
            this.id = id;
        }
        
        public boolean isNotification() { return id == null && method != null; }
        public boolean isResponse() { return id != null; }
        public boolean isError() { return error != null; }
        
        public String getMethod() { return method; }
        public JsonNode getParams() { return params; }
        public JsonNode getResult() { return result; }
        public JsonNode getError() { return error; }
        public Integer getId() { return id; }
    }
    
    /**
     * 初始化结果
     */
    public static class InitializeResult {
        private final JsonNode capabilities;
        private final JsonNode serverInfo;
        
        public InitializeResult(JsonNode capabilities, JsonNode serverInfo) {
            this.capabilities = capabilities;
            this.serverInfo = serverInfo;
        }
        
        public JsonNode getCapabilities() { return capabilities; }
        public JsonNode getServerInfo() { return serverInfo; }
    }
    
    public LspClientImpl(String languageId, LspServerManager.LspServerInstance serverInstance,
                         LspDiagnosticRegistry diagnosticRegistry) {
        this.languageId = languageId;
        this.serverInstance = serverInstance;
        this.diagnosticRegistry = diagnosticRegistry;
        this.objectMapper = new ObjectMapper();
        this.requestIdCounter = new AtomicInteger(0);
        this.pendingRequests = new ConcurrentHashMap<>();
        this.notificationHandlers = new CopyOnWriteArrayList<>();
        this.connected = false;
    }
    
    /**
     * 添加通知处理器
     */
    public void addNotificationHandler(Consumer<LspMessage> handler) {
        notificationHandlers.add(handler);
    }
    
    /**
     * 移除通知处理器
     */
    public void removeNotificationHandler(Consumer<LspMessage> handler) {
        notificationHandlers.remove(handler);
    }
    
    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (connected) {
                    LOGGER.info("LSP client already connected");
                    return;
                }
                
                serverProcess = serverInstance.getProcess();
                inputReader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
                outputWriter = new OutputStreamWriter(
                    serverProcess.getOutputStream(), StandardCharsets.UTF_8);
                executorService = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "LSP-Worker-" + languageId);
                    t.setDaemon(true);
                    return t;
                });
                
                // 启动消息读取线程
                startMessageReader();
                
                connected = true;
                LOGGER.info("LSP client connected for language: " + languageId);
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to connect LSP client", e);
                throw new CompletionException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            try {
                connected = false;
                
                // 发送 shutdown 请求
                if (serverProcess != null && serverProcess.isAlive()) {
                    try {
                        sendRequest("shutdown", null).get(5, TimeUnit.SECONDS);
                        sendNotification("exit", null);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error during LSP shutdown", e);
                    }
                }
                
                // 清理资源
                if (messageReaderFuture != null) {
                    messageReaderFuture.cancel(true);
                }
                
                if (executorService != null) {
                    executorService.shutdownNow();
                }
                
                if (inputReader != null) {
                    try { inputReader.close(); } catch (IOException ignored) {}
                }
                
                if (outputWriter != null) {
                    try { outputWriter.close(); } catch (IOException ignored) {}
                }
                
                // 清理挂起的请求
                pendingRequests.forEach((id, request) -> {
                    request.future.completeExceptionally(
                        new IllegalStateException("Connection closed"));
                });
                pendingRequests.clear();
                
                LOGGER.info("LSP client disconnected for language: " + languageId);
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error disconnecting LSP client", e);
                throw new CompletionException(e);
            }
        });
    }
    
    @Override
    public boolean isConnected() {
        return connected && serverProcess != null && serverProcess.isAlive();
    }
    
    /**
     * 初始化（必须在 connect 后调用）
     */
    public CompletableFuture<InitializeResult> initialize(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
        
        ObjectNode params = objectMapper.createObjectNode();
        params.put("processId", ProcessHandle.current().pid());
        params.set("clientInfo", createClientInfo());
        params.put("locale", "zh-CN");
        params.put("rootPath", workspaceRoot);
        params.put("rootUri", pathToUri(workspaceRoot));
        params.set("capabilities", createClientCapabilities());
        params.put("trace", "off");
        
        return sendRequest("initialize", params)
            .thenApply(response -> {
                JsonNode result = response.get("result");
                if (result == null) {
                    throw new IllegalStateException("Initialize returned null result");
                }
                serverCapabilities = new InitializeResult(
                    result.get("capabilities"),
                    result.get("serverInfo")
                );
                
                // 发送 initialized 通知
                sendNotification("initialized", objectMapper.createObjectNode());
                
                return serverCapabilities;
            });
    }
    
    @Override
    public CompletableFuture<LspHover> hover(String filePath, int line, int column) {
        ObjectNode params = createTextDocumentPositionParams(filePath, line, column);
        
        return sendRequest("textDocument/hover", params)
            .thenApply(response -> {
                JsonNode result = response.get("result");
                if (result == null || result.isNull()) {
                    return new LspHover();
                }
                
                LspHover hover = new LspHover();
                hover.setContents(extractHoverContent(result.get("contents")));
                hover.setRange(parseRange(result.get("range")));
                return hover;
            });
    }
    
    @Override
    public CompletableFuture<List<LspLocation>> definition(String filePath, int line, int column) {
        ObjectNode params = createTextDocumentPositionParams(filePath, line, column);
        
        return sendRequest("textDocument/definition", params)
            .thenApply(response -> parseLocations(response.get("result")));
    }
    
    @Override
    public CompletableFuture<List<LspLocation>> references(String filePath, int line, int column) {
        ObjectNode params = createTextDocumentPositionParams(filePath, line, column);
        ObjectNode context = objectMapper.createObjectNode();
        context.put("includeDeclaration", true);
        params.set("context", context);
        
        return sendRequest("textDocument/references", params)
            .thenApply(response -> parseLocations(response.get("result")));
    }
    
    @Override
    public CompletableFuture<LspWorkspaceEdit> rename(String filePath, int line, int column, String newName) {
        ObjectNode params = createTextDocumentPositionParams(filePath, line, column);
        params.put("newName", newName);
        
        return sendRequest("textDocument/rename", params)
            .thenApply(response -> parseWorkspaceEdit(response.get("result")));
    }
    
    @Override
    public CompletableFuture<List<LspTextEdit>> format(String filePath) {
        ObjectNode params = objectMapper.createObjectNode();
        params.set("textDocument", createTextDocumentIdentifier(filePath));
        ObjectNode options = objectMapper.createObjectNode();
        options.put("tabSize", 4);
        options.put("insertSpaces", true);
        params.set("options", options);
        
        return sendRequest("textDocument/formatting", params)
            .thenApply(response -> parseTextEdits(response.get("result")));
    }
    
    @Override
    public CompletableFuture<List<LspCodeAction>> codeAction(String filePath, int line, int column) {
        ObjectNode params = objectMapper.createObjectNode();
        params.set("textDocument", createTextDocumentIdentifier(filePath));
        
        // 创建范围（整行）
        ObjectNode range = objectMapper.createObjectNode();
        ObjectNode start = objectMapper.createObjectNode();
        start.put("line", line);
        start.put("character", 0);
        ObjectNode end = objectMapper.createObjectNode();
        end.put("line", line);
        end.put("character", 1000);
        range.set("start", start);
        range.set("end", end);
        params.set("range", range);
        
        ObjectNode context = objectMapper.createObjectNode();
        // 添加诊断信息
        String uri = pathToUri(filePath);
        List<LspDiagnosticRegistry.LspDiagnostic> diagnostics = diagnosticRegistry.getDiagnostics(uri);
        ArrayNode diagnosticsNode = objectMapper.createArrayNode();
        for (LspDiagnosticRegistry.LspDiagnostic diag : diagnostics) {
            ObjectNode diagNode = objectMapper.createObjectNode();
            LspDiagnosticRegistry.LspRange diagRange = diag.getRange();
            LspRange lspRange = new LspRange(
                new LspPosition(diagRange.getStart().getLine(), diagRange.getStart().getCharacter()),
                new LspPosition(diagRange.getEnd().getLine(), diagRange.getEnd().getCharacter())
            );
            diagNode.set("range", createRangeNode(lspRange));
            diagNode.put("message", diag.getMessage());
            diagNode.put("severity", diag.getSeverity().getValue());
            diagnosticsNode.add(diagNode);
        }
        context.set("diagnostics", diagnosticsNode);
        params.set("context", context);
        
        return sendRequest("textDocument/codeAction", params)
            .thenApply(response -> parseCodeActions(response.get("result")));
    }
    
    @Override
    public void openDocument(String filePath, String content, String languageId, int version) {
        ObjectNode params = objectMapper.createObjectNode();
        ObjectNode textDocument = objectMapper.createObjectNode();
        textDocument.put("uri", pathToUri(filePath));
        textDocument.put("languageId", languageId);
        textDocument.put("version", version);
        textDocument.put("text", content);
        params.set("textDocument", textDocument);
        
        sendNotification("textDocument/didOpen", params);
    }
    
    @Override
    public void closeDocument(String filePath) {
        ObjectNode params = objectMapper.createObjectNode();
        params.set("textDocument", createTextDocumentIdentifier(filePath));
        
        sendNotification("textDocument/didClose", params);
    }
    
    @Override
    public void updateDocument(String filePath, String content, int version) {
        ObjectNode params = objectMapper.createObjectNode();
        ObjectNode textDocument = objectMapper.createObjectNode();
        textDocument.put("uri", pathToUri(filePath));
        textDocument.put("version", version);
        params.set("textDocument", textDocument);
        
        ArrayNode contentChanges = objectMapper.createArrayNode();
        ObjectNode change = objectMapper.createObjectNode();
        change.put("text", content);
        contentChanges.add(change);
        params.set("contentChanges", contentChanges);
        
        sendNotification("textDocument/didChange", params);
    }
    
    // ==================== 私有方法 ====================
    
    private void startMessageReader() {
        messageReaderFuture = CompletableFuture.runAsync(() -> {
            try {
                StringBuilder header = new StringBuilder();
                int contentLength = -1;
                
                while (connected && !Thread.currentThread().isInterrupted()) {
                    String line = inputReader.readLine();
                    if (line == null) {
                        break;
                    }
                    
                    if (line.isEmpty()) {
                        // 头部结束，读取内容
                        if (contentLength > 0) {
                            char[] content = new char[contentLength];
                            int read = 0;
                            while (read < contentLength) {
                                int r = inputReader.read(content, read, contentLength - read);
                                if (r == -1) break;
                                read += r;
                            }
                            String jsonContent = new String(content, 0, read);
                            handleMessage(jsonContent);
                        }
                        header.setLength(0);
                        contentLength = -1;
                    } else if (line.startsWith("Content-Length:")) {
                        contentLength = Integer.parseInt(line.substring(16).trim());
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    LOGGER.log(Level.SEVERE, "Error reading LSP message", e);
                }
            }
        }, executorService);
    }
    
    private void handleMessage(String json) {
        try {
            JsonNode message = objectMapper.readTree(json);
            Integer id = message.has("id") ? message.get("id").asInt() : null;
            String method = message.has("method") ? message.get("method").asText() : null;
            JsonNode params = message.get("params");
            JsonNode result = message.get("result");
            JsonNode error = message.get("error");
            
            LspMessage lspMessage = new LspMessage(method, params, result, error, id);
            
            if (id != null) {
                // 响应消息
                PendingRequest request = pendingRequests.remove(id);
                if (request != null) {
                    if (error != null) {
                        String errorMsg = error.has("message") ? 
                            error.get("message").asText() : "Unknown error";
                        request.future.completeExceptionally(new RuntimeException(errorMsg));
                    } else {
                        request.future.complete(message);
                    }
                }
            } else if (method != null) {
                // 通知消息
                handleNotification(lspMessage);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to handle LSP message", e);
        }
    }
    
    private void handleNotification(LspMessage message) {
        String method = message.getMethod();
        JsonNode params = message.getParams();
        
        switch (method) {
            case "textDocument/publishDiagnostics":
                handleDiagnostics(params);
                break;
            case "window/showMessage":
            case "window/logMessage":
                // 处理窗口消息
                break;
            default:
                // 分发给其他处理器
                break;
        }
        
        // 通知所有处理器
        for (Consumer<LspMessage> handler : notificationHandlers) {
            try {
                handler.accept(message);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Notification handler error", e);
            }
        }
    }
    
    private void handleDiagnostics(JsonNode params) {
        if (params == null || !params.has("uri")) return;
        
        String uri = params.get("uri").asText();
        JsonNode diagnostics = params.get("diagnostics");
        
        if (diagnostics != null && diagnostics.isArray()) {
            List<LspDiagnosticRegistry.LspDiagnostic> diagList = new ArrayList<>();
            for (JsonNode diag : diagnostics) {
                LspDiagnosticRegistry.LspDiagnostic d = parseDiagnostic(diag);
                if (d != null) {
                    diagList.add(d);
                }
            }
            diagnosticRegistry.setDiagnostics(uri, diagList);
        }
    }
    
    private LspDiagnosticRegistry.LspDiagnostic parseDiagnostic(JsonNode diag) {
        if (diag == null) return null;
        
        LspRange range = parseRange(diag.get("range"));
        String message = diag.has("message") ? diag.get("message").asText() : "";
        int severity = diag.has("severity") ? diag.get("severity").asInt() : 1;
        String source = diag.has("source") ? diag.get("source").asText() : null;
        String code = diag.has("code") ? diag.get("code").asText() : null;
        
        LspDiagnosticRegistry.LspDiagnosticSeverity sev = 
            LspDiagnosticRegistry.LspDiagnosticSeverity.fromValue(severity);
        
        LspDiagnosticRegistry.LspRange registryRange = new LspDiagnosticRegistry.LspRange(
            new LspDiagnosticRegistry.LspPosition(range.getStart().getLine(), range.getStart().getCharacter()),
            new LspDiagnosticRegistry.LspPosition(range.getEnd().getLine(), range.getEnd().getCharacter())
        );
        
        return new LspDiagnosticRegistry.LspDiagnostic(sev, registryRange, message, source, code);
    }
    
    private CompletableFuture<JsonNode> sendRequest(String method, JsonNode params) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        int id = requestIdCounter.incrementAndGet();
        
        pendingRequests.put(id, new PendingRequest(future, method));
        
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", JSON_RPC_VERSION);
            request.put("id", id);
            request.put("method", method);
            if (params != null) {
                request.set("params", params);
            }
            
            sendMessage(request);
            
            // 设置超时
            future.orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    pendingRequests.remove(id);
                    throw new CompletionException(ex);
                });
            
        } catch (Exception e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    private void sendNotification(String method, JsonNode params) {
        try {
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", JSON_RPC_VERSION);
            notification.put("method", method);
            if (params != null) {
                notification.set("params", params);
            }
            
            sendMessage(notification);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send notification: " + method, e);
        }
    }
    
    private synchronized void sendMessage(JsonNode message) throws IOException {
        String json = objectMapper.writeValueAsString(message);
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        
        String header = String.format("Content-Length: %d\r\n\r\n", content.length);
        outputWriter.write(header);
        outputWriter.write(json);
        outputWriter.flush();
    }
    
    private ObjectNode createClientInfo() {
        ObjectNode info = objectMapper.createObjectNode();
        info.put("name", "JWCode");
        info.put("version", "1.0.0");
        return info;
    }
    
    private ObjectNode createClientCapabilities() {
        ObjectNode capabilities = objectMapper.createObjectNode();
        
        ObjectNode textDocument = objectMapper.createObjectNode();
        
        // 同步能力
        ObjectNode sync = objectMapper.createObjectNode();
        sync.put("dynamicRegistration", false);
        sync.put("willSave", true);
        sync.put("willSaveWaitUntil", true);
        sync.put("didSave", true);
        textDocument.set("synchronization", sync);
        
        // 补全能力
        ObjectNode completion = objectMapper.createObjectNode();
        completion.put("dynamicRegistration", false);
        ObjectNode completionItem = objectMapper.createObjectNode();
        completionItem.put("snippetSupport", true);
        completionItem.put("commitCharactersSupport", true);
        completionItem.put("documentationFormat", 
            objectMapper.createArrayNode().add("markdown").add("plaintext"));
        completion.set("completionItem", completionItem);
        textDocument.set("completion", completion);
        
        // 悬停能力
        ObjectNode hover = objectMapper.createObjectNode();
        hover.put("dynamicRegistration", false);
        hover.set("contentFormat", 
            objectMapper.createArrayNode().add("markdown").add("plaintext"));
        textDocument.set("hover", hover);
        
        // 定义能力
        ObjectNode definition = objectMapper.createObjectNode();
        definition.put("dynamicRegistration", false);
        definition.put("linkSupport", true);
        textDocument.set("definition", definition);
        
        // 引用能力
        ObjectNode references = objectMapper.createObjectNode();
        references.put("dynamicRegistration", false);
        textDocument.set("references", references);
        
        // 重命名能力
        ObjectNode rename = objectMapper.createObjectNode();
        rename.put("dynamicRegistration", false);
        rename.put("prepareSupport", true);
        textDocument.set("rename", rename);
        
        // 格式化能力
        ObjectNode formatting = objectMapper.createObjectNode();
        formatting.put("dynamicRegistration", false);
        textDocument.set("formatting", formatting);
        textDocument.set("rangeFormatting", formatting);
        textDocument.set("onTypeFormatting", formatting);
        
        // 代码操作能力
        ObjectNode codeAction = objectMapper.createObjectNode();
        codeAction.put("dynamicRegistration", false);
        ObjectNode codeActionLiteralSupport = objectMapper.createObjectNode();
        ObjectNode codeActionKind = objectMapper.createObjectNode();
        codeActionKind.set("valueSet", objectMapper.createArrayNode()
            .add("quickfix")
            .add("refactor")
            .add("source"));
        codeActionLiteralSupport.set("codeActionKind", codeActionKind);
        codeAction.set("codeActionLiteralSupport", codeActionLiteralSupport);
        textDocument.set("codeAction", codeAction);
        
        capabilities.set("textDocument", textDocument);
        
        // 工作区能力
        ObjectNode workspace = objectMapper.createObjectNode();
        workspace.put("applyEdit", true);
        workspace.put("workspaceEdit", true);
        workspace.put("didChangeConfiguration", true);
        workspace.put("executeCommand", true);
        workspace.put("workspaceFolders", true);
        workspace.put("configuration", true);
        capabilities.set("workspace", workspace);
        
        return capabilities;
    }
    
    private ObjectNode createTextDocumentIdentifier(String filePath) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("uri", pathToUri(filePath));
        return doc;
    }
    
    private ObjectNode createTextDocumentPositionParams(String filePath, int line, int column) {
        ObjectNode params = objectMapper.createObjectNode();
        params.set("textDocument", createTextDocumentIdentifier(filePath));
        
        ObjectNode position = objectMapper.createObjectNode();
        position.put("line", line);
        position.put("character", column);
        params.set("position", position);
        
        return params;
    }
    
    private String pathToUri(String path) {
        return Paths.get(path).toUri().toString();
    }
    
    private String extractHoverContent(JsonNode contents) {
        if (contents == null || contents.isNull()) {
            return "";
        }
        
        if (contents.isTextual()) {
            return contents.asText();
        }
        
        if (contents.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contents) {
                sb.append(extractHoverContent(item)).append("\n");
            }
            return sb.toString().trim();
        }
        
        if (contents.isObject()) {
            if (contents.has("value")) {
                return contents.get("value").asText();
            }
            if (contents.has("language") && contents.has("value")) {
                return "```" + contents.get("language").asText() + "\n" +
                       contents.get("value").asText() + "\n```";
            }
        }
        
        return contents.toString();
    }
    
    private LspRange parseRange(JsonNode rangeNode) {
        if (rangeNode == null || !rangeNode.has("start") || !rangeNode.has("end")) {
            return null;
        }
        
        JsonNode start = rangeNode.get("start");
        JsonNode end = rangeNode.get("end");
        
        return new LspRange(
            new LspPosition(start.get("line").asInt(), start.get("character").asInt()),
            new LspPosition(end.get("line").asInt(), end.get("character").asInt())
        );
    }
    
    private ObjectNode createRangeNode(LspRange range) {
        ObjectNode rangeNode = objectMapper.createObjectNode();
        ObjectNode start = objectMapper.createObjectNode();
        start.put("line", range.getStart().getLine());
        start.put("character", range.getStart().getCharacter());
        ObjectNode end = objectMapper.createObjectNode();
        end.put("line", range.getEnd().getLine());
        end.put("character", range.getEnd().getCharacter());
        rangeNode.set("start", start);
        rangeNode.set("end", end);
        return rangeNode;
    }
    
    private List<LspLocation> parseLocations(JsonNode result) {
        List<LspLocation> locations = new ArrayList<>();
        
        if (result == null || result.isNull()) {
            return locations;
        }
        
        if (result.isArray()) {
            for (JsonNode loc : result) {
                LspLocation location = parseLocation(loc);
                if (location != null) {
                    locations.add(location);
                }
            }
        } else {
            // 可能是单个位置
            LspLocation location = parseLocation(result);
            if (location != null) {
                locations.add(location);
            }
        }
        
        return locations;
    }
    
    private LspLocation parseLocation(JsonNode loc) {
        if (loc == null || !loc.has("uri")) {
            return null;
        }
        
        String uri = loc.get("uri").asText();
        LspRange range = parseRange(loc.get("range"));
        
        return new LspLocation(uri, range);
    }
    
    private LspWorkspaceEdit parseWorkspaceEdit(JsonNode result) {
        if (result == null || result.isNull()) {
            return new LspWorkspaceEdit();
        }
        
        LspWorkspaceEdit edit = new LspWorkspaceEdit();
        
        if (result.has("changes")) {
            Map<String, List<LspTextEdit>> changes = new HashMap<>();
            JsonNode changesNode = result.get("changes");
            changesNode.fields().forEachRemaining(entry -> {
                changes.put(entry.getKey(), parseTextEdits(entry.getValue()));
            });
            edit.setChanges(changes);
        }
        
        if (result.has("documentChanges")) {
            // 处理文档变更（简化实现）
        }
        
        return edit;
    }
    
    private List<LspTextEdit> parseTextEdits(JsonNode editsNode) {
        List<LspTextEdit> edits = new ArrayList<>();
        
        if (editsNode == null || !editsNode.isArray()) {
            return edits;
        }
        
        for (JsonNode edit : editsNode) {
            LspRange range = parseRange(edit.get("range"));
            String newText = edit.has("newText") ? edit.get("newText").asText() : "";
            edits.add(new LspTextEdit(range, newText));
        }
        
        return edits;
    }
    
    private List<LspCodeAction> parseCodeActions(JsonNode result) {
        List<LspCodeAction> actions = new ArrayList<>();
        
        if (result == null || !result.isArray()) {
            return actions;
        }
        
        for (JsonNode action : result) {
            if (action.has("title")) {
                LspCodeAction codeAction = new LspCodeAction();
                codeAction.setTitle(action.get("title").asText());
                if (action.has("kind")) {
                    codeAction.setKind(action.get("kind").asText());
                }
                if (action.has("edit")) {
                    codeAction.setEdit(parseWorkspaceEdit(action.get("edit")));
                }
                if (action.has("command")) {
                    codeAction.setCommand(action.get("command").toString());
                }
                actions.add(codeAction);
            }
        }
        
        return actions;
    }
    
    public InitializeResult getServerCapabilities() {
        return serverCapabilities;
    }
    
    public String getLanguageId() {
        return languageId;
    }
}
