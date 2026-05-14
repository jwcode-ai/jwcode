package com.jwcode.core.service.structured;

import com.jwcode.core.aicl.BlockLifecycle;
import com.jwcode.core.aicl.BlockPriority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 结构化消息
 * 
 * 带有元数据的对话消息，用于结构化上下文管理。
 * 
 * <p>v1.1 新增 AICL 协议支持：priority、ttl、lastAccess、accessCount、generation
 * 字段，使 StructuredMessage 可直接映射为 AICL ContextBlock。</p>
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

    // ===== AICL 生命周期字段（v1.1 新增） =====
    private BlockPriority priority;
    private BlockLifecycle state;
    private int ttl;
    private long lastAccess;
    private int accessCount;
    private int generation;
    
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
        // AICL 默认值
        this.priority = BlockPriority.MEDIUM;
        this.state = BlockLifecycle.ACTIVE;
        this.ttl = 3;
        this.lastAccess = System.currentTimeMillis();
        this.accessCount = 0;
        this.generation = 0;
    }
    
    // ===== 基础 Getters =====
    public String getId() { return id; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public MessageMetadata getMetadata() { return metadata; }
    public Instant getTimestamp() { return timestamp; }
    public String getToolUseId() { return toolUseId; }
    public String getToolName() { return toolName; }

    // ===== AICL Getters/Setters =====
    public BlockPriority getPriority() { return priority; }
    public void setPriority(BlockPriority priority) { this.priority = priority; }

    public BlockLifecycle getState() { return state; }
    public void setState(BlockLifecycle state) { this.state = state; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public long getLastAccess() { return lastAccess; }
    public void setLastAccess(long lastAccess) { this.lastAccess = lastAccess; }

    public int getAccessCount() { return accessCount; }
    public void setAccessCount(int accessCount) { this.accessCount = accessCount; }

    public int getGeneration() { return generation; }
    public void setGeneration(int generation) { this.generation = generation; }

    /** 记录访问（更新时间戳和计数） */
    public void touch() {
        this.lastAccess = System.currentTimeMillis();
        this.accessCount++;
    }

    /** TTL 倒计时，返回 true 表示到期 */
    public boolean tick() {
        if (ttl > 0) {
            ttl--;
            return ttl <= 0;
        }
        return false;
    }

    /** 衰减到下一生命周期状态 */
    public void decay() {
        if (this.state == BlockLifecycle.PINNED) return;
        BlockLifecycle next = this.state.decay();
        if (next != this.state) {
            this.state = next;
            this.generation++;
        }
    }
    
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
     * 克隆消息（用于归档恢复），保留 AICL 生命周期字段。
     */
    public StructuredMessage clone() {
        StructuredMessage clone = new StructuredMessage(
            this.id,
            this.role,
            this.content,
            this.metadata.clone(),
            this.toolUseId,
            this.toolName,
            this.timestamp
        );
        clone.priority = this.priority;
        clone.state = this.state;
        clone.ttl = this.ttl;
        clone.lastAccess = this.lastAccess;
        clone.accessCount = this.accessCount;
        clone.generation = this.generation;
        return clone;
    }
    
    @Override
    public String toString() {
        return String.format(
            "StructuredMessage{id=%s, role=%s, type=%s, priority=%s, state=%s, content=%s}",
            id, role, metadata.getType(), priority != null ? priority.getName() : "null",
            state != null ? state.getState() : "null",
            content != null ? content.substring(0, Math.min(50, content.length())) + "..." : null
        );
    }
}