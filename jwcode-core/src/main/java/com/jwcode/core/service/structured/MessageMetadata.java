package com.jwcode.core.service.structured;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息元数据
 * 
 * 用于结构化上下文管理，记录消息的额外信息
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class MessageMetadata {
    
    private static final AtomicInteger idGenerator = new AtomicInteger(0);
    
    private final String id;
    private MessageType type;
    private double importance;
    private int refCount;
    private final List<String> dependsOn;
    private final List<String> tags;
    private String taskId;
    private boolean isDroppable;
    private String dropReason;
    private Instant lastAccessed;
    private Instant creationTime;
    
    public MessageMetadata() {
        this.id = "meta_" + idGenerator.incrementAndGet();
        this.type = MessageType.ANSWER; // 默认类型
        this.importance = 0.5;
        this.refCount = 0;
        this.dependsOn = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.isDroppable = false;
        this.creationTime = Instant.now();
        this.lastAccessed = Instant.now();
    }
    
    public MessageMetadata(MessageType type) {
        this();
        this.type = type;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    
    public double getImportance() { return importance; }
    public void setImportance(double importance) { 
        this.importance = Math.max(0.0, Math.min(1.0, importance)); 
    }
    
    public int getRefCount() { return refCount; }
    public void incrementRefCount() { this.refCount++; }
    public void decrementRefCount() { 
        if (this.refCount > 0) this.refCount--; 
    }
    public void setRefCount(int refCount) { this.refCount = refCount; }
    
    public List<String> getDependsOn() { return dependsOn; }
    public void addDependency(String messageId) {
        if (messageId != null && !dependsOn.contains(messageId)) {
            dependsOn.add(messageId);
        }
    }
    public void removeDependency(String messageId) {
        dependsOn.remove(messageId);
    }
    
    public List<String> getTags() { return tags; }
    public void addTag(String tag) {
        if (tag != null && !tags.contains(tag)) {
            tags.add(tag);
        }
    }
    public void addTags(List<String> tags) {
        if (tags != null) {
            for (String tag : tags) {
                addTag(tag);
            }
        }
    }
    
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public boolean isDroppable() { return isDroppable; }
    public void setDroppable(boolean droppable) { this.isDroppable = droppable; }
    
    public String getDropReason() { return dropReason; }
    public void setDropReason(String dropReason) { this.dropReason = dropReason; }
    
    public Instant getLastAccessed() { return lastAccessed; }
    public void updateLastAccessed() { this.lastAccessed = Instant.now(); }
    
    public Instant getCreationTime() { return creationTime; }
    
    /**
     * 克隆元数据（用于消息复制时）
     */
    public MessageMetadata clone() {
        MessageMetadata cloned = new MessageMetadata(this.type);
        cloned.importance = this.importance;
        cloned.dependsOn.addAll(this.dependsOn);
        cloned.tags.addAll(this.tags);
        cloned.taskId = this.taskId;
        return cloned;
    }
    
    @Override
    public String toString() {
        return String.format(
            "MessageMetadata{id=%s, type=%s, importance=%.2f, refCount=%d, tags=%s, taskId=%s, droppable=%s}",
            id, type, importance, refCount, tags, taskId, isDroppable
        );
    }
}