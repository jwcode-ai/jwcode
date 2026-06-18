package com.jwcode.core.command;

import com.jwcode.core.session.Session;

import java.util.List;

/**
 * 命令接口 - 参照 Claude Code 的命令系统
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface Command {
    
    /**
     * 获取命令名称
     */
    String getName();
    
    /**
     * 获取命令别名
     */
    default List<String> getAliases() {
        return List.of();
    }
    
    /**
     * 获取命令描述
     */
    String getDescription();
    
    /**
     * 获取命令用法
     */
    String getUsage();
    
    /**
     * 执行命令
     * 
     * @param args 命令参数
     * @param session 当前会话
     * @return 命令执行结果
     */
    CommandResult execute(String[] args, Session session);
    
    /**
     * 是否需要交互式会话
     */
    default boolean requiresInteractive() {
        return false;
    }
    
    /**
     * 是否需要确认
     */
    default boolean requiresConfirmation() {
        return false;
    }

    /** Logical category, used for grouping in command manifests. */
    default String getCategory() {
        return "core";
    }

    /** Whether the command requires an argument to run. */
    default boolean requiresArgs() {
        return false;
    }

    /** Origin source of the command implementation. */
    default CommandSource getSource() {
        return CommandSource.CORE;
    }
}
