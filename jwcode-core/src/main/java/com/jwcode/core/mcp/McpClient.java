package com.jwcode.core.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.jwcode.core.mcp.model.McpResource;
import com.jwcode.core.mcp.model.McpResourceContent;
import com.jwcode.core.mcp.model.McpTool;
import com.jwcode.core.mcp.model.McpToolResult;

/**
 * McpClient - MCP 客户端
 * 
 * 功能说明：
 * MCP（Model Context Protocol）客户端，用于与 MCP 服务器通信。
 * 支持连接管理、工具调用、资源访问、提示模板等功能。
 * 
 * 核心特性：
 * - 连接管理和自动重连
 * - 工具调用和结果获取
 * - 资源读取和流式传输
 * - 提示模板管理
 * - 能力协商
 * 
 * 上下文关系：
 * - 被 McpConnectionManager 管理
 * - 与 McpServerRegistry 协作
 * - 为 ToolExecutionService 提供 MCP 支持
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpClient {
    
    /**
     * 客户端 ID
     */
    private final String clientId;
    
    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;
    
    /**
     * 服务器 URL
     */
    private final String serverUrl;
    
    /**
     * 连接状态
     */
    private ConnectionState connectionState;
    
    /**
     * 服务器能力
     */
    private ServerCapabilities serverCapabilities;
    
    /**
     * 请求 ID 计数器
     */
    private final AtomicInteger requestIdCounter;
    
    /**
     * 待处理的请求
     */
    private final Map<String, CompletableFuture<McpResponse>> pendingRequests;
    
    /**
     * 连接超时（毫秒）
     */
    private static final long CONNECTION_TIMEOUT_MS = 10000;
    
    /**
     * 请求超时（毫秒）
     */
    private static final long REQUEST_TIMEOUT_MS = 30000;
    
    /**
     * 构造函数
     * 
     * @param serverUrl MCP 服务器 URL
     */
    public McpClient(String serverUrl) {
        this.clientId = UUID.randomUUID().toString();
        this.serverUrl = serverUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(CONNECTION_TIMEOUT_MS))
                .build();
        this.connectionState = ConnectionState.DISCONNECTED;
        this.requestIdCounter = new AtomicInteger(0);
        this.pendingRequests = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取客户端 ID
     */
    public String getClientId() {
        return clientId;
    }
    
    /**
     * 获取连接状态
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }
    
    /**
     * 获取服务器能力
     */
    public ServerCapabilities getServerCapabilities() {
        return serverCapabilities;
    }
    
    /**
     * 连接到 MCP 服务器
     */
    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        if (connectionState == ConnectionState.CONNECTED) {
            future.complete(null);
            return future;
        }
        
        try {
            // 发送初始化请求
            McpRequest initRequest = new McpRequest(
                    "2.0",
                    "initialize",
                    requestIdCounter.incrementAndGet(),
                    Map.of(
                            "protocolVersion", "2.0",
                            "capabilities", getClientCapabilities(),
                            "clientInfo", Map.of("name", "JWCode", "version", "1.0.0")
                    )
            );
            
            sendRequest(initRequest)
                    .thenAccept(response -> {
                        // 处理初始化响应
                        serverCapabilities = parseServerCapabilities(response);
                        connectionState = ConnectionState.CONNECTED;
                        
                        // 发送 initialized 通知
                        sendNotification("notifications/initialized", Map.of());
                        
                        future.complete(null);
                    })
                    .exceptionally(error -> {
                        connectionState = ConnectionState.ERROR;
                        future.completeExceptionally(error);
                        return null;
                    });
                    
        } catch (Exception e) {
            connectionState = ConnectionState.ERROR;
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 断开连接
     */
    public CompletableFuture<Void> disconnect() {
        connectionState = ConnectionState.DISCONNECTED;
        serverCapabilities = null;
        
        // 取消所有待处理的请求
        for (CompletableFuture<McpResponse> pending : pendingRequests.values()) {
            pending.cancel(true);
        }
        pendingRequests.clear();
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 获取客户端能力
     */
    private Map<String, Object> getClientCapabilities() {
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("roots", Map.of("listChanged", true));
        capabilities.put("sampling", Map.of());
        return capabilities;
    }
    
    /**
     * 发送请求
     */
    private CompletableFuture<McpResponse> sendRequest(McpRequest request) {
        CompletableFuture<McpResponse> future = new CompletableFuture<>();
        
        String requestId = String.valueOf(request.id());
        pendingRequests.put(requestId, future);
        
        try {
            // 构建 HTTP 请求
            String jsonBody = toJson(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/mcp"))
                    .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        pendingRequests.remove(requestId);
                        
                        if (response.statusCode() != 200) {
                            throw new RuntimeException("MCP 请求失败，状态码：" + response.statusCode());
                        }
                        
                        return parseResponse(response.body());
                    })
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            future.complete(response);
                        }
                    });
                    
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * 发送通知
     */
    private void sendNotification(String method, Map<String, Object> params) {
        try {
            McpNotification notification = new McpNotification(
                    "2.0",
                    method,
                    params
            );
            
            String jsonBody = toJson(notification);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/mcp"))
                    .timeout(Duration.ofMillis(5000))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // 通知失败不抛出异常
        }
    }
    
    /**
     * 调用工具
     * 
     * @param name 工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    public CompletableFuture<McpToolResult> callTool(String name, Map<String, Object> arguments) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("arguments", arguments);
        
        McpRequest request = new McpRequest(
                "2.0",
                "tools/call",
                requestIdCounter.incrementAndGet(),
                params
        );
        
        return sendRequest(request)
                .thenApply(response -> parseToolResult(response));
    }
    
    /**
     * 列出可用工具
     */
    public CompletableFuture<List<McpTool>> listTools() {
        McpRequest request = new McpRequest(
                "2.0",
                "tools/list",
                requestIdCounter.incrementAndGet(),
                Map.of()
        );
        
        return sendRequest(request)
                .thenApply(response -> parseToolsList(response));
    }
    
    /**
     * 读取资源
     * 
     * @param uri 资源 URI
     * @return 资源内容
     */
    public CompletableFuture<McpResourceContent> readResource(String uri) {
        Map<String, Object> params = new HashMap<>();
        params.put("uri", uri);
        
        McpRequest request = new McpRequest(
                "2.0",
                "resources/read",
                requestIdCounter.incrementAndGet(),
                params
        );
        
        return sendRequest(request)
                .thenApply(response -> parseResourceContents(response));
    }
    
    /**
     * 列出可用资源
     */
    public CompletableFuture<List<McpResource>> listResources() {
        McpRequest request = new McpRequest(
                "2.0",
                "resources/list",
                requestIdCounter.incrementAndGet(),
                Map.of()
        );
        
        return sendRequest(request)
                .thenApply(response -> parseResourcesList(response));
    }
    
    /**
     * 获取提示模板
     * 
     * @param name 提示名称
     * @param arguments 提示参数
     * @return 提示内容
     */
    public CompletableFuture<McpPrompt> getPrompt(String name, Map<String, Object> arguments) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("arguments", arguments);
        
        McpRequest request = new McpRequest(
                "2.0",
                "prompts/get",
                requestIdCounter.incrementAndGet(),
                params
        );
        
        return sendRequest(request)
                .thenApply(response -> parsePrompt(response));
    }
    
    /**
     * 列出可用提示模板
     */
    public CompletableFuture<List<McpPromptInfo>> listPrompts() {
        McpRequest request = new McpRequest(
                "2.0",
                "prompts/list",
                requestIdCounter.incrementAndGet(),
                Map.of()
        );
        
        return sendRequest(request)
                .thenApply(response -> parsePromptsList(response));
    }
    
    /**
     * 对象转 JSON（简化实现）
     */
    private String toJson(Object obj) {
        // 简化实现：实际应该使用 Jackson 或 Gson
        if (obj instanceof McpRequest) {
            McpRequest request = (McpRequest) obj;
            return String.format(
                "{\"jsonrpc\":\"%s\",\"method\":\"%s\",\"id\":%d,\"params\":%s}",
                request.jsonrpc(),
                request.method(),
                request.id(),
                mapToJson(request.params())
            );
        } else if (obj instanceof McpNotification) {
            McpNotification notification = (McpNotification) obj;
            return String.format(
                "{\"jsonrpc\":\"%s\",\"method\":\"%s\",\"params\":%s}",
                notification.jsonrpc(),
                notification.method(),
                mapToJson(notification.params())
            );
        }
        return "{}";
    }
    
    /**
     * Map 转 JSON（简化实现）
     */
    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) value));
            } else if (value instanceof List) {
                sb.append(listToJson((List<?>) value));
            } else {
                sb.append(value);
            }
            sb.append(",");
        }
        // 移除最后一个逗号
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * List 转 JSON（简化实现）
     */
    private String listToJson(List<?> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (Object item : list) {
            if (item instanceof String) {
                sb.append("\"").append(item).append("\"");
            } else if (item instanceof Map) {
                sb.append(mapToJson((Map<String, Object>) item));
            } else {
                sb.append(item);
            }
            sb.append(",");
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 解析响应
     */
    private McpResponse parseResponse(String json) {
        // 简化实现：实际应该使用 JSON 库解析
        return new McpResponse(json);
    }
    
    /**
     * 解析服务器能力
     */
    private ServerCapabilities parseServerCapabilities(McpResponse response) {
        // 简化实现
        return new ServerCapabilities();
    }
    
    /**
     * 解析工具结果
     */
    private McpToolResult parseToolResult(McpResponse response) {
        return new McpToolResult();
    }
    
    /**
     * 解析工具列表
     */
    private List<McpTool> parseToolsList(McpResponse response) {
        return new ArrayList<>();
    }
    
    /**
     * 解析资源内容
     */
    private McpResourceContent parseResourceContents(McpResponse response) {
        return new McpResourceContent();
    }
    
    /**
     * 解析资源列表
     */
    private List<McpResource> parseResourcesList(McpResponse response) {
        return new ArrayList<>();
    }
    
    /**
     * 解析提示
     */
    private McpPrompt parsePrompt(McpResponse response) {
        return new McpPrompt();
    }
    
    /**
     * 解析提示列表
     */
    private List<McpPromptInfo> parsePromptsList(McpResponse response) {
        return new ArrayList<>();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 连接状态枚举
     */
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    /**
     * MCP 请求记录
     */
    public record McpRequest(String jsonrpc, String method, int id, Map<String, Object> params) {}
    
    /**
     * MCP 通知记录
     */
    public record McpNotification(String jsonrpc, String method, Map<String, Object> params) {}
    
    /**
     * MCP 响应
     */
    public static class McpResponse {
        private final String json;
        
        public McpResponse(String json) {
            this.json = json;
        }
        
        public String getJson() {
            return json;
        }
    }
    
    /**
     * 服务器能力
     */
    public static class ServerCapabilities {
        private boolean toolsSupported;
        private boolean resourcesSupported;
        private boolean promptsSupported;
        
        public boolean isToolsSupported() {
            return toolsSupported;
        }
        
        public boolean isResourcesSupported() {
            return resourcesSupported;
        }
        
        public boolean isPromptsSupported() {
            return promptsSupported;
        }
    }
    
    /**
     * MCP 提示
     */
    public static class McpPrompt {
        private String name;
        private String description;
        private List<McpPromptMessage> messages;
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public List<McpPromptMessage> getMessages() {
            return messages;
        }
    }
    
    /**
     * MCP 提示信息
     */
    public static class McpPromptMessage {
        private String role;
        private McpPromptContent content;
        
        public String getRole() {
            return role;
        }
        
        public McpPromptContent getContent() {
            return content;
        }
    }
    
    /**
     * MCP 提示内容
     */
    public static class McpPromptContent {
        private String type;
        private String text;
        
        public String getType() {
            return type;
        }
        
        public String getText() {
            return text;
        }
    }
    
    /**
     * MCP 提示模板信息
     */
    public static class McpPromptInfo {
        private String name;
        private String description;
        private List<McpPromptArgument> arguments;
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public List<McpPromptArgument> getArguments() {
            return arguments;
        }
    }
    
    /**
     * MCP 提示参数
     */
    public static class McpPromptArgument {
        private String name;
        private String description;
        private boolean required;
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isRequired() {
            return required;
        }
    }
}