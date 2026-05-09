package com.jwcode.core.a2a.registry;

import com.jwcode.core.a2a.model.AgentCard;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * AgentSession — Agent 会话模型。
 *
 * <p>代表一个已连接到 A2A Registry 的 Agent 会话。
 * 包含 Agent 的元信息、WebSocket 连接标识、心跳状态等。</p>
 */
public class AgentSession {

    /** 会话唯一标识 */
    private final String sessionId;

    /** Agent 名称 */
    private final String agentName;

    /** Agent 类型 */
    private final String agentType;

    /** Agent Card（能力声明） */
    private final AgentCard agentCard;

    /** WebSocket 连接标识（用于定向推送） */
    private final String connectionId;

    /** 当前负载（正在执行的任务数） */
    private volatile int currentLoad;

    /** 最大负载 */
    private final int maxLoad;

    /** 会话创建时间 */
    private final Instant createdAt;

    /** 最后心跳时间 */
    private volatile Instant lastHeartbeat;

    /** 会话状态 */
    private volatile SessionStatus status;

    /** 会话状态枚举 */
    public enum SessionStatus {
        /** 在线 */
        ONLINE,
        /** 忙碌 */
        BUSY,
        /** 离线 */
        OFFLINE
    }

    public AgentSession(String agentName, String agentType, AgentCard agentCard,
                        String connectionId, int maxLoad) {
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.agentName = Objects.requireNonNull(agentName, "agentName must not be null");
        this.agentType = Objects.requireNonNull(agentType, "agentType must not be null");
        this.agentCard = Objects.requireNonNull(agentCard, "agentCard must not be null");
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId must not be null");
        this.currentLoad = 0;
        this.maxLoad = maxLoad > 0 ? maxLoad : 1;
        this.createdAt = Instant.now();
        this.lastHeartbeat = Instant.now();
        this.status = SessionStatus.ONLINE;
    }

    // ==================== Getters ====================

    public String getSessionId() { return sessionId; }
    public String getAgentName() { return agentName; }
    public String getAgentType() { return agentType; }
    public AgentCard getAgentCard() { return agentCard; }
    public String getConnectionId() { return connectionId; }
    public int getCurrentLoad() { return currentLoad; }
    public int getMaxLoad() { return maxLoad; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public SessionStatus getStatus() { return status; }

    // ==================== 状态操作 ====================

    /**
     * 更新心跳时间
     */
    public void heartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    /**
     * 增加负载（接受新任务时调用）
     */
    public synchronized void incrementLoad() {
        this.currentLoad++;
        if (this.currentLoad >= this.maxLoad) {
            this.status = SessionStatus.BUSY;
        }
    }

    /**
     * 减少负载（任务完成时调用）
     */
    public synchronized void decrementLoad() {
        this.currentLoad = Math.max(0, this.currentLoad - 1);
        if (this.currentLoad < this.maxLoad) {
            this.status = SessionStatus.ONLINE;
        }
    }

    /**
     * 标记为离线
     */
    public void markOffline() {
        this.status = SessionStatus.OFFLINE;
    }

    /**
     * 判断是否可接受新任务
     */
    public boolean isAvailable() {
        return status == SessionStatus.ONLINE && currentLoad < maxLoad;
    }

    /**
     * 判断会话是否已过期（超过指定秒数未收到心跳）
     */
    public boolean isExpired(long timeoutSeconds) {
        return Instant.now().getEpochSecond() - lastHeartbeat.getEpochSecond() > timeoutSeconds;
    }

    /**
     * 获取负载率（0.0 ~ 1.0）
     */
    public double getLoadRatio() {
        return (double) currentLoad / maxLoad;
    }

    @Override
    public String toString() {
        return String.format("AgentSession{name=%s, type=%s, load=%d/%d, status=%s}",
            agentName, agentType, currentLoad, maxLoad, status);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentName;
        private String agentType;
        private AgentCard agentCard;
        private String connectionId;
        private int maxLoad = 1;

        public Builder agentName(String agentName) { this.agentName = agentName; return this; }
        public Builder agentType(String agentType) { this.agentType = agentType; return this; }
        public Builder agentCard(AgentCard agentCard) { this.agentCard = agentCard; return this; }
        public Builder connectionId(String connectionId) { this.connectionId = connectionId; return this; }
        public Builder maxLoad(int maxLoad) { this.maxLoad = maxLoad; return this; }

        public AgentSession build() {
            return new AgentSession(agentName, agentType, agentCard, connectionId, maxLoad);
        }
    }
}
