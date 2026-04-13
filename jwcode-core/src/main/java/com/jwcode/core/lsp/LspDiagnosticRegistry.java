package com.jwcode.core.lsp;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * LspDiagnosticRegistry - LSP 诊断注册表
 * 
 * 功能说明：
 * 管理 LSP（Language Server Protocol）诊断信息。
 * 处理语言服务器发送的诊断（错误、警告、提示等），
 * 支持诊断订阅、过滤、聚合和快速修复建议。
 * 
 * 核心特性：
 * - 诊断信息管理
 * - 按文件 URI 组织诊断
 * - 诊断严重性过滤（错误/警告/信息/提示）
 * - 诊断订阅/发布
 * - 诊断历史记录
 * - 快速修复建议
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
    
    private static final Logger LOGGER = Logger.getLogger(LspDiagnosticRegistry.class.getName());
    
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
     * 快速修复映射表
     */
    private final Map<String, List<QuickFix>> quickFixesByUri;
    
    /**
     * 构造函数
     */
    public LspDiagnosticRegistry() {
        this.diagnosticsByUri = new ConcurrentHashMap<>();
        this.subscribers = new CopyOnWriteArrayList<>();
        this.history = new ArrayList<>();
        this.quickFixesByUri = new ConcurrentHashMap<>();
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
        
        LOGGER.fine(String.format("Updated diagnostics for %s: %d total, %d added, %d removed",
            uri, diagnostics.size(), added.size(), removed.size()));
    }
    
    /**
     * 清除文件的诊断
     * 
     * @param uri 文件 URI
     */
    public void clearDiagnostics(String uri) {
        List<LspDiagnostic> removed = diagnosticsByUri.remove(uri);
        quickFixesByUri.remove(uri);
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
        
        return diagnostics.stream()
            .filter(d -> d.getSeverity() != null && 
                        d.getSeverity().getValue() <= minSeverity.getValue())
            .collect(Collectors.toList());
    }
    
    /**
     * 获取文件的诊断（按范围过滤）
     * 
     * @param uri 文件 URI
     * @param line 行号
     * @return 诊断列表
     */
    public List<LspDiagnostic> getDiagnosticsAtLine(String uri, int line) {
        List<LspDiagnostic> diagnostics = diagnosticsByUri.get(uri);
        if (diagnostics == null) {
            return new ArrayList<>();
        }
        
        return diagnostics.stream()
            .filter(d -> d.getRange() != null &&
                        d.getRange().getStart().getLine() <= line &&
                        d.getRange().getEnd().getLine() >= line)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取指定位置的诊断
     * 
     * @param uri 文件 URI
     * @param line 行号
     * @param column 列号
     * @return 诊断列表
     */
    public List<LspDiagnostic> getDiagnosticsAtPosition(String uri, int line, int column) {
        List<LspDiagnostic> diagnostics = diagnosticsByUri.get(uri);
        if (diagnostics == null) {
            return new ArrayList<>();
        }
        
        return diagnostics.stream()
            .filter(d -> d.getRange() != null &&
                        d.getRange().contains(line, column))
            .collect(Collectors.toList());
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
     * 获取所有诊断（按严重性过滤）
     * 
     * @param minSeverity 最小严重性
     * @return 所有文件的诊断映射
     */
    public Map<String, List<LspDiagnostic>> getAllDiagnostics(LspDiagnosticSeverity minSeverity) {
        Map<String, List<LspDiagnostic>> result = new HashMap<>();
        for (Map.Entry<String, List<LspDiagnostic>> entry : diagnosticsByUri.entrySet()) {
            List<LspDiagnostic> filtered = entry.getValue().stream()
                .filter(d -> d.getSeverity() != null && 
                            d.getSeverity().getValue() <= minSeverity.getValue())
                .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                result.put(entry.getKey(), filtered);
            }
        }
        return result;
    }
    
    /**
     * 获取所有文件的诊断总数
     * 
     * @return 诊断总数
     */
    public int getTotalDiagnosticCount() {
        return diagnosticsByUri.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    /**
     * 获取按严重性统计的诊断数量
     * 
     * @param severity 严重性
     * @return 诊断数量
     */
    public int getDiagnosticCountBySeverity(LspDiagnosticSeverity severity) {
        return diagnosticsByUri.values().stream()
            .flatMap(List::stream)
            .filter(d -> d.getSeverity() == severity)
            .mapToInt(d -> 1)
            .sum();
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
     * 获取信息数量
     * 
     * @return 信息数量
     */
    public int getInformationCount() {
        return getDiagnosticCountBySeverity(LspDiagnosticSeverity.INFORMATION);
    }
    
    /**
     * 获取提示数量
     * 
     * @return 提示数量
     */
    public int getHintCount() {
        return getDiagnosticCountBySeverity(LspDiagnosticSeverity.HINT);
    }
    
    /**
     * 获取诊断统计信息
     * 
     * @return 诊断统计
     */
    public DiagnosticStats getStats() {
        return new DiagnosticStats(
            getTotalDiagnosticCount(),
            getErrorCount(),
            getWarningCount(),
            getInformationCount(),
            getHintCount()
        );
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
        quickFixesByUri.clear();
        notifySubscribers(new DiagnosticEvent(null, DiagnosticEventType.ALL_CLEARED, new ArrayList<>()));
    }
    
    /**
     * 添加快速修复
     * 
     * @param uri 文件 URI
     * @param diagnosticId 诊断 ID
     * @param quickFix 快速修复
     */
    public void addQuickFix(String uri, String diagnosticId, QuickFix quickFix) {
        quickFixesByUri.computeIfAbsent(uri + "#" + diagnosticId, k -> new ArrayList<>())
                       .add(quickFix);
    }
    
    /**
     * 获取快速修复
     * 
     * @param uri 文件 URI
     * @param diagnosticId 诊断 ID
     * @return 快速修复列表
     */
    public List<QuickFix> getQuickFixes(String uri, String diagnosticId) {
        return quickFixesByUri.getOrDefault(uri + "#" + diagnosticId, new ArrayList<>());
    }
    
    /**
     * 获取诊断的快速修复
     * 
     * @param diagnostic 诊断
     * @return 快速修复列表
     */
    public List<QuickFix> getQuickFixesForDiagnostic(LspDiagnostic diagnostic) {
        if (diagnostic == null || diagnostic.getQuickFixes().isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(diagnostic.getQuickFixes());
    }
    
    /**
     * 清除快速修复
     * 
     * @param uri 文件 URI
     */
    public void clearQuickFixes(String uri) {
        quickFixesByUri.entrySet().removeIf(e -> e.getKey().startsWith(uri + "#"));
    }
    
    /**
     * 通知订阅者
     */
    private void notifySubscribers(DiagnosticEvent event) {
        for (Consumer<DiagnosticEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Diagnostic subscriber error", e);
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
            countBySeverity(diagnostics, LspDiagnosticSeverity.HINT),
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
        return (int) diagnostics.stream()
            .filter(d -> d.getSeverity() == severity)
            .count();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 诊断统计信息
     */
    public static class DiagnosticStats {
        private final int total;
        private final int errors;
        private final int warnings;
        private final int informations;
        private final int hints;
        
        public DiagnosticStats(int total, int errors, int warnings, 
                              int informations, int hints) {
            this.total = total;
            this.errors = errors;
            this.warnings = warnings;
            this.informations = informations;
            this.hints = hints;
        }
        
        public int getTotal() { return total; }
        public int getErrors() { return errors; }
        public int getWarnings() { return warnings; }
        public int getInformations() { return informations; }
        public int getHints() { return hints; }
        
        public boolean hasErrors() { return errors > 0; }
        public boolean hasWarnings() { return warnings > 0; }
    }
    
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
        private final Instant timestamp;
        
        public DiagnosticEvent(String uri, DiagnosticEventType type, List<LspDiagnostic> diagnostics) {
            this.uri = uri;
            this.type = type;
            this.diagnostics = diagnostics;
            this.timestamp = Instant.now();
        }
        
        public String getUri() { return uri; }
        public DiagnosticEventType getType() { return type; }
        public List<LspDiagnostic> getDiagnostics() { return diagnostics; }
        public Instant getTimestamp() { return timestamp; }
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
        private final int hints;
        private final Instant timestamp;
        
        public DiagnosticRecord(String id, String uri, int total, int errors,
                               int warnings, int informations, int hints, Instant timestamp) {
            this.id = id;
            this.uri = uri;
            this.total = total;
            this.errors = errors;
            this.warnings = warnings;
            this.informations = informations;
            this.hints = hints;
            this.timestamp = timestamp;
        }
        
        public String getId() { return id; }
        public String getUri() { return uri; }
        public int getTotal() { return total; }
        public int getErrors() { return errors; }
        public int getWarnings() { return warnings; }
        public int getInformations() { return informations; }
        public int getHints() { return hints; }
        public Instant getTimestamp() { return timestamp; }
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
        
        public int getValue() { return value; }
        
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
        private final String id;
        private final LspDiagnosticSeverity severity;
        private final LspRange range;
        private final String message;
        private final String source;
        private final String code;
        private final List<QuickFix> quickFixes;
        
        public LspDiagnostic(LspDiagnosticSeverity severity, LspRange range,
                            String message, String source, String code) {
            this(UUID.randomUUID().toString(), severity, range, message, source, code);
        }
        
        public LspDiagnostic(String id, LspDiagnosticSeverity severity, LspRange range,
                            String message, String source, String code) {
            this.id = id;
            this.severity = severity;
            this.range = range;
            this.message = message;
            this.source = source;
            this.code = code;
            this.quickFixes = new ArrayList<>();
        }
        
        public String getId() { return id; }
        public LspDiagnosticSeverity getSeverity() { return severity; }
        public LspRange getRange() { return range; }
        public String getMessage() { return message; }
        public String getSource() { return source; }
        public String getCode() { return code; }
        public List<QuickFix> getQuickFixes() { return quickFixes; }
        
        public void addQuickFix(QuickFix quickFix) {
            quickFixes.add(quickFix);
        }
        
        public boolean hasQuickFixes() {
            return !quickFixes.isEmpty();
        }
        
        /**
         * 检查是否与另一个诊断相同
         */
        public boolean isSameAs(LspDiagnostic other) {
            if (other == null) return false;
            return this.severity == other.severity &&
                   Objects.equals(this.range, other.range) &&
                   Objects.equals(this.message, other.message) &&
                   Objects.equals(this.source, other.source) &&
                   Objects.equals(this.code, other.code);
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s", severity, source, message);
        }
    }
    
    /**
     * 快速修复类
     */
    public static class QuickFix {
        private final String id;
        private final String title;
        private final String kind;
        private final List<LspTextEdit> edits;
        private final boolean isPreferred;
        private final String diagnosticId;
        
        public QuickFix(String title, String kind, List<LspTextEdit> edits) {
            this(UUID.randomUUID().toString(), title, kind, edits, false, null);
        }
        
        public QuickFix(String id, String title, String kind, List<LspTextEdit> edits,
                       boolean isPreferred, String diagnosticId) {
            this.id = id;
            this.title = title;
            this.kind = kind;
            this.edits = edits != null ? edits : new ArrayList<>();
            this.isPreferred = isPreferred;
            this.diagnosticId = diagnosticId;
        }
        
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getKind() { return kind; }
        public List<LspTextEdit> getEdits() { return edits; }
        public boolean isPreferred() { return isPreferred; }
        public String getDiagnosticId() { return diagnosticId; }
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
        
        public LspPosition getStart() { return start; }
        public LspPosition getEnd() { return end; }
        
        /**
         * 检查位置是否在范围内
         */
        public boolean contains(int line, int column) {
            if (line < start.getLine() || line > end.getLine()) {
                return false;
            }
            if (line == start.getLine() && column < start.getCharacter()) {
                return false;
            }
            if (line == end.getLine() && column > end.getCharacter()) {
                return false;
            }
            return true;
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
        
        @Override
        public String toString() {
            return String.format("[%d:%d - %d:%d]", 
                start.getLine(), start.getCharacter(),
                end.getLine(), end.getCharacter());
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
        
        public int getLine() { return line; }
        public int getCharacter() { return character; }
        
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
