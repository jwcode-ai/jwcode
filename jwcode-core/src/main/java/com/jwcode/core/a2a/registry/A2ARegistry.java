package com.jwcode.core.a2a.registry;

import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.Skill;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A2ARegistry — Agent 注册中心。
 *
 * <p>管理所有已连接到系统的 Agent 会话，支持：
 * <ul>
 *   <li>Agent 注册/注销</li>
 *   <li>按 agentId / skillId / agentType 查找 Agent</li>
 *   <li>定向推送消息到指定 Agent</li>
 *   <li>心跳检测与过期清理</li>
 *   <li>负载感知的 Agent 选择</li>
 * </ul>
 * </p>
 *
 * <p>线程安全，使用 ConcurrentHashMap 作为底层存储。</p>
 */
public class A2ARegistry {

    private static final Logger logger = Logger.getLogger(A2ARegistry.class.getName());

    /** 单例实例 */
    private static volatile A2ARegistry instance;

    /** Agent 会话存储：connectionId -> AgentSession */
    private final ConcurrentHashMap<String, AgentSession> sessionsByConnection = new ConcurrentHashMap<>();

    /** Agent 会话存储：agentName -> AgentSession（用于按名称查找） */
    private final ConcurrentHashMap<String, AgentSession> sessionsByName = new ConcurrentHashMap<>();

    /** 按 skillId 索引的 Agent 连接ID列表 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> agentsBySkill = new ConcurrentHashMap<>();

    /** 按 agentType 索引的 Agent 连接ID列表 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> agentsByType = new ConcurrentHashMap<>();

    /** 心跳超时时间（秒），默认 60 秒 */
    private volatile long heartbeatTimeoutSeconds = 60;

    /** 注册监听器 */
    private final List<RegistryListener> listeners = new CopyOnWriteArrayList<>();

    // ==================== 单例 ====================

    private A2ARegistry() {
    }

    public static A2ARegistry getInstance() {
        if (instance == null) {
            synchronized (A2ARegistry.class) {
                if (instance == null) {
                    instance = new A2ARegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 重置单例（主要用于测试）
     */
    public static void resetInstance() {
        synchronized (A2ARegistry.class) {
            if (instance != null) {
                instance.clear();
            }
            instance = null;
        }
    }

    // ==================== 注册与注销 ====================

    /**
     * 注册一个 Agent 会话
     *
     * @param session Agent 会话
     * @return 如果注册成功返回 true，如果连接ID已存在返回 false
     */
    public boolean register(AgentSession session) {
        Objects.requireNonNull(session, "session must not be null");

        String connectionId = session.getConnectionId();
        if (sessionsByConnection.putIfAbsent(connectionId, session) != null) {
            logger.warning("[A2ARegistry] Connection already registered: " + connectionId);
            return false;
        }

        // 按名称注册
        sessionsByName.put(session.getAgentName(), session);

        // 按 skill 索引
        AgentCard card = session.getAgentCard();
        if (card != null && card.getSkills() != null) {
            for (Skill skill : card.getSkills()) {
                agentsBySkill.computeIfAbsent(skill.getId(), k -> new CopyOnWriteArrayList<>()).add(connectionId);
            }
        }

        // 按类型索引
        agentsByType.computeIfAbsent(session.getAgentType(), k -> new CopyOnWriteArrayList<>()).add(connectionId);

        logger.info("[A2ARegistry] Agent registered: " + session);
        notifyListeners(RegistryEvent.Type.REGISTERED, session);
        return true;
    }

    /**
     * 注销一个 Agent 会话
     *
     * @param connectionId WebSocket 连接标识
     * @return 如果注销成功返回 true
     */
    public boolean unregister(String connectionId) {
        AgentSession session = sessionsByConnection.remove(connectionId);
        if (session == null) {
            return false;
        }

        sessionsByName.remove(session.getAgentName());

        // 从 skill 索引中移除
        AgentCard card = session.getAgentCard();
        if (card != null && card.getSkills() != null) {
            for (Skill skill : card.getSkills()) {
                CopyOnWriteArrayList<String> list = agentsBySkill.get(skill.getId());
                if (list != null) {
                    list.remove(connectionId);
                    if (list.isEmpty()) {
                        agentsBySkill.remove(skill.getId());
                    }
                }
            }
        }

        // 从类型索引中移除
        CopyOnWriteArrayList<String> typeList = agentsByType.get(session.getAgentType());
        if (typeList != null) {
            typeList.remove(connectionId);
            if (typeList.isEmpty()) {
                agentsByType.remove(session.getAgentType());
            }
        }

        session.markOffline();
        logger.info("[A2ARegistry] Agent unregistered: " + session);
        notifyListeners(RegistryEvent.Type.UNREGISTERED, session);
        return true;
    }

    /**
     * 更新 Agent 心跳
     */
    public void heartbeat(String connectionId) {
        AgentSession session = sessionsByConnection.get(connectionId);
        if (session != null) {
            session.heartbeat();
        }
    }

    // ==================== 查找方法 ====================

    /**
     * 按连接ID查找会话
     */
    public Optional<AgentSession> findByConnectionId(String connectionId) {
        return Optional.ofNullable(sessionsByConnection.get(connectionId));
    }

    /**
     * 按 Agent 名称查找会话
     */
    public Optional<AgentSession> findByName(String agentName) {
        return Optional.ofNullable(sessionsByName.get(agentName));
    }

    /**
     * 按 skillId 查找所有具备该技能的在线 Agent
     */
    public List<AgentSession> findBySkillId(String skillId) {
        CopyOnWriteArrayList<String> connectionIds = agentsBySkill.get(skillId);
        if (connectionIds == null) {
            return Collections.emptyList();
        }
        return connectionIds.stream()
            .map(sessionsByConnection::get)
            .filter(Objects::nonNull)
            .filter(AgentSession::isAvailable)
            .collect(Collectors.toList());
    }

    /**
     * 按 agentType 查找所有在线 Agent
     */
    public List<AgentSession> findByType(String agentType) {
        CopyOnWriteArrayList<String> connectionIds = agentsByType.get(agentType);
        if (connectionIds == null) {
            return Collections.emptyList();
        }
        return connectionIds.stream()
            .map(sessionsByConnection::get)
            .filter(Objects::nonNull)
            .filter(s -> s.getStatus() != AgentSession.SessionStatus.OFFLINE)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有已注册的 Agent 会话
     */
    public List<AgentSession> getAllSessions() {
        return new ArrayList<>(sessionsByConnection.values());
    }

    /**
     * 获取所有在线的 Agent 会话
     */
    public List<AgentSession> getOnlineSessions() {
        return sessionsByConnection.values().stream()
            .filter(s -> s.getStatus() != AgentSession.SessionStatus.OFFLINE)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有可用的 Agent 会话（在线且负载未满）
     */
    public List<AgentSession> getAvailableSessions() {
        return sessionsByConnection.values().stream()
            .filter(AgentSession::isAvailable)
            .collect(Collectors.toList());
    }

    // ==================== 负载感知选择 ====================

    /**
     * 选择负载最低的可用 Agent
     */
    public Optional<AgentSession> selectLeastLoaded() {
        return getAvailableSessions().stream()
            .min(Comparator.comparingDouble(AgentSession::getLoadRatio));
    }

    /**
     * 选择具备指定 skill 且负载最低的 Agent
     */
    public Optional<AgentSession> selectLeastLoadedBySkill(String skillId) {
        return findBySkillId(skillId).stream()
            .min(Comparator.comparingDouble(AgentSession::getLoadRatio));
    }

    // ==================== 过期清理 ====================

    /**
     * 清理所有过期的 Agent 会话（心跳超时）
     *
     * @return 被清理的会话数量
     */
    public int purgeExpiredSessions() {
        List<String> expiredIds = new ArrayList<>();
        for (Map.Entry<String, AgentSession> entry : sessionsByConnection.entrySet()) {
            if (entry.getValue().isExpired(heartbeatTimeoutSeconds)) {
                expiredIds.add(entry.getKey());
            }
        }
        for (String id : expiredIds) {
            unregister(id);
        }
        if (!expiredIds.isEmpty()) {
            logger.info("[A2ARegistry] Purged " + expiredIds.size() + " expired sessions");
        }
        return expiredIds.size();
    }

    // ==================== 统计信息 ====================

    /**
     * 获取注册统计
     */
    public RegistryStats getStats() {
        long total = sessionsByConnection.size();
        long online = getOnlineSessions().size();
        long available = getAvailableSessions().size();
        return new RegistryStats(total, online, available, agentsBySkill.size(), agentsByType.size());
    }

    /**
     * 注册统计
     */
    public record RegistryStats(long totalSessions, long onlineSessions,
                                 long availableSessions, long skillCount, long typeCount) {
    }

    // ==================== 监听器 ====================

    public void addListener(RegistryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RegistryListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(RegistryEvent.Type type, AgentSession session) {
        RegistryEvent event = new RegistryEvent(type, session, Instant.now());
        for (RegistryListener listener : listeners) {
            try {
                listener.onRegistryEvent(event);
            } catch (Exception e) {
                logger.log(Level.WARNING, "RegistryListener threw exception", e);
            }
        }
    }

    // ==================== 配置 ====================

    public void setHeartbeatTimeoutSeconds(long timeoutSeconds) {
        this.heartbeatTimeoutSeconds = timeoutSeconds;
    }

    public long getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    // ==================== 内部方法 ====================

    private void clear() {
        sessionsByConnection.clear();
        sessionsByName.clear();
        agentsBySkill.clear();
        agentsByType.clear();
        listeners.clear();
    }

    // ==================== 内部类 ====================

    /**
     * 注册事件
     */
    public record RegistryEvent(Type type, AgentSession session, Instant timestamp) {
        public enum Type {
            REGISTERED,
            UNREGISTERED,
            HEARTBEAT
        }
    }

    /**
     * 注册监听器接口
     */
    @FunctionalInterface
    public interface RegistryListener {
        void onRegistryEvent(RegistryEvent event);
    }
}
