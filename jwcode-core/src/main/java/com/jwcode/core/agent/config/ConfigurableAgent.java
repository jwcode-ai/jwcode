package com.jwcode.core.agent.config;

import com.jwcode.core.agent.Agent;
import com.jwcode.core.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * ConfigurableAgent - 可配置的 Agent 实现
 * 
 * 功能说明：
 * 基于 AgentConfig 创建的 Agent 实例，支持动态配置。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ConfigurableAgent implements Agent {
    
    private final String id;
    private final String name;
    private final String description;
    private final String systemPrompt;
    private final List<Tool<?, ?, ?>> tools;
    private final Map<String, Object> config;
    private final ModelConfig modelConfig;
    private final AgentConfig.PermissionConfig permissions;
    
    public ConfigurableAgent(String id, String name, String description,
                            String systemPrompt, List<Tool<?, ?, ?>> tools,
                            Map<String, Object> config, ModelConfig modelConfig,
                            AgentConfig.PermissionConfig permissions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.config = config;
        this.modelConfig = modelConfig;
        this.permissions = permissions;
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
    public boolean canUseTool(String toolName) {
        // 检查权限
        if (permissions != null) {
            switch (toolName) {
                case "FileRead":
                    return permissions.isAllowFileRead();
                case "FileWrite":
                    return permissions.isAllowFileWrite();
                case "FileEdit":
                    return permissions.isAllowFileEdit();
                case "Bash":
                case "PowerShell":
                    return permissions.isAllowShell();
                case "WebSearch":
                    return permissions.isAllowWebSearch();
                case "WebFetch":
                    return permissions.isAllowWebFetch();
            }
        }
        
        // 默认检查工具是否在列表中
        return tools.stream().anyMatch(t -> t.getName().equals(toolName));
    }
    
    /**
     * 获取权限配置
     */
    public AgentConfig.PermissionConfig getPermissions() {
        return permissions;
    }
    
    /**
     * 检查是否有文件写入权限
     */
    public boolean canWriteFiles() {
        return permissions == null || permissions.isAllowFileWrite();
    }
    
    /**
     * 检查是否有 Shell 执行权限
     */
    public boolean canExecuteShell() {
        return permissions == null || permissions.isAllowShell();
    }
    
    @Override
    public String toString() {
        return String.format("ConfigurableAgent{id='%s', name='%s', tools=%d}", 
            id, name, tools.size());
    }
}
