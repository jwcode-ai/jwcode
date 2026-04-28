package com.jwcode.core.service.structured;

import java.time.Instant;

/**
 * 归档记录
 * 
 * 存储被丢弃消息的完整信息，支持回溯和审计
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ArchiveRecord {
    
    private final String messageId;
    private final String sessionId;
    private final String role;
    private final String content;
    private final MessageMetadata metadata;
    private final Instant archivedTime;
    private final String droppedBy;
    private final String dropReason;
    private final String dropStrategy;
    
    public ArchiveRecord(String messageId, String sessionId, String role, String content, 
                   MessageMetadata metadata, String droppedBy, String dropReason, String dropStrategy) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.metadata = metadata;
        this.archivedTime = Instant.now();
        this.droppedBy = droppedBy;
        this.dropReason = dropReason;
        this.dropStrategy = dropStrategy;
    }
    
    // Getters
    public String getMessageId() { return messageId; }
    public String getSessionId() { return sessionId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public MessageMetadata getMetadata() { return metadata; }
    public Instant getArchivedTime() { return archivedTime; }
    public String getDroppedBy() { return droppedBy; }
    public String getDropReason() { return dropReason; }
    public String getDropStrategy() { return dropStrategy; }
    
    @Override
    public String toString() {
        return String.format(
            "ArchiveRecord{messageId=%s, sessionId=%s, role=%s, archivedTime=%s, droppedBy=%s, dropReason=%s}",
            messageId, sessionId, role, archivedTime, droppedBy, dropReason
        );
    }
}