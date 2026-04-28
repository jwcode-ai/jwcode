package com.jwcode.core.service.structured;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 任务上下文
 * 
 * 用于跟踪和管理用户会话中的任务上下文
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskContext {
    
    private final String taskId;
    private String originalIntent;
    private final List<String> keyMessageIds;
    private TaskStatus status;
    private String result;
    private final Instant startTime;
    private Instant endTime;
    
    public TaskContext() {
        this.taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        this.keyMessageIds = new ArrayList<>();
        this.status = TaskStatus.ACTIVE;
        this.startTime = Instant.now();
    }
    
    public TaskContext(String originalIntent) {
        this();
        this.originalIntent = originalIntent;
    }
    
    public TaskContext(String taskId, String originalIntent) {
        this.taskId = taskId;
        this.originalIntent = originalIntent;
        this.keyMessageIds = new ArrayList<>();
        this.status = TaskStatus.ACTIVE;
        this.startTime = Instant.now();
    }
    
    // Getters and Setters
    public String getTaskId() { return taskId; }
    
    public String getOriginalIntent() { return originalIntent; }
    public void setOriginalIntent(String originalIntent) { this.originalIntent = originalIntent; }
    
    public List<String> getKeyMessageIds() { return keyMessageIds; }
    public void addKeyMessageId(String messageId) {
        if (messageId != null && !keyMessageIds.contains(messageId)) {
            keyMessageIds.add(messageId);
        }
    }
    public void removeKeyMessageId(String messageId) {
        keyMessageIds.remove(messageId);
    }
    
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { 
        this.status = status; 
        if (status == TaskStatus.COMPLETED || status == TaskStatus.ABANDONED) {
            this.endTime = Instant.now();
        }
    }
    
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    
    public Instant getStartTime() { return startTime; }
    
    public Instant getEndTime() { return endTime; }
    
    /**
     * 检查任务是否仍在进行
     */
    public boolean isActive() {
        return status == TaskStatus.ACTIVE;
    }
    
    /**
     * 获取任务持续时间（毫秒）
     */
    public long getDurationMs() {
        Instant end = endTime != null ? endTime : Instant.now();
        return end.toEpochMilli() - startTime.toEpochMilli();
    }
    
    @Override
    public String toString() {
        return String.format(
            "TaskContext{taskId=%s, originalIntent=%s, status=%s, keyMessages=%d, startTime=%s}",
            taskId, 
            originalIntent != null ? originalIntent.substring(0, Math.min(30, originalIntent.length())) + "..." : null,
            status, 
            keyMessageIds.size(),
            startTime
        );
    }
}