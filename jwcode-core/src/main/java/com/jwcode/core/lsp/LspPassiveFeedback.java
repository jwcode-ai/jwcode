package com.jwcode.core.lsp;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * LspPassiveFeedback - LSP 被动反馈
 * 
 * 功能说明：
 * 收集和分析 LSP 被动反馈数据，如代码补全接受率、诊断修复时间等。
 * 用于改进语言服务器性能和用户体验。
 * 
 * 核心特性：
 * - 代码补全反馈收集
 * - 诊断修复时间追踪
 * - 用户行为分析
 * - 性能指标统计
 * - 反馈数据导出
 * 
 * 上下文关系：
 * - 被 LspClient 用来收集反馈
 * - 与 LspDiagnosticRegistry 协作
 * - 为性能优化提供数据支持
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspPassiveFeedback {
    
    /**
     * 反馈事件列表
     */
    private final List<FeedbackEvent> feedbackEvents;
    
    /**
     * 补全反馈映射表
     */
    private final Map<String, CompletionFeedback> completionFeedback;
    
    /**
     * 诊断反馈映射表
     */
    private final Map<String, DiagnosticFeedback> diagnosticFeedback;
    
    /**
     * 反馈监听器
     */
    private final List<Consumer<FeedbackEvent>> feedbackListeners;
    
    /**
     * 最大事件历史数量
     */
    private static final int MAX_EVENT_HISTORY = 10000;
    
    /**
     * 反馈清理间隔（毫秒）
     */
    private static final long CLEANUP_INTERVAL_MS = 300000;
    
    /**
     * 反馈过期时间（毫秒）
     */
    private static final long FEEDBACK_EXPIRY_MS = 3600000;
    
    /**
     * 构造函数
     */
    public LspPassiveFeedback() {
        this.feedbackEvents = new CopyOnWriteArrayList<>();
        this.completionFeedback = new ConcurrentHashMap<>();
        this.diagnosticFeedback = new ConcurrentHashMap<>();
        this.feedbackListeners = new CopyOnWriteArrayList<>();
        
        // 启动清理任务
        startCleanupTask();
    }
    
    /**
     * 添加反馈监听器
     */
    public void addFeedbackListener(Consumer<FeedbackEvent> listener) {
        this.feedbackListeners.add(listener);
    }
    
    /**
     * 移除反馈监听器
     */
    public void removeFeedbackListener(Consumer<FeedbackEvent> listener) {
        this.feedbackListeners.remove(listener);
    }
    
    /**
     * 记录补全请求
     */
    public void recordCompletionRequest(String sessionId, String uri, int line, int character) {
        CompletionFeedback feedback = new CompletionFeedback(sessionId, uri, line, character);
        completionFeedback.put(sessionId, feedback);
        
        FeedbackEvent event = new FeedbackEvent(
                FeedbackEventType.COMPLETION_REQUESTED,
                uri,
                Map.of("sessionId", sessionId, "line", line, "character", character),
                Instant.now()
        );
        addEvent(event);
    }
    
    /**
     * 记录补全接受
     */
    public void recordCompletionAccepted(String sessionId, String completionLabel) {
        CompletionFeedback feedback = completionFeedback.get(sessionId);
        if (feedback != null) {
            feedback.accept(completionLabel);
            
            FeedbackEvent event = new FeedbackEvent(
                    FeedbackEventType.COMPLETION_ACCEPTED,
                    feedback.getUri(),
                    Map.of("sessionId", sessionId, "completion", completionLabel),
                    Instant.now()
            );
            addEvent(event);
        }
    }
    
    /**
     * 记录补全拒绝
     */
    public void recordCompletionRejected(String sessionId) {
        CompletionFeedback feedback = completionFeedback.get(sessionId);
        if (feedback != null) {
            feedback.reject();
            
            FeedbackEvent event = new FeedbackEvent(
                    FeedbackEventType.COMPLETION_REJECTED,
                    feedback.getUri(),
                    Map.of("sessionId", sessionId),
                    Instant.now()
            );
            addEvent(event);
        }
    }
    
    /**
     * 记录诊断出现
     */
    public void recordDiagnosticAppeared(String uri, String diagnosticId, String severity) {
        DiagnosticFeedback feedback = new DiagnosticFeedback(uri, diagnosticId, severity);
        diagnosticFeedback.put(diagnosticId, feedback);
        
        FeedbackEvent event = new FeedbackEvent(
                FeedbackEventType.DIAGNOSTIC_APPEARED,
                uri,
                Map.of("diagnosticId", diagnosticId, "severity", severity),
                Instant.now()
        );
        addEvent(event);
    }
    
    /**
     * 记录诊断修复
     */
    public void recordDiagnosticFixed(String diagnosticId) {
        DiagnosticFeedback feedback = diagnosticFeedback.remove(diagnosticId);
        if (feedback != null) {
            feedback.fix();
            
            FeedbackEvent event = new FeedbackEvent(
                    FeedbackEventType.DIAGNOSTIC_FIXED,
                    feedback.getUri(),
                    Map.of("diagnosticId", diagnosticId, "fixTime", feedback.getFixTimeMs()),
                    Instant.now()
            );
            addEvent(event);
        }
    }
    
    /**
     * 记录请求延迟
     */
    public void recordRequestLatency(String requestType, long latencyMs) {
        FeedbackEvent event = new FeedbackEvent(
                FeedbackEventType.REQUEST_LATENCY,
                null,
                Map.of("requestType", requestType, "latencyMs", latencyMs),
                Instant.now()
        );
        addEvent(event);
    }
    
    /**
     * 获取补全接受率
     */
    public double getCompletionAcceptanceRate() {
        int total = 0;
        int accepted = 0;
        
        for (FeedbackEvent event : feedbackEvents) {
            if (event.getType() == FeedbackEventType.COMPLETION_REQUESTED) {
                total++;
            } else if (event.getType() == FeedbackEventType.COMPLETION_ACCEPTED) {
                accepted++;
            }
        }
        
        return total > 0 ? (double) accepted / total : 0.0;
    }
    
    /**
     * 获取平均诊断修复时间（毫秒）
     */
    public long getAverageDiagnosticFixTime() {
        List<Long> fixTimes = new ArrayList<>();
        for (FeedbackEvent event : feedbackEvents) {
            if (event.getType() == FeedbackEventType.DIAGNOSTIC_FIXED) {
                Object fixTime = event.getData().get("fixTime");
                if (fixTime instanceof Number) {
                    fixTimes.add(((Number) fixTime).longValue());
                }
            }
        }
        
        if (fixTimes.isEmpty()) {
            return 0;
        }
        
        long sum = 0;
        for (long time : fixTimes) {
            sum += time;
        }
        return sum / fixTimes.size();
    }
    
    /**
     * 获取平均请求延迟（毫秒）
     */
    public long getAverageRequestLatency() {
        List<Long> latencies = new ArrayList<>();
        for (FeedbackEvent event : feedbackEvents) {
            if (event.getType() == FeedbackEventType.REQUEST_LATENCY) {
                Object latency = event.getData().get("latencyMs");
                if (latency instanceof Number) {
                    latencies.add(((Number) latency).longValue());
                }
            }
        }
        
        if (latencies.isEmpty()) {
            return 0;
        }
        
        long sum = 0;
        for (long latency : latencies) {
            sum += latency;
        }
        return sum / latencies.size();
    }
    
    /**
     * 获取反馈统计
     */
    public FeedbackStats getStats() {
        int completionRequested = 0;
        int completionAccepted = 0;
        int diagnosticAppeared = 0;
        int diagnosticFixed = 0;
        
        for (FeedbackEvent event : feedbackEvents) {
            switch (event.getType()) {
                case COMPLETION_REQUESTED:
                    completionRequested++;
                    break;
                case COMPLETION_ACCEPTED:
                    completionAccepted++;
                    break;
                case DIAGNOSTIC_APPEARED:
                    diagnosticAppeared++;
                    break;
                case DIAGNOSTIC_FIXED:
                    diagnosticFixed++;
                    break;
            }
        }
        
        return new FeedbackStats(
                completionRequested,
                completionAccepted,
                diagnosticAppeared,
                diagnosticFixed,
                getAverageRequestLatency(),
                getAverageDiagnosticFixTime()
        );
    }
    
    /**
     * 获取反馈事件历史
     */
    public List<FeedbackEvent> getEventHistory(int limit) {
        int size = feedbackEvents.size();
        if (limit >= size) {
            return new ArrayList<>(feedbackEvents);
        }
        return new ArrayList<>(feedbackEvents.subList(size - limit, size));
    }
    
    /**
     * 清除反馈历史
     */
    public void clearHistory() {
        feedbackEvents.clear();
        completionFeedback.clear();
        diagnosticFeedback.clear();
    }
    
    /**
     * 添加事件
     */
    private void addEvent(FeedbackEvent event) {
        feedbackEvents.add(event);
        
        // 限制历史大小
        while (feedbackEvents.size() > MAX_EVENT_HISTORY) {
            feedbackEvents.remove(0);
        }
        
        // 通知监听器
        for (Consumer<FeedbackEvent> listener : feedbackListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
    
    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            
            // 清理过期的补全反馈
            completionFeedback.entrySet().removeIf(entry -> 
                now - entry.getValue().getTimestamp().toEpochMilli() > FEEDBACK_EXPIRY_MS);
            
            // 清理过期的诊断反馈
            diagnosticFeedback.entrySet().removeIf(entry -> 
                now - entry.getValue().getTimestamp().toEpochMilli() > FEEDBACK_EXPIRY_MS);
            
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 反馈事件类型枚举
     */
    public enum FeedbackEventType {
        COMPLETION_REQUESTED,
        COMPLETION_ACCEPTED,
        COMPLETION_REJECTED,
        DIAGNOSTIC_APPEARED,
        DIAGNOSTIC_FIXED,
        REQUEST_LATENCY
    }
    
    /**
     * 反馈事件类
     */
    public static class FeedbackEvent {
        private final FeedbackEventType type;
        private final String uri;
        private final Map<String, Object> data;
        private final Instant timestamp;
        
        public FeedbackEvent(FeedbackEventType type, String uri, Map<String, Object> data, Instant timestamp) {
            this.type = type;
            this.uri = uri;
            this.data = data != null ? new HashMap<>(data) : new HashMap<>();
            this.timestamp = timestamp;
        }
        
        public FeedbackEventType getType() {
            return type;
        }
        
        public String getUri() {
            return uri;
        }
        
        public Map<String, Object> getData() {
            return new HashMap<>(data);
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 反馈统计类
     */
    public static class FeedbackStats {
        private final int completionRequested;
        private final int completionAccepted;
        private final int diagnosticAppeared;
        private final int diagnosticFixed;
        private final long averageRequestLatency;
        private final long averageDiagnosticFixTime;
        
        public FeedbackStats(int completionRequested, int completionAccepted,
                            int diagnosticAppeared, int diagnosticFixed,
                            long averageRequestLatency, long averageDiagnosticFixTime) {
            this.completionRequested = completionRequested;
            this.completionAccepted = completionAccepted;
            this.diagnosticAppeared = diagnosticAppeared;
            this.diagnosticFixed = diagnosticFixed;
            this.averageRequestLatency = averageRequestLatency;
            this.averageDiagnosticFixTime = averageDiagnosticFixTime;
        }
        
        public int getCompletionRequested() {
            return completionRequested;
        }
        
        public int getCompletionAccepted() {
            return completionAccepted;
        }
        
        public int getDiagnosticAppeared() {
            return diagnosticAppeared;
        }
        
        public int getDiagnosticFixed() {
            return diagnosticFixed;
        }
        
        public long getAverageRequestLatency() {
            return averageRequestLatency;
        }
        
        public long getAverageDiagnosticFixTime() {
            return averageDiagnosticFixTime;
        }
        
        public double getCompletionAcceptanceRate() {
            return completionRequested > 0 ? (double) completionAccepted / completionRequested : 0.0;
        }
    }
    
    /**
     * 补全反馈类
     */
    private static class CompletionFeedback {
        private final String sessionId;
        private final String uri;
        private final int line;
        private final int character;
        private final Instant timestamp;
        private boolean accepted;
        private String acceptedCompletion;
        
        public CompletionFeedback(String sessionId, String uri, int line, int character) {
            this.sessionId = sessionId;
            this.uri = uri;
            this.line = line;
            this.character = character;
            this.timestamp = Instant.now();
            this.accepted = false;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public String getUri() {
            return uri;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
        
        public void accept(String completionLabel) {
            this.accepted = true;
            this.acceptedCompletion = completionLabel;
        }
        
        public void reject() {
            this.accepted = false;
        }
        
        public boolean isAccepted() {
            return accepted;
        }
        
        public String getAcceptedCompletion() {
            return acceptedCompletion;
        }
    }
    
    /**
     * 诊断反馈类
     */
    private static class DiagnosticFeedback {
        private final String uri;
        private final String diagnosticId;
        private final String severity;
        private final Instant timestamp;
        private Instant fixedTime;
        
        public DiagnosticFeedback(String uri, String diagnosticId, String severity) {
            this.uri = uri;
            this.diagnosticId = diagnosticId;
            this.severity = severity;
            this.timestamp = Instant.now();
        }
        
        public String getUri() {
            return uri;
        }
        
        public String getDiagnosticId() {
            return diagnosticId;
        }
        
        public String getSeverity() {
            return severity;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
        
        public void fix() {
            this.fixedTime = Instant.now();
        }
        
        public long getFixTimeMs() {
            if (fixedTime == null) {
                return -1;
            }
            return Duration.between(timestamp, fixedTime).toMillis();
        }
    }
}