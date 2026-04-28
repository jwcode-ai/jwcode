package com.jwcode.core.service.structured;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 结构化消息
 * 
 * 带有元数据的对话消息，用于结构化上下文管理
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class StructuredMessage {
    
    private final String id;
    private final String role;
    private final String content;
    private final MessageMetadata metadata;
    private final Instant timestamp;
    private final String toolUseId;
    private final String toolName;
    
    public StructuredMessage(String role, String content) {
        this(role, content, null);
    }
    
    public StructuredMessage(String role, String content, MessageMetadata metadata) {
        this(UUID.randomUUID().toString(), role, content, metadata, null, null, null);
    }
    
    public StructuredMessage(String id, String role, String content, MessageMetadata metadata, 
                   String toolUseId, String toolName, Instant timestamp) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.metadata = metadata != null ? metadata : new MessageMetadata();
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.toolUseId = toolUseId;
        this.toolName = toolName;
    }
    
    // Getters
    public String getId() { return id; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public MessageMetadata getMetadata() { return metadata; }
    public Instant getTimestamp() { return timestamp; }
    public String getToolUseId() { return toolUseId; }
    public String getToolName() { return toolName; }
    
    /**
     * 获取消息类型
     */
    public MessageType getType() {
        return metadata.getType();
    }
    
    /**
     * 设置消息类型
     */
    public void setType(MessageType type) {
        metadata.setType(type);
    }
    
    /**
     * 获取引用计数
     */
    public int getRefCount() {
        return metadata.getRefCount();
    }
    
    /**
     * 增加引用计数
     */
    public void incrementRefCount() {
        metadata.incrementRefCount();
    }
    
    /**
     * 获取依赖的消息ID列表
     */
    public List<String> getDependsOn() {
        return metadata.getDependsOn();
    }
    
    /**
     * 添加依赖
     */
    public void addDependency(String messageId) {
        metadata.addDependency(messageId);
    }
    
    /**
     * 获取标签列表
     */
    public List<String> getTags() {
        return metadata.getTags();
    }
    
    /**
     * 添加标签
     */
    public void addTag(String tag) {
        metadata.addTag(tag);
    }
    
    /**
     * 获取任务ID
     */
    public String getTaskId() {
        return metadata.getTaskId();
    }
    
    /**
     * 设置任务ID
     */
    public void setTaskId(String taskId) {
        metadata.setTaskId(taskId);
    }
    
    /**
     * 检查是否为用户消息
     */
    public boolean isUserMessage() {
        return "user".equalsIgnoreCase(role);
    }
    
    /**
     * 检查是否为助手消息
     */
    public boolean isAssistantMessage() {
        return "assistant".equalsIgnoreCase(role);
    }
    
    /**
     * 检查是否为系统消息
     */
    public boolean isSystemMessage() {
        return "system".equalsIgnoreCase(role);
    }
    
    /**
     * 检查是否为工具结果消息
     */
    public boolean isToolResultMessage() {
        return "tool".equalsIgnoreCase(role);
    }
    
    /**
     * 克隆消息（用于归档恢复）
     */
    public StructuredMessage clone() {
        return new StructuredMessage(
            this.id,
            this.role,
            this.content,
            this.metadata.clone(),
            this.toolUseId,
            this.toolName,
            this.timestamp
        );
    }
    
    @Override
    public String toString() {
        return String.format(
            "StructuredMessage{id=%s, role=%s, type=%s, content=%s}",
            id, role, metadata.getType(), 
            content != null ? content.substring(0, Math.min(50, content.length())) + "..." : null
        );
    }
}