package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
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
    
    /**
     * 添加消息 - 自动处理消息顺序和验证
     */
    public OpenAIRequestBuilder addMessage(LLMMessage message) {
        if (message == null) return this;
        
        if (!message.isValid()) {
            logger.warning("[OpenAI] Skipping invalid message: role=" + message.getRole());
            return this;
        }
        
        messages.add(message);
        return this;
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
     * 处理消息列表 - 确保符合 OpenAI 规范
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
        
        // 3. 限制消息数量（保留系统消息 + 最近的消息）
        int keepCount = MAX_MESSAGES - (systemMsg != null ? 1 : 0);
        if (nonSystemMessages.size() > keepCount) {
            logger.info("[OpenAI] Truncating messages from " + nonSystemMessages.size() + " to " + keepCount);
            nonSystemMessages = nonSystemMessages.subList(
                nonSystemMessages.size() - keepCount, 
                nonSystemMessages.size()
            );
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
