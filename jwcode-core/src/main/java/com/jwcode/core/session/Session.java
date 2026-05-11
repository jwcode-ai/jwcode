package com.jwcode.core.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.common.util.Preconditions;
import com.jwcode.core.model.Message;
import com.jwcode.core.task.ActiveTask;

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
    private String workingDirectory;
    private String model;
    private final Map<String, Object> metadata;
    private final List<SessionInsight> insights;
    private final Set<String> importantMessageIds;
    private int compactCount;
    private ActiveTask activeTask;
    private int maxMessageHistory = 0; // 默认 0 表示不限制
    
    public Session(String id, String workingDirectory) {
        this.id = Preconditions.checkNotNull(id, "id cannot be null");
        this.workingDirectory = workingDirectory != null ? workingDirectory : System.getProperty("user.dir");
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.messages = new CopyOnWriteArrayList<>();
        this.metadata = new HashMap<>();
        this.insights = new CopyOnWriteArrayList<>();
        this.importantMessageIds = new HashSet<>();
        this.activeTask = null;
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
        this.insights = new CopyOnWriteArrayList<>();
        this.importantMessageIds = new HashSet<>();
        this.activeTask = null;
        this.model = null;
    }
    
    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; this.updatedAt = Instant.now(); }
    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { 
        this.workingDirectory = workingDirectory != null ? workingDirectory : System.getProperty("user.dir"); 
        this.updatedAt = Instant.now(); 
    }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; this.updatedAt = Instant.now(); }
    
    public Session addMessage(Message message) {
        Preconditions.checkNotNull(message, "message cannot be null");
        messages.add(message);
        this.updatedAt = Instant.now();
        if (maxMessageHistory > 0 && messages.size() > maxMessageHistory) {
            trimToSize();
        }
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
    
    /**
     * 设置消息列表（用于上下文压缩后更新消息）
     * 
     * @param newMessages 压缩后的消息列表
     * @return this
     */
    public Session setMessages(List<Message> newMessages) {
        this.messages.clear();
        if (newMessages != null) {
            this.messages.addAll(newMessages);
        }
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * 移除内容包含指定关键词的系统消息
     * 用于工作目录切换时清除旧的 [ENV_INFO] 消息，确保下次注入获取最新环境信息
     *
     * @param keyword 要匹配的关键词
     * @return 被移除的消息数量
     */
    public int removeSystemMessagesContaining(String keyword) {
        int before = messages.size();
        messages.removeIf(msg ->
            msg.getRole() == Message.Role.SYSTEM
                && msg.getTextContent() != null
                && msg.getTextContent().contains(keyword)
        );
        int removed = before - messages.size();
        if (removed > 0) {
            this.updatedAt = Instant.now();
        }
        return removed;
    }

    /**
     * 标记会话已被压缩，用于通知 LLMQueryEngine 重置 TokenBudget
     */
    public synchronized void markCompacted() {
        this.compactCount++;
        this.updatedAt = Instant.now();
    }

    /**
     * 获取会话被压缩的次数
     */
    public synchronized int getCompactCount() {
        return this.compactCount;
    }

    // ==================== 活的 Session — AI 自我进化记忆 ====================

    /**
     * AI 可以自主记录的洞察
     */
    public record SessionInsight(
        String key,
        String value,
        String sourceMessageId,
        double confidence
    ) {}

    /**
     * 添加 AI 洞察
     */
    public Session addInsight(String key, String value, String sourceMessageId, double confidence) {
        insights.add(new SessionInsight(key, value, sourceMessageId, confidence));
        this.updatedAt = Instant.now();
        return this;
    }

    public List<SessionInsight> getInsights() {
        return Collections.unmodifiableList(new ArrayList<>(insights));
    }

    /**
     * 标记关键消息，用于未来的上下文压缩
     */
    public void markImportant(String messageId, String reason) {
        importantMessageIds.add(messageId);
        logger.debug("Marked message {} as important: {}", messageId, reason);
    }

    public boolean isImportant(String messageId) {
        return importantMessageIds.contains(messageId);
    }

    public Set<String> getImportantMessageIds() {
        return Collections.unmodifiableSet(new HashSet<>(importantMessageIds));
    }

    // ==================== 激活任务管理 ====================

    /**
     * 获取当前激活任务
     */
    public ActiveTask getActiveTask() {
        return activeTask;
    }

    /**
     * 设置当前激活任务
     */
    public Session setActiveTask(ActiveTask activeTask) {
        this.activeTask = activeTask;
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * 设置消息历史最大长度（FIFO 限制）。
     * @param maxMessageHistory 最大消息数，0 表示不限制
     */
    public void setMaxMessageHistory(int maxMessageHistory) {
        this.maxMessageHistory = maxMessageHistory;
    }

    public int getMaxMessageHistory() {
        return maxMessageHistory;
    }

    /**
     * 将消息历史裁剪到 maxMessageHistory 长度。
     * 保留所有 SYSTEM 消息，对非 SYSTEM 消息保留最近的部分。
     * 确保不破坏 tool_calls 与 TOOL 结果的配对。
     */
    public Session trimToSize() {
        if (maxMessageHistory <= 0 || messages.size() <= maxMessageHistory) {
            return this;
        }

        List<Message> systemMessages = new ArrayList<>();
        List<Message> nonSystemMessages = new ArrayList<>();

        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.SYSTEM) {
                systemMessages.add(msg);
            } else {
                nonSystemMessages.add(msg);
            }
        }

        int keepNonSystem = maxMessageHistory - systemMessages.size();
        if (keepNonSystem < 0) {
            // SYSTEM 消息本身已超过上限，只保留最后几个 SYSTEM
            keepNonSystem = 0;
            int sysStart = Math.max(0, systemMessages.size() - maxMessageHistory);
            systemMessages = systemMessages.subList(sysStart, systemMessages.size());
        }

        List<Message> retained = new ArrayList<>(systemMessages);

        if (keepNonSystem > 0 && nonSystemMessages.size() > keepNonSystem) {
            int start = nonSystemMessages.size() - keepNonSystem;
            start = fixToolCallBoundary(nonSystemMessages, start);
            retained.addAll(nonSystemMessages.subList(start, nonSystemMessages.size()));
        } else {
            retained.addAll(nonSystemMessages);
        }

        int beforeSize = messages.size();
        messages.clear();
        messages.addAll(retained);
        this.updatedAt = Instant.now();
        logger.info("[Session] FIFO trim: {} -> {} messages (max={})", beforeSize, retained.size(), maxMessageHistory);
        return this;
    }

    /**
     * 修复截断边界，确保不破坏 tool_calls 配对。
     * 如果起始位置是 TOOL，向前移动到对应的 ASSISTANT。
     */
    private int fixToolCallBoundary(List<Message> list, int start) {
        while (start > 0 && start < list.size()) {
            Message msg = list.get(start);
            if (msg.getRole() != Message.Role.TOOL) {
                break;
            }
            String toolUseId = extractToolUseId(msg);
            if (toolUseId == null) {
                start++;
                continue;
            }
            int assistantIndex = -1;
            for (int i = start - 1; i >= 0; i--) {
                Message candidate = list.get(i);
                if (candidate.getRole() == Message.Role.ASSISTANT && candidate.hasToolCalls()) {
                    for (Message.ToolCallInfo tc : candidate.getToolCalls()) {
                        if (toolUseId.equals(tc.getId())) {
                            assistantIndex = i;
                            break;
                        }
                    }
                    if (assistantIndex >= 0) break;
                }
            }
            if (assistantIndex >= 0) {
                start = assistantIndex;
                // 继续循环，因为新的 start 可能也是 TOOL（多个连续 TOOL 的情况）
            } else {
                start++; // 跳过孤立 TOOL
                break;
            }
        }
        return start;
    }

    private String extractToolUseId(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return null;
        }
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.ToolResultContent) {
                return ((Message.ToolResultContent) block).getToolUseId();
            }
        }
        return null;
    }

    /**
     * 为新任务重置会话上下文。
     * 保留第一条 system message，清空其余消息，重置压缩计数。
     */
    public Session resetForNewTask() {
        // 保存第一条 system message
        Message systemPrompt = null;
        for (Message msg : this.messages) {
            if (msg.getRole() == Message.Role.SYSTEM) {
                systemPrompt = msg;
                break;
            }
        }

        // 清空消息
        clearMessages();

        // 恢复系统提示
        if (systemPrompt != null) {
            addMessage(systemPrompt);
        }

        // 重置压缩计数
        this.compactCount = 0;

        // 清空重要消息标记（保留到新任务中可能有用的）
        this.importantMessageIds.clear();

        // 清空洞察（可选：是否保留跨任务的洞察？当前选择清空）
        this.insights.clear();

        logger.info("[Session] 上下文已重置，保留 system prompt，消息数={}", this.messages.size());
        return this;
    }

    /**
     * 生成结构化压缩摘要
     *
     * <p>当前为启发式实现；未来可接入轻量级 LLM 做智能摘要。</p>
     */
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Session Summary\n\n");
        sb.append("- **Title**: ").append(title != null ? title : "Untitled").append("\n");
        sb.append("- **Messages**: ").append(messages.size()).append("\n");
        sb.append("- **Insights**: ").append(insights.size()).append("\n");
        sb.append("- **Important Messages**: ").append(importantMessageIds.size()).append("\n\n");

        if (!insights.isEmpty()) {
            sb.append("## Key Insights\n\n");
            for (SessionInsight insight : insights) {
                sb.append("- **").append(insight.key()).append("**: ")
                    .append(insight.value())
                    .append(" (confidence=").append(insight.confidence()).append(")\n");
            }
            sb.append("\n");
        }

        if (!importantMessageIds.isEmpty()) {
            sb.append("## Important Messages\n\n");
            for (Message msg : messages) {
                String msgId = msg.getTimestamp().toString(); // 使用时间戳作为简单 ID
                if (importantMessageIds.contains(msgId)) {
                    sb.append("- [").append(msg.getRole()).append("]: ")
                        .append(truncate(msg.getTextContent(), 100)).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
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
            msgMap.put("reasoningContent", msg.getReasoningContent());
            // 序列化 toolCalls
            if (msg.hasToolCalls()) {
                List<Map<String, String>> toolCallsList = new ArrayList<>();
                for (Message.ToolCallInfo tc : msg.getToolCalls()) {
                    Map<String, String> tcMap = new HashMap<>();
                    tcMap.put("id", tc.getId());
                    tcMap.put("name", tc.getName());
                    tcMap.put("arguments", tc.getArguments());
                    toolCallsList.add(tcMap);
                }
                msgMap.put("toolCalls", toolCallsList);
            }
            msgMap.put("timestamp", msg.getTimestamp().toString());
            messagesList.add(msgMap);
        }
        map.put("messages", messagesList);
        map.put("metadata", metadata);
        // 序列化 activeTask（如果有）
        if (activeTask != null) {
            Map<String, Object> taskMap = new HashMap<>();
            taskMap.put("taskId", activeTask.getTaskId());
            taskMap.put("description", activeTask.getDescription());
            taskMap.put("status", activeTask.getStatus().name());
            taskMap.put("currentStepIndex", activeTask.getCurrentStepIndex());
            taskMap.put("waitingFor", activeTask.getWaitingFor());
            taskMap.put("createdAt", activeTask.getCreatedAt().toString());
            taskMap.put("updatedAt", activeTask.getUpdatedAt().toString());
            map.put("activeTask", taskMap);
        }
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
                            String reasoningContent = (String) msgMap.get("reasoningContent");
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> toolCallsList = (List<Map<String, Object>>) msgMap.get("toolCalls");
                            if (toolCallsList != null && !toolCallsList.isEmpty()) {
                                List<Message.ToolCallInfo> toolCalls = new ArrayList<>();
                                for (Map<String, Object> tcMap : toolCallsList) {
                                    String tcId = (String) tcMap.get("id");
                                    String tcName = (String) tcMap.get("name");
                                    String tcArgs = (String) tcMap.get("arguments");
                                    toolCalls.add(new Message.ToolCallInfo(tcId, tcName, tcArgs));
                                }
                                message = Message.createAssistantMessageWithToolCalls(content, toolCalls, reasoningContent);
                            } else {
                                message = Message.createAssistantMessage(content, reasoningContent);
                            }
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
