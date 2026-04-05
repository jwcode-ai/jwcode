package com.jwcode.core.session;

import com.jwcode.core.model.Message;

import java.util.*;

/**
 * 上下文压缩器 - 当会话接近 token 限制时自动压缩
 * 
 * 参照 Kimi Code 的上下文压缩策略
 */
public class ContextCompressor {
    
    // Token 限制
    private static final int MAX_TOKENS = 8000;
    private static final int WARNING_TOKENS = 6000;
    private static final int TARGET_TOKENS = 4000;
    
    // 简单估算：英文约 4 字符/token，中文约 2 字符/token
    private static final double ENGLISH_RATIO = 4.0;
    private static final double CHINESE_RATIO = 2.0;
    
    /**
     * 检查是否需要压缩
     */
    public static boolean needsCompression(List<Message> messages) {
        return estimateTokens(messages) > WARNING_TOKENS;
    }
    
    /**
     * 压缩会话消息
     * 
     * 策略：
     * 1. 保留系统消息
     * 2. 保留最近的用户消息
     * 3. 压缩/移除早期的工具调用结果
     * 4. 总结早期对话内容
     */
    public static List<Message> compress(List<Message> messages) {
        if (messages.size() <= 4) {
            return messages; // 消息太少，不压缩
        }
        
        List<Message> compressed = new ArrayList<>();
        
        // 1. 保留系统消息（如果有）
        Message systemMessage = findSystemMessage(messages);
        if (systemMessage != null) {
            compressed.add(systemMessage);
        }
        
        // 2. 保留最近的 4 条消息（用户 + AI 对话）
        int recentCount = Math.min(4, messages.size());
        for (int i = messages.size() - recentCount; i < messages.size(); i++) {
            compressed.add(messages.get(i));
        }
        
        // 3. 压缩早期的工具调用结果
        List<Message> oldMessages = messages.subList(0, messages.size() - recentCount);
        Message summaryMessage = createSummaryMessage(oldMessages);
        if (summaryMessage != null) {
            compressed.add(1, summaryMessage); // 插入到系统消息之后
        }
        
        return compressed;
    }
    
    /**
     * 查找系统消息
     */
    private static Message findSystemMessage(List<Message> messages) {
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.SYSTEM) {
                return msg;
            }
        }
        return null;
    }
    
    /**
     * 创建总结消息
     */
    private static Message createSummaryMessage(List<Message> messages) {
        if (messages.isEmpty()) {
            return null;
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("[ earlier conversation summary ]\n");
        
        // 统计信息
        int userMsgCount = 0;
        int assistantMsgCount = 0;
        int toolCallCount = 0;
        Set<String> toolsUsed = new HashSet<>();
        
        for (Message msg : messages) {
            switch (msg.getRole()) {
                case USER:
                    userMsgCount++;
                    break;
                case ASSISTANT:
                    assistantMsgCount++;
                    // 提取工具调用信息
                    List<Message.ToolCallInfo> toolCalls = msg.getToolCalls();
                    if (toolCalls != null) {
                        toolCallCount += toolCalls.size();
                        for (Message.ToolCallInfo toolCall : toolCalls) {
                            toolsUsed.add(toolCall.getName());
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        
        summary.append("- ").append(userMsgCount).append(" user messages\n");
        summary.append("- ").append(assistantMsgCount).append(" assistant responses\n");
        if (toolCallCount > 0) {
            summary.append("- ").append(toolCallCount).append(" tool calls (");
            summary.append(String.join(", ", toolsUsed)).append(")\n");
        }
        
        // 添加关键内容预览
        for (int i = Math.max(0, messages.size() - 2); i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.USER) {
                String content = extractTextContent(msg);
                if (content != null && !content.isEmpty()) {
                    summary.append("\nLast user request: ");
                    summary.append(truncate(content, 100));
                }
            }
        }
        
        return Message.createSystemMessage(summary.toString());
    }
    
    /**
     * 提取文本内容
     */
    private static String extractTextContent(Message message) {
        // 简化处理，实际应从 ContentBlock 中提取
        return message.toString();
    }
    
    /**
     * 估算 token 数量
     */
    public static int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            total += estimateTokens(msg);
        }
        return total;
    }
    
    /**
     * 估算单条消息的 token 数量
     */
    public static int estimateTokens(Message message) {
        String content = message.toString();
        return estimateTokens(content);
    }
    
    /**
     * 估算字符串的 token 数量
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int chineseChars = 0;
        int otherChars = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        
        return (int) (chineseChars / CHINESE_RATIO + otherChars / ENGLISH_RATIO);
    }
    
    /**
     * 截断字符串
     */
    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * 获取压缩建议
     */
    public static String getCompressionSuggestion(List<Message> messages) {
        int tokens = estimateTokens(messages);
        if (tokens > MAX_TOKENS) {
            return "Token 数量 (" + tokens + ") 超过限制，已自动压缩上下文";
        } else if (tokens > WARNING_TOKENS) {
            return "Token 数量 (" + tokens + ") 接近限制，建议开始新的会话";
        }
        return null;
    }
}
