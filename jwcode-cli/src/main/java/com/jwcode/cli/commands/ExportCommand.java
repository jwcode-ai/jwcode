package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

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
        
        // 获取会话内容
        String sessionContent = getSessionContent(context, options.sessionId);
        
        if (sessionContent == null || sessionContent.isEmpty()) {
            return CommandResult.error("无法获取会话内容，会话可能为空或不存在。");
        }
        
        // 根据格式转换内容
        String formattedContent;
        switch (options.format) {
            case "json":
                formattedContent = formatAsJson(sessionContent, options.sessionId);
                break;
            case "text":
                formattedContent = formatAsText(sessionContent);
                break;
            case "markdown":
            default:
                formattedContent = formatAsMarkdown(sessionContent, options.sessionId);
                break;
        }
        
        // 确定输出路径
        Path outputPath;
        if (options.outputPath != null && !options.outputPath.isEmpty()) {
            outputPath = Paths.get(options.outputPath);
        } else {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String extension = getExtension(options.format);
            outputPath = Paths.get("jwcode_export_" + timestamp + extension);
        }
        
        // 写入文件
        try {
            Files.writeString(outputPath, formattedContent);
            
            String message = String.format("会话已成功导出到：%s\n格式：%s\n大小：%d 字节",
                    outputPath.toAbsolutePath(),
                    options.format,
                    formattedContent.length());
            
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
                        options.format = parts[++i];
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
     * 获取会话内容
     */
    private String getSessionContent(CommandContext context, String sessionId) {
        // 如果指定了会话 ID，尝试获取特定会话
        if (sessionId != null && !sessionId.isEmpty()) {
            // TODO: 实现从会话管理器获取特定会话
            return context.getSessionHistory();
        }
        
        // 获取当前会话
        return context.getSessionHistory();
    }
    
    /**
     * 格式化为 Markdown
     */
    private String formatAsMarkdown(String content, String sessionId) {
        StringBuilder md = new StringBuilder();
        
        md.append("# JWCode 会话导出\n\n");
        md.append("**导出时间**：").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        
        if (sessionId != null && !sessionId.isEmpty()) {
            md.append("**会话 ID**：").append(sessionId).append("\n");
        }
        
        md.append("\n---\n\n");
        md.append("## 会话内容\n\n");
        md.append(content);
        md.append("\n\n---\n\n");
        md.append("*由 JWCode 导出*\n");
        
        return md.toString();
    }
    
    /**
     * 格式化为 JSON
     */
    private String formatAsJson(String content, String sessionId) {
        // 简单的 JSON 格式化（实际项目中应使用 JSON 库）
        String escapedContent = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"exportedAt\": \"").append(LocalDateTime.now().format(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        
        if (sessionId != null && !sessionId.isEmpty()) {
            json.append("  \"sessionId\": \"").append(sessionId).append("\",\n");
        }
        
        json.append("  \"format\": \"json\",\n");
        json.append("  \"content\": \"").append(escapedContent).append("\"\n");
        json.append("}\n");
        
        return json.toString();
    }
    
    /**
     * 格式化为纯文本
     */
    private String formatAsText(String content) {
        StringBuilder text = new StringBuilder();
        
        text.append("JWCode 会话导出\n");
        text.append("==============\n\n");
        text.append("导出时间：").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        text.append("--------------\n\n");
        text.append(content);
        text.append("\n\n--------------\n");
        text.append("由 JWCode 导出\n");
        
        return text.toString();
    }
    
    /**
     * 获取文件扩展名
     */
    private String getExtension(String format) {
        switch (format) {
            case "json":
                return ".json";
            case "text":
                return ".txt";
            case "markdown":
            default:
                return ".md";
        }
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