package com.jwcode.core.message;

/**
 * 消息发送结果
 */
public class MessageResult {
    
    private final boolean success;
    private final String message;
    private final String messageId;
    private final long timestamp;
    
    public MessageResult(boolean success, String message, String messageId) {
        this.success = success;
        this.message = message;
        this.messageId = messageId;
        this.timestamp = System.currentTimeMillis();
    }
    
    public static MessageResult success(String messageId) {
        return new MessageResult(true, "发送成功", messageId);
    }
    
    public static MessageResult success() {
        return new MessageResult(true, "发送成功", null);
    }
    
    public static MessageResult error(String errorMessage) {
        return new MessageResult(false, errorMessage, null);
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "MessageResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", messageId='" + messageId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
