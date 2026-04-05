package com.jwcode.core.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AnalyticsService - 分析服务
 * 
 * 功能说明：
 * 提供使用分析、事件追踪和统计功能。
 * 收集用户操作、工具使用、API 调用等数据，用于产品改进和性能优化。
 * 
 * 主要功能：
 * - 事件收集和记录
 * - 使用统计追踪
 * - 性能指标监控
 * - 数据导出支持
 * 
 * 隐私保护：
 * - 不收集敏感个人信息
 * - 支持匿名模式
 * - 支持数据导出和删除
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AnalyticsService {
    
    private static final String ANALYTICS_VERSION = "1.0.0";
    
    // 单例实例
    private static volatile AnalyticsService instance;
    
    // 事件存储
    private final List<AnalyticsEvent> eventStore;
    
    // 统计计数器
    private final Map<String, AtomicLong> counters;
    
    // 用户标识（匿名）
    private String anonymousUserId;
    
    // 是否启用分析
    private boolean enabled = true;
    
    // 是否匿名模式
    private boolean anonymousMode = true;
    
    // 会话 ID
    private String currentSessionId;
    
    /**
     * 私有构造函数
     */
    private AnalyticsService() {
        this.eventStore = new ArrayList<>();
        this.counters = new ConcurrentHashMap<>();
        this.anonymousUserId = generateAnonymousId();
        this.currentSessionId = UUID.randomUUID().toString();
        
        // 初始化计数器
        initializeCounters();
    }
    
    /**
     * 获取单例实例
     */
    public static AnalyticsService getInstance() {
        if (instance == null) {
            synchronized (AnalyticsService.class) {
                if (instance == null) {
                    instance = new AnalyticsService();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化计数器
     */
    private void initializeCounters() {
        counters.put("tool_calls", new AtomicLong(0));
        counters.put("api_requests", new AtomicLong(0));
        counters.put("commands_executed", new AtomicLong(0));
        counters.put("errors", new AtomicLong(0));
        counters.put("session_count", new AtomicLong(0));
        counters.put("tokens_used", new AtomicLong(0));
    }
    
    /**
     * 生成匿名用户 ID
     */
    private String generateAnonymousId() {
        return "anon_" + UUID.randomUUID().toString().substring(0, 12);
    }
    
    /**
     * 启用分析
     */
    public void enable() {
        this.enabled = true;
    }
    
    /**
     * 禁用分析
     */
    public void disable() {
        this.enabled = false;
    }
    
    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 设置匿名模式
     */
    public void setAnonymousMode(boolean anonymous) {
        this.anonymousMode = anonymous;
    }
    
    /**
     * 获取当前会话 ID
     */
    public String getSessionId() {
        return currentSessionId;
    }
    
    /**
     * 开始新会话
     */
    public void startNewSession() {
        this.currentSessionId = UUID.randomUUID().toString();
        counters.get("session_count").incrementAndGet();
        
        trackEvent("session_start", Map.of(
                "session_id", currentSessionId
        ));
    }
    
    /**
     * 结束当前会话
     */
    public void endSession() {
        trackEvent("session_end", Map.of(
                "session_id", currentSessionId,
                "duration_seconds", getSessionDuration()
        ));
        
        // 生成新的会话 ID
        this.currentSessionId = UUID.randomUUID().toString();
    }
    
    /**
     * 获取会话时长（秒）
     */
    private long getSessionDuration() {
        // 简化实现，实际应记录会话开始时间
        return 0;
    }
    
    /**
     * 追踪工具调用
     */
    public void trackToolCall(String toolName, long durationMs, boolean success) {
        if (!enabled) return;
        
        counters.get("tool_calls").incrementAndGet();
        
        trackEvent("tool_call", Map.of(
                "tool_name", toolName,
                "duration_ms", durationMs,
                "success", success
        ));
    }
    
    /**
     * 追踪 API 请求
     */
    public void trackApiRequest(String endpoint, long durationMs, int statusCode, long tokensUsed) {
        if (!enabled) return;
        
        counters.get("api_requests").incrementAndGet();
        if (tokensUsed > 0) {
            counters.get("tokens_used").addAndGet(tokensUsed);
        }
        
        trackEvent("api_request", Map.of(
                "endpoint", endpoint,
                "duration_ms", durationMs,
                "status_code", statusCode,
                "tokens_used", tokensUsed
        ));
    }
    
    /**
     * 追踪命令执行
     */
    public void trackCommandExecution(String commandName, boolean success) {
        if (!enabled) return;
        
        counters.get("commands_executed").incrementAndGet();
        
        if (!success) {
            counters.get("errors").incrementAndGet();
        }
        
        trackEvent("command_execution", Map.of(
                "command_name", commandName,
                "success", success
        ));
    }
    
    /**
     * 追踪错误
     */
    public void trackError(String errorType, String errorMessage, String context) {
        if (!enabled) return;
        
        counters.get("errors").incrementAndGet();
        
        trackEvent("error", Map.of(
                "error_type", errorType,
                "error_message", truncate(errorMessage, 500),
                "context", truncate(context, 200)
        ));
    }
    
    /**
     * 追踪自定义事件
     */
    public void trackEvent(String eventType, Map<String, Object> properties) {
        if (!enabled) return;
        
        AnalyticsEvent event = new AnalyticsEvent(
                eventType,
                anonymousMode ? anonymousUserId : null,
                currentSessionId,
                properties
        );
        
        synchronized (eventStore) {
            eventStore.add(event);
            
            // 限制存储大小
            if (eventStore.size() > 10000) {
                eventStore.remove(0);
            }
        }
    }
    
    /**
     * 获取统计数据
     */
    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().get());
        }
        return stats;
    }
    /**
     * 获取特定计数器值
     */
    public long getCounter(String name) {
        AtomicLong counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 重置统计数据
     */
    public void resetStatistics() {
        initializeCounters();
        synchronized (eventStore) {
            eventStore.clear();
        }
    }
    
    /**
     * 导出事件数据（JSON 格式）
     */
    public String exportEvents() {
        synchronized (eventStore) {
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            
            for (int i = 0; i < eventStore.size(); i++) {
                AnalyticsEvent event = eventStore.get(i);
                json.append(event.toJson());
                if (i < eventStore.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            
            json.append("]");
            return json.toString();
        }
    }
    
    /**
     * 导出统计数据
     */
    public String exportStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": \"").append(ANALYTICS_VERSION).append("\",\n");
        sb.append("  \"exported_at\": \"").append(LocalDateTime.now().format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        sb.append("  \"anonymous_user_id\": \"").append(anonymousUserId).append("\",\n");
        sb.append("  \"current_session_id\": \"").append(currentSessionId).append("\",\n");
        sb.append("  \"counters\": {\n");
        
        int i = 0;
        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": ")
              .append(entry.getValue().get());
            if (i < counters.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            i++;
        }
        
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
    
    /**
     * 分析事件类
     */
    public static class AnalyticsEvent {
        public final String eventType;
        public final String timestamp;
        public final String userId;
        public final String sessionId;
        public final Map<String, Object> properties;
        
        public AnalyticsEvent(String eventType, String userId, String sessionId, 
                             Map<String, Object> properties) {
            this.eventType = eventType;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            this.userId = userId;
            this.sessionId = sessionId;
            this.properties = properties;
        }
        
        /**
         * 转换为 JSON 字符串
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("  {\n");
            sb.append("    \"event_type\": \"").append(escapeJson(eventType)).append("\",\n");
            sb.append("    \"timestamp\": \"").append(timestamp).append("\",\n");
            
            if (userId != null) {
                sb.append("    \"user_id\": \"").append(escapeJson(userId)).append("\",\n");
            }
            
            sb.append("    \"session_id\": \"").append(escapeJson(sessionId)).append("\",\n");
            sb.append("    \"properties\": {\n");
            
            int i = 0;
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                sb.append("      \"").append(escapeJson(entry.getKey())).append("\": ");
                sb.append(valueToJson(entry.getValue()));
                if (i < properties.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
                i++;
            }
            
            sb.append("    }\n");
            sb.append("  }");
            return sb.toString();
        }
        
        private String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
        }
        
        private String valueToJson(Object value) {
            if (value == null) return "null";
            if (value instanceof String) {
                return "\"" + escapeJson(value.toString()) + "\"";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            return "\"" + escapeJson(value.toString()) + "\"";
        }
    }
}