package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 清除命令 - 清除屏幕或会话历史
 */
public class ClearCommand implements Command {
    
    @Override
    public String getName() {
        return "clear";
    }
    
    @Override
    public java.util.List<String> getAliases() {
        return java.util.List.of("cls");
    }
    
    @Override
    public String getDescription() {
        return "清除屏幕或会话历史";
    }
    
    @Override
    public String getUsage() {
        return "clear [history]";
    }
    
    @Override
    public CommandResult execute(String[] args, Session session) {
        if (args.length > 0 && "history".equals(args[0])) {
            // 清除会话历史
            if (session != null) {
                session.clearMessages();
                return CommandResult.success("会话历史已清除");
            }
            return CommandResult.error("无活动会话");
        }
        
        // 清屏
        System.out.print("\033[H\033[2J");
        System.out.flush();
        
        return CommandResult.success("屏幕已清除");
    }
}
