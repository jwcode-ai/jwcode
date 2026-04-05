package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ToolResult - 工具执行结果
 * 
 * 功能说明：
 * 封装工具执行的结果数据，包括返回值、新消息和 MCP 元数据。
 * 
 * 上下文关系：
 * - 由 Tool.call() 方法返回
 * - 被 ToolExecutor 处理并传递给 QueryEngine
 * - 包含的数据会被添加到会话消息历史中
 * 
 * 类型参数：
 * @param <T> 结果数据类型
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ToolResult<T> {
    
    /**
     * 工具执行的主要数据
     */
    private final T data;
    
    /**
     * 执行过程中产生的新消息列表
     */
    private final List<Object> newMessages;
    
    /**
     * MCP 协议元数据
     */
    private McpMeta mcpMeta;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 内容
     */
    private String content;
    
    /**
     * 元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 构造函数
     * 
     * @param data 工具执行数据
     */
    public ToolResult(T data) {
        this.data = data;
        this.newMessages = new ArrayList<>();
    }
    
    /**
     * 构造函数
     * 
     * @param data 工具执行数据
     * @param newMessages 新消息列表
     */
    public ToolResult(T data, List<Object> newMessages) {
        this.data = Objects.requireNonNull(data, "data cannot be null");
        this.newMessages = newMessages != null ? new ArrayList<>(newMessages) : new ArrayList<>();
    }
    
    /**
     * 获取工具执行数据
     * 
     * @return 执行数据
     */
    public T getData() {
        return data;
    }
    
    /**
     * 获取新消息列表
     * 
     * @return 新消息列表（只读）
     */
    public List<Object> getNewMessages() {
        return new ArrayList<>(newMessages);
    }
    
    /**
     * 添加新消息
     * 
     * @param message 要添加的消息
     * @return this（用于链式调用）
     */
    public ToolResult<T> addMessage(Object message) {
        this.newMessages.add(message);
        return this;
    }
    
    /**
     * 获取 MCP 元数据
     * 
     * @return MCP 元数据
     */
    public McpMeta getMcpMeta() {
        return mcpMeta;
    }
    
    /**
     * 设置 MCP 元数据
     * 
     * @param mcpMeta MCP 元数据
     */
    public void setMcpMeta(McpMeta mcpMeta) {
        this.mcpMeta = mcpMeta;
    }
    
    /**
     * 获取是否成功
     * 
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 设置是否成功
     * 
     * @param success 是否成功
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    /**
     * 获取内容
     * 
     * @return 内容
     */
    public String getContent() {
        return content;
    }
    
    /**
     * 设置内容
     * 
     * @param content 内容
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * 获取元数据
     * 
     * @return 元数据
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * 设置元数据
     * 
     * @param metadata 元数据
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    /**
     * 创建错误结果（无参构造函数）
     */
    public ToolResult() {
        this.data = null;
        this.newMessages = new ArrayList<>();
    }
    
    /**
     * 创建错误结果的静态工厂方法
     * 
     * @param errorMessage 错误消息
     * @return ToolResult 实例
     */
    public static <T> ToolResult<T> error(String errorMessage) {
        ToolResult<T> result = new ToolResult<>();
        result.setSuccess(false);
        result.setContent(errorMessage);
        return result;
    }
    
    /**
     * 创建成功结果的静态工厂方法
     * 
     * @param data 结果数据
     * @return ToolResult 实例
     */
    public static <T> ToolResult<T> success(T data) {
        ToolResult<T> result = new ToolResult<>(data);
        result.setSuccess(true);
        return result;
    }
    
    /**
     * 构建器模式创建 ToolResult
     * 
     * @param <T> 数据类型
     */
    public static class Builder<T> {
        private T data;
        private List<Object> newMessages = new ArrayList<>();
        private McpMeta mcpMeta;
        
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }
        
        public Builder<T> addMessage(Object message) {
            this.newMessages.add(message);
            return this;
        }
        
        public Builder<T> mcpMeta(McpMeta mcpMeta) {
            this.mcpMeta = mcpMeta;
            return this;
        }
        
        public ToolResult<T> build() {
            ToolResult<T> result = new ToolResult<>(data, newMessages);
            result.setMcpMeta(mcpMeta);
            return result;
        }
    }
    
    /**
     * 创建构建器
     * 
     * @param <T> 数据类型
     * @return 新的构建器实例
     */
    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }
    
    /**
     * 创建带有明确类型参数的构建器（解决泛型推断问题）
     * 
     * @param type 数据类型类（用于类型推断）
     * @param <T> 数据类型
     * @return 新的构建器实例
     */
    @SuppressWarnings("unused")
    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<T>();
    }
    
    /**
     * MCP 元数据类
     */
    public static class McpMeta {
        private Object meta;
        private Object structuredContent;
        
        public McpMeta() {
        }
        
        public McpMeta(Object meta, Object structuredContent) {
            this.meta = meta;
            this.structuredContent = structuredContent;
        }
        
        public Object getMeta() {
            return meta;
        }
        
        public void setMeta(Object meta) {
            this.meta = meta;
        }
        
        public Object getStructuredContent() {
            return structuredContent;
        }
        
        public void setStructuredContent(Object structuredContent) {
            this.structuredContent = structuredContent;
        }
    }
}
