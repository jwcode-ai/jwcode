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
    public static final int DEFAULT_CONTEXT_LIMIT = 1000000;
    // 安全余量，留出一些空间给响应
    public static final int SAFETY_MARGIN = 1000;
    // 默认最大消息数
    public static final int DEFAULT_MAX_MESSAGES = 50;
    // 默认最小保留消息数
    public static final int DEFAULT_MIN_MESSAGES = 4;
    
    private final int contextLimit;
    private final int maxMessages;
    private final int minMessages;
    private final SimpleCompactionStrategy compactionStrategy;
    
    public ContextWindowManager() {
        this(DEFAULT_CONTEXT_LIMIT, DEFAULT_MAX_MESSAGES, DEFAULT_MIN_MESSAGES, null);
    }
    
    public ContextWindowManager(int contextLimit) {
        this(contextLimit, DEFAULT_MAX_MESSAGES, DEFAULT_MIN_MESSAGES, null);
    }
    
    public ContextWindowManager(int contextLimit, int maxMessages, int minMessages) {
        this(contextLimit, maxMessages, minMessages, null);
    }
    
    public ContextWindowManager(int contextLimit, int maxMessages, int minMessages,
                                SimpleCompactionStrategy compactionStrategy) {
        this.contextLimit = contextLimit;
        this.maxMessages = maxMessages;
        this.minMessages = Math.max(minMessages, 2); // 至少保留2条消息
        this.compactionStrategy = compactionStrategy;
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
        
        List<Message> compressed;
        // 优先尝试 LLM 语义压缩（若配置了 strategy 且消息足够多）
        if (compactionStrategy != null && messages.size() > compactionStrategy.getTailSize() + 2) {
            compressed = compactionStrategy.compact(messages);
            // 如果 LLM 压缩后仍然超限，再降级为截断
            if (estimateTokens(compressed) > contextLimit - SAFETY_MARGIN || compressed.size() > maxMessages) {
                logger.info("[ContextWindowManager] LLM 压缩后仍超限，降级为截断策略");
                compressed = compressMessages(compressed);
            }
        } else {
            compressed = compressMessages(messages);
        }
        
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
        
        // 【修复】保护前 2 条非系统消息（通常是用户初始任务描述），防止截断后任务目标丢失
        List<Message> preservedEarly = new ArrayList<>();
        int preservedCount = 0;
        for (Message msg : historicalMessages) {
            if (preservedCount < 2) {
                preservedEarly.add(msg);
                preservedCount++;
            } else {
                break;
            }
        }
        
        // 从待压缩历史中移除已保护消息
        List<Message> compressibleHistory = new ArrayList<>(historicalMessages);
        compressibleHistory.removeAll(preservedEarly);
        
        // 如果剩余消息数不多，直接返回
        if (compressibleHistory.size() <= minMessages) {
            result.addAll(preservedEarly);
            result.addAll(compressibleHistory);
            return result;
        }
        
        // 保留最近的消息（从 compressibleHistory 中计算）
        int recentCount = Math.min(minMessages, compressibleHistory.size());
        int startIndex = compressibleHistory.size() - recentCount;
        
        // 【关键修复】确保截断点不破坏 tool_calls 配对
        // 如果从截断点开始的第一个消息是 TOOL，需要向前包含对应的 ASSISTANT
        startIndex = fixTruncationBoundary(compressibleHistory, startIndex);
        
        recentMessages = compressibleHistory.subList(startIndex, compressibleHistory.size());
        
        // 对旧消息进行摘要
        List<Message> oldMessages = compressibleHistory.subList(0, compressibleHistory.size() - recentMessages.size());
        if (!oldMessages.isEmpty()) {
            Message summaryMessage = createSummaryMessage(oldMessages);
            if (summaryMessage != null) {
                result.add(summaryMessage);
            }
        }
        
        // 添加被保护的早期消息（在摘要之后、最近消息之前）
        result.addAll(preservedEarly);
        
        // 添加最近的消息
        result.addAll(recentMessages);
        
        // 如果还是超过限制，进行截断（优先移除摘要，保留早期消息和最近消息）
        while (result.size() > maxMessages && result.size() > minMessages + preservedEarly.size() + 1) {
            // 保留系统消息、早期消息和最近的消息，移除中间的摘要
            int removeIndex = systemMessage != null ? 1 : 0;
            if (removeIndex < result.size() - minMessages - preservedEarly.size()) {
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
    
    // ==================== Tool Calls 配对修复 ====================
    
    /**
     * 修复截断边界，确保不破坏 tool_calls 配对
     * 
     * 问题：当消息历史被截断时，如果截断边界落在 ASSISTANT-TOOL 序列中间，
     * 会导致 TOOL 消息被保留而对应的 ASSISTANT 消息（包含 tool_calls）被丢弃。
     * 这会导致 API 报错 "tool result's tool id ... not found"。
     * 
     * 解决方案：
     * 1. 如果从截断点开始的第一个消息是 TOOL，向前查找对应的 ASSISTANT
     * 2. 将截断点移动到该 ASSISTANT 的位置，确保配对完整
     * 3. 如果找不到对应的 ASSISTANT，跳过这条孤立的 TOOL 消息
     */
    private int fixTruncationBoundary(List<Message> messages, int startIndex) {
        while (startIndex > 0 && startIndex < messages.size()) {
            Message msg = messages.get(startIndex);
            if (msg.getRole() != Message.Role.TOOL) {
                break;
            }
            
            String toolUseId = extractToolUseId(msg);
            if (toolUseId == null) {
                startIndex++;
                continue;
            }
            
            // 向前查找对应的 ASSISTANT 消息
            int assistantIndex = findAssistantWithToolCall(messages, toolUseId, startIndex - 1);
            if (assistantIndex >= 0) {
                startIndex = assistantIndex;
                // 继续循环，因为新的 startIndex 可能也是 TOOL（多个连续 TOOL 的情况）
            } else {
                // 找不到对应的 ASSISTANT，跳过这条孤立的 TOOL 消息
                logger.warning(String.format(
                    "[ContextWindowManager] 跳过孤立 TOOL 消息: toolUseId=%s 无对应 ASSISTANT", 
                    toolUseId
                ));
                startIndex++;
                break;
            }
        }
        return startIndex;
    }
    
    /**
     * 从 TOOL 消息中提取 toolUseId
     */
    private String extractToolUseId(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return null;
        }
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.ToolResultContent) {
                return ((Message.ToolResultContent) block).getToolUseId();
            }
        }
        return null;
    }
    
    /**
     * 向前查找包含指定 tool_call_id 的 ASSISTANT 消息
     * 
     * @param messages 消息列表
     * @param toolUseId 要查找的 tool_call_id
     * @param endIndex 搜索结束位置（包含）
     * @return ASSISTANT 消息的索引，如果找不到则返回 -1
     */
    private int findAssistantWithToolCall(List<Message> messages, String toolUseId, int endIndex) {
        for (int i = endIndex; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                for (Message.ToolCallInfo tc : msg.getToolCalls()) {
                    if (toolUseId.equals(tc.getId())) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }
    
    // Getters
    public int getContextLimit() { return contextLimit; }
    public int getMaxMessages() { return maxMessages; }
    public int getMinMessages() { return minMessages; }
}
