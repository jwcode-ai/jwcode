package com.jwcode.core.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.common.util.Preconditions;
import com.jwcode.core.model.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Session - 会话类
 * 
 * 功能说明：
 * 表示一次完整的对话会话，包含会话 ID、创建时间、消息历史等信息。
 * 
 * 上下文关系：
 * - 被 QueryEngine 使用
 * - 被 SessionManager 管理
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Session {
    
    private static final Logger logger = LoggerFactory.getLogger(Session.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    private final String id;
    private final Instant createdAt;
    private Instant updatedAt;
    private String title;
    private final List<Message> messages;
    private final String workingDirectory;
    private String model;
    private final Map<String, Object> metadata;
    
    public Session(String id, String workingDirectory) {
        this.id = Preconditions.checkNotNull(id, "id cannot be null");
        this.workingDirectory = workingDirectory != null ? workingDirectory : System.getProperty("user.dir");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.messages = new CopyOnWriteArrayList<>();
        this.metadata = new HashMap<>();
        // 模型必须通过 setModel() 设置，不允许硬编码
        this.model = null;
    }
    
    // 默认构造函数用于JSON反序列化
    private Session() {
        this.id = UUID.randomUUID().toString();
        this.workingDirectory = System.getProperty("user.dir");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.messages = new CopyOnWriteArrayList<>();
        this.metadata = new HashMap<>();
        this.model = null;
    }
    
    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; this.updatedAt = Instant.now(); }
    public String getWorkingDirectory() { return workingDirectory; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; this.updatedAt = Instant.now(); }
    
    public Session addMessage(Message message) {
        Preconditions.checkNotNull(message, "message cannot be null");
        messages.add(message);
        this.updatedAt = Instant.now();
        return this;
    }
    
    public List<Message> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }
    
    public int getMessageCount() { return messages.size(); }
    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) { return (T) metadata.get(key); }
    public Session setMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
    
    public Session clearMessages() {
        messages.clear();
        this.updatedAt = Instant.now();
        return this;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Session session = (Session) o;
        return Objects.equals(id, session.id);
    }
    
    @Override
    public int hashCode() { return Objects.hash(id); }
    
    @Override
    public String toString() {
        return "Session{id='" + id + "', title='" + title + "', messageCount=" + messages.size() + "}";
    }
    
    /**
     * 将会话序列化为 JSON 字符串
     * 使用 Map 方式序列化，因为 Message 是抽象类
     */
    public String toJson() {
        try {
            Map<String, Object> map = toMap();
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize session to JSON", e);
            throw new RuntimeException("Failed to serialize session", e);
        }
    }
    
    /**
     * 从 JSON 字符串反序列化会话
     * 使用 Map 方式反序列化，因为 Message 是抽象类
     */
    public static Session fromJson(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return Session.fromMap(map);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize session from JSON", e);
            throw new RuntimeException("Failed to deserialize session", e);
        }
    }
    
    /**
     * 将会话转换为 Map（用于序列化）
     * 这是主要的序列化方法，因为 Message 是抽象类，不能直接 JSON 序列化
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("createdAt", createdAt.toString());
        map.put("updatedAt", updatedAt.toString());
        map.put("title", title);
        map.put("workingDirectory", workingDirectory);
        map.put("model", model);
        List<Map<String, Object>> messagesList = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("role", msg.getRole().name());
            msgMap.put("content", msg.getTextContent());
            msgMap.put("timestamp", msg.getTimestamp().toString());
            messagesList.add(msgMap);
        }
        map.put("messages", messagesList);
        map.put("metadata", metadata);
        return map;
    }
    
    /**
     * 从 Map 创建会话（用于反序列化）
     * 这是主要的反序列化方法，因为 Message 是抽象类，不能直接 JSON 反序列化
     */
    public static Session fromMap(Map<String, Object> map) {
        String id = (String) map.get("id");
        String workingDirectory = (String) map.get("workingDirectory");
        Session session = new Session(id, workingDirectory);
        session.title = (String) map.get("title");
        session.model = (String) map.get("model");
        // 恢复消息
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messagesList = (List<Map<String, Object>>) map.get("messages");
        if (messagesList != null) {
            for (Map<String, Object> msgMap : messagesList) {
                String role = (String) msgMap.get("role");
                String content = (String) msgMap.get("content");
                if (role != null && content != null) {
                    Message message;
                    switch (role.toUpperCase()) {
                        case "USER":
                            message = Message.createUserMessage(content);
                            break;
                        case "ASSISTANT":
                            message = Message.createAssistantMessage(content);
                            break;
                        case "SYSTEM":
                            message = Message.createSystemMessage(content);
                            break;
                        default:
                            continue;
                    }
                    session.addMessage(message);
                }
            }
        }
        // 恢复元数据
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) map.get("metadata");
        if (meta != null) {
            session.metadata.putAll(meta);
        }
        return session;
    }
    
    /**
     * 获取日志记录器
     * 
     * @return Logger 实例
     */
    public Logger getLogger() {
        return logger;
    }
    
    /**
     * Fork 当前会话，创建一个新的独立会话
     * 新会话继承当前会话的消息历史和工作目录
     * 
     * @param reason Fork 原因/任务描述
     * @return 新的 Session 实例
     */
    public Session fork(String reason) {
        return SessionFork.from(this, reason).execute();
    }
    
    /**
     * Fork 当前会话，使用默认原因
     * 
     * @return 新的 Session 实例
     */
    public Session fork() {
        return fork("sub-task");
    }
    
    /**
     * 导出会话为指定格式
     * @param format 导出格式: markdown, json, text
     * @return 格式化后的字符串
     */
    public String export(String format) {
        switch (format.toLowerCase()) {
            case "json":
                return toJson();
            case "markdown":
                return exportAsMarkdown();
            case "text":
                return exportAsText();
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }
    
    private String exportAsMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# Session: ").append(title != null ? title : id).append("\n\n");
        md.append("**Created:** ").append(createdAt).append("\n");
        md.append("**Updated:** ").append(updatedAt).append("\n");
        md.append("**Model:** ").append(model != null ? model : "N/A").append("\n");
        md.append("**Messages:** ").append(messages.size()).append("\n\n");
        md.append("---\n\n");
        
        for (Message msg : messages) {
            String role = msg.getRole().name().toLowerCase();
            md.append("**").append(role.substring(0, 1).toUpperCase()).append(role.substring(1)).append(":**\n");
            md.append(msg.getTextContent()).append("\n\n");
        }
        
        md.append("---\n\n");
        md.append("*Exported from JWCode*\n");
        return md.toString();
    }
    
    private String exportAsText() {
        StringBuilder text = new StringBuilder();
        text.append("Session: ").append(title != null ? title : id).append("\n");
        text.append("Created: ").append(createdAt).append("\n");
        text.append("Model: ").append(model != null ? model : "N/A").append("\n\n");
        text.append("=" .repeat(50)).append("\n\n");
        
        for (Message msg : messages) {
            String role = msg.getRole().name();
            text.append("[").append(role).append("]\n");
            text.append(msg.getTextContent()).append("\n\n");
        }
        
        return text.toString();
    }
}
