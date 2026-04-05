package com.jwcode.core.lsp;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * LspDiagnosticRegistry - LSP 诊断注册表
 * 
 * 功能说明：
 * 管理 LSP（Language Server Protocol）诊断信息。
 * 处理语言服务器发送的诊断（错误、警告、提示等），
 * 支持诊断订阅、过滤和聚合。
 * 
 * 核心特性：
 * - 诊断信息管理
 * - 按文件 URI 组织诊断
 * - 诊断严重性过滤
 * - 诊断订阅/发布
 * - 诊断历史记录
 * 
 * 上下文关系：
 * - 被 LspClient 用来接收诊断
 * - 与 LspServerManager 协作
 * - 为 UI 层提供诊断显示数据
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspDiagnosticRegistry {
    
    /**
     * 诊断映射表（文件 URI -> 诊断列表）
     */
    private final Map<String, List<LspDiagnostic>> diagnosticsByUri;
    
    /**
     * 诊断订阅者
     */
    private final List<Consumer<DiagnosticEvent>> subscribers;
    
    /**
     * 诊断历史记录
     */
    private final List<DiagnosticRecord> history;
    
    /**
     * 最大历史记录数量
     */
    private static final int MAX_HISTORY_SIZE = 1000;
    
    /**
     * 构造函数
     */
    public LspDiagnosticRegistry() {
        this.diagnosticsByUri = new ConcurrentHashMap<>();
        this.subscribers = new CopyOnWriteArrayList<>();
        this.history = new ArrayList<>();
    }
    
    /**
     * 订阅诊断事件
     * 
     * @param subscriber 订阅者
     */
    public void subscribe(Consumer<DiagnosticEvent> subscriber) {
        this.subscribers.add(subscriber);
    }
    
    /**
     * 取消订阅
     * 
     * @param subscriber 订阅者
     */
    public void unsubscribe(Consumer<DiagnosticEvent> subscriber) {
        this.subscribers.remove(subscriber);
    }
    
    /**
     * 设置文件的诊断
     * 
     * @param uri 文件 URI
     * @param diagnostics 诊断列表
     */
    public void setDiagnostics(String uri, List<LspDiagnostic> diagnostics) {
        List<LspDiagnostic> existing = diagnosticsByUri.get(uri);
        List<LspDiagnostic> removed = new ArrayList<>();
        List<LspDiagnostic> added = new ArrayList<>(diagnostics);
        
        if (existing != null) {
            // 找出被移除的诊断
            for (LspDiagnostic existingDiag : existing) {
                boolean found = false;
                for (LspDiagnostic newDiag : diagnostics) {
                    if (existingDiag.isSameAs(newDiag)) {
                        found = true;
                        added.remove(newDiag);
                        break;
                    }
                }
                if (!found) {
                    removed.add(existingDiag);
                }
            }
        }
        
        diagnosticsByUri.put(uri, new ArrayList<>(diagnostics));
        
        // 通知订阅者
        if (!added.isEmpty()) {
            notifySubscribers(new DiagnosticEvent(uri, DiagnosticEventType.ADDED, added));
        }
        if (!removed.isEmpty()) {
            notifySubscribers(new DiagnosticEvent(uri, DiagnosticEventType.REMOVED, removed));
        }
        
        // 记录历史
        recordDiagnostics(uri, diagnostics);
    }
    
    /**
     * 清除文件的诊断
     * 
     * @param uri 文件 URI
     */
    public void clearDiagnostics(String uri) {
        List<LspDiagnostic> removed = diagnosticsByUri.remove(uri);
        if (removed != null && !removed.isEmpty()) {
            notifySubscribers(new DiagnosticEvent(uri, DiagnosticEventType.CLEARED, removed));
        }
    }
    
    /**
     * 获取文件的诊断
     * 
     * @param uri 文件 URI
     * @return 诊断列表
     */
    public List<LspDiagnostic> getDiagnostics(String uri) {
        List<LspDiagnostic> diagnostics = diagnosticsByUri.get(uri);
        return diagnostics != null ? new ArrayList<>(diagnostics) : new ArrayList<>();
    }
    
    /**
     * 获取文件的诊断（按严重性过滤）
     * 
     * @param uri 文件 URI
     * @param minSeverity 最小严重性
     * @return 诊断列表
     */
    public List<LspDiagnostic> getDiagnostics(String uri, LspDiagnosticSeverity minSeverity) {
        List<LspDiagnostic> diagnostics = diagnosticsByUri.get(uri);
        if (diagnostics == null) {
            return new ArrayList<>();
        }
        
        List<LspDiagnostic> filtered = new ArrayList<>();
        for (LspDiagnostic diag : diagnostics) {
            if (diag.getSeverity() != null && diag.getSeverity().getValue() <= minSeverity.getValue()) {
                filtered.add(diag);
            }
        }
        return filtered;
    }
    
    /**
     * 获取所有诊断
     * 
     * @return 所有文件的诊断映射
     */
    public Map<String, List<LspDiagnostic>> getAllDiagnostics() {
        Map<String, List<LspDiagnostic>> result = new HashMap<>();
        for (Map.Entry<String, List<LspDiagnostic>> entry : diagnosticsByUri.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }
    
    /**
     * 获取所有文件的诊断总数
     * 
     * @return 诊断总数
     */
    public int getTotalDiagnosticCount() {
        int count = 0;
        for (List<LspDiagnostic> diagnostics : diagnosticsByUri.values()) {
            count += diagnostics.size();
        }
        return count;
    }
    
    /**
     * 获取按严重性统计的诊断数量
     * 
     * @param severity 严重性
     * @return 诊断数量
     */
    public int getDiagnosticCountBySeverity(LspDiagnosticSeverity severity) {
        int count = 0;
        for (List<LspDiagnostic> diagnostics : diagnosticsByUri.values()) {
            for (LspDiagnostic diag : diagnostics) {
                if (diag.getSeverity() == severity) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * 获取错误数量
     * 
     * @return 错误数量
     */
    public int getErrorCount() {
        return getDiagnosticCountBySeverity(LspDiagnosticSeverity.ERROR);
    }
    
    /**
     * 获取警告数量
     * 
     * @return 警告数量
     */
    public int getWarningCount() {
        return getDiagnosticCountBySeverity(LspDiagnosticSeverity.WARNING);
    }
    
    /**
     * 获取提示数量
     * 
     * @return 提示数量
     */
    public int getInformationCount() {
        return getDiagnosticCountBySeverity(LspDiagnosticSeverity.INFORMATION);
    }
    
    /**
     * 获取诊断历史
     * 
     * @param limit 限制数量
     * @return 诊断历史记录
     */
    public List<DiagnosticRecord> getHistory(int limit) {
        int size = history.size();
        if (limit >= size) {
            return new ArrayList<>(history);
        }
        return new ArrayList<>(history.subList(size - limit, size));
    }
    
    /**
     * 清除诊断历史
     */
    public void clearHistory() {
        history.clear();
    }
    
    /**
     * 清除所有诊断
     */
    public void clearAll() {
        diagnosticsByUri.clear();
        notifySubscribers(new DiagnosticEvent(null, DiagnosticEventType.ALL_CLEARED, new ArrayList<>()));
    }
    
    /**
     * 通知订阅者
     */
    private void notifySubscribers(DiagnosticEvent event) {
        for (Consumer<DiagnosticEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                // 忽略订阅者异常
            }
        }
    }
    
    /**
     * 记录诊断历史
     */
    private void recordDiagnostics(String uri, List<LspDiagnostic> diagnostics) {
        DiagnosticRecord record = new DiagnosticRecord(
                UUID.randomUUID().toString(),
                uri,
                diagnostics.size(),
                countBySeverity(diagnostics, LspDiagnosticSeverity.ERROR),
                countBySeverity(diagnostics, LspDiagnosticSeverity.WARNING),
                countBySeverity(diagnostics, LspDiagnosticSeverity.INFORMATION),
                Instant.now()
        );
        
        history.add(record);
        
        // 限制历史记录大小
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
    }
    
    /**
     * 按严重性计数
     */
    private int countBySeverity(List<LspDiagnostic> diagnostics, LspDiagnosticSeverity severity) {
        int count = 0;
        for (LspDiagnostic diag : diagnostics) {
            if (diag.getSeverity() == severity) {
                count++;
            }
        }
        return count;
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 诊断事件类型枚举
     */
    public enum DiagnosticEventType {
        /** 诊断添加 */
        ADDED,
        /** 诊断移除 */
        REMOVED,
        /** 诊断清除 */
        CLEARED,
        /** 全部清除 */
        ALL_CLEARED
    }
    
    /**
     * 诊断事件类
     */
    public static class DiagnosticEvent {
        private final String uri;
        private final DiagnosticEventType type;
        private final List<LspDiagnostic> diagnostics;
        
        public DiagnosticEvent(String uri, DiagnosticEventType type, List<LspDiagnostic> diagnostics) {
            this.uri = uri;
            this.type = type;
            this.diagnostics = diagnostics;
        }
        
        public String getUri() {
            return uri;
        }
        
        public DiagnosticEventType getType() {
            return type;
        }
        
        public List<LspDiagnostic> getDiagnostics() {
            return diagnostics;
        }
    }
    
    /**
     * 诊断记录类
     */
    public static class DiagnosticRecord {
        private final String id;
        private final String uri;
        private final int total;
        private final int errors;
        private final int warnings;
        private final int informations;
        private final Instant timestamp;
        
        public DiagnosticRecord(String id, String uri, int total, int errors,
                               int warnings, int informations, Instant timestamp) {
            this.id = id;
            this.uri = uri;
            this.total = total;
            this.errors = errors;
            this.warnings = warnings;
            this.informations = informations;
            this.timestamp = timestamp;
        }
        
        public String getId() {
            return id;
        }
        
        public String getUri() {
            return uri;
        }
        
        public int getTotal() {
            return total;
        }
        
        public int getErrors() {
            return errors;
        }
        
        public int getWarnings() {
            return warnings;
        }
        
        public int getInformations() {
            return informations;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * LSP 诊断严重性枚举
     */
    public enum LspDiagnosticSeverity {
        /** 错误 */
        ERROR(1),
        /** 警告 */
        WARNING(2),
        /** 信息 */
        INFORMATION(3),
        /** 提示 */
        HINT(4);
        
        private final int value;
        
        LspDiagnosticSeverity(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static LspDiagnosticSeverity fromValue(int value) {
            for (LspDiagnosticSeverity severity : values()) {
                if (severity.value == value) {
                    return severity;
                }
            }
            return HINT;
        }
    }
    
    /**
     * LSP 诊断类
     */
    public static class LspDiagnostic {
        private final LspDiagnosticSeverity severity;
        private final LspRange range;
        private final String message;
        private final String source;
        private final String code;
        
        public LspDiagnostic(LspDiagnosticSeverity severity, LspRange range,
                            String message, String source, String code) {
            this.severity = severity;
            this.range = range;
            this.message = message;
            this.source = source;
            this.code = code;
        }
        
        public LspDiagnosticSeverity getSeverity() {
            return severity;
        }
        
        public LspRange getRange() {
            return range;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getSource() {
            return source;
        }
        
        public String getCode() {
            return code;
        }
        
        /**
         * 检查是否与另一个诊断相同
         */
        public boolean isSameAs(LspDiagnostic other) {
            if (other == null) return false;
            return this.severity == other.severity &&
                   this.range != null && this.range.equals(other.range) &&
                   Objects.equals(this.message, other.message) &&
                   Objects.equals(this.source, other.source);
        }
    }
    
    /**
     * LSP 范围类
     */
    public static class LspRange {
        private final LspPosition start;
        private final LspPosition end;
        
        public LspRange(LspPosition start, LspPosition end) {
            this.start = start;
            this.end = end;
        }
        
        public LspPosition getStart() {
            return start;
        }
        
        public LspPosition getEnd() {
            return end;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LspRange lspRange = (LspRange) o;
            return Objects.equals(start, lspRange.start) &&
                   Objects.equals(end, lspRange.end);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }
    }
    
    /**
     * LSP 位置类
     */
    public static class LspPosition {
        private final int line;
        private final int character;
        
        public LspPosition(int line, int character) {
            this.line = line;
            this.character = character;
        }
        
        public int getLine() {
            return line;
        }
        
        public int getCharacter() {
            return character;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LspPosition that = (LspPosition) o;
            return line == that.line && character == that.character;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(line, character);
        }
    }
}