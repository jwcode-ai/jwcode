package com.jwcode.core.llm;

import java.util.List;

/**
 * LLM 响应
 */
public class LLMResponse {
    
    private String content;
    private String reasoningContent;
    private List<LLMMessage.ToolCall> toolCalls;
    private String finishReason;
    private String model;
    
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    
    private String rawResponse;
    private String errorMessage;
    private String errorCode;
    
    public LLMResponse() {}
    
    public static LLMResponse success(String content) {
        LLMResponse r = new LLMResponse();
        r.setContent(content);
        return r;
    }
    
    public static LLMResponse error(String message) {
        LLMResponse r = new LLMResponse();
        r.setErrorMessage(message);
        r.setContent("");
        return r;
    }
    
    public static LLMResponse error(String code, String message) {
        LLMResponse r = new LLMResponse();
        r.setErrorCode(code);
        r.setErrorMessage(message);
        r.setContent("");
        return r;
    }
    
    // Getters and Setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getReasoningContent() { return reasoningContent; }
    public void setReasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; }
    
    public List<LLMMessage.ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<LLMMessage.ToolCall> toolCalls) { this.toolCalls = toolCalls; }
    
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
    
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    
    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public boolean isSuccess() {
        return errorMessage == null;
    }
    
    public boolean hasError() {
        return errorMessage != null;
    }
    
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
    
    public LLMMessage toAssistantMessage() {
        if (hasToolCalls()) {
            return LLMMessage.assistantWithTools(content, toolCalls, reasoningContent);
        }
        return LLMMessage.assistant(content, reasoningContent);
    }
    
    // ==================== Builder ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String content;
        private String reasoningContent;
        private List<LLMMessage.ToolCall> toolCalls;
        private String finishReason;
        private String model;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private String rawResponse;
        private String errorMessage;
        private String errorCode;
        
        public Builder content(String content) {
            this.content = content;
            return this;
        }
        
        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }
        
        public Builder toolCalls(List<LLMMessage.ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }
        
        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }
        
        public Builder model(String model) {
            this.model = model;
            return this;
        }
        
        public Builder promptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }
        
        public Builder completionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }
        
        public Builder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }
        
        public Builder rawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        
        public LLMResponse build() {
            LLMResponse response = new LLMResponse();
            response.content = this.content;
            response.reasoningContent = this.reasoningContent;
            response.toolCalls = this.toolCalls;
            response.finishReason = this.finishReason;
            response.model = this.model;
            response.promptTokens = this.promptTokens;
            response.completionTokens = this.completionTokens;
            response.totalTokens = this.totalTokens;
            response.rawResponse = this.rawResponse;
            response.errorMessage = this.errorMessage;
            response.errorCode = this.errorCode;
            return response;
        }
    }
}
