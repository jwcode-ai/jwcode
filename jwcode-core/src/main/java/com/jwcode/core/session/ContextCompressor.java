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
     * 压缩会话消息（旧版兼容）
     */
    public static List<Message> compress(List<Message> messages) {
        return compress(messages, null);
    }

    /**
     * AI 驱动上下文压缩 — 考虑消息重要性、保留价值、错误经验。
     *
     * <p>核心思想：不是 LRU（最近最少使用），而是"最少信息量"。</p>
     * <ul>
     *   <li>高价值：用户明确要求、工具调用失败、被 AI 标记为重要 — 保留原文</li>
     *   <li>中等价值：成功执行的工具调用 — 生成摘要</li>
     *   <li>低价值：重复的系统提示、冗余的 [FINISH] 提醒 — 丢弃</li>
     * </ul>
     */
    public static List<Message> compress(List<Message> messages, Session session) {
        if (messages.size() <= 4) {
            return messages;
        }

        Set<String> importantIds = session != null ? session.getImportantMessageIds() : Collections.emptySet();

        List<Message> retained = new ArrayList<>();
        List<Message> summarized = new ArrayList<>();

        // 1. 保留系统消息（去重，只保留最新的）
        Message lastSystem = null;
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.SYSTEM) {
                lastSystem = msg;
            }
        }
        if (lastSystem != null) {
            retained.add(lastSystem);
        }

        // 2. 保留最近 4 条消息
        int recentCount = Math.min(4, messages.size());
        for (int i = messages.size() - recentCount; i < messages.size(); i++) {
            retained.add(messages.get(i));
        }

        // 3. 评估早期消息的价值
        List<Message> oldMessages = messages.subList(0, messages.size() - recentCount);
        for (Message msg : oldMessages) {
            if (msg.getRole() == Message.Role.SYSTEM) {
                continue; // 系统消息已处理
            }

            // 高价值：被标记为重要 或 包含失败信息 或 用户明确要求
            if (isHighValue(msg, importantIds)) {
                retained.add(msg);
                continue;
            }

            // 低价值：可丢弃
            if (isLowValue(msg)) {
                continue;
            }

            // 中等价值：加入待摘要列表
            summarized.add(msg);
        }

        // 4. 对中等价值消息生成摘要
        if (!summarized.isEmpty()) {
            Message summary = createSmartSummary(summarized);
            if (summary != null) {
                // 插入到系统消息之后、保留消息之前
                int insertPos = lastSystem != null ? 1 : 0;
                retained.add(insertPos, summary);
            }
        }

        // 5. 按时间排序（保持消息顺序）
        retained.sort(Comparator.comparing(Message::getTimestamp));

        return retained;
    }

    /**
     * 高价值消息判断
     */
    private static boolean isHighValue(Message msg, Set<String> importantIds) {
        String msgId = msg.getTimestamp().toString();
        if (importantIds.contains(msgId)) {
            return true;
        }
        String text = msg.getTextContent();
        if (text == null) return false;
        String lower = text.toLowerCase();
        // 包含失败信息（对错误恢复至关重要）
        if (lower.contains("error") || lower.contains("失败") || lower.contains("exception")) {
            return true;
        }
        // 用户明确要求
        if (msg.getRole() == Message.Role.USER &&
            (lower.contains("必须") || lower.contains("不要") || lower.contains("重要"))) {
            return true;
        }
        return false;
    }

    /**
     * 低价值消息判断
     */
    private static boolean isLowValue(Message msg) {
        String text = msg.getTextContent();
        if (text == null) return true;
        String lower = text.toLowerCase();
        // 重复的系统提示词
        if (msg.getRole() == Message.Role.SYSTEM) {
            return true;
        }
        // 冗余的 [FINISH] 提醒
        if (lower.contains("[finish]") && lower.length() < 100) {
            return true;
        }
        // 空的工具成功结果
        if (msg.getRole() == Message.Role.TOOL && lower.contains("success") && text.length() < 50) {
            return true;
        }
        return false;
    }

    /**
     * 智能摘要生成
     */
    private static Message createSmartSummary(List<Message> messages) {
        if (messages.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("[Context Summary] 早期对话摘要:\n");

        int userCount = 0;
        int assistantCount = 0;
        int toolCallCount = 0;
        Set<String> toolsUsed = new HashSet<>();
        Set<String> topics = new HashSet<>();

        for (Message msg : messages) {
            switch (msg.getRole()) {
                case USER:
                    userCount++;
                    String userText = msg.getTextContent();
                    if (userText != null && !userText.isEmpty()) {
                        topics.add(truncate(userText, 30));
                    }
                    break;
                case ASSISTANT:
                    assistantCount++;
                    List<Message.ToolCallInfo> tcs = msg.getToolCalls();
                    if (tcs != null) {
                        toolCallCount += tcs.size();
                        for (Message.ToolCallInfo tc : tcs) {
                            toolsUsed.add(tc.getName());
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        sb.append("- ").append(userCount).append(" user messages, ")
          .append(assistantCount).append(" assistant responses\n");
        if (toolCallCount > 0) {
            sb.append("- ").append(toolCallCount).append(" tool calls (")
              .append(String.join(", ", toolsUsed)).append(")\n");
        }
        if (!topics.isEmpty()) {
            sb.append("- Topics: ").append(String.join("; ", topics)).append("\n");
        }

        return Message.createSystemMessage(sb.toString());
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
