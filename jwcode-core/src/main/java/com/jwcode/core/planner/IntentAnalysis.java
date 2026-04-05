package com.jwcode.core.planner;

import java.util.HashMap;
import java.util.Map;

/**
 * 意图分析结果
 */
public class IntentAnalysis {
    
    public enum IntentType {
        CREATE,         // 创建类
        DEBUG,          // 调试类
        REFACTOR,       // 重构类
        ANALYZE,        // 分析类
        TEST,           // 测试类
        OPTIMIZE,       // 优化类
        DOCUMENT,       // 文档类
        GENERAL         // 通用类
    }
    
    private IntentType type;
    private double confidence;
    private String rawRequest;
    private Map<String, Object> entities = new HashMap<>();
    
    public IntentAnalysis() {}
    
    // Getters
    public IntentType getType() { return type; }
    public double getConfidence() { return confidence; }
    public String getRawRequest() { return rawRequest; }
    public Map<String, Object> getEntities() { 
        if (entities == null) entities = new HashMap<>();
        return entities; 
    }
    
    // Setters
    public void setType(IntentType type) { this.type = type; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public void setRawRequest(String rawRequest) { this.rawRequest = rawRequest; }
    public void setEntities(Map<String, Object> entities) { this.entities = entities; }
    
    /**
     * 获取实体值
     */
    @SuppressWarnings("unchecked")
    public <T> T getEntity(String key) {
        return (T) entities.get(key);
    }
    
    /**
     * 是否是高置信度
     */
    public boolean isHighConfidence() {
        return confidence >= 0.7;
    }
    
    /**
     * 是否需要确认
     */
    public boolean needsConfirmation() {
        return confidence < 0.5;
    }
    
    // Builder
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private IntentType type;
        private double confidence;
        private String rawRequest;
        private Map<String, Object> entities = new HashMap<>();
        
        public Builder type(IntentType type) { this.type = type; return this; }
        public Builder confidence(double confidence) { this.confidence = confidence; return this; }
        public Builder rawRequest(String rawRequest) { this.rawRequest = rawRequest; return this; }
        public Builder entities(Map<String, Object> entities) { this.entities = entities; return this; }
        
        public IntentAnalysis build() {
            IntentAnalysis analysis = new IntentAnalysis();
            analysis.type = this.type;
            analysis.confidence = this.confidence;
            analysis.rawRequest = this.rawRequest;
            analysis.entities = this.entities;
            return analysis;
        }
    }
}
