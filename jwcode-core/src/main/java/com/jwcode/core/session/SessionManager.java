package com.jwcode.core.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.common.util.Preconditions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final String SESSIONS_DIR = ".jwcode/sessions";
    private static final String SESSION_EXTENSION = ".json";
    private static final String ACTIVE_SESSION_FILE = ".jwcode/active_session";
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    
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
     * 单例实例
     */
    private static SessionManager instance;
    
    /**
     * 获取单例实例
     */
    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }
    
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
        restoreActiveSession();
    }
    
    /**
     * 初始化会话目录
     */
    private void initializeSessionsDir() {
        try {
            if (!Files.exists(sessionsDir)) {
                Files.createDirectories(sessionsDir);
                logger.info("Created sessions directory: {}", sessionsDir);
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
        persistActiveSessionId();
        logger.info("Created new session: {}", sessionId);
        return session;
    }
    
    /**
     * 创建新会话（使用默认工作目录）
     * 
     * @return 新创建的会话
     */
    public Session createSession() {
        return createSession(System.getProperty("user.dir"));
    }
    
    /**
     * 加载会话
     * 
     * @param sessionId 会话 ID
     * @return 会话，如果不存在则返回 null
     */
    public Session loadSession(String sessionId) {
        // 首先检查内存缓存
        if (sessions.containsKey(sessionId)) {
            return sessions.get(sessionId);
        }
        
        // 从文件加载
        Path sessionFile = sessionsDir.resolve(sessionId + SESSION_EXTENSION);
        if (!Files.exists(sessionFile)) {
            logger.warn("Session file not found: {}", sessionFile);
            return null;
        }
        
        try {
            String content = Files.readString(sessionFile);
            // 使用 Map 方式反序列化，因为 Message 是抽象类
            Map<String, Object> map = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            Session session = Session.fromMap(map);
            sessions.put(sessionId, session);
            logger.info("Loaded session from file: {}", sessionId);
            return session;
        } catch (IOException e) {
            logger.error("Failed to load session: {}", sessionId, e);
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
        
        Path sessionFile = sessionsDir.resolve(session.getId() + SESSION_EXTENSION);
        try {
            // 使用 Map 方式序列化，因为 Message 是抽象类
            Map<String, Object> map = session.toMap();
            String json = objectMapper.writeValueAsString(map);
            Files.writeString(sessionFile, json);
            logger.debug("Saved session: {}", session.getId());
        } catch (IOException e) {
            logger.error("Failed to save session: {}", session.getId(), e);
            throw new RuntimeException("Failed to save session: " + session.getId(), e);
        }
    }
    
    /**
     * 保存所有会话
     */
    public void saveAllSessions() {
        for (Session session : sessions.values()) {
            saveSession(session);
        }
        logger.info("Saved all sessions ({} total)", sessions.size());
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
            Path sessionFile = sessionsDir.resolve(sessionId + SESSION_EXTENSION);
            try {
                Files.deleteIfExists(sessionFile);
                logger.info("Deleted session: {}", sessionId);
                
                // 如果删除的是当前活动会话，清空活动会话
                if (sessionId.equals(activeSessionId)) {
                    activeSessionId = null;
                    deleteActiveSessionFile();
                }
                return true;
            } catch (IOException e) {
                logger.error("Failed to delete session file: {}", sessionId, e);
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
            // 尝试从文件加载
            Session session = loadSession(sessionId);
            if (session == null) {
                throw new IllegalArgumentException("Session not found: " + sessionId);
            }
        }
        this.activeSessionId = sessionId;
        persistActiveSessionId();
        logger.info("Set active session: {}", sessionId);
        return this;
    }
    
    /**
     * 清除活动会话
     */
    public void clearActiveSession() {
        this.activeSessionId = null;
        deleteActiveSessionFile();
    }
    
    /**
     * 加载所有会话
     */
    private void loadAllSessions() {
        if (!Files.exists(sessionsDir)) {
            return;
        }
        
        try (Stream<Path> paths = Files.walk(sessionsDir)) {
            paths.filter(p -> p.toString().endsWith(SESSION_EXTENSION))
                 .forEach(path -> {
                     String sessionId = path.getFileName().toString()
                             .replace(SESSION_EXTENSION, "");
                     try {
                         loadSession(sessionId);
                     } catch (Exception e) {
                         logger.error("Failed to load session: {}", sessionId, e);
                     }
                 });
            logger.info("Loaded {} sessions from {}", sessions.size(), sessionsDir);
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
        
        logger.info("Cleaned up {} old sessions", toRemove.size());
        return toRemove.size();
    }
    
    /**
     * 检查会话是否存在
     * 
     * @param sessionId 会话 ID
     * @return true 如果存在
     */
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId) || 
               Files.exists(sessionsDir.resolve(sessionId + SESSION_EXTENSION));
    }
    
    /**
     * 获取或创建会话
     * 
     * @param sessionId 会话 ID
     * @return 会话
     */
    public Session getOrCreateSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            session = loadSession(sessionId);
            if (session == null) {
                session = new Session(sessionId, System.getProperty("user.dir"));
                sessions.put(sessionId, session);
                saveSession(session);
            }
        }
        return session;
    }
    
    /**
     * 获取会话存储目录
     */
    public Path getSessionsDir() {
        return sessionsDir;
    }

    // ==================== Active Session 持久化 ====================

    /**
     * 持久化当前活动会话 ID 到文件
     */
    private void persistActiveSessionId() {
        try {
            Path activeFile = Paths.get(System.getProperty("user.home"), ACTIVE_SESSION_FILE);
            Path parentDir = activeFile.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(activeFile, activeSessionId);
            logger.debug("Persisted active session ID: {}", activeSessionId);
        } catch (IOException e) {
            logger.warn("Failed to persist active session ID", e);
        }
    }

    /**
     * 从文件恢复活动会话 ID
     */
    private void restoreActiveSession() {
        try {
            Path activeFile = Paths.get(System.getProperty("user.home"), ACTIVE_SESSION_FILE);
            if (!Files.exists(activeFile)) {
                logger.debug("No active session file found, starting fresh");
                return;
            }
            String savedId = Files.readString(activeFile).trim();
            if (savedId.isEmpty()) {
                return;
            }
            // 验证该会话确实已加载（文件可能已被删除）
            if (sessions.containsKey(savedId)) {
                this.activeSessionId = savedId;
                logger.info("Restored active session: {} (workingDirectory: {})",
                        savedId, sessions.get(savedId).getWorkingDirectory());
            } else {
                // 会话文件可能被手动删除，清理无效的 active_session 记录
                logger.warn("Active session '{}' not found in loaded sessions, clearing stale reference", savedId);
                deleteActiveSessionFile();
            }
        } catch (IOException e) {
            logger.warn("Failed to restore active session ID", e);
        }
    }

    /**
     * 删除活动会话 ID 文件
     */
    private void deleteActiveSessionFile() {
        try {
            Path activeFile = Paths.get(System.getProperty("user.home"), ACTIVE_SESSION_FILE);
            Files.deleteIfExists(activeFile);
            logger.debug("Deleted active session file");
        } catch (IOException e) {
            logger.warn("Failed to delete active session file", e);
        }
    }
}
