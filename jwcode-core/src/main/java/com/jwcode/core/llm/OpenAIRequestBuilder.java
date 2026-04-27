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
    private static final int MAX_MESSAGES = Integer.MAX_VALUE; // 不限制消息数量，由上下文窗口管理器处理 token 超限
    
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
        
        // 【关键修复】验证并修复 tool_calls 配对完整性
        processedMessages = validateAndFixToolCalls(processedMessages);
        
        // 构建 messages 数组
        ArrayNode messagesArray = body.putArray("messages");
        int validCount = 0;
        
        for (LLMMessage msg : processedMessages) {
            messagesArray.addPOJO(msg.toOpenAIFormat());
            validCount++;
        }
        
        logger.fine("[OpenAI] Building request with " + validCount + " messages");
        
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
     * 【关键修复】验证并修复 tool_calls 配对完整性（双向验证）
     * 
     * 问题：当消息被截断或压缩时，可能出现两种不匹配：
     * 1. ASSISTANT 消息中的 tool_calls 被保留，但对应的 TOOL 消息被丢弃
     *    -> API 报错："an assistant message with 'tool_calls' must be followed by tool messages"
     * 2. TOOL 消息被保留，但对应的 ASSISTANT 消息（包含 tool_calls）被丢弃
     *    -> API 报错："tool result's tool id ... not found"
     * 
     * 解决方案（双向验证）：
     * 1. 收集所有 ASSISTANT 消息中的 tool_call_id
     * 2. 移除没有对应 ASSISTANT 的孤立 TOOL 消息
     * 3. 基于清理后的 TOOL 消息，移除 ASSISTANT 中孤立的 tool_calls
     * 
     * @param messages 处理后的消息列表
     * @return 修复后的消息列表
     */
    private List<LLMMessage> validateAndFixToolCalls(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        
        // ========== 第一轮：收集所有 ASSISTANT 消息中的 tool_call_id ==========
        Set<String> assistantToolCallIds = new HashSet<>();
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getId() != null) {
                        assistantToolCallIds.add(tc.getId());
                    }
                }
            }
        }
        
        // ========== 第二轮：移除孤立的 TOOL 消息（没有对应 ASSISTANT） ==========
        List<LLMMessage> afterToolCleanup = new ArrayList<>();
        int removedToolCount = 0;
        
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.TOOL) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId == null || !assistantToolCallIds.contains(toolCallId)) {
                    logger.warning("[OpenAI] Removing orphaned TOOL message: tool_call_id=" + toolCallId + 
                        " (no corresponding ASSISTANT tool_calls)");
                    removedToolCount++;
                    continue;
                }
            }
            afterToolCleanup.add(msg);
        }
        
        if (removedToolCount > 0) {
            logger.warning("[OpenAI] Removed " + removedToolCount + " orphaned TOOL message(s)");
        }
        
        // 如果没有 TOOL 消息了，直接返回（ASSISTANT 中的 tool_calls 将在下一轮被清理）
        // ========== 第三轮：收集清理后的 TOOL 消息中的 tool_call_id ==========
        Set<String> validToolCallIds = new HashSet<>();
        for (LLMMessage msg : afterToolCleanup) {
            if (msg.getRole() == LLMMessage.Role.TOOL) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId != null && !toolCallId.isEmpty()) {
                    validToolCallIds.add(toolCallId);
                }
            }
        }
        
        if (validToolCallIds.isEmpty() && assistantToolCallIds.isEmpty()) {
            // 没有 tool_calls，不需要验证
            return afterToolCleanup;
        }
        
        logger.info("[OpenAI] Valid tool_call_ids found: " + validToolCallIds.size());
        
        // ========== 第四轮：处理每个 ASSISTANT 消息，移除孤立的 tool_calls ==========
        List<LLMMessage> finalResult = new ArrayList<>();
        int fixedAssistantCount = 0;
        
        for (LLMMessage msg : afterToolCleanup) {
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                List<LLMMessage.ToolCall> originalToolCalls = msg.getToolCalls();
                List<LLMMessage.ToolCall> validToolCalls = new ArrayList<>();
                
                for (LLMMessage.ToolCall tc : originalToolCalls) {
                    String tcId = tc.getId();
                    if (tcId != null && validToolCallIds.contains(tcId)) {
                        validToolCalls.add(tc);
                    } else {
                        logger.fine("[OpenAI] Removing orphaned tool_call: " + tcId + 
                            " (no corresponding TOOL message)");
                    }
                }
                
                // 如果所有 tool_calls 都被移除，创建纯文本 assistant 消息
                if (validToolCalls.isEmpty()) {
                    logger.warning("[OpenAI] All tool_calls orphaned, converting to text-only message. " +
                        "Original tool_calls=" + originalToolCalls.stream().map(LLMMessage.ToolCall::getId).toList() + 
                        ", validToolCallIds=" + validToolCallIds);
                    finalResult.add(LLMMessage.assistant(
                        msg.getContent() != null ? msg.getContent() : "",
                        msg.getReasoningContent()
                    ));
                } else if (validToolCalls.size() < originalToolCalls.size()) {
                    // 部分 tool_calls 被移除
                    logger.info("[OpenAI] Fixed assistant message: " + originalToolCalls.size() + 
                        " -> " + validToolCalls.size() + " tool_calls");
                    fixedAssistantCount++;
                    finalResult.add(LLMMessage.assistantWithTools(
                        msg.getContent() != null ? msg.getContent() : "",
                        validToolCalls,
                        msg.getReasoningContent()
                    ));
                } else {
                    // 所有 tool_calls 都有效
                    finalResult.add(msg);
                }
            } else {
                finalResult.add(msg);
            }
        }
        
        if (fixedAssistantCount > 0) {
            logger.info("[OpenAI] Fixed " + fixedAssistantCount + " assistant messages with orphaned tool_calls");
        }
        
        return finalResult;
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
     * 智能截断消息列表 - 保留 tool_calls 上下文，确保消息序列有效
     * 
     * 规则：
     * 1. 按组截断：USER -> ASSISTANT -> TOOL 为一组，不可拆分
     * 2. TOOL 消息必须保留其对应的 ASSISTANT 消息（包含 tool_calls）
     * 3. 从后向前遍历，优先保留最近的消息
     * 4. 如果截断会破坏序列，则整组丢弃
     */
    private List<LLMMessage> smartTruncate(List<LLMMessage> messages, int keepCount) {
        if (messages.size() <= keepCount) {
            return messages;
        }
        
        // 第一步：分析消息结构，按组划分
        // 每组结构：USER -> ASSISTANT -> TOOL*
        List<List<LLMMessage>> groups = new ArrayList<>();
        List<LLMMessage> currentGroup = new ArrayList<>();
        LLMMessage.Role lastRole = null;
        
        for (LLMMessage msg : messages) {
            // 跳过系统消息（会在后面单独处理）
            if (msg.getRole() == LLMMessage.Role.SYSTEM) {
                if (!currentGroup.isEmpty()) {
                    groups.add(currentGroup);
                    currentGroup = new ArrayList<>();
                }
                continue;
            }
            
            // 新的 USER 消息开始新组
            if (msg.getRole() == LLMMessage.Role.USER && lastRole != LLMMessage.Role.USER) {
                if (!currentGroup.isEmpty()) {
                    groups.add(currentGroup);
                }
                currentGroup = new ArrayList<>();
            }
            
            currentGroup.add(msg);
            lastRole = msg.getRole();
        }
        
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        
        // 第二步：从后向前选择要保留的组
        List<LLMMessage> toKeep = new ArrayList<>();
        Set<String> validToolCallIds = new HashSet<>();
        
        // 收集所有有效的 tool_call_ids（来自保留的 ASSISTANT 消息）
        for (int i = groups.size() - 1; i >= 0 && toKeep.size() < keepCount; i--) {
            List<LLMMessage> group = groups.get(i);
            
            // 检查这组是否会导致消息数量超限
            if (toKeep.size() + group.size() > keepCount) {
                // 如果超限，检查是否可以部分保留
                // 规则：TOOL 必须跟在 ASSISTANT 后面
                if (canPartiallyKeepGroup(group, validToolCallIds, keepCount - toKeep.size())) {
                    List<LLMMessage> partial = getPartialGroup(group, validToolCallIds, keepCount - toKeep.size());
                    toKeep.addAll(0, partial);
                }
                break;
            }
            
            // 验证组的有效性
            if (isValidGroup(group, validToolCallIds)) {
                // 更新有效的 tool_call_ids
                for (LLMMessage msg : group) {
                    if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                        for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                            if (tc.getId() != null) {
                                validToolCallIds.add(tc.getId());
                            }
                        }
                    }
                }
                toKeep.addAll(0, group);
            } else {
                // 组无效，跳过（可能是孤立的 TOOL 消息）
                logger.fine("[OpenAI] Skipping invalid group at index " + i);
            }
        }
        
        int skipped = messages.size() - toKeep.size();
        if (skipped > 0) {
            logger.info("[OpenAI] Smart truncate: kept " + toKeep.size() + " messages, skipped " + skipped);
        }
        
        return toKeep;
    }
    
    /**
     * 检查组是否有效
     * 组必须是完整的 USER -> ASSISTANT -> TOOL* 序列
     */
    private boolean isValidGroup(List<LLMMessage> group, Set<String> validToolCallIds) {
        if (group.isEmpty()) {
            return false;
        }
        
        // 检查第一个消息是否是 USER 或 ASSISTANT
        LLMMessage.Role firstRole = group.get(0).getRole();
        if (firstRole != LLMMessage.Role.USER && firstRole != LLMMessage.Role.ASSISTANT) {
            // 如果是 TOOL，检查是否有对应的有效 tool_call_id
            if (firstRole == LLMMessage.Role.TOOL) {
                return group.stream().allMatch(msg -> {
                    if (msg.getRole() == LLMMessage.Role.TOOL) {
                        String toolCallId = msg.getToolCallId();
                        return toolCallId != null && validToolCallIds.contains(toolCallId);
                    }
                    return true;
                });
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查是否可以部分保留组
     */
    private boolean canPartiallyKeepGroup(List<LLMMessage> group, Set<String> validToolCallIds, int remainingSlots) {
        // 至少保留 USER + ASSISTANT
        return remainingSlots >= 2;
    }
    
    /**
     * 获取部分保留的组
     * 优先保留 USER + ASSISTANT，丢弃 TOOL（如果超限）
     * 
     * FIX: 如果组内 ASSISTANT 带有 tool_calls，必须整组（含所有 TOOL）保留或整组丢弃，
     *      不允许出现 "保留 ASSISTANT 但丢弃其 TOOL 结果" 的半吊子状态，否则会导致
     *      API 报错 "an assistant message with 'tool_calls' must be followed by tool messages"。
     */
    private List<LLMMessage> getPartialGroup(List<LLMMessage> group, Set<String> validToolCallIds, int maxSlots) {
        // 先检查组内是否包含带 tool_calls 的 ASSISTANT
        boolean hasToolCallsInGroup = group.stream()
            .anyMatch(m -> m.getRole() == LLMMessage.Role.ASSISTANT && m.hasToolCalls());
        
        // 如果包含 tool_calls，预判整组（含 TOOL）是否能完整容纳
        if (hasToolCallsInGroup) {
            long toolCount = group.stream()
                .filter(m -> m.getRole() == LLMMessage.Role.TOOL)
                .filter(m -> {
                    String tid = m.getToolCallId();
                    return tid != null && validToolCallIds.contains(tid);
                })
                .count();
            long nonToolCount = group.size() - toolCount;
            
            // 如果完整组（含有效 TOOL）超出容量，直接放弃该组，防止 orphaned tool_calls
            if (nonToolCount + toolCount > maxSlots) {
                logger.warning("[OpenAI] Dropping entire group with tool_calls (size=" + 
                    group.size() + ") to preserve sequence integrity. maxSlots=" + maxSlots);
                return new ArrayList<>();
            }
        }
        
        List<LLMMessage> result = new ArrayList<>();
        
        for (LLMMessage msg : group) {
            if (result.size() >= maxSlots) {
                break;
            }
            
            // 跳过孤立的 TOOL 消息
            if (msg.getRole() == LLMMessage.Role.TOOL) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId == null || !validToolCallIds.contains(toolCallId)) {
                    logger.fine("[OpenAI] Skipping orphaned TOOL in partial group: " + toolCallId);
                    continue;
                }
            }
            
            result.add(msg);
            
            // 更新有效的 tool_call_ids
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getId() != null) {
                        validToolCallIds.add(tc.getId());
                    }
                }
            }
        }
        
        return result;
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
                    // 【修复】TOOL 后跟 USER 是允许的（用户可以中断工具执行并发送新消息）
                    // 不再输出警告，因为这是有效的使用场景
                    logger.info("[OpenAI] Valid sequence: TOOL followed by " + role + " (user interrupt)");
                }
            }
            
            lastRole = role;
        }
    }
}
