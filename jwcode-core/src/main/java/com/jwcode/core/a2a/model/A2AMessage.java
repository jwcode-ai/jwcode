package com.jwcode.core.a2a.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A2AMessage — A2A 协议中的消息模型。
 *
 * <p>用于 Orchestrator 与子Agent 之间的通信消息。
 * 支持任务提交、状态更新、进度推送、结果返回等场景。</p>
 */
public class A2AMessage {

    /** 消息唯一标识 */
    private final String messageId;

    /** 消息类型 */
    private final MessageType type;

    /** 关联的任务 ID */
    private final String taskId;

    /** 消息内容 */
    private final String content;

    /** 消息来源 Agent */
    private final String source;

    /** 消息目标 Agent */
    private final String target;

    /** 时间戳 */
    private final LocalDateTime timestamp;

    public A2AMessage(String messageId, MessageType type, String taskId,
                      String content, String source, String target,
                      LocalDateTime timestamp) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.taskId = taskId;
        this.content = content;
        this.source = source;
        this.target = target;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    // ==================== 消息类型枚举 ====================

    public enum MessageType {
        /** 任务提交 */
        TASK_SUBMIT,
        /** 任务开始 */
        TASK_STARTED,
        /** 任务进度更新 */
        TASK_PROGRESS,
        /** 任务完成 */
        TASK_COMPLETED,
        /** 任务失败 */
        TASK_FAILED,
        /** 任务取消 */
        TASK_CANCELLED,
        /** Agent Card 查询 */
        AGENT_CARD_REQUEST,
        /** Agent Card 响应 */
        AGENT_CARD_RESPONSE,
        /** 心跳 */
        HEARTBEAT,
        /** 错误 */
        ERROR
    }

    // ==================== Getters ====================

    public String getMessageId() { return messageId; }
    public MessageType getType() { return type; }
    public String getTaskId() { return taskId; }
    public String getContent() { return content; }
    public String getSource() { return source; }
    public String getTarget() { return target; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "A2AMessage{id='" + messageId + "', type=" + type +
               ", task='" + taskId + "', from=" + source + " -> " + target + "}";
    }

    // ==================== Factory Methods ====================

    public static A2AMessage taskSubmit(String taskId, String content,
                                         String source, String target) {
        return new A2AMessage(
            UUID.randomUUID().toString().substring(0, 8),
            MessageType.TASK_SUBMIT, taskId, content, source, target,
            LocalDateTime.now()
        );
    }

    public static A2AMessage taskProgress(String taskId, String content,
                                           String source, String target) {
        return new A2AMessage(
            UUID.randomUUID().toString().substring(0, 8),
            MessageType.TASK_PROGRESS, taskId, content, source, target,
            LocalDateTime.now()
        );
    }

    public static A2AMessage taskCompleted(String taskId, String content,
                                            String source, String target) {
        return new A2AMessage(
            UUID.randomUUID().toString().substring(0, 8),
            MessageType.TASK_COMPLETED, taskId, content, source, target,
            LocalDateTime.now()
        );
    }

    public static A2AMessage taskFailed(String taskId, String content,
                                         String source, String target) {
        return new A2AMessage(
            UUID.randomUUID().toString().substring(0, 8),
            MessageType.TASK_FAILED, taskId, content, source, target,
            LocalDateTime.now()
        );
    }

    public static A2AMessage agentCardRequest(String source, String target) {
        return new A2AMessage(
            UUID.randomUUID().toString().substring(0, 8),
            MessageType.AGENT_CARD_REQUEST, null, null, source, target,
            LocalDateTime.now()
        );
    }

    public static A2AMessage agentCardResponse(String content,
                                                String source, String target) {
        return new A2AMessage(
            UUID.randomUUID().toString().substring(0, 8),
            MessageType.AGENT_CARD_RESPONSE, null, content, source, target,
            LocalDateTime.now()
        );
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String messageId;
        private MessageType type;
        private String taskId;
        private String content;
        private String source;
        private String target;
        private LocalDateTime timestamp;

        public Builder messageId(String messageId) { this.messageId = messageId; return this; }
        public Builder type(MessageType type) { this.type = type; return this; }
        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder target(String target) { this.target = target; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }

        public A2AMessage build() {
            return new A2AMessage(messageId, type, taskId, content, source, target, timestamp);
        }
    }
}
