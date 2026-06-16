package com.jwcode.core.event;

import java.time.Instant;
import java.util.UUID;

/**
 * SessionEvent — 会话生命周期中的 typed 事件基类。
 *
 * <p>设计参考 opencode 的 event sourcing 体系，但适配 Java 17 和现有 Session 架构。
 * 每个事件有唯一的 eventId、类型、会话 ID、创建时间和同步版本号。
 *
 * <p>事件类型层次（通过 getType() 区分）：
 * <ul>
 *   <li>SESSION — session 生命周期</li>
 *   <li>PROMPT — 用户/系统输入</li>
 *   <li>STEP — LLM 步骤</li>
 *   <li>MESSAGE — 消息文本</li>
 *   <li>TOOL — 工具调用</li>
 *   <li>COMPACTION — 上下文压缩</li>
 *   <li>AGENT — agent 切换</li>
 *   <li>MODEL — 模型切换</li>
 *   <li>ERROR — 错误</li>
 * </ul>
 */
public class SessionEvent {

    public enum EventType {
        // Session lifecycle
        SESSION_STARTED, SESSION_ENDED,
        // Prompt
        PROMPT_SUBMITTED, PROMPT_ADMITTED, PROMPT_PROMOTED,
        // Step lifecycle
        STEP_STARTED, STEP_ENDED, STEP_FAILED,
        // Message streaming
        TEXT_STARTED, TEXT_DELTA, TEXT_ENDED,
        REASONING_STARTED, REASONING_DELTA, REASONING_ENDED,
        // Tool execution
        TOOL_CALLED, TOOL_SUCCESS, TOOL_FAILED, TOOL_PROGRESS,
        // Compaction
        COMPACTION_STARTED, COMPACTION_ENDED,
        // Agent/Model switch
        AGENT_SWITCHED, MODEL_SWITCHED,
        // Error
        ERROR_OCCURRED
    }

    private final String eventId;
    private final EventType type;
    private final String sessionId;
    private final Instant timestamp;
    private final long version;
    private final String data;

    public SessionEvent(EventType type, String sessionId, long version, String data) {
        this.eventId = UUID.randomUUID().toString();
        this.type = type;
        this.sessionId = sessionId;
        this.timestamp = Instant.now();
        this.version = version;
        this.data = data;
    }

    /**
     * 全参数构造（用于从 EventStore 恢复）。
     */
    public SessionEvent(String eventId, EventType type, String sessionId, Instant timestamp, long version, String data) {
        this.eventId = eventId;
        this.type = type;
        this.sessionId = sessionId;
        this.timestamp = timestamp;
        this.version = version;
        this.data = data;
    }

    // ==================== 便捷工厂方法 ====================

    public static SessionEvent create(String sessionId, EventType type, String data) {
        return new SessionEvent(type, sessionId, 0, data);
    }

    public static SessionEvent create(String sessionId, EventType type, long version, String data) {
        return new SessionEvent(type, sessionId, version, data);
    }

    // ==================== Getters ====================

    public String getEventId() { return eventId; }
    public EventType getType() { return type; }
    public String getSessionId() { return sessionId; }
    public Instant getTimestamp() { return timestamp; }
    public long getVersion() { return version; }
    public String getData() { return data; }

    @Override
    public String toString() {
        return "SessionEvent{type=" + type + ", session=" + sessionId
            + ", version=" + version + ", ts=" + timestamp + "}";
    }
}
