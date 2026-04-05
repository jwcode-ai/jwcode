package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 状态命令 - 显示当前会话状态
 */
public class StatusCommand implements Command {
    
    @Override
    public String getName() {
        return "status";
    }
    
    @Override
    public String getDescription() {
        return "显示当前会话状态";
    }
    
    @Override
    public String getUsage() {
        return "status";
    }
    
    @Override
    public CommandResult execute(String[] args, Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                     当前会话状态                             ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        
        if (session != null) {
            sb.append("会话 ID: ").append(session.getId()).append("\n");
            sb.append("消息数: ").append(session.getMessageCount()).append("\n");
            sb.append("模型: ").append(session.getModel()).append("\n");
            sb.append("工作目录: ").append(System.getProperty("user.dir")).append("\n");
        } else {
            sb.append("无活动会话\n");
        }
        
        sb.append("\n系统信息:\n");
        sb.append("  Java 版本: ").append(System.getProperty("java.version")).append("\n");
        sb.append("  操作系统: ").append(System.getProperty("os.name")).append("\n");
        sb.append("  可用内存: ")
            .append(Runtime.getRuntime().freeMemory() / 1024 / 1024)
            .append(" MB\n");
        
        return CommandResult.success(sb.toString());
    }
}
