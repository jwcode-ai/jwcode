package com.jwcode.core.llm;

/**
 * LLM 服务测试结果
 */
public class LLMTestResult {
    
    private boolean available;
    private String message;
    private long latencyMs;
    private String errorMessage;
    private String suggestion;
    
    public LLMTestResult() {}
    
    public static LLMTestResult success(String message, long latencyMs) {
        LLMTestResult r = new LLMTestResult();
        r.setAvailable(true);
        r.setMessage(message);
        r.setLatencyMs(latencyMs);
        return r;
    }
    
    public static LLMTestResult error(String errorMessage, String suggestion) {
        LLMTestResult r = new LLMTestResult();
        r.setAvailable(false);
        r.setMessage("Test failed: " + errorMessage);
        r.setErrorMessage(errorMessage);
        r.setSuggestion(suggestion);
        return r;
    }
    
    // Getters and Setters
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    
    public boolean hasSuggestion() {
        return suggestion != null && !suggestion.isEmpty();
    }
}
