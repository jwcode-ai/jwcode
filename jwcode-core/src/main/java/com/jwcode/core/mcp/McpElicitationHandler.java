package com.jwcode.core.mcp;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * McpElicitationHandler - MCP 诱导处理
 * 
 * 功能说明：
 * 处理 MCP 协议中的诱导（Elicitation）请求。
 * 诱导是服务器向客户端请求额外信息或确认的机制，
 * 用于在工具执行过程中获取用户输入或确认危险操作。
 * 
 * 核心特性：
 * - 支持多种诱导类型（确认、输入、选择）
 * - 异步诱导处理
 * - 诱导超时管理
 * - 诱导历史记录
 * - 自定义诱导处理器
 * 
 * 上下文关系：
 * - 被 McpClient 用来处理诱导请求
 * - 与 UI 层交互获取用户输入
 * - 为工具执行提供用户确认机制
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class McpElicitationHandler {
    
    /**
     * 待处理的诱导请求
     */
    private final Map<String, PendingElicitation> pendingElicitations;
    
    /**
     * 诱导历史记录
     */
    private final List<ElicitationRecord> elicitationHistory;
    
    /**
     * 自定义诱导处理器
     */
    private final List<ElicitationProcessor> processors;
    
    /**
     * 默认诱导超时（毫秒）
     */
    private static final long DEFAULT_TIMEOUT_MS = 60000;
    
    /**
     * 最大历史记录数量
     */
    private static final int MAX_HISTORY_SIZE = 100;
    
    /**
     * 构造函数
     */
    public McpElicitationHandler() {
        this.pendingElicitations = new ConcurrentHashMap<>();
        this.elicitationHistory = new ArrayList<>();
        this.processors = new ArrayList<>();
    }
    
    /**
     * 注册诱导处理器
     * 
     * @param processor 诱导处理器
     */
    public void registerProcessor(ElicitationProcessor processor) {
        processors.add(processor);
    }
    
    /**
     * 移除诱导处理器
     * 
     * @param processor 诱导处理器
     */
    public void unregisterProcessor(ElicitationProcessor processor) {
        processors.remove(processor);
    }
    
    /**
     * 处理诱导请求
     * 
     * @param request 诱导请求
     * @return 诱导响应的 CompletableFuture
     */
    public CompletableFuture<ElicitationResponse> handleElicitation(ElicitationRequest request) {
        CompletableFuture<ElicitationResponse> future = new CompletableFuture<>();
        
        String elicitationId = UUID.randomUUID().toString();
        PendingElicitation pending = new PendingElicitation(elicitationId, request, future);
        
        pendingElicitations.put(elicitationId, pending);
        
        // 设置超时
        scheduleTimeout(elicitationId, request.getTimeoutMs());
        
        // 尝试使用自定义处理器处理
        boolean handled = false;
        for (ElicitationProcessor processor : processors) {
            if (processor.canHandle(request)) {
                try {
                    processor.handle(request, response -> {
                        completeElicitation(elicitationId, response);
                    });
                    handled = true;
                    break;
                } catch (Exception e) {
                    // 处理器失败，继续尝试下一个
                }
            }
        }
        
        // 如果没有处理器处理，使用默认处理方式
        if (!handled) {
            handleDefault(request, response -> {
                completeElicitation(elicitationId, response);
            });
        }
        
        return future;
    }
    
    /**
     * 默认处理方式
     */
    private void handleDefault(ElicitationRequest request, Consumer<ElicitationResponse> callback) {
        // 默认直接拒绝诱导请求
        // 实际实现应该与 UI 交互获取用户输入
        callback.accept(ElicitationResponse.decline("未配置诱导处理器"));
    }
    
    /**
     * 完成诱导请求
     */
    private void completeElicitation(String elicitationId, ElicitationResponse response) {
        PendingElicitation pending = pendingElicitations.remove(elicitationId);
        if (pending != null) {
            pending.getFuture().complete(response);
            
            // 记录到历史
            recordElicitation(pending.getRequest(), response);
        }
    }
    
    /**
     * 取消诱导请求
     * 
     * @param elicitationId 诱导 ID
     */
    public void cancelElicitation(String elicitationId) {
        PendingElicitation pending = pendingElicitations.remove(elicitationId);
        if (pending != null) {
            pending.getFuture().cancel(true);
            
            ElicitationResponse response = new ElicitationResponse(
                    ElicitationAction.DECLINED,
                    null,
                    "用户取消了请求"
            );
            recordElicitation(pending.getRequest(), response);
        }
    }
    
    /**
     * 设置超时
     */
    private void scheduleTimeout(String elicitationId, long timeoutMs) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                PendingElicitation pending = pendingElicitations.get(elicitationId);
                if (pending != null) {
                    pendingElicitations.remove(elicitationId);
                    pending.getFuture().completeExceptionally(
                            new TimeoutException("诱导请求超时"));
                    
                    ElicitationResponse response = new ElicitationResponse(
                            ElicitationAction.DECLINED,
                            null,
                            "请求超时"
                    );
                    recordElicitation(pending.getRequest(), response);
                }
                timer.cancel();
            }
        }, timeoutMs);
    }
    
    /**
     * 记录诱导历史
     */
    private void recordElicitation(ElicitationRequest request, ElicitationResponse response) {
        ElicitationRecord record = new ElicitationRecord(
                UUID.randomUUID().toString(),
                request,
                response,
                Instant.now()
        );
        
        elicitationHistory.add(record);
        
        // 限制历史记录大小
        while (elicitationHistory.size() > MAX_HISTORY_SIZE) {
            elicitationHistory.remove(0);
        }
    }
    
    /**
     * 获取诱导历史
     * 
     * @param limit 限制数量
     * @return 诱导历史记录
     */
    public List<ElicitationRecord> getHistory(int limit) {
        int size = elicitationHistory.size();
        if (limit >= size) {
            return new ArrayList<>(elicitationHistory);
        }
        return new ArrayList<>(elicitationHistory.subList(size - limit, size));
    }
    
    /**
     * 清除诱导历史
     */
    public void clearHistory() {
        elicitationHistory.clear();
    }
    
    /**
     * 获取待处理的诱导数量
     * 
     * @return 待处理的诱导数量
     */
    public int getPendingCount() {
        return pendingElicitations.size();
    }
    
    /**
     * 获取所有待处理的诱导 ID
     * 
     * @return 诱导 ID 集合
     */
    public Set<String> getPendingElicitationIds() {
        return pendingElicitations.keySet();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 待处理的诱导请求
     */
    private static class PendingElicitation {
        private final String elicitationId;
        private final ElicitationRequest request;
        private final CompletableFuture<ElicitationResponse> future;
        
        public PendingElicitation(String elicitationId, ElicitationRequest request,
                                  CompletableFuture<ElicitationResponse> future) {
            this.elicitationId = elicitationId;
            this.request = request;
            this.future = future;
        }
        
        public String getElicitationId() {
            return elicitationId;
        }
        
        public ElicitationRequest getRequest() {
            return request;
        }
        
        public CompletableFuture<ElicitationResponse> getFuture() {
            return future;
        }
    }
    
    /**
     * 诱导动作枚举
     */
    public enum ElicitationAction {
        /** 接受 */
        ACCEPT,
        /** 拒绝 */
        DECLINED,
        /** 取消 */
        CANCEL
    }
    
    /**
     * 诱导类型枚举
     */
    public enum ElicitationType {
        /** 确认 */
        CONFIRM("confirm"),
        /** 输入文本 */
        INPUT_TEXT("input_text"),
        /** 选择 */
        SELECT("select"),
        /** 多选 */
        MULTI_SELECT("multi_select");
        
        private final String value;
        
        ElicitationType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static ElicitationType fromValue(String value) {
            for (ElicitationType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return CONFIRM;
        }
    }
    
    /**
     * 诱导请求类
     */
    public static class ElicitationRequest {
        private final String id;
        private final ElicitationType type;
        private final String title;
        private final String message;
        private final Map<String, Object> options;
        private final long timeoutMs;
        
        public ElicitationRequest(String id, ElicitationType type, String title,
                                  String message, Map<String, Object> options, long timeoutMs) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.message = message;
            this.options = options != null ? options : new HashMap<>();
            this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        }
        
        public String getId() {
            return id;
        }
        
        public ElicitationType getType() {
            return type;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Map<String, Object> getOptions() {
            return options;
        }
        
        public long getTimeoutMs() {
            return timeoutMs;
        }
        
        /**
         * 创建确认请求
         */
        public static ElicitationRequest confirm(String title, String message) {
            return new ElicitationRequest(
                    UUID.randomUUID().toString(),
                    ElicitationType.CONFIRM,
                    title,
                    message,
                    null,
                    DEFAULT_TIMEOUT_MS
            );
        }
        
        /**
         * 创建输入请求
         */
        public static ElicitationRequest input(String title, String message, String placeholder) {
            Map<String, Object> options = new HashMap<>();
            options.put("placeholder", placeholder);
            return new ElicitationRequest(
                    UUID.randomUUID().toString(),
                    ElicitationType.INPUT_TEXT,
                    title,
                    message,
                    options,
                    DEFAULT_TIMEOUT_MS
            );
        }
        
        /**
         * 创建选择请求
         */
        public static ElicitationRequest select(String title, String message, List<String> options) {
            Map<String, Object> opts = new HashMap<>();
            opts.put("choices", options);
            return new ElicitationRequest(
                    UUID.randomUUID().toString(),
                    ElicitationType.SELECT,
                    title,
                    message,
                    opts,
                    DEFAULT_TIMEOUT_MS
            );
        }
    }
    
    /**
     * 诱导响应类
     */
    public static class ElicitationResponse {
        private final ElicitationAction action;
        private final Object content;
        private final String reason;
        
        public ElicitationResponse(ElicitationAction action, Object content, String reason) {
            this.action = action;
            this.content = content;
            this.reason = reason;
        }
        
        public ElicitationAction getAction() {
            return action;
        }
        
        public Object getContent() {
            return content;
        }
        
        public String getReason() {
            return reason;
        }
        
        /**
         * 创建接受响应
         */
        public static ElicitationResponse accept(Object content) {
            return new ElicitationResponse(ElicitationAction.ACCEPT, content, null);
        }
        
        /**
         * 创建拒绝响应
         */
        public static ElicitationResponse decline(String reason) {
            return new ElicitationResponse(ElicitationAction.DECLINED, null, reason);
        }
        
        /**
         * 创建取消响应
         */
        public static ElicitationResponse cancel(String reason) {
            return new ElicitationResponse(ElicitationAction.CANCEL, null, reason);
        }
    }
    
    /**
     * 诱导记录类
     */
    public static class ElicitationRecord {
        private final String recordId;
        private final ElicitationRequest request;
        private final ElicitationResponse response;
        private final Instant timestamp;
        
        public ElicitationRecord(String recordId, ElicitationRequest request,
                                 ElicitationResponse response, Instant timestamp) {
            this.recordId = recordId;
            this.request = request;
            this.response = response;
            this.timestamp = timestamp;
        }
        
        public String getRecordId() {
            return recordId;
        }
        
        public ElicitationRequest getRequest() {
            return request;
        }
        
        public ElicitationResponse getResponse() {
            return response;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 诱导处理器接口
     */
    public interface ElicitationProcessor {
        
        /**
         * 检查是否可以处理该诱导请求
         * 
         * @param request 诱导请求
         * @return true 如果可以处理
         */
        boolean canHandle(ElicitationRequest request);
        
        /**
         * 处理诱导请求
         * 
         * @param request 诱导请求
         * @param callback 响应回调
         */
        void handle(ElicitationRequest request, Consumer<ElicitationResponse> callback);
    }
    
    /**
     * 超时异常类
     */
    public static class TimeoutException extends RuntimeException {
        public TimeoutException(String message) {
            super(message);
        }
    }
}