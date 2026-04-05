package com.jwcode.core.command;

import com.jwcode.core.config.ConfigManager;
import com.jwcode.core.session.Session;

/**
 * 配置命令 - 管理应用程序配置
 * 
 * 支持的操作:
 * - config get <key>       获取配置项
 * - config set <key> <value> 设置配置项
 * - config list            列出所有配置
 * - config delete <key>    删除配置项
 */
public class ConfigCommand implements Command {
    
    @Override
    public String getName() {
        return "config";
    }
    
    @Override
    public String getDescription() {
        return "管理应用程序配置";
    }
    
    @Override
    public String getUsage() {
        return "config <get|set|list|delete> [key] [value]";
    }
    
    @Override
    public CommandResult execute(String[] args, Session session) {
        if (args.length == 0) {
            return CommandResult.error("请指定操作: get, set, list, delete");
        }
        
        String action = args[0];
        ConfigManager config = ConfigManager.getInstance();
        
        switch (action) {
            case "get":
                if (args.length < 2) {
                    return CommandResult.error("请指定配置键");
                }
                String value = config.get(args[1]);
                if (value == null) {
                    return CommandResult.error("配置项不存在: " + args[1]);
                }
                return CommandResult.success(args[1] + " = " + value);
                
            case "set":
                if (args.length < 3) {
                    return CommandResult.error("请指定配置键和值");
                }
                config.set(args[1], args[2]);
                return CommandResult.success("配置已更新: " + args[1] + " = " + args[2]);
                
            case "list":
                StringBuilder sb = new StringBuilder();
                sb.append("当前配置:\n");
                config.getAll().forEach((k, v) -> {
                    sb.append("  ").append(k).append(" = ").append(v).append("\n");
                });
                return CommandResult.success(sb.toString());
                
            case "delete":
                if (args.length < 2) {
                    return CommandResult.error("请指定配置键");
                }
                config.delete(args[1]);
                return CommandResult.success("配置已删除: " + args[1]);
                
            default:
                return CommandResult.error("未知操作: " + action);
        }
    }
}
