package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

/**
 * /exit 命令 - 退出应用
 * 
 * 退出 JWCode 应用，可以选择保存会话或执行清理操作。
 */
public class ExitCommand implements Command {
    
    @Override
    public String getName() {
        return "exit";
    }
    
    @Override
    public String getDescription() {
        return "退出应用";
    }
    
    @Override
    public String getUsage() {
        return "/exit [--save] [--force] 或 /quit [--save]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        ExitOptions options = parseArgs(args);
        
        // 检查是否有未完成的任务
        if (!options.force && hasUnfinishedTasks(context)) {
            return CommandResult.error(
                "还有未完成的任务。使用 /exit --force 强制退出，或先完成任务。\n" +
                "使用 /tasks 查看任务列表。"
            );
        }
        
        // 保存会话（如果请求）
        if (options.save) {
            saveSession(context);
        }
        
        // 执行清理
        cleanup(context);
        
        // 退出
        StringBuilder output = new StringBuilder();
        output.append("感谢使用 JWCode!\n\n");
        
        if (options.save) {
            output.append("会话已保存。\n");
        }
        
        output.append("再见！\n");
        
        // 设置退出标志
        context.getSession().setExitRequested(true);
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 解析参数
     */
    private ExitOptions parseArgs(String args) {
        ExitOptions options = new ExitOptions();
        
        if (args == null || args.trim().isEmpty()) {
            return options;
        }
        
        String[] parts = args.trim().split("\\s+");
        
        for (String part : parts) {
            if ("--save".equals(part) || "-s".equals(part)) {
                options.save = true;
            } else if ("--force".equals(part) || "-f".equals(part)) {
                options.force = true;
            }
        }
        
        return options;
    }
    
    /**
     * 检查是否有未完成的任务
     */
    private boolean hasUnfinishedTasks(CommandContext context) {
        // 检查是否有进行中的任务
        return context.getSession().hasPendingTasks();
    }
    
    /**
     * 保存会话
     */
    private void saveSession(CommandContext context) {
        try {
            context.getSession().save();
        } catch (Exception e) {
            // 记录保存失败，但不阻止退出
            System.err.println("警告：会话保存失败：" + e.getMessage());
        }
    }
    
    /**
     * 执行清理
     */
    private void cleanup(CommandContext context) {
        try {
            // 关闭 Agent
            context.getSession().shutdownAgents();
            
            // 关闭 MCP 连接
            context.getSession().closeMcpConnections();
            
            // 释放其他资源
            context.getSession().releaseResources();
            
        } catch (Exception e) {
            // 记录清理失败，但不阻止退出
            System.err.println("警告：清理失败：" + e.getMessage());
        }
    }
    
    /**
     * 退出选项类
     */
    private static class ExitOptions {
        boolean save;
        boolean force;
    }
}