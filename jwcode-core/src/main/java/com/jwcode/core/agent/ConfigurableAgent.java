package com.jwcode.core.agent;

import com.jwcode.core.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * 可配置 Agent - 从配置文件创建的 Agent
 */
public class ConfigurableAgent implements Agent {
    
    private final String id;
    private final String name;
    private final String description;
    private final String systemPrompt;
    private final List<Tool<?, ?, ?>> tools;
    private final Map<String, Object> config;
    private final ModelConfig modelConfig;
    private final String parentId;
    private final List<String> disallowedTools;
    
    public ConfigurableAgent(String id, String name, String description,
                             String systemPrompt, List<Tool<?, ?, ?>> tools,
                             Map<String, Object> config, ModelConfig modelConfig,
                             String parentId, List<String> disallowedTools) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.config = config != null ? config : Map.of();
        this.modelConfig = modelConfig;
        this.parentId = parentId;
        this.disallowedTools = disallowedTools != null ? disallowedTools : List.of();
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    @Override
    public List<Tool<?, ?, ?>> getTools() {
        return tools;
    }
    
    @Override
    public Map<String, Object> getConfig() {
        return config;
    }
    
    @Override
    public ModelConfig getModelConfig() {
        return modelConfig;
    }
    
    @Override
    public String getParentAgentId() {
        return parentId;
    }
    
    @Override
    public boolean canUseTool(String toolName) {
        return !disallowedTools.contains(toolName);
    }
}
