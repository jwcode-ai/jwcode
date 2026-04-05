package com.jwcode.core.compact;

import java.time.Instant;

/**
 * CompactRecord - 压缩记录
 * 
 * 功能说明：
 * 记录单次会话压缩的详细信息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompactRecord {
    
    private final String id;
    private final String sessionId;
    private final CompactStrategy strategy;
    private final int messagesCompacted;
    private final int tokensSaved;
    private final long durationMs;
    private final Instant timestamp;
    
    public CompactRecord(String id, String sessionId, CompactStrategy strategy,
                         int messagesCompacted, int tokensSaved, long durationMs,
                         Instant timestamp) {
        this.id = id;
        this.sessionId = sessionId;
        this.strategy = strategy;
        this.messagesCompacted = messagesCompacted;
        this.tokensSaved = tokensSaved;
        this.durationMs = durationMs;
        this.timestamp = timestamp;
    }
    
    public String getId() {
        return id;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public CompactStrategy getStrategy() {
        return strategy;
    }
    
    public int getMessagesCompacted() {
        return messagesCompacted;
    }
    
    public int getTokensSaved() {
        return tokensSaved;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
}