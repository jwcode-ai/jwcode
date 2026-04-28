package com.jwcode.core.service.structured;

import java.util.List;

/**
 * 消息工厂
 * 
 * 用于创建结构化消息，自动设置元数据
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class MessageFactory {
    
    private static final MessageFactory INSTANCE = new MessageFactory();
    
    public static MessageFactory getInstance() {
        return INSTANCE;
    }
    
    private MessageFactory() {
    }
    
    /**
     * 创建用户意图消息
     */
    public StructuredMessage createIntent(String content) {
        return createUserMessage(content, MessageType.INTENT);
    }
    
    /**
     * 创建用户消息（带标签）
     */
    public StructuredMessage createIntent(String content, List<String> tags) {
        MessageMetadata metadata = new MessageMetadata(MessageType.INTENT);
        metadata.addTags(tags);
        return new StructuredMessage("user", content, metadata);
    }
    
    /**
     * 创建用户问题消息
     */
    public StructuredMessage createQuestion(String content) {
        return createUserMessage(content, MessageType.QUESTION);
    }
    
    /**
     * 创建用户确认消息
     */
    public StructuredMessage createConfirmation(String content) {
        return createUserMessage(content, MessageType.CONFIRMATION);
    }
    
    /**
     * 创建 AI 回复消息
     */
    public StructuredMessage createResponse(String content, String respondingToId) {
        MessageMetadata metadata = new MessageMetadata(MessageType.ANSWER);
        if (respondingToId != null) {
            metadata.addDependency(respondingToId);
        }
        return new StructuredMessage("assistant", content, metadata);
    }
    
    /**
     * 创建需要澄清的消息
     */
    public StructuredMessage createClarification(String content) {
        return createAssistantMessage(content, MessageType.CLARIFICATION);
    }
    
    /**
     * 创建最终决策消息
     */
    public StructuredMessage createDecision(String content, String dependsOnId) {
        MessageMetadata metadata = new MessageMetadata(MessageType.DECISION);
        if (dependsOnId != null) {
            metadata.addDependency(dependsOnId);
        }
        return new StructuredMessage("assistant", content, metadata);
    }
    
    /**
     * 创建工具结果消息
     */
    public StructuredMessage createToolResult(String toolName, boolean success, String toolUseId) {
        String content = success 
            ? "工具执行成功: " + toolName 
            : "工具执行失败: " + toolName;
        MessageMetadata metadata = new MessageMetadata(MessageType.TOOL_RESULT);
        
        StructuredMessage msg = new StructuredMessage(
            null, // ID 由管理器分配
            "tool",
            content,
            metadata,
            toolUseId,
            toolName,
            null
        );
        
        // 标记重要性
        metadata.setImportance(success ? 0.3 : 0.8);
        
        return msg;
    }
    
    /**
     * 创建工具结果消息（带输出）
     */
    public StructuredMessage createToolResult(String toolName, boolean success, 
                      String toolUseId, String output) {
        String content = success 
            ? "工具: " + toolName + "\n输出: " + output
            : "工具执行失败: " + toolName + "\n错误: " + output;
        
        MessageMetadata metadata = new MessageMetadata(MessageType.TOOL_RESULT);
        metadata.setImportance(success ? 0.2 : 0.9);
        
        return new StructuredMessage(
            null,
            "tool",
            content,
            metadata,
            toolUseId,
            toolName,
            null
        );
    }
    
    /**
     * 创建系统事件消息
     */
    public StructuredMessage createSystemEvent(String content) {
        return new StructuredMessage("system", content, new MessageMetadata(MessageType.SYSTEM_EVENT));
    }
    
    /**
     * 创建错误消息
     */
    public StructuredMessage createError(String errorMessage) {
        MessageMetadata metadata = new MessageMetadata(MessageType.ERROR);
        metadata.setImportance(0.9); // 错误信息高重要性
        return new StructuredMessage("system", errorMessage, metadata);
    }
    
    /**
     * 创建中间讨论消息
     */
    public StructuredMessage createInterimDiscussion(String content) {
        return createAssistantMessage(content, MessageType.INTERIM_DISCUSSION);
    }
    
    // ==================== 私有辅助方法 ====================
    
    private StructuredMessage createUserMessage(String content, MessageType type) {
        MessageMetadata metadata = new MessageMetadata(type);
        return new StructuredMessage("user", content, metadata);
    }
    
    private StructuredMessage createAssistantMessage(String content, MessageType type) {
        MessageMetadata metadata = new MessageMetadata(type);
        return new StructuredMessage("assistant", content, metadata);
    }
}