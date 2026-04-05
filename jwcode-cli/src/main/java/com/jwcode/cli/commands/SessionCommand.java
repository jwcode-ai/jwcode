package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionCommand - /session 命令
 * 
 * 功能说明：
 * 会话管理，创建、切换、保存、加载会话。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/session", description = "会话管理")
public class SessionCommand implements Runnable {
    
    @Parameters(index = "0", description = "操作类型 (list, new, switch, save, load, delete)", arity = "0..1")
    private String action;
    
    @Parameters(index = "1", description = "会话 ID 或名称", arity = "0..1")
    private String sessionId;
    
    @Option(names = {"-l", "--list"}, description = "列出所有会话")
    private boolean listOnly;
    
    @Option(names = {"-n", "--new"}, description = "创建新会话")
    private boolean createNew;
    
    @Option(names = {"-s", "--save"}, description = "保存当前会话")
    private boolean save;
    
    @Option(names = {"-N", "--name"}, description = "会话名称")
    private String name;
    
    private static final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private static String currentSessionId = "default";
    
    static {
        // 创建默认会话
        sessions.put("default", new SessionInfo("default", "默认会话"));
    }
    
    @Override
    public void run() {
        if (listOnly || action == null) {
            listSessions();
            return;
        }
        
        if (createNew) {
            createSession();
            return;
        }
        
        if (save) {
            saveSession();
            return;
        }
        
        switch (action.toLowerCase()) {
            case "list":
                listSessions();
                break;
            case "new":
                createSession();
                break;
            case "switch":
                switchSession();
                break;
            case "save":
                saveSession();
                break;
            case "load":
                loadSession();
                break;
            case "delete":
                deleteSession();
                break;
            default:
                showHelp();
        }
    }
    
    private void listSessions() {
        System.out.println("=== 会话列表 ===");
        System.out.println();
        
        for (Map.Entry<String, SessionInfo> entry : sessions.entrySet()) {
            String marker = entry.getKey().equals(currentSessionId) ? " * " : "   ";
            SessionInfo info = entry.getValue();
            System.out.println(marker + "[" + info.id + "] " + info.name);
            System.out.println("     创建时间：" + info.createdAt);
        }
        System.out.println();
        System.out.println("当前会话：" + currentSessionId);
    }
    
    private void createSession() {
        String newId = UUID.randomUUID().toString().substring(0, 8);
        String sessionName = name != null ? name : "会话-" + newId;
        
        SessionInfo session = new SessionInfo(newId, sessionName);
        sessions.put(newId, session);
        currentSessionId = newId;
        
        System.out.println("已创建新会话：" + sessionName + " [" + newId + "]");
    }
    
    private void switchSession() {
        if (sessionId == null) {
            System.out.println("错误：需要指定会话 ID");
            System.out.println("用法：/session switch <id>");
            return;
        }
        
        if (!sessions.containsKey(sessionId)) {
            System.out.println("会话不存在：" + sessionId);
            return;
        }
        
        currentSessionId = sessionId;
        System.out.println("已切换到会话：" + sessions.get(sessionId).name);
    }
    
    private void saveSession() {
        System.out.println("当前会话已保存");
        // TODO: 实现实际的保存逻辑
    }
    
    private void loadSession() {
        if (sessionId == null) {
            System.out.println("错误：需要指定会话 ID");
            System.out.println("用法：/session load <id>");
            return;
        }
        
        if (!sessions.containsKey(sessionId)) {
            System.out.println("会话不存在：" + sessionId);
            return;
        }
        
        currentSessionId = sessionId;
        System.out.println("已加载会话：" + sessions.get(sessionId).name);
    }
    
    private void deleteSession() {
        if (sessionId == null) {
            System.out.println("错误：需要指定会话 ID");
            System.out.println("用法：/session delete <id>");
            return;
        }
        
        if (sessionId.equals("default")) {
            System.out.println("不能删除默认会话");
            return;
        }
        
        if (!sessions.containsKey(sessionId)) {
            System.out.println("会话不存在：" + sessionId);
            return;
        }
        
        sessions.remove(sessionId);
        if (currentSessionId.equals(sessionId)) {
            currentSessionId = "default";
        }
        System.out.println("已删除会话：" + sessionId);
    }
    
    private void showHelp() {
        System.out.println("会话管理命令");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /session list              - 列出所有会话");
        System.out.println("  /session new [-N <name>]   - 创建新会话");
        System.out.println("  /session switch <id>       - 切换会话");
        System.out.println("  /session save              - 保存当前会话");
        System.out.println("  /session load <id>         - 加载会话");
        System.out.println("  /session delete <id>       - 删除会话");
    }
    
    /**
     * 会话信息
     */
    public static class SessionInfo {
        public final String id;
        public final String name;
        public final String createdAt;
        
        public SessionInfo(String id, String name) {
            this.id = id;
            this.name = name;
            this.createdAt = java.time.Instant.now().toString();
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "name", name,
                    "createdAt", createdAt
            );
        }
    }
    
    public static String getCurrentSessionId() {
        return currentSessionId;
    }
}