package com.jwcode.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.model.Message;

import java.util.List;
import java.util.Map;

/**
 * ApiResponse - API 响应
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ApiResponse {
    
    private boolean success;
    private String message;
    private Map<String, Object> data;
    private int statusCode;
    private List<ToolUse> toolUses;
    private Message messageObj;
    private String errorMessage;
    private String content;
    
    public ApiResponse() {
    }
    
    public ApiResponse(boolean success, String message, Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
    
    public List<ToolUse> getToolUses() {
        return toolUses;
    }
    
    public void setToolUses(List<ToolUse> toolUses) {
        this.toolUses = toolUses;
    }
    
    public Message getMessageObj() {
        return messageObj;
    }
    
    public void setMessageObj(Message messageObj) {
        this.messageObj = messageObj;
    }
    
    /**
     * 检查响应是否包含工具调用
     * 
     * @return true 如果包含工具调用
     */
    public boolean hasToolUse() {
        return toolUses != null && !toolUses.isEmpty();
    }
    
    /**
     * 将响应转换为消息
     * 
     * @return 消息对象
     */
    public Message toMessage() {
        if (messageObj != null) {
            return messageObj;
        }
        // 如果没有消息对象，创建一个基于响应内容的消息
        return Message.createAssistantMessage(message != null ? message : "");
    }
    
    /**
     * 获取字符串数据
     * 
     * @param key 键
     * @return 字符串值
     */
    public String getString(String key) {
        if (data == null || !data.containsKey(key)) {
            return null;
        }
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 获取字符串数据（带默认值）
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 字符串值
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 获取布尔值数据
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 布尔值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        if (data == null || !data.containsKey(key)) {
            return defaultValue;
        }
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 检查是否有错误
     */
    public boolean hasError() {
        return !success || errorMessage != null;
    }
    
    /**
     * 获取错误消息
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 设置错误消息
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * 获取内容
     */
    public String getContent() {
        return content;
    }
    
    /**
     * 设置内容
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * 创建builder
     */
    public static ApiResponseBuilder builder() {
        return new ApiResponseBuilder();
    }
    
    /**
     * 工具使用信息
     */
    public static class ToolUse {
        private String toolName;
        private Map<String, Object> parameters;
        private String toolUseId;
        
        public String getToolName() {
            return toolName;
        }
        
        public void setToolName(String toolName) {
            this.toolName = toolName;
        }
        
        public Map<String, Object> getParameters() {
            return parameters;
        }
        
        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
        
        public String getToolUseId() {
            return toolUseId;
        }
        
        public void setToolUseId(String toolUseId) {
            this.toolUseId = toolUseId;
        }
        
        /**
         * 获取工具名称（兼容性方法）
         */
        public String getName() {
            return toolName;
        }
        
        /**
         * 获取工具ID（兼容性方法）
         */
        public String getId() {
            return toolUseId;
        }
        
        /**
         * 获取输入参数（兼容性方法）
         */
        public Map<String, Object> getInput() {
            return parameters;
        }
    }
    
    /**
     * ApiResponse Builder
     */
    public static class ApiResponseBuilder {
        private final ApiResponse response = new ApiResponse();
        
        public ApiResponseBuilder success(boolean success) {
            response.setSuccess(success);
            return this;
        }
        
        public ApiResponseBuilder message(String message) {
            response.setMessage(message);
            return this;
        }
        
        public ApiResponseBuilder errorMessage(String errorMessage) {
            response.setErrorMessage(errorMessage);
            return this;
        }
        
        public ApiResponseBuilder content(String content) {
            response.setContent(content);
            return this;
        }
        
        public ApiResponseBuilder data(Map<String, Object> data) {
            response.setData(data);
            return this;
        }
        
        public ApiResponseBuilder statusCode(int statusCode) {
            response.setStatusCode(statusCode);
            return this;
        }
        
        public ApiResponseBuilder toolUses(List<ToolUse> toolUses) {
            response.setToolUses(toolUses);
            return this;
        }
        
        public ApiResponseBuilder messageObj(Message messageObj) {
            response.setMessageObj(messageObj);
            return this;
        }
        
        /**
         * 添加工具使用信息
         * 
         * 注意：arguments 可能是 JSON 对象或 JSON 字符串（取决于 API 格式）
         * 需要正确处理这两种情况
         */
        public ApiResponseBuilder addToolUse(String toolCallId, String toolName, JsonNode arguments) {
            if (response.toolUses == null) {
                response.toolUses = new java.util.ArrayList<>();
            }
            ToolUse toolUse = new ToolUse();
            toolUse.setToolUseId(toolCallId);
            toolUse.setToolName(toolName);
            
            // 将 JsonNode 转换为 Map
            // 处理两种情况：arguments 是对象节点或字符串节点（JSON 字符串）
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> params;
                
                if (arguments == null || arguments.isNull()) {
                    // 空值处理
                    params = new java.util.HashMap<>();
                } else if (arguments.isObject()) {
                    // arguments 是 JSON 对象，直接转换
                    params = mapper.convertValue(arguments, Map.class);
                } else if (arguments.isTextual()) {
                    // arguments 是 JSON 字符串（如 OpenAI API 格式），需要解析
                    String argsStr = arguments.asText();
                    if (argsStr != null && !argsStr.trim().isEmpty()) {
                        params = mapper.readValue(argsStr, Map.class);
                    } else {
                        params = new java.util.HashMap<>();
                    }
                } else {
                    // 其他类型，尝试转换
                    params = mapper.convertValue(arguments, Map.class);
                }
                
                toolUse.setParameters(params);
            } catch (Exception e) {
                // 如果转换失败，使用空 Map 并记录错误
                System.err.println("[警告] 工具参数解析失败: " + toolName + " - " + e.getMessage());
                toolUse.setParameters(new java.util.HashMap<>());
            }
            response.toolUses.add(toolUse);
            return this;
        }
        
        public ApiResponse build() {
            return response;
        }
    }
}
