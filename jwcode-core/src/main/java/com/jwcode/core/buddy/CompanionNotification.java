package com.jwcode.core.buddy;

/**
 * CompanionNotification - 伙伴通知
 * 
 * 功能说明：
 * 表示伙伴精灵发出的通知消息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompanionNotification {
    
    private final String message;
    private final NotificationType type;
    private final long timestamp;
    private boolean read;
    
    public CompanionNotification(String message) {
        this(message, NotificationType.INFO);
    }
    
    public CompanionNotification(String message, NotificationType type) {
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
    }
    
    /**
     * 获取消息内容
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * 获取通知类型
     */
    public NotificationType getType() {
        return type;
    }
    
    /**
     * 获取时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 是否已读
     */
    public boolean isRead() {
        return read;
    }
    
    /**
     * 标记为已读
     */
    public void setRead(boolean read) {
        this.read = read;
    }
    
    /**
     * 获取格式化的通知字符串
     */
    public String getFormattedString() {
        String icon = getIconForType(type);
        return String.format("[%s] %s", icon, message);
    }
    
    /**
     * 获取类型对应的图标
     */
    private String getIconForType(NotificationType type) {
        switch (type) {
            case INFO: return "ℹ️";
            case SUCCESS: return "✅";
            case WARNING: return "⚠️";
            case ERROR: return "❌";
            case HAPPY: return "😊";
            case SAD: return "😢";
            default: return "📢";
        }
    }
    
    @Override
    public String toString() {
        return getFormattedString();
    }
    
    /**
     * 通知类型枚举
     */
    public enum NotificationType {
        INFO,       // 信息
        SUCCESS,    // 成功
        WARNING,    // 警告
        ERROR,      // 错误
        HAPPY,      // 开心
        SAD         // 伤心
    }
}