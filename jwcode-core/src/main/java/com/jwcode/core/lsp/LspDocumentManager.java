package com.jwcode.core.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LspDocumentManager - LSP 文档同步管理器
 * 
 * 功能说明：
 * 管理打开的文本文档，处理文档的增量/全量内容同步，维护文档版本控制。
 * 确保编辑器内容与语言服务器保持同步，支持高效的增量更新。
 * 
 * 核心特性：
 * - 管理打开的文件
 * - 增量内容同步（TextDocumentContentChangeEvent）
 * - 全量内容同步（didOpen/didClose）
 * - 版本控制（乐观并发控制）
 * - 文档变更监听
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspDocumentManager {
    
    private static final Logger LOGGER = Logger.getLogger(LspDocumentManager.class.getName());
    
    /**
     * 文档版本管理器
     */
    private final Map<String, DocumentVersion> documentVersions;
    
    /**
     * 文档内容缓存
     */
    private final Map<String, String> documentContents;
    
    /**
     * 打开状态追踪
     */
    private final Map<String, DocumentState> documentStates;
    
    /**
     * 文档变更监听器
     */
    private final List<DocumentChangeListener> changeListeners;
    
    /**
     * 全局版本计数器
     */
    private final AtomicInteger globalVersionCounter;
    
    /**
     * 文档同步类型
     */
    public enum SyncKind {
        /** 无同步 */
        NONE(0),
        /** 全量同步 */
        FULL(1),
        /** 增量同步 */
        INCREMENTAL(2);
        
        private final int value;
        
        SyncKind(int value) {
            this.value = value;
        }
        
        public int getValue() { return value; }
        
        public static SyncKind fromValue(int value) {
            for (SyncKind kind : values()) {
                if (kind.value == value) {
                    return kind;
                }
            }
            return FULL;
        }
    }
    
    /**
     * 文档状态
     */
    public enum DocumentState {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING,
        ERROR
    }
    
    /**
     * 文档信息
     */
    public static class DocumentInfo {
        private final String uri;
        private final String path;
        private final String languageId;
        private final int version;
        private final String content;
        private final long lastModified;
        private final int lineCount;
        
        public DocumentInfo(String uri, String path, String languageId, int version,
                           String content, long lastModified, int lineCount) {
            this.uri = uri;
            this.path = path;
            this.languageId = languageId;
            this.version = version;
            this.content = content;
            this.lastModified = lastModified;
            this.lineCount = lineCount;
        }
        
        public String getUri() { return uri; }
        public String getPath() { return path; }
        public String getLanguageId() { return languageId; }
        public int getVersion() { return version; }
        public String getContent() { return content; }
        public long getLastModified() { return lastModified; }
        public int getLineCount() { return lineCount; }
    }
    
    /**
     * 文档版本
     */
    public static class DocumentVersion {
        private final String uri;
        private volatile int version;
        private volatile long lastModified;
        private final List<DocumentChange> changeHistory;
        private static final int MAX_HISTORY_SIZE = 100;
        
        public DocumentVersion(String uri, int initialVersion) {
            this.uri = uri;
            this.version = initialVersion;
            this.lastModified = System.currentTimeMillis();
            this.changeHistory = new ArrayList<>();
        }
        
        public String getUri() { return uri; }
        public int getVersion() { return version; }
        public long getLastModified() { return lastModified; }
        
        public synchronized int increment() {
            version++;
            lastModified = System.currentTimeMillis();
            return version;
        }
        
        public synchronized void recordChange(DocumentChange change) {
            changeHistory.add(change);
            while (changeHistory.size() > MAX_HISTORY_SIZE) {
                changeHistory.remove(0);
            }
        }
        
        public List<DocumentChange> getChangeHistory() {
            return new ArrayList<>(changeHistory);
        }
    }
    
    /**
     * 文档变更
     */
    public static class DocumentChange {
        private final int fromVersion;
        private final int toVersion;
        private final List<TextDocumentContentChangeEvent> changes;
        private final long timestamp;
        
        public DocumentChange(int fromVersion, int toVersion, 
                             List<TextDocumentContentChangeEvent> changes) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.changes = changes;
            this.timestamp = System.currentTimeMillis();
        }
        
        public int getFromVersion() { return fromVersion; }
        public int getToVersion() { return toVersion; }
        public List<TextDocumentContentChangeEvent> getChanges() { return changes; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 文本内容变更事件
     */
    public static class TextDocumentContentChangeEvent {
        private final LspRange range;
        private final Integer rangeLength;
        private final String text;
        
        public TextDocumentContentChangeEvent(LspRange range, Integer rangeLength, String text) {
            this.range = range;
            this.rangeLength = rangeLength;
            this.text = text;
        }
        
        /**
         * 全量更新
         */
        public static TextDocumentContentChangeEvent full(String text) {
            return new TextDocumentContentChangeEvent(null, null, text);
        }
        
        /**
         * 增量更新
         */
        public static TextDocumentContentChangeEvent incremental(LspRange range, String text) {
            return new TextDocumentContentChangeEvent(range, null, text);
        }
        
        public boolean isFull() { return range == null; }
        public LspRange getRange() { return range; }
        public Integer getRangeLength() { return rangeLength; }
        public String getText() { return text; }
    }
    
    /**
     * 文档变更监听器
     */
    @FunctionalInterface
    public interface DocumentChangeListener {
        void onDocumentChanged(String uri, DocumentChangeEvent event);
    }
    
    /**
     * 文档变更事件
     */
    public static class DocumentChangeEvent {
        private final String uri;
        private final DocumentState oldState;
        private final DocumentState newState;
        private final int version;
        private final List<TextDocumentContentChangeEvent> changes;
        
        public DocumentChangeEvent(String uri, DocumentState oldState, DocumentState newState,
                                   int version, List<TextDocumentContentChangeEvent> changes) {
            this.uri = uri;
            this.oldState = oldState;
            this.newState = newState;
            this.version = version;
            this.changes = changes;
        }
        
        public String getUri() { return uri; }
        public DocumentState getOldState() { return oldState; }
        public DocumentState getNewState() { return newState; }
        public int getVersion() { return version; }
        public List<TextDocumentContentChangeEvent> getChanges() { return changes; }
        
        public boolean isOpenEvent() {
            return oldState == DocumentState.CLOSED && newState == DocumentState.OPEN;
        }
        
        public boolean isCloseEvent() {
            return oldState == DocumentState.OPEN && newState == DocumentState.CLOSED;
        }
        
        public boolean isChangeEvent() {
            return oldState == DocumentState.OPEN && newState == DocumentState.OPEN;
        }
    }
    
    public LspDocumentManager() {
        this.documentVersions = new ConcurrentHashMap<>();
        this.documentContents = new ConcurrentHashMap<>();
        this.documentStates = new ConcurrentHashMap<>();
        this.changeListeners = new ArrayList<>();
        this.globalVersionCounter = new AtomicInteger(0);
    }
    
    /**
     * 添加文档变更监听器
     */
    public void addChangeListener(DocumentChangeListener listener) {
        changeListeners.add(listener);
    }
    
    /**
     * 移除文档变更监听器
     */
    public void removeChangeListener(DocumentChangeListener listener) {
        changeListeners.remove(listener);
    }
    
    /**
     * 打开文档
     * 
     * @param uri 文档 URI
     * @param content 文档内容
     * @param languageId 语言 ID
     * @return 文档版本
     */
    public int openDocument(String uri, String content, String languageId) {
        return openDocument(uri, content, languageId, getNextVersion());
    }
    
    /**
     * 打开文档（指定版本）
     * 
     * @param uri 文档 URI
     * @param content 文档内容
     * @param languageId 语言 ID
     * @param version 初始版本
     * @return 文档版本
     */
    public int openDocument(String uri, String content, String languageId, int version) {
        DocumentState oldState = documentStates.getOrDefault(uri, DocumentState.CLOSED);
        
        documentStates.put(uri, DocumentState.OPENING);
        
        try {
            documentVersions.put(uri, new DocumentVersion(uri, version));
            documentContents.put(uri, content != null ? content : "");
            documentStates.put(uri, DocumentState.OPEN);
            
            LOGGER.fine(String.format("Opened document: %s (version: %d)", uri, version));
            
            // 通知监听器
            notifyListeners(new DocumentChangeEvent(
                uri, oldState, DocumentState.OPEN, version, null));
            
            return version;
            
        } catch (Exception e) {
            documentStates.put(uri, DocumentState.ERROR);
            LOGGER.log(Level.SEVERE, "Failed to open document: " + uri, e);
            throw e;
        }
    }
    
    /**
     * 从文件打开文档
     * 
     * @param path 文件路径
     * @param languageId 语言 ID
     * @return 文档版本
     */
    public int openDocumentFromFile(String path, String languageId) throws IOException {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + path);
        }
        
        String content = Files.readString(filePath);
        String uri = filePath.toUri().toString();
        
        return openDocument(uri, content, languageId);
    }
    
    /**
     * 关闭文档
     * 
     * @param uri 文档 URI
     */
    public void closeDocument(String uri) {
        DocumentState oldState = documentStates.getOrDefault(uri, DocumentState.CLOSED);
        
        if (oldState != DocumentState.OPEN) {
            LOGGER.warning("Attempting to close document that is not open: " + uri);
            return;
        }
        
        documentStates.put(uri, DocumentState.CLOSING);
        
        try {
            DocumentVersion version = documentVersions.remove(uri);
            documentContents.remove(uri);
            documentStates.remove(uri);
            
            LOGGER.fine("Closed document: " + uri);
            
            // 通知监听器
            int versionNumber = version != null ? version.getVersion() : 0;
            notifyListeners(new DocumentChangeEvent(
                uri, oldState, DocumentState.CLOSED, versionNumber, null));
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error closing document: " + uri, e);
        }
    }
    
    /**
     * 更新文档内容（全量更新）
     * 
     * @param uri 文档 URI
     * @param content 新内容
     * @return 新版本号
     */
    public int updateDocument(String uri, String content) {
        return updateDocument(uri, content, null);
    }
    
    /**
     * 更新文档内容（指定范围）
     * 
     * @param uri 文档 URI
     * @param content 新内容
     * @param range 变更范围（null 表示全量更新）
     * @return 新版本号
     */
    public int updateDocument(String uri, String content, LspRange range) {
        DocumentVersion version = documentVersions.get(uri);
        if (version == null) {
            throw new IllegalStateException("Document not open: " + uri);
        }
        
        DocumentState state = documentStates.get(uri);
        if (state != DocumentState.OPEN) {
            throw new IllegalStateException("Document not in OPEN state: " + uri);
        }
        
        int oldVersion = version.getVersion();
        int newVersion = version.increment();
        
        // 应用变更
        String oldContent = documentContents.get(uri);
        String newContent;
        
        if (range == null) {
            // 全量更新
            newContent = content;
            documentContents.put(uri, content);
        } else {
            // 增量更新
            newContent = applyIncrementalChange(oldContent, range, content);
            documentContents.put(uri, newContent);
        }
        
        // 记录变更
        TextDocumentContentChangeEvent change = range == null
            ? TextDocumentContentChangeEvent.full(content)
            : TextDocumentContentChangeEvent.incremental(range, content);
        
        version.recordChange(new DocumentChange(oldVersion, newVersion, 
            Collections.singletonList(change)));
        
        LOGGER.fine(String.format("Updated document: %s (version: %d -> %d)", 
            uri, oldVersion, newVersion));
        
        // 通知监听器
        notifyListeners(new DocumentChangeEvent(
            uri, state, state, newVersion, Collections.singletonList(change)));
        
        return newVersion;
    }
    
    /**
     * 批量更新文档内容
     * 
     * @param uri 文档 URI
     * @param changes 变更列表
     * @return 新版本号
     */
    public int updateDocumentBatch(String uri, List<TextDocumentContentChangeEvent> changes) {
        DocumentVersion version = documentVersions.get(uri);
        if (version == null) {
            throw new IllegalStateException("Document not open: " + uri);
        }
        
        DocumentState state = documentStates.get(uri);
        if (state != DocumentState.OPEN) {
            throw new IllegalStateException("Document not in OPEN state: " + uri);
        }
        
        int oldVersion = version.getVersion();
        int newVersion = version.increment();
        
        // 按顺序应用所有变更
        String content = documentContents.get(uri);
        for (TextDocumentContentChangeEvent change : changes) {
            if (change.isFull()) {
                content = change.getText();
            } else {
                content = applyIncrementalChange(content, change.getRange(), change.getText());
            }
        }
        
        documentContents.put(uri, content);
        
        // 记录变更
        version.recordChange(new DocumentChange(oldVersion, newVersion, changes));
        
        LOGGER.fine(String.format("Batch updated document: %s (version: %d -> %d, changes: %d)", 
            uri, oldVersion, newVersion, changes.size()));
        
        // 通知监听器
        notifyListeners(new DocumentChangeEvent(
            uri, state, state, newVersion, changes));
        
        return newVersion;
    }
    
    /**
     * 获取文档内容
     * 
     * @param uri 文档 URI
     * @return 文档内容
     */
    public String getDocumentContent(String uri) {
        return documentContents.get(uri);
    }
    
    /**
     * 获取文档版本
     * 
     * @param uri 文档 URI
     * @return 版本号，如果文档未打开返回 -1
     */
    public int getDocumentVersion(String uri) {
        DocumentVersion version = documentVersions.get(uri);
        return version != null ? version.getVersion() : -1;
    }
    
    /**
     * 获取文档状态
     * 
     * @param uri 文档 URI
     * @return 文档状态
     */
    public DocumentState getDocumentState(String uri) {
        return documentStates.getOrDefault(uri, DocumentState.CLOSED);
    }
    
    /**
     * 检查文档是否打开
     * 
     * @param uri 文档 URI
     * @return true 如果文档已打开
     */
    public boolean isDocumentOpen(String uri) {
        return documentStates.get(uri) == DocumentState.OPEN;
    }
    
    /**
     * 获取所有打开的文档 URI
     * 
     * @return 打开的文档 URI 列表
     */
    public List<String> getOpenDocuments() {
        return documentStates.entrySet().stream()
            .filter(e -> e.getValue() == DocumentState.OPEN)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取文档信息
     * 
     * @param uri 文档 URI
     * @return 文档信息
     */
    public DocumentInfo getDocumentInfo(String uri) {
        DocumentVersion version = documentVersions.get(uri);
        String content = documentContents.get(uri);
        
        if (version == null || content == null) {
            return null;
        }
        
        Path path = uriToPath(uri);
        String languageId = detectLanguageId(uri);
        int lineCount = content.split("\r?\n", -1).length;
        
        return new DocumentInfo(
            uri,
            path != null ? path.toString() : uri,
            languageId,
            version.getVersion(),
            content,
            version.getLastModified(),
            lineCount
        );
    }
    
    /**
     * 获取所有打开的文档信息
     * 
     * @return 文档信息列表
     */
    public List<DocumentInfo> getAllDocumentInfos() {
        List<DocumentInfo> infos = new ArrayList<>();
        for (String uri : getOpenDocuments()) {
            DocumentInfo info = getDocumentInfo(uri);
            if (info != null) {
                infos.add(info);
            }
        }
        return infos;
    }
    
    /**
     * 关闭所有文档
     */
    public void closeAllDocuments() {
        for (String uri : new ArrayList<>(documentStates.keySet())) {
            closeDocument(uri);
        }
    }
    
    /**
     * 获取文档变更历史
     * 
     * @param uri 文档 URI
     * @return 变更历史列表
     */
    public List<DocumentChange> getDocumentHistory(String uri) {
        DocumentVersion version = documentVersions.get(uri);
        return version != null ? version.getChangeHistory() : new ArrayList<>();
    }
    
    /**
     * 应用增量变更
     */
    private String applyIncrementalChange(String content, LspRange range, String newText) {
        String[] lines = content.split("\r?\n", -1);
        
        int startLine = range.getStart().getLine();
        int startChar = range.getStart().getCharacter();
        int endLine = range.getEnd().getLine();
        int endChar = range.getEnd().getCharacter();
        
        // 验证范围
        if (startLine < 0 || startLine >= lines.length ||
            endLine < 0 || endLine >= lines.length) {
            LOGGER.warning("Invalid range for incremental change: " + range);
            return content;
        }
        
        StringBuilder result = new StringBuilder();
        
        // 添加开始行之前的内容
        for (int i = 0; i < startLine; i++) {
            result.append(lines[i]).append("\n");
        }
        
        // 添加开始行被修改前的部分
        String startLineContent = lines[startLine];
        if (startChar <= startLineContent.length()) {
            result.append(startLineContent, 0, startChar);
        }
        
        // 添加新文本
        result.append(newText);
        
        // 添加结束行被修改后的部分
        if (endLine < lines.length) {
            String endLineContent = lines[endLine];
            if (endChar <= endLineContent.length()) {
                result.append(endLineContent.substring(endChar));
            }
        }
        
        // 添加结束行之后的内容
        for (int i = endLine + 1; i < lines.length; i++) {
            result.append("\n").append(lines[i]);
        }
        
        return result.toString();
    }
    
    /**
     * 获取下一个版本号
     */
    private int getNextVersion() {
        return globalVersionCounter.incrementAndGet();
    }
    
    /**
     * 通知监听器
     */
    private void notifyListeners(DocumentChangeEvent event) {
        for (DocumentChangeListener listener : changeListeners) {
            try {
                listener.onDocumentChanged(event.getUri(), event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Document change listener error", e);
            }
        }
    }
    
    /**
     * URI 转路径
     */
    private Path uriToPath(String uri) {
        try {
            return Paths.get(java.net.URI.create(uri));
        } catch (Exception e) {
            return Paths.get(uri);
        }
    }
    
    /**
     * 检测语言 ID
     */
    private String detectLanguageId(String uri) {
        String path = uri.toLowerCase();
        if (path.endsWith(".java")) return "java";
        if (path.endsWith(".js")) return "javascript";
        if (path.endsWith(".ts")) return "typescript";
        if (path.endsWith(".py")) return "python";
        if (path.endsWith(".rs")) return "rust";
        if (path.endsWith(".go")) return "go";
        if (path.endsWith(".cpp") || path.endsWith(".cc") || path.endsWith(".h")) return "cpp";
        if (path.endsWith(".c")) return "c";
        if (path.endsWith(".cs")) return "csharp";
        if (path.endsWith(".rb")) return "ruby";
        if (path.endsWith(".php")) return "php";
        if (path.endsWith(".swift")) return "swift";
        if (path.endsWith(".kt")) return "kotlin";
        if (path.endsWith(".scala")) return "scala";
        return "plaintext";
    }
}
