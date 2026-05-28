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
    private final String reasoningContent; // 保留 reasoning_content 用于 thinking 模式
    // 压缩代际追踪
    private int compactionGeneration = 0;
    private java.util.Set<String> compressedFromIds;

    protected Message(String id, Role role, List<ContentBlock> content) {
        this(id, role, content, null, null);
    }
    
    protected Message(String id, Role role, List<ContentBlock> content, List<ToolCallInfo> toolCalls) {
        this(id, role, content, toolCalls, null);
    }
    
    protected Message(String id, Role role, List<ContentBlock> content, List<ToolCallInfo> toolCalls, String reasoningContent) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
        this.toolCalls = toolCalls;
        this.reasoningContent = reasoningContent;
    }
    
    public String getId() { return id; }
    public Role getRole() { return role; }
    public List<ContentBlock> getContent() { return content; }
    public Instant getTimestamp() { return timestamp; }
    public List<ToolCallInfo> getToolCalls() { return toolCalls; }
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
    public String getReasoningContent() { return reasoningContent; }
    public int getCompactionGeneration() { return compactionGeneration; }
    public void setCompactionGeneration(int gen) { this.compactionGeneration = gen; }
    public java.util.Set<String> getCompressedFromIds() { return compressedFromIds; }
    public void setCompressedFromIds(java.util.Set<String> ids) { this.compressedFromIds = ids; }

    /**
     * 获取消息的文本内容
     * 如果消息包含多个 TextContent，将它们连接起来
     */
    public String getTextContent() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof TextContent) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(((TextContent) block).getText());
            }
        }
        return sb.toString();
    }
    
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
    
    public static Message createAssistantMessage(String content, String reasoningContent) {
        return new Message(null, Role.ASSISTANT, Arrays.asList(new TextContent(content)), null, reasoningContent) {};
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
    
    /**
     * 创建工具结果消息（包含输入参数）
     * @param toolUseId 工具调用ID
     * @param toolName 工具名称
     * @param inputArguments 工具输入参数（JSON格式）
     * @param result 工具执行结果
     */
    public static Message createToolResultMessage(String toolUseId, String toolName, String inputArguments, Object result) {
        return new Message(null, Role.TOOL, Arrays.asList(new ToolResultContent(toolUseId, toolName, inputArguments, result))) {};
    }
    
    public static class ToolResultContent extends ContentBlock {
        private final String toolUseId;
        private final String toolName;
        private final String inputArguments;  // 新增：工具输入参数
        private final Object result;
        
        public ToolResultContent(String toolUseId, String toolName, String inputArguments, Object result) {
            super(ContentType.TOOL_RESULT);
            this.toolUseId = toolUseId;
            this.toolName = toolName;
            this.inputArguments = inputArguments;
            this.result = result;
        }
        
        // 兼容旧版本的构造方法
        public ToolResultContent(String toolUseId, String toolName, Object result) {
            this(toolUseId, toolName, null, result);
        }
        
        public String getToolUseId() { return toolUseId; }
        public String getToolName() { return toolName; }
        public String getInputArguments() { return inputArguments; }
        public Object getResult() { return result; }
        
        /**
         * 获取格式化的完整内容，包含输入和输出
         * 格式：工具: xxx\n输入参数: {json}\n执行结果: {result}
         */
        public String getFormattedContent() {
            StringBuilder sb = new StringBuilder();
            sb.append("工具: ").append(toolName);
            if (inputArguments != null && !inputArguments.isEmpty()) {
                sb.append("\n输入参数: ").append(inputArguments);
            }
            sb.append("\n执行结果: ").append(result != null ? result.toString() : "null");
            return sb.toString();
        }
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
     * 
     * 注意：即使 content 为空，也必须添加一个空字符串的 TextContent，
     * 因为某些 API（如 Moonshot/Kimi）要求 assistant 消息必须有 content 字段
     */
    public static Message createAssistantMessageWithToolCalls(String content, List<ToolCallInfo> toolCalls) {
        return createAssistantMessageWithToolCalls(content, toolCalls, null);
    }
    
    public static Message createAssistantMessageWithToolCalls(String content, List<ToolCallInfo> toolCalls, String reasoningContent) {
        List<ContentBlock> blocks = new ArrayList<>();
        // 必须添加 content，即使是空字符串
        blocks.add(new TextContent(content != null ? content : ""));
        return new Message(null, Role.ASSISTANT, blocks, toolCalls, reasoningContent) {};
    }
}
