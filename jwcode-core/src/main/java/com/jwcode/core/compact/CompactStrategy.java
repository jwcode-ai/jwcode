package com.jwcode.core.compact;

/**
 * CompactStrategy - 压缩策略枚举
 * 
 * 功能说明：
 * 定义会话压缩的不同策略。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public enum CompactStrategy {
    /**
     * 摘要压缩
     * 使用 AI 生成会话摘要，保留关键信息
     */
    SUMMARIZE,
    
    /**
     * 截断压缩
     * 直接删除最旧的消息，保留最近的消息
     */
    TRUNCATE,
    
    /**
     * 关键点压缩
     * 提取关键点，保留重要信息
     */
    KEY_POINTS,
    
    /**
     * 混合压缩
     * 结合多种策略，先摘要后截断
     */
    HYBRID
}