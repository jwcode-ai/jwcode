package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.ConfigManager;

/**
 * ConfigCommand - 配置命令
 * 
 * 管理 JWCode 的配置，包括 API 密钥设置
 */
public class ConfigCommand implements Command {
    
    private final ConfigManager configManager;
    
    public ConfigCommand() {
        this.configManager = new ConfigManager();
    }
    
    @Override
    public String getName() {
        return "config";
    }
    
    @Override
    public String getDescription() {
        return "配置管理（设置 API 密钥等）";
    }
    
    @Override
    public String getUsage() {
        return "config [set-key <key>|set-endpoint <url>|set-model <model>|show]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            return showConfig();
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String subCommand = parts[0];
        String value = parts.length > 1 ? parts[1] : "";
        
        switch (subCommand) {
            case "set-key":
                return setApiKey(value);
            case "set-endpoint":
                return setEndpoint(value);
            case "set-model":
                return setModel(value);
            case "show":
                return showConfig();
            default:
                return CommandResult.error("未知子命令: " + subCommand + "\n用法: " + getUsage());
        }
    }
    
    private CommandResult setApiKey(String key) {
        if (key.isEmpty()) {
            return CommandResult.error("请提供 API 密钥\n用法: config set-key <your-api-key>");
        }
        
        configManager.setApiKey(key);
        configManager.saveConfig();
        
        StringBuilder sb = new StringBuilder();
        sb.append("✓ API 密钥已设置\n");
        sb.append("  配置文件位置: ").append(configManager.getConfigPath()).append("\n");
        sb.append("  模型: ").append(configManager.getModel()).append("\n");
        sb.append("  端点: ").append(configManager.getApiEndpoint()).append("\n");
        
        return CommandResult.success(sb.toString());
    }
    
    private CommandResult setEndpoint(String endpoint) {
        if (endpoint.isEmpty()) {
            return CommandResult.error("请提供 API 端点 URL\n用法: config set-endpoint <url>");
        }
        
        configManager.setApiEndpoint(endpoint);
        configManager.saveConfig();
        
        return CommandResult.success("✓ API 端点已设置为: " + endpoint);
    }
    
    private CommandResult setModel(String model) {
        if (model.isEmpty()) {
            return CommandResult.error("请提供模型名称\n用法: config set-model <model>");
        }
        
        configManager.setModel(model);
        configManager.saveConfig();
        
        return CommandResult.success("✓ 模型已设置为: " + model);
    }
    
    private CommandResult showConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("JWCode 配置信息\n");
        sb.append("================\n\n");
        
        String apiKey = configManager.getApiKey();
        sb.append("API 密钥: ").append(apiKey.isEmpty() ? "未设置 ⚠️" : "已设置 ✓").append("\n");
        if (!apiKey.isEmpty()) {
            sb.append("  密钥前缀: ").append(apiKey.substring(0, Math.min(10, apiKey.length()))).append("...\n");
        }
        
        sb.append("API 端点: ").append(configManager.getApiEndpoint()).append("\n");
        sb.append("模型: ").append(configManager.getModel()).append("\n");
        sb.append("配置文件: ").append(configManager.getConfigPath()).append("\n\n");
        
        if (apiKey.isEmpty()) {
            sb.append("提示: 使用 'config set-key <your-api-key>' 设置 API 密钥\n");
        }
        
        return CommandResult.success(sb.toString());
    }
}
