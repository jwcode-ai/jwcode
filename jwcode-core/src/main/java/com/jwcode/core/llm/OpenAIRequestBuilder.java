package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * OpenAI 请求构建器 - 严格遵循 OpenAI API 标准
 * 
 * OpenAI Chat Completions API 规范：
 * - messages 数组必须包含至少一条消息
 * - 系统消息（system）可选，但必须在第一位
 * - 消息必须交替：user -> assistant -> user -> assistant
 * - tool 消息必须紧跟在 assistant 消息之后（对应 tool_calls）
 */
public class OpenAIRequestBuilder {
    
    private static final Logger logger = Logger.getLogger(OpenAIRequestBuilder.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private final String model;
    private Double temperature;
    private Integer maxTokens;
    private final List<LLMMessage> messages = new ArrayList<>();
    private final List<LLMTool> tools = new ArrayList<>();
    
    // 常量配置
    private static final int MAX_MESSAGES = 20; // 限制消息数量防止请求过大
    
    public OpenAIRequestBuilder(String model) {
        if (model == null || model.isEmpty()) {
            throw new IllegalArgumentException("Model is required");
        }
        this.model = model;
    }
    
    public OpenAIRequestBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }
    
    public OpenAIRequestBuilder maxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }
    
    // 用于汇总无效消息警告
    private int invalidMessageCount = 0;
    private String lastInvalidRole = null;
    
    /**
     * 添加消息 - 自动处理消息顺序和验证
     */
    public OpenAIRequestBuilder addMessage(LLMMessage message) {
        if (message == null) return this;
        
        if (!message.isValid()) {
            invalidMessageCount++;
            lastInvalidRole = message.getRole().name();
            return this;
        }
        
        messages.add(message);
        return this;
    }
    
    /**
     * 获取无效消息统计（用于日志输出）
     */
    public String getInvalidMessageStats() {
        if (invalidMessageCount == 0) {
            return null;
        }
        return String.format("[OpenAI] Skipped %d invalid message(s), last role=%s", 
            invalidMessageCount, lastInvalidRole);
    }
    
    /**
     * 添加系统消息 - 会自动放在最前面
     */
    public OpenAIRequestBuilder addSystemMessage(String content) {
        if (content == null || content.isEmpty()) return this;
        
        // 检查是否已有系统消息
        boolean hasSystem = messages.stream()
            .anyMatch(m -> m.getRole() == LLMMessage.Role.SYSTEM);
        
        if (hasSystem) {
            logger.fine("[OpenAI] Replacing existing system message");
            messages.removeIf(m -> m.getRole() == LLMMessage.Role.SYSTEM);
        }
        
        // 插入到最前面
        messages.add(0, LLMMessage.system(content));
        return this;
    }
    
    /**
     * 添加用户消息
     */
    public OpenAIRequestBuilder addUserMessage(String content) {
        messages.add(LLMMessage.user(content));
        return this;
    }
    
    /**
     * 添加工具
     */
    public OpenAIRequestBuilder addTool(LLMTool tool) {
        if (tool != null) {
            tools.add(tool);
        }
        return this;
    }
    
    /**
     * 添加工具列表
     */
    public OpenAIRequestBuilder addTools(List<LLMTool> tools) {
        if (tools != null) {
            this.tools.addAll(tools);
        }
        return this;
    }
    
    /**
     * 构建请求体
     */
    public ObjectNode build() {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        
        // 处理消息
        List<LLMMessage> processedMessages = processMessages(messages);
        
        // 构建 messages 数组
        ArrayNode messagesArray = body.putArray("messages");
        int validCount = 0;
        
        for (LLMMessage msg : processedMessages) {
            messagesArray.addPOJO(msg.toOpenAIFormat());
            validCount++;
        }
        
        logger.info("[OpenAI] Building request with " + validCount + " messages");
        
        // 输出无效消息统计（如果有）
        String invalidStats = getInvalidMessageStats();
        if (invalidStats != null) {
            logger.warning(invalidStats);
        }
        
        // 可选参数
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        
        body.put("stream", false);
        
        // 工具
        if (!tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (LLMTool tool : tools) {
                toolsArray.addPOJO(tool.toOpenAIFormat());
            }
        }
        
        return body;
    }
    
    /**
     * 处理消息列表 - 智能截断，保留 tool_calls 上下文
     * 
     * 关键修复：TOOL 消息必须保留其对应的 ASSISTANT 消息（包含 tool_calls），
     * 否则会导致 "tool_call_id is not found" 错误
     */
    private List<LLMMessage> processMessages(List<LLMMessage> originalMessages) {
        if (originalMessages.isEmpty()) {
            throw new IllegalArgumentException("At least one message is required");
        }
        
        List<LLMMessage> result = new ArrayList<>();
        
        // 1. 提取系统消息（必须在第一位）
        LLMMessage systemMsg = null;
        for (LLMMessage msg : originalMessages) {
            if (msg.getRole() == LLMMessage.Role.SYSTEM) {
                systemMsg = msg;
                break;
            }
        }
        
        // 2. 收集非系统消息
        List<LLMMessage> nonSystemMessages = new ArrayList<>();
        for (LLMMessage msg : originalMessages) {
            if (msg.getRole() != LLMMessage.Role.SYSTEM && msg.isValid()) {
                nonSystemMessages.add(msg);
            }
        }
        
        // 3. 智能截断消息（保留 tool_calls 上下文）
        int keepCount = MAX_MESSAGES - (systemMsg != null ? 1 : 0);
        if (nonSystemMessages.size() > keepCount) {
            logger.info("[OpenAI] Smart truncating messages from " + nonSystemMessages.size() + " to " + keepCount);
            nonSystemMessages = smartTruncate(nonSystemMessages, keepCount);
        }
        
        // 4. 组装最终消息列表
        if (systemMsg != null) {
            result.add(systemMsg);
        }
        result.addAll(nonSystemMessages);
        
        // 5. 验证消息顺序（user -> assistant -> tool）
        validateMessageSequence(result);
        
        return result;
    }
    
    /**
     * 智能截断消息列表 - 保留 tool_calls 上下文
     * 
     * 规则：
     * 1. TOOL 消息必须保留其对应的 ASSISTANT 消息（包含 tool_calls）
     * 2. 从后向前遍历，优先保留最近的消息
     * 3. 超出限制时，丢弃最旧的消息（但保留必要的 tool_calls 上下文）
     */
    private List<LLMMessage> smartTruncate(List<LLMMessage> messages, int keepCount) {
        if (messages.size() <= keepCount) {
            return messages;
        }
        
        // 收集所有需要保留的 tool_call_ids
        Set<String> requiredToolCallIds = new HashSet<>();
        
        // 第一遍：从后向前收集所有 tool_call_ids
        for (int i = messages.size() - 1; i >= 0; i--) {
            LLMMessage msg = messages.get(i);
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getId() != null) {
                        requiredToolCallIds.add(tc.getId());
                    }
                }
            }
        }
        
        // 第二遍：从后向前选择要保留的消息
        List<LLMMessage> toKeep = new ArrayList<>();
        Set<String> usedToolCallIds = new HashSet<>();
        
        for (int i = messages.size() - 1; i >= 0 && toKeep.size() < keepCount; i--) {
            LLMMessage msg = messages.get(i);
            
            // ASSISTANT 消息：总是保留（可能包含 tool_calls）
            if (msg.getRole() == LLMMessage.Role.ASSISTANT) {
                toKeep.add(0, msg);
                // 更新已使用的 tool_call_ids
                if (msg.hasToolCalls()) {
                    for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                        if (tc.getId() != null) {
                            usedToolCallIds.add(tc.getId());
                        }
                    }
                }
            }
            // TOOL 消息：只保留有对应 ASSISTANT 的
            else if (msg.getRole() == LLMMessage.Role.TOOL) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId != null && usedToolCallIds.contains(toolCallId)) {
                    toKeep.add(0, msg);
                } else if (toolCallId == null || !requiredToolCallIds.contains(toolCallId)) {
                    // 没有对应的 ASSISTANT 或不在需要列表中，跳过
                    logger.fine("[OpenAI] Skipping orphaned TOOL message: " + toolCallId);
                } else {
                    // TOOL 有对应的 tool_call_id 但 ASSISTANT 还没添加
                    // 先添加，后续循环会处理
                    toKeep.add(0, msg);
                }
            }
            // USER 消息：直接保留
            else {
                toKeep.add(0, msg);
            }
        }
        
        // 过滤掉孤立的 TOOL 消息（没有对应 ASSISTANT 的）
        List<LLMMessage> filtered = new ArrayList<>();
        usedToolCallIds.clear();
        
        for (LLMMessage msg : toKeep) {
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                filtered.add(msg);
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getId() != null) {
                        usedToolCallIds.add(tc.getId());
                    }
                }
            } else if (msg.getRole() == LLMMessage.Role.TOOL) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId != null && usedToolCallIds.contains(toolCallId)) {
                    filtered.add(msg);
                } else {
                    logger.fine("[OpenAI] Removed orphaned TOOL message: " + toolCallId);
                }
            } else {
                filtered.add(msg);
            }
        }
        
        int skipped = messages.size() - filtered.size();
        if (skipped > 0) {
            logger.info("[OpenAI] Smart truncate: kept " + filtered.size() + " messages, skipped " + skipped);
        }
        
        return filtered;
    }
    
    /**
     * 验证消息序列是否符合 OpenAI 规范
     */
    private void validateMessageSequence(List<LLMMessage> messages) {
        LLMMessage.Role lastRole = null;
        
        for (int i = 0; i < messages.size(); i++) {
            LLMMessage msg = messages.get(i);
            LLMMessage.Role role = msg.getRole();
            
            // 系统消息只能在第一位
            if (role == LLMMessage.Role.SYSTEM && i != 0) {
                logger.warning("[OpenAI] System message should be at position 0, found at " + i);
            }
            
            // 检查消息序列
            if (lastRole != null) {
                // TOOL 消息后面必须是 ASSISTANT 或另一个 TOOL（多个工具结果）
                // 不允许 TOOL -> USER 或 TOOL -> SYSTEM
                if (lastRole == LLMMessage.Role.TOOL && 
                    (role == LLMMessage.Role.USER || role == LLMMessage.Role.SYSTEM)) {
                    logger.warning("[OpenAI] Invalid sequence: TOOL should be followed by ASSISTANT/TOOL, found " + role);
                }
            }
            
            lastRole = role;
        }
    }
}
