package com.jwcode.core.compact;

import java.util.*;

/**
 * CompactResult - 压缩结果
 * 
 * 功能说明：
 * 封装会话压缩的执行结果。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompactResult {
    
    /**
     * 使用的压缩策略
     */
    private CompactStrategy strategy;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 压缩的消息数量
     */
    private int messagesCompacted;
    
    /**
     * 节省的 Token 数量
     */
    private int tokensSaved;
    
    /**
     * 执行时间（毫秒）
     */
    private long durationMs;
    
    /**
     * 生成的摘要
     */
    private String summary;
    
    /**
     * 保留的消息列表
     */
    private List<CompactService.SessionMessage> retainedMessages;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 默认构造函数
     */
    public CompactResult() {
        this.retainedMessages = new ArrayList<>();
    }
    
    // ==================== Getter 和 Setter ====================
    
    public CompactStrategy getStrategy() {
        return strategy;
    }
    
    public void setStrategy(CompactStrategy strategy) {
        this.strategy = strategy;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public int getMessagesCompacted() {
        return messagesCompacted;
    }
    
    public void setMessagesCompacted(int messagesCompacted) {
        this.messagesCompacted = messagesCompacted;
    }
    
    public int getTokensSaved() {
        return tokensSaved;
    }
    
    public void setTokensSaved(int tokensSaved) {
        this.tokensSaved = tokensSaved;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public List<CompactService.SessionMessage> getRetainedMessages() {
        return retainedMessages;
    }
    
    public void setRetainedMessages(List<CompactService.SessionMessage> retainedMessages) {
        this.retainedMessages = retainedMessages;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * 获取压缩率
     */
    public double getCompressionRatio() {
        if (retainedMessages.isEmpty()) {
            return 0;
        }
        return (double) messagesCompacted / (messagesCompacted + retainedMessages.size());
    }
    
    @Override
    public String toString() {
        return String.format("CompactResult{strategy=%s, success=%s, messagesCompacted=%d, tokensSaved=%d}",
                strategy, success, messagesCompacted, tokensSaved);
    }
}