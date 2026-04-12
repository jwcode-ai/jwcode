package com.jwcode.core.service;

import com.jwcode.core.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * ContextWindowManager - 上下文窗口管理器
 * 
 * 功能说明：
 * 管理会话消息的 Token 数量，防止超出 API 的上下文窗口限制。
 * 当消息总 Token 数超过限制时，自动进行截断或压缩。
 * 
 * 策略：
 * 1. 保留系统消息（system）
 * 2. 保留最新的用户消息和 AI 响应
 * 3. 对旧消息进行摘要或截断
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ContextWindowManager {
    
    private static final Logger logger = Logger.getLogger(ContextWindowManager.class.getName());
    
    // 默认上下文窗口限制（Token 数）
    public static final int DEFAULT_CONTEXT_LIMIT = 204800;
    // 安全余量，留出一些空间给响应
    public static final int SAFETY_MARGIN = 1000;
    // 默认最大消息数
    public static final int DEFAULT_MAX_MESSAGES = 50;
    // 默认最小保留消息数
    public static final int DEFAULT_MIN_MESSAGES = 4;
    
    private final int contextLimit;
    private final int maxMessages;
    private final int minMessages;
    
    public ContextWindowManager() {
        this(DEFAULT_CONTEXT_LIMIT, DEFAULT_MAX_MESSAGES, DEFAULT_MIN_MESSAGES);
    }
    
    public ContextWindowManager(int contextLimit) {
        this(contextLimit, DEFAULT_MAX_MESSAGES, DEFAULT_MIN_MESSAGES);
    }
    
    public ContextWindowManager(int contextLimit, int maxMessages, int minMessages) {
        this.contextLimit = contextLimit;
        this.maxMessages = maxMessages;
        this.minMessages = Math.max(minMessages, 2); // 至少保留2条消息
    }
    
    /**
     * 准备消息列表，确保不超过上下文窗口限制
     * 
     * @param messages 原始消息列表
     * @return 处理后的消息列表
     */
    public List<Message> prepareMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        
        // 估算当前 Token 数
        int estimatedTokens = estimateTokens(messages);
        
        // 如果在限制内，直接返回
        if (estimatedTokens <= contextLimit - SAFETY_MARGIN && messages.size() <= maxMessages) {
            return messages;
        }
        
        logger.info(String.format(
            "上下文窗口超限: 估计 Token 数=%d, 消息数=%d, 限制=%d. 开始压缩...",
            estimatedTokens, messages.size(), contextLimit
        ));
        
        // 执行消息压缩
        List<Message> compressed = compressMessages(messages);
        
        int newTokens = estimateTokens(compressed);
        logger.info(String.format(
            "消息压缩完成: 原始消息数=%d -> 压缩后=%d, 估计 Token 数=%d -> %d",
            messages.size(), compressed.size(), estimatedTokens, newTokens
        ));
        
        return compressed;
    }
    
    /**
     * 压缩消息列表
     */
    private List<Message> compressMessages(List<Message> messages) {
        List<Message> result = new ArrayList<>();
        
        // 分离不同类型的消息
        Message systemMessage = null;
        List<Message> historicalMessages = new ArrayList<>();
        List<Message> recentMessages = new ArrayList<>();
        
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.SYSTEM) {
                // 保留系统消息
                if (systemMessage == null) {
                    systemMessage = msg;
                }
            } else {
                historicalMessages.add(msg);
            }
        }
        
        // 添加系统消息
        if (systemMessage != null) {
            result.add(systemMessage);
        }
        
        // 如果消息数不多，直接返回
        if (historicalMessages.size() <= minMessages) {
            result.addAll(historicalMessages);
            return result;
        }
        
        // 保留最近的消息
        int recentCount = Math.min(minMessages, historicalMessages.size());
        recentMessages = historicalMessages.subList(
            historicalMessages.size() - recentCount, 
            historicalMessages.size()
        );
        
        // 对旧消息进行摘要
        List<Message> oldMessages = historicalMessages.subList(0, historicalMessages.size() - recentCount);
        if (!oldMessages.isEmpty()) {
            Message summaryMessage = createSummaryMessage(oldMessages);
            if (summaryMessage != null) {
                result.add(summaryMessage);
            }
        }
        
        // 添加最近的消息
        result.addAll(recentMessages);
        
        // 如果还是超过限制，进行截断
        while (result.size() > maxMessages && result.size() > minMessages + 1) {
            // 保留系统消息和最近的消息，移除中间的摘要
            int removeIndex = systemMessage != null ? 1 : 0;
            if (removeIndex < result.size() - minMessages) {
                result.remove(removeIndex);
            } else {
                break;
            }
        }
        
        return result;
    }
    
    /**
     * 创建摘要消息
     */
    private Message createSummaryMessage(List<Message> messages) {
        if (messages.isEmpty()) {
            return null;
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("[历史对话摘要] 之前共 ").append(messages.size()).append(" 轮对话。\n");
        
        // 提取关键信息
        List<String> keyPoints = new ArrayList<>();
        for (Message msg : messages) {
            String content = extractContent(msg);
            if (content != null && !content.isEmpty()) {
                // 只取前 50 个字符作为要点
                String point = content.length() > 50 
                    ? content.substring(0, 50) + "..." 
                    : content;
                keyPoints.add("[" + msg.getRole().name().toLowerCase() + "] " + point);
            }
            
            // 限制要点数量
            if (keyPoints.size() >= 5) {
                break;
            }
        }
        
        if (!keyPoints.isEmpty()) {
            summary.append("关键信息: ").append(String.join("; ", keyPoints));
        }
        
        return Message.createSystemMessage(summary.toString());
    }
    
    /**
     * 提取消息内容
     */
    private String extractContent(Message message) {
        if (message.getContent() == null || message.getContent().isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : message.getContent()) {
            if (block instanceof Message.TextContent) {
                sb.append(((Message.TextContent) block).getText());
            }
        }
        return sb.toString().trim();
    }
    
    /**
     * 估算消息列表的 Token 数
     * 
     * 简化估算：
     * - 中文字符：1.5 tokens
     * - 英文单词：1.3 tokens
     * - 消息开销：4 tokens/消息
     */
    public int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            total += estimateMessageTokens(msg);
        }
        return total;
    }
    
    /**
     * 估算单条消息的 Token 数
     */
    public int estimateMessageTokens(Message message) {
        int tokens = 4; // 消息基础开销
        
        String content = extractContent(message);
        if (content.isEmpty()) {
            return tokens;
        }
        
        // 估算字符 Token
        int chineseChars = 0;
        int englishWords = 0;
        
        for (char c : content.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chineseChars++;
            } else if (c == ' ' || c == '\n' || c == '\t') {
                // 分隔符，英文单词计数
                if (englishWords > 0) {
                    // 已经有字符了，算一个单词的一部分
                }
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                // 英文字符
                if (englishWords == 0) {
                    englishWords = 1;
                }
            }
        }
        
        // 更精确的英文单词计数
        englishWords = content.split("\\s+").length;
        
        tokens += (int) (chineseChars * 1.5 + englishWords * 1.3);
        
        return tokens;
    }
    
    /**
     * 估算文本的 Token 数
     */
    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int chineseChars = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chineseChars++;
            }
        }
        
        int englishWords = text.split("\\s+").length;
        
        return (int) (chineseChars * 1.5 + englishWords * 1.3);
    }
    
    // Getters
    public int getContextLimit() { return contextLimit; }
    public int getMaxMessages() { return maxMessages; }
    public int getMinMessages() { return minMessages; }
}
