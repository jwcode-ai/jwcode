package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

/**
 * WhoamiCommand - 显示当前用户信息
 */
public class WhoamiCommand implements Command {
    
    @Override
    public String getName() {
        return "whoami";
    }
    
    @Override
    public String getDescription() {
        return "显示当前用户信息";
    }
    
    @Override
    public String getUsage() {
        return "whoami";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户信息:\n");
        sb.append("  用户名: ").append(System.getProperty("user.name")).append("\n");
        sb.append("  主目录: ").append(System.getProperty("user.home")).append("\n");
        sb.append("  工作目录: ").append(System.getProperty("user.dir")).append("\n");
        
        if (context != null && context.getSession() != null) {
            sb.append("\n会话信息:\n");
            sb.append("  会话ID: ").append(context.getSession().getSessionId()).append("\n");
        }
        
        return CommandResult.success(sb.toString());
    }
}
