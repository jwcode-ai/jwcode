package com.jwcode.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Message - 消息基类
 * 
 * 功能说明：
 * 表示对话中的一条消息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public abstract class Message {
    
    private final String id;
    private final Role role;
    private final List<ContentBlock> content;
    private final Instant timestamp;
    private final List<ToolCallInfo> toolCalls; // 保留 tool_calls 信息用于 API 请求
    
    protected Message(String id, Role role, List<ContentBlock> content) {
        this(id, role, content, null);
    }
    
    protected Message(String id, Role role, List<ContentBlock> content, List<ToolCallInfo> toolCalls) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
        this.toolCalls = toolCalls;
    }
    
    public String getId() { return id; }
    public Role getRole() { return role; }
    public List<ContentBlock> getContent() { return content; }
    public Instant getTimestamp() { return timestamp; }
    public List<ToolCallInfo> getToolCalls() { return toolCalls; }
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
    
    public enum Role { 
        USER("user"), 
        ASSISTANT("assistant"), 
        SYSTEM("system"),
        TOOL("tool");  // 工具结果消息使用 TOOL role
        
        private final String value;
        
        Role(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    public static abstract class ContentBlock {
        private final ContentType type;
        protected ContentBlock(ContentType type) { this.type = type; }
        public ContentType getType() { return type; }
        public enum ContentType { TEXT, IMAGE, TOOL_USE, TOOL_RESULT }
    }
    
    public static class TextContent extends ContentBlock {
        private final String text;
        public TextContent(String text) { super(ContentType.TEXT); this.text = text; }
        public String getText() { return text; }
    }
    
    public static Message createUserMessage(String content) {
        return new Message(null, Role.USER, Arrays.asList(new TextContent(content))) {};
    }
    
    public static Message createAssistantMessage(String content) {
        return new Message(null, Role.ASSISTANT, Arrays.asList(new TextContent(content))) {};
    }
    
    public static Message createSystemMessage(String content) {
        return new Message(null, Role.SYSTEM, Arrays.asList(new TextContent(content))) {};
    }
    
    /**
     * 创建工具结果消息
     * 注意: 使用 Role.TOOL 而不是 ASSISTANT，因为 API 要求工具结果消息的 role 为 "tool"
     */
    public static Message createToolResultMessage(String toolUseId, String toolName, Object result) {
        return new Message(null, Role.TOOL, Arrays.asList(new ToolResultContent(toolUseId, toolName, result))) {};
    }
    
    public static class ToolResultContent extends ContentBlock {
        private final String toolUseId;
        private final String toolName;
        private final Object result;
        
        public ToolResultContent(String toolUseId, String toolName, Object result) {
            super(ContentType.TOOL_RESULT);
            this.toolUseId = toolUseId;
            this.toolName = toolName;
            this.result = result;
        }
        
        public String getToolUseId() { return toolUseId; }
        public String getToolName() { return toolName; }
        public Object getResult() { return result; }
    }
    
    /**
     * 工具调用信息
     * 用于在 assistant 消息中保留 tool_calls 信息，以便后续请求时使用
     */
    public static class ToolCallInfo {
        private final String id;
        private final String name;
        private final String arguments;
        
        public ToolCallInfo(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getArguments() { return arguments; }
    }
    
    /**
     * 创建包含工具调用的 assistant 消息
     * 这是修复 tool_call_id not found 问题的关键方法
     */
    public static Message createAssistantMessageWithToolCalls(String content, List<ToolCallInfo> toolCalls) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (content != null && !content.isEmpty()) {
            blocks.add(new TextContent(content));
        }
        return new Message(null, Role.ASSISTANT, blocks, toolCalls) {};
    }
}
