package com.jwcode.cli.commands;

import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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
    
    @Parameters(index = "0", description = "操作类型 (list, new, switch, save, load, delete, export, info)", arity = "0..1")
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
    
    @Option(names = {"-f", "--format"}, description = "导出格式 (markdown, json, text)", defaultValue = "markdown")
    private String exportFormat;
    
    @Option(names = {"-o", "--output"}, description = "导出文件路径")
    private String outputPath;
    
    private final SessionManager sessionManager;
    
    public SessionCommand() {
        this.sessionManager = SessionManager.getInstance();
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
            saveCurrentSession();
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
                saveCurrentSession();
                break;
            case "load":
                loadSession();
                break;
            case "delete":
                deleteSession();
                break;
            case "export":
                exportSession();
                break;
            case "info":
                showSessionInfo();
                break;
            case "clean":
                cleanOldSessions();
                break;
            default:
                showHelp();
        }
    }
    
    private void listSessions() {
        List<Session> sessions = sessionManager.getAllSessions();
        Session activeSession = sessionManager.getActiveSession();
        
        System.out.println("=== 会话列表 ===");
        System.out.println();
        
        if (sessions.isEmpty()) {
            System.out.println("  暂无会话");
        } else {
            System.out.printf("  %-4s %-12s %-20s %-10s %-20s%n", 
                "", "ID", "名称", "消息数", "更新时间");
            System.out.println("  " + "-".repeat(70));
            
            for (Session session : sessions) {
                boolean isActive = activeSession != null && 
                                   session.getId().equals(activeSession.getId());
                String marker = isActive ? " * " : "   ";
                String title = session.getTitle() != null ? session.getTitle() : "未命名";
                String shortId = session.getId().substring(0, Math.min(8, session.getId().length()));
                String updatedAt = session.getUpdatedAt()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                
                System.out.printf("  %-4s %-12s %-20s %-10d %-20s%n",
                    marker, shortId, 
                    title.length() > 20 ? title.substring(0, 17) + "..." : title,
                    session.getMessageCount(),
                    updatedAt);
            }
        }
        
        System.out.println();
        Path sessionsDir = sessionManager.getSessionsDir();
        System.out.println("存储位置: " + sessionsDir);
        System.out.println("总会话数: " + sessions.size());
        if (activeSession != null) {
            System.out.println("当前会话: " + activeSession.getId().substring(0, 8));
        }
    }
    
    private void createSession() {
        Session session = sessionManager.createSession(System.getProperty("user.dir"));
        
        if (name != null && !name.isEmpty()) {
            session.setTitle(name);
        } else {
            session.setTitle("会话 " + session.getId().substring(0, 8));
        }
        
        sessionManager.saveSession(session);
        
        System.out.println("✓ 已创建新会话:");
        System.out.println("  ID:    " + session.getId());
        System.out.println("  名称:  " + session.getTitle());
        System.out.println("  工作目录: " + session.getWorkingDirectory());
    }
    
    private void switchSession() {
        if (sessionId == null || sessionId.isEmpty()) {
            System.out.println("错误：需要指定会话 ID");
            System.out.println("用法：/session switch <id>");
            return;
        }
        
        // 尝试查找完整 ID
        String fullId = findSessionId(sessionId);
        if (fullId == null) {
            System.out.println("会话不存在：" + sessionId);
            System.out.println("使用 '/session list' 查看所有会话");
            return;
        }
        
        try {
            sessionManager.setActiveSession(fullId);
            Session session = sessionManager.getActiveSession();
            System.out.println("✓ 已切换到会话: " + (session.getTitle() != null ? session.getTitle() : session.getId()));
            System.out.println("  消息数: " + session.getMessageCount());
        } catch (IllegalArgumentException e) {
            System.out.println("错误：" + e.getMessage());
        }
    }
    
    private void saveCurrentSession() {
        Session activeSession = sessionManager.getActiveSession();
        if (activeSession == null) {
            System.out.println("当前没有活动会话，创建新会话...");
            activeSession = sessionManager.createSession();
            activeSession.setTitle("自动保存会话");
        }
        
        sessionManager.saveSession(activeSession);
        System.out.println("✓ 会话已保存");
        System.out.println("  ID: " + activeSession.getId());
        System.out.println("  消息数: " + activeSession.getMessageCount());
    }
    
    private void loadSession() {
        if (sessionId == null || sessionId.isEmpty()) {
            System.out.println("错误：需要指定会话 ID");
            System.out.println("用法：/session load <id>");
            return;
        }
        
        String fullId = findSessionId(sessionId);
        if (fullId == null) {
            System.out.println("会话不存在：" + sessionId);
            return;
        }
        
        Session session = sessionManager.loadSession(fullId);
        if (session != null) {
            sessionManager.setActiveSession(fullId);
            System.out.println("✓ 已加载会话:");
            System.out.println("  ID: " + session.getId());
            System.out.println("  名称: " + (session.getTitle() != null ? session.getTitle() : "未命名"));
            System.out.println("  消息数: " + session.getMessageCount());
            System.out.println("  创建时间: " + session.getCreatedAt());
        } else {
            System.out.println("错误：无法加载会话");
        }
    }
    
    private void deleteSession() {
        if (sessionId == null || sessionId.isEmpty()) {
            System.out.println("错误：需要指定会话 ID");
            System.out.println("用法：/session delete <id>");
            return;
        }
        
        String fullId = findSessionId(sessionId);
        if (fullId == null) {
            System.out.println("会话不存在：" + sessionId);
            return;
        }
        
        Session session = sessionManager.getAllSessions().stream()
            .filter(s -> s.getId().equals(fullId))
            .findFirst()
            .orElse(null);
        
        String displayName = session != null && session.getTitle() != null 
            ? session.getTitle() 
            : fullId.substring(0, 8);
        
        if (sessionManager.deleteSession(fullId)) {
            System.out.println("✓ 已删除会话: " + displayName);
        } else {
            System.out.println("删除失败");
        }
    }
    
    private void exportSession() {
        String targetId = sessionId;
        if (targetId == null || targetId.isEmpty()) {
            Session activeSession = sessionManager.getActiveSession();
            if (activeSession == null) {
                System.out.println("错误：没有指定会话，也没有活动会话");
                return;
            }
            targetId = activeSession.getId();
        }
        
        String fullId = findSessionId(targetId);
        if (fullId == null) {
            System.out.println("会话不存在：" + targetId);
            return;
        }
        
        Session session = sessionManager.loadSession(fullId);
        if (session == null) {
            System.out.println("错误：无法加载会话");
            return;
        }
        
        try {
            String content = session.export(exportFormat);
            
            Path output;
            if (outputPath != null) {
                output = Path.of(outputPath);
            } else {
                String timestamp = java.time.LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String extension = switch (exportFormat.toLowerCase()) {
                    case "json" -> ".json";
                    case "text" -> ".txt";
                    default -> ".md";
                };
                String filename = "jwcode_session_" + 
                    session.getId().substring(0, 8) + "_" + timestamp + extension;
                output = Path.of(System.getProperty("user.dir"), filename);
            }
            
            java.nio.file.Files.writeString(output, content);
            System.out.println("✓ 会话已导出");
            System.out.println("  格式: " + exportFormat);
            System.out.println("  文件: " + output.toAbsolutePath());
            System.out.println("  大小: " + content.length() + " 字节");
            
        } catch (Exception e) {
            System.out.println("导出失败: " + e.getMessage());
        }
    }
    
    private void showSessionInfo() {
        Session session = null;
        
        if (sessionId != null && !sessionId.isEmpty()) {
            String fullId = findSessionId(sessionId);
            if (fullId != null) {
                session = sessionManager.loadSession(fullId);
            }
        } else {
            session = sessionManager.getActiveSession();
        }
        
        if (session == null) {
            System.out.println("错误：未找到会话");
            return;
        }
        
        System.out.println("=== 会话信息 ===");
        System.out.println();
        System.out.println("基本信息:");
        System.out.println("  ID:          " + session.getId());
        System.out.println("  名称:        " + (session.getTitle() != null ? session.getTitle() : "未命名"));
        System.out.println("  模型:        " + (session.getModel() != null ? session.getModel() : "未设置"));
        System.out.println("  工作目录:    " + session.getWorkingDirectory());
        System.out.println();
        System.out.println("统计信息:");
        System.out.println("  消息数:      " + session.getMessageCount());
        System.out.println("  创建时间:    " + session.getCreatedAt());
        System.out.println("  更新时间:    " + session.getUpdatedAt());
        System.out.println();
        System.out.println("元数据:");
        if (session.getMetadata("cost") != null) {
            System.out.println("  费用:        " + session.getMetadata("cost"));
        }
        if (session.getMetadata("tokens") != null) {
            System.out.println("  Token 数:    " + session.getMetadata("tokens"));
        }
    }
    
    private void cleanOldSessions() {
        int maxAge = 30; // 默认30天
        if (sessionId != null) {
            try {
                maxAge = Integer.parseInt(sessionId);
            } catch (NumberFormatException e) {
                // 使用默认值
            }
        }
        
        int cleaned = sessionManager.cleanupOldSessions(maxAge);
        System.out.println("✓ 清理完成");
        System.out.println("  清理了 " + cleaned + " 个超过 " + maxAge + " 天的旧会话");
        System.out.println("  剩余会话: " + sessionManager.getSessionCount());
    }
    
    private String findSessionId(String partialId) {
        // 首先尝试精确匹配
        for (Session s : sessionManager.getAllSessions()) {
            if (s.getId().equals(partialId)) {
                return partialId;
            }
        }
        
        // 尝试前缀匹配
        for (Session s : sessionManager.getAllSessions()) {
            if (s.getId().startsWith(partialId)) {
                return s.getId();
            }
        }
        
        // 尝试从文件加载
        if (sessionManager.hasSession(partialId)) {
            return partialId;
        }
        
        return null;
    }
    
    private void showHelp() {
        System.out.println("会话管理命令");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /session list                    - 列出所有会话");
        System.out.println("  /session new [-N <name>]         - 创建新会话");
        System.out.println("  /session switch <id>             - 切换会话");
        System.out.println("  /session save                    - 保存当前会话");
        System.out.println("  /session load <id>               - 加载会话");
        System.out.println("  /session delete <id>             - 删除会话");
        System.out.println("  /session export [id] [-f format] [-o path]  - 导出会话");
        System.out.println("  /session info [id]               - 显示会话信息");
        System.out.println("  /session clean [days]            - 清理旧会话");
        System.out.println();
        System.out.println("导出格式: markdown (默认), json, text");
    }
}
