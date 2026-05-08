package com.jwcode.core.a2a.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TaskOutput — A2A 任务执行输出。
 *
 * <p>包含任务执行的结果数据、文件变更、消息日志等。</p>
 */
public class TaskOutput {

    /** 输出摘要 */
    private final String summary;

    /** 输出数据（键值对） */
    private final Map<String, Object> data;

    /** 文件变更列表 */
    private final List<FileChange> fileChanges;

    /** 消息日志 */
    private final List<String> messages;

    public TaskOutput(String summary, Map<String, Object> data,
                      List<FileChange> fileChanges, List<String> messages) {
        this.summary = summary;
        this.data = data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
        this.fileChanges = fileChanges != null ? Collections.unmodifiableList(fileChanges) : Collections.emptyList();
        this.messages = messages != null ? Collections.unmodifiableList(messages) : Collections.emptyList();
    }

    // ==================== Getters ====================

    public String getSummary() { return summary; }
    public Map<String, Object> getData() { return data; }
    public List<FileChange> getFileChanges() { return fileChanges; }
    public List<String> getMessages() { return messages; }

    // ==================== 内部类 ====================

    public static class FileChange {
        public enum Operation { ADDED, MODIFIED, DELETED }

        private final Operation operation;
        private final String filePath;
        private final int linesAdded;
        private final int linesDeleted;

        public FileChange(Operation operation, String filePath,
                          int linesAdded, int linesDeleted) {
            this.operation = operation;
            this.filePath = filePath;
            this.linesAdded = linesAdded;
            this.linesDeleted = linesDeleted;
        }

        public Operation getOperation() { return operation; }
        public String getFilePath() { return filePath; }
        public int getLinesAdded() { return linesAdded; }
        public int getLinesDeleted() { return linesDeleted; }
    }

    // ==================== 状态判断 ====================

    /**
     * 判断任务是否成功（summary 不为空且不以 "Task failed" 或 "Task timeout" 开头）
     */
    public boolean isSuccess() {
        return summary != null && !summary.isEmpty()
            && !summary.startsWith("Task failed")
            && !summary.startsWith("Task timeout");
    }

    // ==================== Factory ====================

    public static TaskOutput success(String summary) {
        return new TaskOutput(summary, Collections.emptyMap(),
            Collections.emptyList(), Collections.emptyList());
    }

    public static TaskOutput success(String summary, Map<String, Object> data) {
        return new TaskOutput(summary, data,
            Collections.emptyList(), Collections.emptyList());
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String summary;
        private Map<String, Object> data;
        private List<FileChange> fileChanges;
        private List<String> messages;

        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder data(Map<String, Object> data) { this.data = data; return this; }
        public Builder fileChanges(List<FileChange> fileChanges) { this.fileChanges = fileChanges; return this; }
        public Builder messages(List<String> messages) { this.messages = messages; return this; }

        public TaskOutput build() {
            return new TaskOutput(summary, data, fileChanges, messages);
        }
    }
}
