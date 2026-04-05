package com.jwcode.core.command;

import com.jwcode.core.session.Session;

import java.util.Arrays;

/**
 * 命令执行器 - 解析和执行命令
 */
public class CommandExecutor {
    
    private final CommandRegistry registry;
    
    public CommandExecutor(CommandRegistry registry) {
        this.registry = registry;
    }
    
    /**
     * 执行命令字符串
     * 
     * @param input 用户输入（如 "help config"）
     * @param session 当前会话
     * @return 命令执行结果
     */
    public CommandResult execute(String input, Session session) {
        if (input == null || input.trim().isEmpty()) {
            return CommandResult.error("空命令");
        }
        
        // 解析命令
        String[] parts = parseCommand(input.trim());
        if (parts.length == 0) {
            return CommandResult.error("空命令");
        }
        
        String commandName = parts[0];
        String[] args = parts.length > 1 ? 
            Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
        
        // 查找命令
        Command command = registry.getCommand(commandName);
        if (command == null) {
            return CommandResult.error("未知命令: " + commandName + 
                "\n使用 'help' 查看可用命令");
        }
        
        // 执行命令
        try {
            return command.execute(args, session);
        } catch (Exception e) {
            return CommandResult.error("命令执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查输入是否是命令
     */
    public boolean isCommand(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = input.trim();
        
        // 以 / 开头的是命令
        if (trimmed.startsWith("/")) {
            return true;
        }
        
        // 检查是否是已知命令
        String[] parts = parseCommand(trimmed);
        if (parts.length > 0) {
            return registry.hasCommand(parts[0]);
        }
        
        return false;
    }
    
    /**
     * 解析命令字符串
     */
    private String[] parseCommand(String input) {
        // 移除前导 / 如果存在
        String trimmed = input.startsWith("/") ? input.substring(1) : input;
        
        // 简单分割（支持引号内的空格需要更复杂的解析）
        return trimmed.split("\\s+");
    }
    
    /**
     * 获取命令注册表
     */
    public CommandRegistry getRegistry() {
        return registry;
    }
    
    /**
     * 创建默认执行器
     */
    public static CommandExecutor createDefault() {
        return new CommandExecutor(CommandRegistry.createDefault());
    }
}
