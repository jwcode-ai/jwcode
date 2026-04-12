package com.jwcode.cli;

/**
 * 命令接口 - 定义所有命令的基本行为
 * 
 * 所有 CLI 命令都需要实现此接口，以提供统一的命令执行方式。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface Command {
    
    /**
     * 获取命令名称
     * 
     * @return 命令名称，如 "clear", "help" 等
     */
    String getName();
    
    /**
     * 获取命令描述
     * 
     * @return 命令的简短描述
     */
    String getDescription();
    
    /**
     * 获取命令用法
     * 
     * @return 命令的用法说明
     */
    String getUsage();
    
    /**
     * 执行命令
     * 
     * @param args 命令参数
     * @param context 命令执行上下文
     * @return 命令执行结果
     */
    CommandResult execute(String args, CommandContext context);
    
    /**
     * 获取命令别名
     * 
     * @return 命令别名数组
     */
    default String[] getAliases() {
        return new String[0];
    }
}
