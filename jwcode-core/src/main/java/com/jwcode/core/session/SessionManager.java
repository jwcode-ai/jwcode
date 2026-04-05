package com.jwcode.core.session;

import com.jwcode.common.util.Preconditions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * SessionManager - 会话管理器
 * 
 * 功能说明：
 * 管理会话的创建、加载、保存和删除。
 * 会话持久化到磁盘，支持会话恢复。
 * 
 * 上下文关系：
 * - 被 CLI/REPL 层调用
 * - 管理 Session 实例
 * - 使用文件系统存储会话
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SessionManager {
    
    private static final String SESSIONS_DIR = ".jwcode/sessions";
    
    /**
     * 内存中的会话缓存
     */
    private final Map<String, Session> sessions;
    
    /**
     * 当前活动会话 ID
     */
    private String activeSessionId;
    
    /**
     * 会话存储目录
     */
    private final Path sessionsDir;
    
    /**
     * 构造函数
     */
    public SessionManager() {
        this(Paths.get(System.getProperty("user.home"), SESSIONS_DIR));
    }
    
    /**
     * 构造函数
     * 
     * @param sessionsDir 会话存储目录
     */
    public SessionManager(Path sessionsDir) {
        this.sessionsDir = sessionsDir;
        this.sessions = new ConcurrentHashMap<>();
        initializeSessionsDir();
        loadAllSessions();
    }
    
    /**
     * 初始化会话目录
     */
    private void initializeSessionsDir() {
        try {
            if (!Files.exists(sessionsDir)) {
                Files.createDirectories(sessionsDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create sessions directory", e);
        }
    }
    
    /**
     * 创建新会话
     * 
     * @param workingDirectory 工作目录
     * @return 新创建的会话
     */
    public Session createSession(String workingDirectory) {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId, workingDirectory);
        sessions.put(sessionId, session);
        activeSessionId = sessionId;
        saveSession(session);
        return session;
    }
    
    /**
     * 加载会话
     * 
     * @param sessionId 会话 ID
     * @return 会话，如果不存在则返回 null
     */
    public Session loadSession(String sessionId) {
        if (sessions.containsKey(sessionId)) {
            return sessions.get(sessionId);
        }
        
        Path sessionFile = sessionsDir.resolve(sessionId + ".json");
        if (!Files.exists(sessionFile)) {
            return null;
        }
        
        try {
            String content = Files.readString(sessionFile);
            // TODO: 实现 JSON 反序列化
            Session session = Session.fromMap(new HashMap<>());
            sessions.put(sessionId, session);
            return session;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load session: " + sessionId, e);
        }
    }
    
    /**
     * 保存会话
     * 
     * @param session 会话
     */
    public void saveSession(Session session) {
        Preconditions.checkNotNull(session, "session cannot be null");
        
        Path sessionFile = sessionsDir.resolve(session.getId() + ".json");
        try {
            // TODO: 实现 JSON 序列化
            String content = session.toMap().toString();
            Files.writeString(sessionFile, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save session: " + session.getId(), e);
        }
    }
    
    /**
     * 删除会话
     * 
     * @param sessionId 会话 ID
     * @return true 如果删除成功
     */
    public boolean deleteSession(String sessionId) {
        Session removed = sessions.remove(sessionId);
        if (removed != null) {
            Path sessionFile = sessionsDir.resolve(sessionId + ".json");
            try {
                Files.deleteIfExists(sessionFile);
                return true;
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete session: " + sessionId, e);
            }
        }
        return false;
    }
    
    /**
     * 获取所有会话
     * 
     * @return 会话列表
     */
    public List<Session> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }
    
    /**
     * 获取最近会话
     * 
     * @param limit 数量限制
     * @return 最近的会话列表
     */
    public List<Session> getRecentSessions(int limit) {
        return sessions.values().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .limit(limit)
                .toList();
    }
    
    /**
     * 获取活动会话
     * 
     * @return 活动会话，如果没有则返回 null
     */
    public Session getActiveSession() {
        if (activeSessionId == null) {
            return null;
        }
        return sessions.get(activeSessionId);
    }
    
    /**
     * 设置活动会话
     * 
     * @param sessionId 会话 ID
     * @return this
     */
    public SessionManager setActiveSession(String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        this.activeSessionId = sessionId;
        return this;
    }
    
    /**
     * 清除活动会话
     */
    public void clearActiveSession() {
        this.activeSessionId = null;
    }
    
    /**
     * 加载所有会话
     */
    private void loadAllSessions() {
        if (!Files.exists(sessionsDir)) {
            return;
        }
        
        try (Stream<Path> paths = Files.walk(sessionsDir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                 .forEach(path -> {
                     String sessionId = path.getFileName().toString()
                             .replace(".json", "");
                     try {
                         loadSession(sessionId);
                     } catch (Exception e) {
                         System.err.println("Failed to load session: " + sessionId);
                     }
                 });
        } catch (IOException e) {
            throw new RuntimeException("Failed to load sessions", e);
        }
    }
    
    /**
     * 获取会话数量
     * 
     * @return 会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }
    
    /**
     * 清理旧会话
     * 
     * @param maxAge 最大保存天数
     * @return 清理的会话数量
     */
    public int cleanupOldSessions(int maxAge) {
        long now = System.currentTimeMillis();
        long maxAgeMillis = maxAge * 24L * 60 * 60 * 1000;
        
        List<String> toRemove = new ArrayList<>();
        for (Session session : sessions.values()) {
            if (now - session.getUpdatedAt().toEpochMilli() > maxAgeMillis) {
                toRemove.add(session.getId());
            }
        }
        
        for (String sessionId : toRemove) {
            deleteSession(sessionId);
        }
        
        return toRemove.size();
    }
}
