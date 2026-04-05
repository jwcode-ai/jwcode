package com.jwcode.core.service;

import com.jwcode.core.model.Message;
import com.jwcode.core.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * ApiRequest - API 请求
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ApiRequest {
    
    private String method;
    private String path;
    private Map<String, Object> body;
    private Map<String, String> headers;
    private String model;
    private List<Message> messages;
    private List<Tool<?, ?, ?>> tools;
    
    public ApiRequest() {
    }
    
    public ApiRequest(String method, String path, Map<String, Object> body) {
        this.method = method;
        this.path = path;
        this.body = body;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public Map<String, Object> getBody() {
        return body;
    }
    
    public void setBody(Map<String, Object> body) {
        this.body = body;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public List<Message> getMessages() {
        return messages;
    }
    
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
    
    public List<Tool<?, ?, ?>> getTools() {
        return tools;
    }
    
    public void setTools(List<Tool<?, ?, ?>> tools) {
        this.tools = tools;
    }
    
    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ApiRequest request = new ApiRequest();
        
        public Builder method(String method) {
            request.setMethod(method);
            return this;
        }
        
        public Builder path(String path) {
            request.setPath(path);
            return this;
        }
        
        public Builder body(Map<String, Object> body) {
            request.setBody(body);
            return this;
        }
        
        public Builder headers(Map<String, String> headers) {
            request.setHeaders(headers);
            return this;
        }
        
        public Builder model(String model) {
            request.setModel(model);
            return this;
        }
        
        public Builder messages(List<Message> messages) {
            request.setMessages(messages);
            return this;
        }
        
        public Builder tools(List<Tool<?, ?, ?>> tools) {
            request.setTools(tools);
            return this;
        }
        
        public ApiRequest build() {
            return request;
        }
    }
}
