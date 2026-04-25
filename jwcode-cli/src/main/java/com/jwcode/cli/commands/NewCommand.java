package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;

import java.util.Scanner;

/**
 * NewCommand - /new 命令
 * 
 * 功能说明：
 * 强制创建新会话，放弃当前会话。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class NewCommand implements Command {
    
    private boolean force;
    private String name;
    private final SessionManager sessionManager;
    
    public NewCommand() {
        this.sessionManager = SessionManager.getInstance();
    }
    
    @Override
    public String getName() {
        return "new";
    }
    
    @Override
    public String getDescription() {
        return "创建新会话";
    }
    
    @Override
    public String getUsage() {
        return "/new [-f|--force] [-N|--name <名称>]";
    }
    
    @Override
    public String[] getAliases() {
        return new String[] { "n" };
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        // 解析参数
        parseArgs(args);
        
        // 获取当前活动会话
        Session currentSession = sessionManager.getActiveSession();
        
        // 如果有当前会话且非强制模式，询问确认
        if (currentSession != null && !force && currentSession.getMessageCount() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append(CliLogger.YELLOW).append("⚠️  当前有活动会话，未保存的消息将丢失").append(CliLogger.RESET).append("\n");
            sb.append("  当前会话: ").append(currentSession.getTitle() != null ? currentSession.getTitle() : currentSession.getId().substring(0, 8)).append("\n");
            sb.append("  消息数: ").append(currentSession.getMessageCount()).append("\n\n");
            sb.append("确认创建新会话? (y/N): ");
            
            System.out.print(sb.toString());
            Scanner scanner = new Scanner(System.in);
            String confirm = scanner.nextLine().trim().toLowerCase();
            
            if (!confirm.equals("y") && !confirm.equals("是")) {
                return CommandResult.success("已取消");
            }
        }
        
        // 创建新会话
        Session newSession = sessionManager.createSession(System.getProperty("user.dir"));
        
        if (name != null && !name.isEmpty()) {
            newSession.setTitle(name);
        } else {
            newSession.setTitle("新会话 " + newSession.getId().substring(0, 8));
        }
        
        // 保存新会话
        sessionManager.saveSession(newSession);
        sessionManager.setActiveSession(newSession.getId());
        
        StringBuilder sb = new StringBuilder();
        sb.append(CliLogger.GREEN).append("✓ 已创建新会话").append(CliLogger.RESET).append("\n");
        sb.append("  ID:   ").append(newSession.getId()).append("\n");
        sb.append("  名称: ").append(newSession.getTitle()).append("\n");
        
        return CommandResult.success(sb.toString());
    }
    
    private void parseArgs(String args) {
        if (args == null || args.isEmpty()) {
            return;
        }
        
        String[] parts = args.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            switch (parts[i]) {
                case "-f":
                case "--force":
                    force = true;
                    break;
                case "-N":
                case "--name":
                    if (i + 1 < parts.length) {
                        name = parts[++i];
                    }
                    break;
            }
        }
    }
}
