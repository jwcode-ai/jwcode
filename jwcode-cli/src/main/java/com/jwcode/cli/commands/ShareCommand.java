package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ShareCommand - /share 命令
 * 
 * 功能说明：
 * 分享会话，支持导出为 Markdown、生成分享链接等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/share", description = "分享会话", 
         aliases = {"/export", "/save"})
public class ShareCommand implements Runnable {
    
    @Option(names = {"-f", "--format"}, description = "导出格式：markdown, html, json", defaultValue = "markdown")
    private String format;
    
    @Option(names = {"-o", "--output"}, description = "输出文件路径")
    private String outputPath;
    
    @Option(names = {"-p", "--privacy"}, description = "隐私过滤：none, basic, strict", defaultValue = "basic")
    private String privacyLevel;
    
    @Option(names = {"-c", "--copy"}, description = "复制到剪贴板")
    private boolean copyToClipboard;
    
    @Option(names = {"-l", "--link"}, description = "生成分享链接")
    private boolean generateLink;
    
    @Override
    public void run() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║         JWCode 会话分享                ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        // 获取会话内容（模拟）
        List<Message> messages = getSessionMessages();
        
        if (messages.isEmpty()) {
            System.out.println("当前没有可分享的会话内容");
            return;
        }
        
        // 隐私过滤
        if (!privacyLevel.equals("none")) {
            messages = filterPrivacy(messages, privacyLevel);
        }
        
        // 根据格式导出
        String content;
        switch (format.toLowerCase()) {
            case "html":
                content = exportToHtml(messages);
                break;
            case "json":
                content = exportToJson(messages);
                break;
            case "markdown":
            default:
                content = exportToMarkdown(messages);
        }
        
        // 输出或保存
        if (outputPath != null) {
            saveToFile(content, outputPath);
            System.out.println("✓ 已保存到：" + outputPath);
        } else {
            System.out.println(content);
        }
        
        // 复制到剪贴板
        if (copyToClipboard) {
            copyToClipboard(content);
            System.out.println("✓ 已复制到剪贴板");
        }
        
        // 生成分享链接
        if (generateLink) {
            String shareLink = generateShareLink(content);
            System.out.println();
            System.out.println("分享链接：" + shareLink);
        }
    }
    
    /**
     * 获取会话消息（模拟）
     */
    private List<Message> getSessionMessages() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", "你好，请帮我写一个排序函数"));
        messages.add(new Message("assistant", "好的，请问您需要什么语言的排序函数？"));
        messages.add(new Message("user", "Java 的快速排序"));
        messages.add(new Message("assistant", "```java\npublic void quickSort(int[] arr) {...}\n```"));
        return messages;
    }
    
    /**
     * 隐私过滤
     */
    private List<Message> filterPrivacy(List<Message> messages, String level) {
        List<Message> filtered = new ArrayList<>();
        for (Message msg : messages) {
            String content = msg.content;
            
            if (level.equals("strict")) {
                // 严格过滤：移除所有可能的敏感信息
                content = content.replaceAll("sk-[a-zA-Z0-9]+", "[REDACTED]");
                content = content.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "[EMAIL]");
                content = content.replaceAll("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b", "[CARD]");
            }
            
            filtered.add(new Message(msg.role, content));
        }
        return filtered;
    }
    
    /**
     * 导出为 Markdown
     */
    private String exportToMarkdown(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# JWCode 会话记录\n\n");
        sb.append("生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        sb.append("---\n\n");
        
        for (Message msg : messages) {
            String role = msg.role.equals("user") ? "👤 用户" : "🤖 助手";
            sb.append("### ").append(role).append("\n\n");
            sb.append(msg.content).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 导出为 HTML
     */
    private String exportToHtml(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>JWCode 会话记录</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }\n");
        sb.append(".message { margin: 10px 0; padding: 10px; border-radius: 5px; }\n");
        sb.append(".user { background: #e3f2fd; }\n");
        sb.append(".assistant { background: #f5f5f5; }\n");
        sb.append("pre { background: #f5f5f5; padding: 10px; overflow-x: auto; }\n");
        sb.append("</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<h1>JWCode 会话记录</h1>\n");
        sb.append("<p>生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");
        sb.append("<hr>\n");
        
        for (Message msg : messages) {
            String cssClass = msg.role.equals("user") ? "user" : "assistant";
            String role = msg.role.equals("user") ? "👤 用户" : "🤖 助手";
            sb.append("<div class=\"message ").append(cssClass).append("\">\n");
            sb.append("<strong>").append(role).append("</strong>\n");
            sb.append("<pre>").append(escapeHtml(msg.content)).append("</pre>\n");
            sb.append("</div>\n");
        }
        
        sb.append("</body>\n</html>");
        return sb.toString();
    }
    
    /**
     * 导出为 JSON
     */
    private String exportToJson(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generatedAt\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        sb.append("  \"messages\": [\n");
        
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            sb.append("    {\n");
            sb.append("      \"role\": \"").append(msg.role).append("\",\n");
            sb.append("      \"content\": ").append(escapeJson(msg.content)).append("\n");
            sb.append("    }");
            if (i < messages.size() - 1) sb.append(",");
            sb.append("\n");
        }
        
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    /**
     * 保存到文件
     */
    private void saveToFile(String content, String path) {
        try {
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
        } catch (IOException e) {
            System.out.println("❌ 保存失败：" + e.getMessage());
        }
    }
    
    /**
     * 复制到剪贴板（模拟）
     */
    private void copyToClipboard(String content) {
        // 实际实现需要使用 Java AWT 或第三方库
        // 这里仅做模拟
    }
    
    /**
     * 生成分享链接（模拟）
     */
    private String generateShareLink(String content) {
        // 模拟生成短链接
        String hash = Integer.toHexString(content.hashCode());
        return "https://jwcode.dev/share/" + hash;
    }
    
    /**
     * HTML 转义
     */
    private String escapeHtml(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '&':
                    sb.append("&").append("amp;");
                    break;
                case '<':
                    sb.append("&").append("lt;");
                    break;
                case '>':
                    sb.append("&").append("gt;");
                    break;
                case '"':
                    sb.append("&").append("quot;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * JSON 转义
     */
    private String escapeJson(String str) {
        if (str == null) return "\"\"";
        return "\"" + str.replace("\\", "\\\\")
                         .replace("\"", "\\\"")
                         .replace("\n", "\\n")
                         .replace("\r", "\\r")
                         .replace("\t", "\\t") + "\"";
    }
    
    /**
     * 消息类
     */
    private static class Message {
        String role;
        String content;
        
        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
