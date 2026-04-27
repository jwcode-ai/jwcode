package com.jwcode.core.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM 消息 - OpenAI 兼容格式
 * 
 * OpenAI API 标准格式：
 * - role: system | user | assistant | tool
 * - content: 文本内容（不能为 null）
 * - tool_calls: assistant 消息中的工具调用（可选）
 * - tool_call_id: tool 消息必须（标识哪个工具调用的结果）
 */
public class LLMMessage {
    
    private final Role role;
    private final String content;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;
    private final String reasoningContent;
    
    private LLMMessage(Builder builder) {
        this.role = builder.role;
        this.content = builder.content != null ? builder.content : "";
        this.toolCalls = builder.toolCalls;
        this.toolCallId = builder.toolCallId;
        this.reasoningContent = builder.reasoningContent;
    }
    
    // Getters
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public String getToolCallId() { return toolCallId; }
    public String getReasoningContent() { return reasoningContent; }
    
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
    
    /**
     * 验证消息是否符合 OpenAI 标准
     */
    public boolean isValid() {
        if (role == null) return false;
        if (content == null) return false; // OpenAI 要求 content 不能为 null
        
        // TOOL 角色必须有 tool_call_id
        if (role == Role.TOOL && (toolCallId == null || toolCallId.isEmpty())) {
            return false;
        }
        
        // ASSISTANT 角色如果有 tool_calls，content 可以为空字符串
        if (role == Role.ASSISTANT && hasToolCalls()) {
            return true;
        }
        
        return true;
    }
    
    /**
     * 转换为 OpenAI API 格式
     */
    public Map<String, Object> toOpenAIFormat() {
        Map<String, Object> map = new HashMap<>();
        
        map.put("role", role.getValue());
        map.put("content", content.isEmpty() ? "" : content);
        
        if (role == Role.TOOL) {
            // tool 消息必须包含 tool_call_id，即使为空字符串也要添加
            // 这样可以避免 API 报错 "tool_call_id is not found"
            map.put("tool_call_id", toolCallId != null ? toolCallId : "");
        }
        
        // FIXED: Always include reasoning_content for ASSISTANT messages
        // This ensures DeepSeek API receives reasoning_content in thinking mode
        if (role == Role.ASSISTANT) {
            map.put("reasoning_content", reasoningContent != null ? reasoningContent : "");
        }
        
        if (hasToolCalls()) {
            map.put("tool_calls", toolCalls.stream()
                .map(ToolCall::toOpenAIFormat)
                .collect(Collectors.toList()));
        }
        
        return map;
    }
    
    /**
     * 角色枚举 - OpenAI 标准
     */
    public enum Role {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        TOOL("tool");
        
        private final String value;
        
        Role(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Role role;
        private String content;
        private List<ToolCall> toolCalls;
        private String toolCallId;
        private String reasoningContent;
        
        public Builder role(Role role) {
            this.role = role;
            return this;
        }
        
        public Builder content(String content) {
            this.content = content != null ? content : "";
            return this;
        }
        
        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }
        
        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }
        
        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }
        
        public LLMMessage build() {
            if (role == null) {
                throw new IllegalArgumentException("Role is required");
            }
            return new LLMMessage(this);
        }
    }
    
    // ==================== 便捷工厂方法 ====================
    
    public static LLMMessage system(String content) {
        return builder().role(Role.SYSTEM).content(content).build();
    }
    
    public static LLMMessage user(String content) {
        return builder().role(Role.USER).content(content).build();
    }
    
    public static LLMMessage assistant(String content) {
        return builder().role(Role.ASSISTANT).content(content).build();
    }
    
    public static LLMMessage assistant(String content, String reasoningContent) {
        return builder().role(Role.ASSISTANT).content(content).reasoningContent(reasoningContent).build();
    }
    
    public static LLMMessage assistantWithTools(String content, List<ToolCall> toolCalls) {
        return assistantWithTools(content, toolCalls, null);
    }
    
    public static LLMMessage assistantWithTools(String content, List<ToolCall> toolCalls, String reasoningContent) {
        return builder()
            .role(Role.ASSISTANT)
            .content(content != null ? content : "")
            .toolCalls(toolCalls)
            .reasoningContent(reasoningContent)
            .build();
    }
    
    public static LLMMessage tool(String toolCallId, String content) {
        return builder()
            .role(Role.TOOL)
            .toolCallId(toolCallId)
            .content(content != null ? content : "")
            .build();
    }
    
    // ==================== ToolCall 内部类 ====================
    
    public static class ToolCall {
        private final String id;
        private final String type;
        private final Function function;
        
        private ToolCall(Builder builder) {
            this.id = builder.id;
            this.type = builder.type != null ? builder.type : "function";
            this.function = builder.function;
        }
        
        public String getId() { return id; }
        public String getType() { return type; }
        public Function getFunction() { return function; }
        
        public Map<String, Object> toOpenAIFormat() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("type", type);
            
            Map<String, Object> funcMap = new HashMap<>();
            funcMap.put("name", function.getName());
            funcMap.put("arguments", function.getArguments());
            map.put("function", funcMap);
            
            return map;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String id;
            private String type = "function";
            private Function function;
            
            public Builder id(String id) {
                this.id = id;
                return this;
            }
            
            public Builder type(String type) {
                this.type = type;
                return this;
            }
            
            public Builder function(Function function) {
                this.function = function;
                return this;
            }
            
            public Builder function(String name, String arguments) {
                this.function = new Function(name, arguments);
                return this;
            }
            
            public ToolCall build() {
                if (id == null || function == null) {
                    throw new IllegalArgumentException("ToolCall id and function are required");
                }
                return new ToolCall(this);
            }
        }
        
        public static class Function {
            private final String name;
            private final String arguments;
            
            public Function(String name, String arguments) {
                this.name = name;
                this.arguments = arguments != null ? arguments : "";
            }
            
            public String getName() { return name; }
            public String getArguments() { return arguments; }
        }
    }
}