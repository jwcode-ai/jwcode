package com.jwcode.web;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Web 会话管理器
 */
public class WebSessionManager {
    
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(1);
    
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
     * 获取所有会话
     */
    public Collection<WebSession> getAllSessions() {
        return sessions.values();
    }
    
    /**
     * 生成新会话 ID
     */
    public String generateSessionId() {
        return "session_" + String.format("%03d", sessionCounter.getAndIncrement());
    }
    
    /**
     * Web 会话
     */
    public static class WebSession {
        private final String id;
        private final long createdAt;
        private String title;
        
        public WebSession(String id) {
            this.id = id;
            this.createdAt = System.currentTimeMillis();
            this.title = "新会话";
        }
        
        public String getId() { return id; }
        public long getCreatedAt() { return createdAt; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
