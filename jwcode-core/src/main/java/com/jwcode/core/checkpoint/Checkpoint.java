package com.jwcode.core.checkpoint;

import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 检查点 - 保存会话在特定时间点的状态
 * 
 * 参照 Kimi Code 的 Checkpoint 系统
 */
public class Checkpoint {
    
    private final String id;
    private final int stepNumber;
    private final Instant timestamp;
    private final List<Message> messages;
    private final Map<String, Object> metadata;
    private final String description;
    
    public Checkpoint(String id, int stepNumber, List<Message> messages, 
                      Map<String, Object> metadata, String description) {
        this.id = id;
        this.stepNumber = stepNumber;
        this.timestamp = Instant.now();
        this.messages = messages;
        this.metadata = metadata;
        this.description = description;
    }
    
    /**
     * 从会话创建检查点
     */
    public static Checkpoint fromSession(Session session, int stepNumber, String description) {
        return new Checkpoint(
            generateId(),
            stepNumber,
            List.copyOf(session.getMessages()), // 复制消息列表
            Map.of(
                "workingDirectory", session.getWorkingDirectory(),
                "model", session.getModel(),
                "messageCount", session.getMessageCount()
            ),
            description
        );
    }
    
    /**
     * 生成唯一 ID
     */
    private static String generateId() {
        return "cp_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    /**
     * 恢复到检查点状态
     */
    public void restoreTo(Session session) {
        session.clearMessages();
        for (Message msg : messages) {
            session.addMessage(msg);
        }
    }
    
    // Getters
    public String getId() { return id; }
    public int getStepNumber() { return stepNumber; }
    public Instant getTimestamp() { return timestamp; }
    public List<Message> getMessages() { return messages; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getDescription() { return description; }
    
    /**
     * 获取检查点摘要
     */
    public String getSummary() {
        return String.format("[%d] %s - %d messages - %s", 
            stepNumber, 
            timestamp.toString().substring(0, 19),
            messages.size(),
            description
        );
    }
}
