package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ExportCommand - 导出会话命令
 * 
 * 功能说明：
 * 导出当前会话或指定会话的内容到文件。
 * 支持多种导出格式：Markdown、JSON、纯文本。
 * 
 * 使用方式：
 * /export [选项]
 * 
 * 选项：
 * --format <markdown|json|text>  导出格式（默认：markdown）
 * --output <路径>                输出文件路径
 * --session <会话 ID>            指定要导出的会话
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ExportCommand implements Command {
    
    private final SessionManager sessionManager;
    
    public ExportCommand() {
        this.sessionManager = SessionManager.getInstance();
    }
    
    @Override
    public String getName() {
        return "export";
    }
    
    @Override
    public String getDescription() {
        return "导出会话内容到文件。支持 Markdown、JSON、纯文本格式。";
    }
    
    @Override
    public String getUsage() {
        return "/export [--format markdown|json|text] [--output 路径] [--session 会话 ID]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        // 解析参数
        ExportOptions options = parseOptions(args);
        
        // 获取会话
        Session session = getTargetSession(options.sessionId, context);
        
        if (session == null) {
            return CommandResult.error("无法获取会话，请确保会话存在。\n" +
                "使用 '/session list' 查看可用会话，或 '/session new' 创建新会话。");
        }
        
        if (session.getMessageCount() == 0) {
            return CommandResult.error("会话为空，没有可导出的内容。");
        }
        
        // 根据格式转换内容
        String formattedContent;
        try {
            formattedContent = formatContent(session, options.format);
        } catch (Exception e) {
            return CommandResult.error("格式化内容失败: " + e.getMessage());
        }
        
        // 确定输出路径
        Path outputPath = determineOutputPath(options, session);
        
        // 写入文件
        try {
            // 确保父目录存在
            Path parentDir = outputPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            Files.writeString(outputPath, formattedContent);
            
            String message = String.format(
                "✓ 会话已成功导出%n%n" +
                "文件: %s%n" +
                "格式: %s%n" +
                "会话: %s%n" +
                "消息数: %d%n" +
                "大小: %d 字节",
                outputPath.toAbsolutePath(),
                options.format,
                session.getTitle() != null ? session.getTitle() : session.getId().substring(0, 8),
                session.getMessageCount(),
                formattedContent.length()
            );
            
            return CommandResult.success(message);
        } catch (IOException e) {
            return CommandResult.error("导出失败：" + e.getMessage());
        }
    }
    
    /**
     * 解析命令行选项
     */
    private ExportOptions parseOptions(String args) {
        ExportOptions options = new ExportOptions();
        
        if (args == null || args.trim().isEmpty()) {
            return options;
        }
        
        String[] parts = args.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            switch (parts[i]) {
                case "--format":
                case "-f":
                    if (i + 1 < parts.length) {
                        options.format = parts[++i].toLowerCase();
                    }
                    break;
                case "--output":
                case "-o":
                    if (i + 1 < parts.length) {
                        options.outputPath = parts[++i];
                    }
                    break;
                case "--session":
                case "-s":
                    if (i + 1 < parts.length) {
                        options.sessionId = parts[++i];
                    }
                    break;
            }
        }
        
        // 验证格式
        if (!options.format.equals("markdown") && 
            !options.format.equals("json") && 
            !options.format.equals("text")) {
            options.format = "markdown"; // 默认格式
        }
        
        return options;
    }
    
    /**
     * 获取目标会话
     */
    private Session getTargetSession(String sessionId, CommandContext context) {
        if (sessionId != null && !sessionId.isEmpty()) {
            // 尝试查找会话
            Session session = findSessionByPartialId(sessionId);
            if (session != null) {
                return session;
            }
            // 尝试从文件加载
            return sessionManager.loadSession(sessionId);
        }
        
        // 使用活动会话
        Session activeSession = sessionManager.getActiveSession();
        if (activeSession != null) {
            return activeSession;
        }
        
        // 尝试从 context 获取会话
        if (context != null && context.getSession() != null) {
            // 转换 CommandContext.Session 到 core Session
            // 这里简化处理，实际应该统一会话类型
            return sessionManager.getActiveSession();
        }
        
        return null;
    }
    
    /**
     * 通过部分 ID 查找会话
     */
    private Session findSessionByPartialId(String partialId) {
        List<Session> allSessions = sessionManager.getAllSessions();
        
        // 精确匹配
        for (Session s : allSessions) {
            if (s.getId().equals(partialId)) {
                return s;
            }
        }
        
        // 前缀匹配
        for (Session s : allSessions) {
            if (s.getId().startsWith(partialId)) {
                return s;
            }
        }
        
        return null;
    }
    
    /**
     * 格式化会话内容
     */
    private String formatContent(Session session, String format) {
        return session.export(format);
    }
    
    /**
     * 确定输出路径
     */
    private Path determineOutputPath(ExportOptions options, Session session) {
        if (options.outputPath != null && !options.outputPath.isEmpty()) {
            return Paths.get(options.outputPath);
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String sessionPrefix = session.getTitle() != null 
            ? sanitizeFilename(session.getTitle())
            : session.getId().substring(0, 8);
        String extension = getExtension(options.format);
        String filename = String.format("jwcode_export_%s_%s%s", sessionPrefix, timestamp, extension);
        
        return Paths.get(System.getProperty("user.dir"), filename);
    }
    
    /**
     * 清理文件名中的非法字符
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_")
                      .replaceAll("_{2,}", "_")
                      .substring(0, Math.min(20, filename.length()));
    }
    
    /**
     * 获取文件扩展名
     */
    private String getExtension(String format) {
        return switch (format) {
            case "json" -> ".json";
            case "text" -> ".txt";
            case "markdown" -> ".md";
            default -> ".md";
        };
    }
    
    /**
     * 导出选项类
     */
    private static class ExportOptions {
        String format = "markdown";
        String outputPath = null;
        String sessionId = null;
    }
}
