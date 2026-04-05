package com.jwcode.cli.commands;

import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

/**
 * ResumeCommand - /resume 命令
 * 
 * 功能说明：
 * 恢复之前的会话。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/resume", description = "恢复会话")
public class ResumeCommand implements Runnable {
    
    private final SessionManager sessionManager;
    
    @Parameters(index = "0", description = "会话 ID 或搜索词", arity = "0..1")
    private String sessionIdOrSearch;
    
    @Option(names = {"-l", "--list"}, description = "列出所有可恢复的会话")
    private boolean listOnly;
    
    @Option(names = {"-n", "--name"}, description = "按会话名称搜索")
    private String searchByName;
    
    public ResumeCommand() {
        this.sessionManager = new SessionManager();
    }
    
    @Override
    public void run() {
        if (listOnly) {
            listSessions();
            return;
        }
        
        if (sessionIdOrSearch != null) {
            resumeSession(sessionIdOrSearch);
        } else if (searchByName != null) {
            searchSessionsByName(searchByName);
        } else {
            showRecentSessions();
        }
    }
    
    private void listSessions() {
        List<Session> sessions = sessionManager.getAllSessions();
        System.out.println("所有会话：");
        for (Session session : sessions) {
            System.out.printf("  %s - %s (最后更新：%s)\n", 
                    session.getId(),
                    session.getTitle() != null ? session.getTitle() : "无标题",
                    session.getUpdatedAt());
        }
    }
    
    private void resumeSession(String sessionId) {
        Session session = sessionManager.loadSession(sessionId);
        if (session != null) {
            sessionManager.setActiveSession(sessionId);
            System.out.println("已恢复会话：" + sessionId);
            System.out.println("标题：" + (session.getTitle() != null ? session.getTitle() : "无标题"));
            System.out.println("消息数：" + session.getMessageCount());
        } else {
            System.out.println("未找到会话：" + sessionId);
            System.out.println("使用 /resume -l 查看所有可用会话");
        }
    }
    
    private void showRecentSessions() {
        List<Session> recent = sessionManager.getRecentSessions(10);
        if (recent.isEmpty()) {
            System.out.println("没有可恢复的会话");
            return;
        }
        
        System.out.println("最近会话：");
        for (int i = 0; i < recent.size(); i++) {
            Session s = recent.get(i);
            System.out.printf("  [%d] %s - %s\n", 
                    i + 1,
                    s.getId().substring(0, 8),
                    s.getTitle() != null ? s.getTitle() : "无标题");
        }
        System.out.println("\n输入 /resume <会话 ID> 恢复特定会话");
    }
    
    private void searchSessionsByName(String name) {
        List<Session> sessions = sessionManager.getAllSessions();
        System.out.println("搜索 '" + name + "' 的结果：");
        for (Session session : sessions) {
            if (session.getTitle() != null && 
                session.getTitle().toLowerCase().contains(name.toLowerCase())) {
                System.out.printf("  %s - %s\n", 
                        session.getId(),
                        session.getTitle());
            }
        }
    }
}
