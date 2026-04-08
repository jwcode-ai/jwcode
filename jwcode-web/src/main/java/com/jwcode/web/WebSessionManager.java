package com.jwcode.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web 会话管理器
 */
public class WebSessionManager {
    
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    
    public WebSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, WebSession::new);
    }
    
    public WebSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }
    
    /**
     * Web 会话
     */
    public static class WebSession {
        private final String id;
        private final long createdAt;
        
        public WebSession(String id) {
            this.id = id;
            this.createdAt = System.currentTimeMillis();
        }
        
        public String getId() { return id; }
        public long getCreatedAt() { return createdAt; }
    }
}
