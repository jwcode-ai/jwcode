package com.jwcode.core.message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 控制台消息发送器
 * 将消息输出到控制台
 */
public class ConsoleMessageSender implements MessageSender {
    
    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private boolean showTimestamp = true;
    private boolean useColor = true;
    
    // ANSI 颜色代码
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    
    public ConsoleMessageSender() {
    }
    
    public ConsoleMessageSender(boolean showTimestamp, boolean useColor) {
        this.showTimestamp = showTimestamp;
        this.useColor = useColor;
    }
    
    @Override
    public CompletableFuture<MessageResult> send(String message, Map<String, String> params) {
        String level = params != null ? params.getOrDefault("level", "info") : "info";
        String formattedMessage = formatMessage(message, level);
        
        // 根据级别输出到不同流
        if ("error".equalsIgnoreCase(level)) {
            System.err.println(formattedMessage);
        } else {
            System.out.println(formattedMessage);
        }
        
        return CompletableFuture.completedFuture(MessageResult.success());
    }
    
    private String formatMessage(String message, String level) {
        StringBuilder sb = new StringBuilder();
        
        // 时间戳
        if (showTimestamp) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            if (useColor) {
                sb.append(CYAN).append("[").append(timestamp).append("]").append(RESET);
            } else {
                sb.append("[").append(timestamp).append("]");
            }
            sb.append(" ");
        }
        
        // 级别标签
        String levelTag = "[" + level.toUpperCase() + "]";
        if (useColor) {
            sb.append(getColorForLevel(level)).append(levelTag).append(RESET);
        } else {
            sb.append(levelTag);
        }
        sb.append(" ");
        
        // 消息内容
        sb.append(message);
        
        return sb.toString();
    }
    
    private String getColorForLevel(String level) {
        return switch (level.toLowerCase()) {
            case "success", "info" -> GREEN;
            case "warn", "warning" -> YELLOW;
            case "error", "fatal" -> RED;
            default -> RESET;
        };
    }
    
    @Override
    public boolean isConfigured() {
        return true; // 总是可用
    }
    
    @Override
    public String getName() {
        return "Console";
    }
    
    // Getters and Setters
    public boolean isShowTimestamp() {
        return showTimestamp;
    }
    
    public void setShowTimestamp(boolean showTimestamp) {
        this.showTimestamp = showTimestamp;
    }
    
    public boolean isUseColor() {
        return useColor;
    }
    
    public void setUseColor(boolean useColor) {
        this.useColor = useColor;
    }
}
