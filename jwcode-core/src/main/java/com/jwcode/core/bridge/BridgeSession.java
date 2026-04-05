package com.jwcode.core.bridge;

import com.jwcode.core.session.Session;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 桥接会话 - 远程客户端与本地会话的桥梁
 */
public class BridgeSession {
    
    private final String sessionId;
    private final long createdAt;
    private Session localSession;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private volatile boolean closed = false;
    
    public BridgeSession(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
    }
    
    /**
     * 发送消息到远程客户端
     */
    public void sendMessage(String message) {
        if (!closed) {
            messageQueue.offer(message);
        }
    }
    
    /**
     * 接收消息（阻塞）
     */
    public String receiveMessage() throws InterruptedException {
        return messageQueue.take();
    }
    
    /**
     * 关联本地会话
     */
    public void attachLocalSession(Session session) {
        this.localSession = session;
    }
    
    /**
     * 关闭会话
     */
    public void close() {
        closed = true;
        messageQueue.clear();
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public long getCreatedAt() { return createdAt; }
    public Session getLocalSession() { return localSession; }
    public boolean isClosed() { return closed; }
    
    /**
     * 获取状态
     */
    public String getStatus() {
        return closed ? "closed" : "active";
    }
}
