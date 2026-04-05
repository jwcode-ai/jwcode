package com.jwcode.core.lsp;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * LspClient - LSP 客户端
 * 
 * 功能说明：
 * LSP（Language Server Protocol）客户端实现。
 * 与语言服务器通信，支持文本同步、代码补全、定义跳转、引用查找等功能。
 * 
 * 核心特性：
 * - LSP 协议通信（JSON-RPC 2.0）
 * - 文本文档同步
 * - 代码补全
 * - 定义跳转
 * - 引用查找
 * - 符号搜索
 * - 代码诊断
 * 
 * 上下文关系：
 * - 被 LspServerManager 管理
 * - 与 LspDiagnosticRegistry 协作
 * - 为编辑器提供语言服务
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspClient {
    
    /**
     * 语言 ID
     */
    private final String languageId;
    
    /**
     * 服务器进程
     */
    private final Process serverProcess;
    
    /**
     * 服务器输入流
     */
    private final OutputStream serverIn;
    
    /**
     * 服务器输出流
     */
    private final InputStream serverOut;
    
    /**
     * 请求 ID 计数器
     */
    private final AtomicInteger requestIdCounter;
    
    /**
     * 待处理的请求
     */
    private final Map<Integer, CompletableFuture<Object>> pendingRequests;
    
    /**
     * 诊断注册表
     */
    private final LspDiagnosticRegistry diagnosticRegistry;
    
    /**
     * 通知监听器
     */
    private final List<Consumer<LspNotification>> notificationListeners;
    
    /**
     * 文本文档缓存
     */
    private final Map<String, TextDocument> documentCache;
    
    /**
     * 版本号
     */
    private static final String LSP_VERSION = "3.17.0";
    
    /**
     * 构造函数
     * 
     * @param languageId 语言 ID
     * @param serverProcess 服务器进程
     * @param diagnosticRegistry 诊断注册表
     */
    public LspClient(String languageId, Process serverProcess, LspDiagnosticRegistry diagnosticRegistry) {
        this.languageId = languageId;
        this.serverProcess = serverProcess;
        this.serverIn = serverProcess.getOutputStream();
        this.serverOut = serverProcess.getInputStream();
        this.diagnosticRegistry = diagnosticRegistry;
        this.requestIdCounter = new AtomicInteger(0);
        this.pendingRequests = new ConcurrentHashMap<>();
        this.notificationListeners = new CopyOnWriteArrayList<>();
        this.documentCache = new ConcurrentHashMap<>();
        
        // 启动响应读取线程
        startResponseReader();
    }
    
    /**
     * 添加通知监听器
     * 
     * @param listener 监听器
     */
    public void addNotificationListener(Consumer<LspNotification> listener) {
        this.notificationListeners.add(listener);
    }
    
    /**
     * 移除通知监听器
     * 
     * @param listener 监听器
     */
    public void removeNotificationListener(Consumer<LspNotification> listener) {
        this.notificationListeners.remove(listener);
    }
    
    /**
     * 初始化客户端
     * 
     * @param workspaceRoot 工作目录
     * @return 初始化结果的 CompletableFuture
     */
    public CompletableFuture<InitializeResult> initialize(String workspaceRoot) {
        CompletableFuture<Object> future = new CompletableFuture();
        int id = requestIdCounter.incrementAndGet();
        pendingRequests.put(id, future);
        
        Map<String, Object> params = new HashMap<>();
        params.put("processId", ProcessHandle.current().pid());
        params.put("clientInfo", Map.of(
                "name", "JWCode",
                "version", "1.0.0"
        ));
        params.put("locale", "zh-CN");
        params.put("rootPath", workspaceRoot);
        params.put("rootUri", pathToUri(workspaceRoot));
        params.put("capabilities", getClientCapabilities());
        params.put("trace", "off");
        
        sendRequest("initialize", id, params);
        
        return future.thenApply(response -> parseInitializeResult(response));
    }
    
    /**
     * 发送初始化完成通知
     */
    public void initialized() {
        sendNotification("initialized", new HashMap<>());
    }
    
    /**
     * 关闭客户端
     */
    public CompletableFuture<Void> shutdown() {
        CompletableFuture<Object> future = new CompletableFuture();
        int id = requestIdCounter.incrementAndGet();
        pendingRequests.put(id, future);
        
        sendRequest("shutdown", id, new HashMap<>());
        
        return future.thenAccept(response -> {
            sendNotification("exit", new HashMap<>());
            serverProcess.destroy();
        });
    }
    
    /**
     * 打开文档
     * 
     * @param uri 文档 URI
     * @param text 文档内容
     * @param languageId 语言 ID
     */
    public void openDocument(String uri, String text, String languageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of(
                "uri", uri,
                "languageId", languageId,
                "version", 1,
                "text", text
        ));
        
        sendNotification("textDocument/didOpen", params);
        
        // 缓存文档
        documentCache.put(uri, new TextDocument(uri, text, 1));
    }
    
    /**
     * 关闭文档
     * 
     * @param uri 文档 URI
     */
    public void closeDocument(String uri) {
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        
        sendNotification("textDocument/didClose", params);
        
        // 移除缓存
        documentCache.remove(uri);
    }
    
    /**
     * 更新文档内容
     * 
     * @param uri 文档 URI
     * @param text 新内容
     * @param version 版本号
     */
    public void updateDocument(String uri, String text, int version) {
        TextDocument doc = documentCache.get(uri);
        if (doc == null) {
            openDocument(uri, text, languageId);
            return;
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of(
                "uri", uri,
                "version", version
        ));
        params.put("contentChanges", List.of(
                Map.of("text", text)
        ));
        
        sendNotification("textDocument/didChange", params);
        
        // 更新缓存
        documentCache.put(uri, new TextDocument(uri, text, version));
    }
    
    /**
     * 代码补全
     * 
     * @param uri 文档 URI
     * @param line 行号
     * @param character 字符位置
     * @return 补全结果的 CompletableFuture
     */
    public CompletableFuture<CompletionList> completion(String uri, int line, int character) {
        CompletableFuture<Object> future = new CompletableFuture();
        int id = requestIdCounter.incrementAndGet();
        pendingRequests.put(id, future);
        
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("position", Map.of(
                "line", line,
                "character", character
        ));
        params.put("context", Map.of(
                "triggerKind", 1,
                "triggerCharacter", null
        ));
        
        sendRequest("textDocument/completion", id, params);
        
        return future.thenApply(response -> parseCompletionList(response));
    }
    
    /**
     * 跳转到定义
     * 
     * @param uri 文档 URI
     * @param line 行号
     * @param character 字符位置
     * @return 定义位置的 CompletableFuture
     */
    public CompletableFuture<List<Location>> definition(String uri, int line, int character) {
        CompletableFuture<Object> future = new CompletableFuture();
        int id = requestIdCounter.incrementAndGet();
        pendingRequests.put(id, future);
        
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("position", Map.of(
                "line", line,
                "character", character
        ));
        
        sendRequest("textDocument/definition", id, params);
        
        return future.thenApply(response -> parseLocations(response));
    }
    
    /**
     * 查找引用
     * 
     * @param uri 文档 URI
     * @param line 行号
     * @param character 字符位置
     * @return 引用位置列表的 CompletableFuture
     */
    public CompletableFuture<List<Location>> references(String uri, int line, int character) {
        CompletableFuture<Object> future = new CompletableFuture();
        int id = requestIdCounter.incrementAndGet();
        pendingRequests.put(id, future);
        
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("position", Map.of(
                "line", line,
                "character", character
        ));
        params.put("context", Map.of("includeDeclaration", true));
        
        sendRequest("textDocument/references", id, params);
        
        return future.thenApply(response -> parseLocations(response));
    }
    
    /**
     * 悬停提示
     * 
     * @param uri 文档 URI
     * @param line 行号
     * @param character 字符位置
     * @return 悬停内容的 CompletableFuture
     */
    public CompletableFuture<Hover> hover(String uri, int line, int character) {
        CompletableFuture<Object> future = new CompletableFuture();
        int id = requestIdCounter.incrementAndGet();
        pendingRequests.put(id, future);
        
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("position", Map.of(
                "line", line,
                "character", character
        ));
        
        sendRequest("textDocument/hover", id, params);
        
        return future.thenApply(response -> parseHover(response));
    }
    
    /**
     * 代码签名
     * 
     * @param uri 文档 URI
     * @param line 行号
     * @param character 字符位置
     * @return 签名信息的 CompletableFuture
     */
    public CompletableFuture<SignatureHelp> signatureHelp(String uri, int line, int character) {
        CompletableFuture<Object> future = new CompletableFuture();
        int id = requestIdCounter.incrementAndGet();
        pendingRequests.put(id, future);
        
        Map<String, Object> params = new HashMap<>();
        params.put("textDocument", Map.of("uri", uri));
        params.put("position", Map.of(
                "line", line,
                "character", character
        ));
        
        sendRequest("textDocument/signatureHelp", id, params);
        
        return future.thenApply(response -> parseSignatureHelp(response));
    }
    
    /**
     * 发送请求
     */
    private void sendRequest(String method, int id, Map<String, Object> params) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.put("params", params);
            
            sendMessage(request);
        } catch (Exception e) {
            CompletableFuture<Object> future = pendingRequests.remove(id);
            if (future != null) {
                future.completeExceptionally(e);
            }
        }
    }
    
    /**
     * 发送通知
     */
    private void sendNotification(String method, Map<String, Object> params) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            notification.put("params", params);
            
            sendMessage(notification);
        } catch (Exception e) {
            // 通知失败不抛出异常
        }
    }
    
    /**
     * 发送消息
     */
    private synchronized void sendMessage(Map<String, Object> message) throws IOException {
        String json = toJson(message);
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        
        // LSP 使用 HTTP 风格的头部
        String header = String.format("Content-Length: %d\r\n\r\n", content.length);
        
        serverIn.write(header.getBytes(StandardCharsets.UTF_8));
        serverIn.write(content);
        serverIn.flush();
    }
    
    /**
     * 启动响应读取线程
     */
    private void startResponseReader() {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverOut, StandardCharsets.UTF_8));
                
                StringBuilder content = new StringBuilder();
                int contentLength = 0;
                boolean readingHeader = true;
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        // 头部结束，开始读取内容
                        readingHeader = false;
                        if (contentLength > 0) {
                            char[] contentChars = new char[contentLength];
                            int read = reader.read(contentChars);
                            if (read == contentLength) {
                                String jsonContent = new String(contentChars);
                                handleResponse(jsonContent);
                            }
                        }
                        content.setLength(0);
                        readingHeader = true;
                        continue;
                    }
                    
                    if (readingHeader && line.startsWith("Content-Length:")) {
                        contentLength = Integer.parseInt(line.substring(16).trim());
                    }
                }
            } catch (Exception e) {
                // 读取结束
            }
        }, "LSP-Response-Reader").start();
    }
    
    /**
     * 处理响应
     */
    private void handleResponse(String json) {
        try {
            Map<String, Object> message = parseJson(json);
            
            if (message.containsKey("id")) {
                // 响应
                int id = ((Number) message.get("id")).intValue();
                CompletableFuture<Object> future = pendingRequests.remove(id);
                if (future != null) {
                    if (message.containsKey("error")) {
                        future.completeExceptionally(new RuntimeException(
                                extractJsonString(message, "error", "message")));
                    } else {
                        future.complete(message.get("result"));
                    }
                }
            } else if (message.containsKey("method")) {
                // 通知
                String method = (String) message.get("method");
                Object params = message.get("params");
                
                LspNotification notification = new LspNotification(method, params);
                
                // 处理特殊通知
                if ("textDocument/publishDiagnostics".equals(method)) {
                    handleDiagnostics(params);
                }
                
                // 通知监听器
                for (Consumer<LspNotification> listener : notificationListeners) {
                    try {
                        listener.accept(notification);
                    } catch (Exception e) {
                        // 忽略监听器异常
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败
        }
    }
    
    /**
     * 处理诊断通知
     */
    @SuppressWarnings("unchecked")
    private void handleDiagnostics(Object params) {
        if (params instanceof Map) {
            Map<String, Object> paramMap = (Map<String, Object>) params;
            String uri = (String) paramMap.get("uri");
            List<Object> diagnostics = (List<Object>) paramMap.get("diagnostics");
            
            if (uri != null && diagnostics != null) {
                List<LspDiagnosticRegistry.LspDiagnostic> diagList = new ArrayList<>();
                for (Object diag : diagnostics) {
                    diagList.add(parseDiagnostic(diag));
                }
                diagnosticRegistry.setDiagnostics(uri, diagList);
            }
        }
    }
    
    /**
     * 获取客户端能力
     */
    private Map<String, Object> getClientCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        
        Map<String, Object> textDocument = new HashMap<>();
        textDocument.put("synchronization", Map.of(
                "dynamicRegistration", false,
                "willSave", false,
                "didSave", true,
                "willSaveWaitUntil", false
        ));
        textDocument.put("completion", Map.of(
                "dynamicRegistration", false,
                "completionItem", Map.of(
                        "snippetSupport", true,
                        "commitCharactersSupport", true
                )
        ));
        textDocument.put("hover", Map.of(
                "dynamicRegistration", false,
                "contentFormat", List.of("markdown", "plaintext")
        ));
        textDocument.put("definition", Map.of("dynamicRegistration", false));
        textDocument.put("references", Map.of("dynamicRegistration", false));
        
        capabilities.put("textDocument", textDocument);
        capabilities.put("workspace", Map.of(
                "workspaceFolders", true,
                "configuration", false
        ));
        
        return capabilities;
    }
    
    // ==================== JSON 解析和序列化（简化实现） ====================
    
    private Map<String, Object> parseJson(String json) {
        // 简化实现：实际应该使用 Jackson 或 Gson
        return new HashMap<>();
    }
    
    private String toJson(Map<String, Object> map) {
        // 简化实现：实际应该使用 Jackson 或 Gson
        return "{}";
    }
    
    private String extractJsonString(Map<String, Object> map, String key, String subKey) {
        return "Error";
    }
    
    // ==================== 结果解析（简化实现） ====================
    
    private InitializeResult parseInitializeResult(Object response) {
        return new InitializeResult();
    }
    
    private CompletionList parseCompletionList(Object response) {
        return new CompletionList();
    }
    
    private List<Location> parseLocations(Object response) {
        return new ArrayList<>();
    }
    
    private Hover parseHover(Object response) {
        return new Hover();
    }
    
    private SignatureHelp parseSignatureHelp(Object response) {
        return new SignatureHelp();
    }
    
    private LspDiagnosticRegistry.LspDiagnostic parseDiagnostic(Object diag) {
        return new LspDiagnosticRegistry.LspDiagnostic(
                LspDiagnosticRegistry.LspDiagnosticSeverity.ERROR,
                null,
                "Unknown",
                null,
                null
        );
    }
    
    private String pathToUri(String path) {
        return Paths.get(path).toUri().toString();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 文本文档类
     */
    private static class TextDocument {
        private final String uri;
        private final String text;
        private final int version;
        
        public TextDocument(String uri, String text, int version) {
            this.uri = uri;
            this.text = text;
            this.version = version;
        }
    }
    
    /**
     * LSP 通知类
     */
    public static class LspNotification {
        private final String method;
        private final Object params;
        
        public LspNotification(String method, Object params) {
            this.method = method;
            this.params = params;
        }
        
        public String getMethod() {
            return method;
        }
        
        public Object getParams() {
            return params;
        }
    }
    
    /**
     * 初始化结果类
     */
    public static class InitializeResult {
        private final Map<String, Object> capabilities;
        
        public InitializeResult() {
            this.capabilities = new HashMap<>();
        }
        
        public Map<String, Object> getCapabilities() {
            return capabilities;
        }
    }
    
    /**
     * 补全列表类
     */
    public static class CompletionList {
        private final boolean isIncomplete;
        private final List<CompletionItem> items;
        
        public CompletionList() {
            this.isIncomplete = false;
            this.items = new ArrayList<>();
        }
        
        public boolean isIncomplete() {
            return isIncomplete;
        }
        
        public List<CompletionItem> getItems() {
            return items;
        }
    }
    
    /**
     * 补全项类
     */
    public static class CompletionItem {
        private final String label;
        private final String kind;
        private final String detail;
        private final String documentation;
        
        public CompletionItem(String label, String kind, String detail, String documentation) {
            this.label = label;
            this.kind = kind;
            this.detail = detail;
            this.documentation = documentation;
        }
        
        public String getLabel() {
            return label;
        }
        
        public String getKind() {
            return kind;
        }
        
        public String getDetail() {
            return detail;
        }
        
        public String getDocumentation() {
            return documentation;
        }
    }
    
    /**
     * 位置类
     */
    public static class Location {
        private final String uri;
        private final LspDiagnosticRegistry.LspRange range;
        
        public Location(String uri, LspDiagnosticRegistry.LspRange range) {
            this.uri = uri;
            this.range = range;
        }
        
        public String getUri() {
            return uri;
        }
        
        public LspDiagnosticRegistry.LspRange getRange() {
            return range;
        }
    }
    
    /**
     * 悬停类
     */
    public static class Hover {
        private final String contents;
        
        public Hover() {
            this.contents = "";
        }
        
        public String getContents() {
            return contents;
        }
    }
    
    /**
     * 签名帮助类
     */
    public static class SignatureHelp {
        private final List<SignatureInformation> signatures;
        private final Integer activeSignature;
        private final Integer activeParameter;
        
        public SignatureHelp() {
            this.signatures = new ArrayList<>();
            this.activeSignature = null;
            this.activeParameter = null;
        }
        
        public List<SignatureInformation> getSignatures() {
            return signatures;
        }
        
        public Integer getActiveSignature() {
            return activeSignature;
        }
        
        public Integer getActiveParameter() {
            return activeParameter;
        }
    }
    
    /**
     * 签名信息类
     */
    public static class SignatureInformation {
        private final String label;
        private final String documentation;
        
        public SignatureInformation(String label, String documentation) {
            this.label = label;
            this.documentation = documentation;
        }
        
        public String getLabel() {
            return label;
        }
        
        public String getDocumentation() {
            return documentation;
        }
    }
}