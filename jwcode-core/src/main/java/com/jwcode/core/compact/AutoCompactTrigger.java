package com.jwcode.core.compact;

/**
 * AutoCompactTrigger - 自动压缩触发器
 * 
 * 功能说明：
 * 判断会话是否需要进行压缩。
 * 基于 Token 数量、消息数量等条件进行判断。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AutoCompactTrigger {
    
    /**
     * 压缩配置
     */
    private final CompactConfig config;
    
    /**
     * 构造函数
     */
    public AutoCompactTrigger(CompactConfig config) {
        this.config = config;
    }
    
    /**
     * 检查是否需要压缩
     * 
     * @param session 会话信息
     * @return true 如果需要压缩
     */
    public boolean shouldCompact(CompactService.SessionInfo session) {
        if (!config.isAutoCompactEnabled()) {
            return false;
        }
        
        // 检查 Token 数量
        if (shouldCompactByTokens(session)) {
            return true;
        }
        
        // 检查消息数量
        if (shouldCompactByMessageCount(session)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 根据 Token 数量判断是否需要压缩
     */
    private boolean shouldCompactByTokens(CompactService.SessionInfo session) {
        int tokenCount = session.getTokenCount();
        
        // 超过最大限制，必须压缩
        if (tokenCount >= config.getMaxTokens()) {
            return true;
        }
        
        // 超过目标阈值，建议压缩
        if (tokenCount >= config.getTargetTokens()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 根据消息数量判断是否需要压缩
     */
    private boolean shouldCompactByMessageCount(CompactService.SessionInfo session) {
        int messageCount = session.getMessages().size();
        
        // 消息数量超过最大限制
        if (messageCount > config.getMaxMessages() * 2) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取压缩优先级
     * 
     * @param session 会话信息
     * @return 压缩优先级
     */
    public CompactPriority getPriority(CompactService.SessionInfo session) {
        int tokenCount = session.getTokenCount();
        int maxTokens = config.getMaxTokens();
        int targetTokens = config.getTargetTokens();
        
        // 紧急：超过最大限制
        if (tokenCount >= maxTokens) {
            return CompactPriority.URGENT;
        }
        
        // 高：接近最大限制
        if (tokenCount >= maxTokens * 0.9) {
            return CompactPriority.HIGH;
        }
        
        // 中：超过目标阈值
        if (tokenCount >= targetTokens) {
            return CompactPriority.MEDIUM;
        }
        
        // 低：接近目标阈值
        if (tokenCount >= targetTokens * 0.8) {
            return CompactPriority.LOW;
        }
        
        // 不需要压缩
        return CompactPriority.NONE;
    }
    
    /**
     * 压缩优先级枚举
     */
    public enum CompactPriority {
        /** 不需要 */
        NONE,
        /** 低 */
        LOW,
        /** 中 */
        MEDIUM,
        /** 高 */
        HIGH,
        /** 紧急 */
        URGENT
    }
}