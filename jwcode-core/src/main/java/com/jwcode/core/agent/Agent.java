package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * Agent 接口 - AI 智能体
 * 
 * 参照 Claude Code 的 Agent 架构
 * 每个 Agent 有独立的：
 * - 系统提示词（System Prompt）
 * - 工具列表
 * - 配置参数
 */
public interface Agent {
    
    /**
     * 获取 Agent ID
     */
    String getId();
    
    /**
     * 获取 Agent 名称
     */
    String getName();
    
    /**
     * 获取描述
     */
    String getDescription();
    
    /**
     * 获取系统提示词
     */
    String getSystemPrompt();
    
    /**
     * 获取可用工具列表
     */
    List<Tool<?, ?, ?>> getTools();
    
    /**
     * 获取配置参数
     */
    Map<String, Object> getConfig();
    
    /**
     * 获取模型配置
     */
    ModelConfig getModelConfig();
    
    /**
     * 检查是否允许使用工具
     */
    boolean canUseTool(String toolName);
    
    /**
     * 获取父 Agent（用于继承）
     */
    default String getParentAgentId() {
        return null;
    }
    
    /**
     * 模型配置
     */
    class ModelConfig {
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        
        public ModelConfig(String model, Double temperature, Integer maxTokens) {
            this.model = model;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
        }
        
        // Getters
        public String getModel() { return model; }
        public Double getTemperature() { return temperature; }
        public Integer getMaxTokens() { return maxTokens; }
        public Double getTopP() { return topP; }
        
        // Setters
        public void setModel(String model) { this.model = model; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
        public void setTopP(Double topP) { this.topP = topP; }
    }
}
