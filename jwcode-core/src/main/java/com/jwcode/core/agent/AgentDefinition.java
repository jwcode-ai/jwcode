package com.jwcode.core.agent;

import java.util.List;
import java.util.Map;

/**
 * Agent 定义 - 用于配置化创建 Agent
 * 
 * 支持从 JSON/YAML 文件加载配置
 */
public class AgentDefinition {
    
    private String id;
    private String name;
    private String description;
    private String systemPrompt;
    private List<String> tools;
    private Map<String, Object> config;
    private ModelConfig model;
    private String parent;
    private List<String> disallowedTools;
    
    public static class ModelConfig {
        private String name;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
        public Integer getMaxTokens() { return maxTokens; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
        public Double getTopP() { return topP; }
        public void setTopP(Double topP) { this.topP = topP; }
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
    
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
    
    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }
    
    public String getParent() { return parent; }
    public void setParent(String parent) { this.parent = parent; }
    
    public List<String> getDisallowedTools() { return disallowedTools; }
    public void setDisallowedTools(List<String> disallowedTools) { this.disallowedTools = disallowedTools; }
}
