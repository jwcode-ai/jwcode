package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

import java.util.Map;

/**
 * HelpCommand - help 命令
 * 
 * 功能说明：
 * 显示可用命令和功能的帮助信息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class HelpCommand implements Command {
    
    private final Map<String, Command> commands;
    
    public HelpCommand(Map<String, Command> commands) {
        this.commands = commands;
    }
    
    @Override
    public String getName() {
        return "help";
    }
    
    @Override
    public String getDescription() {
        return "显示帮助信息";
    }
    
    @Override
    public String getUsage() {
        return "help [命令名]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("JWCode 帮助信息\n");
        sb.append("================\n");
        sb.append("\n");
        sb.append("可用命令：\n");
        
        if (commands != null && !commands.isEmpty()) {
            for (Command cmd : commands.values()) {
                sb.append(String.format("  %-12s - %s\n", cmd.getName(), cmd.getDescription()));
            }
        } else {
            sb.append("  help         - 显示帮助信息\n");
            sb.append("  exit         - 退出程序\n");
            sb.append("  clear        - 清除当前会话\n");
        }
        
        sb.append("\n");
        sb.append("使用方式：\n");
        sb.append("  jwcode         - 启动交互模式\n");
        sb.append("  jwcode <命令>  - 执行单个命令\n");
        
        return CommandResult.success(sb.toString());
    }
}
