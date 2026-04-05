package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 退出命令
 */
public class ExitCommand implements Command {
    
    @Override
    public String getName() {
        return "exit";
    }
    
    @Override
    public java.util.List<String> getAliases() {
        return java.util.List.of("quit", "q");
    }
    
    @Override
    public String getDescription() {
        return "退出程序";
    }
    
    @Override
    public String getUsage() {
        return "exit";
    }
    
    @Override
    public CommandResult execute(String[] args, Session session) {
        System.out.println("再见！");
        System.exit(0);
        return CommandResult.success("已退出"); // 不会执行到这里
    }
}
