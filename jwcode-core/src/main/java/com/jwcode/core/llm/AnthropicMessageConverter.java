package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Anthropic Messages API format message converter.
 * Converts between internal LLM message format and Anthropic Messages API format.
 */
public class AnthropicMessageConverter {

    private static final Logger log = Logger.getLogger(AnthropicMessageConverter.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode toRequestBody(List<LLMMessage> messages, List<LLMTool> tools, ServiceConfig config) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getModel());
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 8192;
        body.put("max_tokens", maxTokens);
        
        String systemPrompt = extractSystemPrompt(messages);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            body.put("system", systemPrompt);
        }
        
        ArrayNode messagesArray = body.putArray("messages");
        for (JsonNode msg : toAnthropicMessages(messages)) {
            messagesArray.add(msg);
        }
        
        if (config.getTemperature() != null) {
            body.put("temperature", config.getTemperature());
        }
        
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (JsonNode tool : toAnthropicTools(tools)) {
                toolsArray.add(tool);
            }
        }
        
        return body;
    }

    private String extractSystemPrompt(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.SYSTEM) return msg.getContent();
        }
        return null;
    }

    public List<JsonNode> toAnthropicMessages(List<LLMMessage> messages) {
        List<JsonNode> result = new ArrayList<>();
        if (messages == null || messages.isEmpty()) return result;

        // 预验证：移除孤立的 tool_use 和 tool_result，避免 DeepSeek 等 API 报错
        List<LLMMessage> validated = preValidateToolPairs(messages);

        List<LLMMessage> nonSystem = new ArrayList<>();
        for (LLMMessage msg : validated) {
            if (msg.getRole() != LLMMessage.Role.SYSTEM) nonSystem.add(msg);
        }

        for (LLMMessage msg : nonSystem) {
            if (msg.getRole() == LLMMessage.Role.USER) {
                ArrayNode contentArray = mapper.createArrayNode();
                contentArray.add(createTextBlock(msg.getContent()));
                result.add(createUserMessage(contentArray));
            } else if (msg.getRole() == LLMMessage.Role.TOOL) {
                JsonNode toolBlock = toToolResultBlock(msg);
                if (!result.isEmpty() && "user".equals(result.get(result.size()-1).get("role").asText())) {
                    ((ArrayNode) result.get(result.size()-1).get("content")).add(toolBlock);
                } else {
                    ArrayNode contentArray = mapper.createArrayNode();
                    contentArray.add(toolBlock);
                    result.add(createUserMessage(contentArray));
                }
            } else if (msg.getRole() == LLMMessage.Role.ASSISTANT) {
                ArrayNode contentArray = mapper.createArrayNode();
                String content = msg.getContent();
                if (content != null && !content.isEmpty()) {
                    contentArray.add(createTextBlock(content));
                }
                String reasoning = msg.getReasoningContent();
                if (reasoning != null && !reasoning.isEmpty()) {
                    contentArray.add(createThinkingBlock(reasoning));
                }
                if (msg.hasToolCalls()) {
                    for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                        contentArray.add(createToolUseBlock(tc));
                    }
                }
                ObjectNode assistantMsg = mapper.createObjectNode();
                assistantMsg.put("role", "assistant");
                assistantMsg.set("content", contentArray);
                result.add(assistantMsg);
           }
       }

        // Anthropic API要求：tool_use后必须紧邻tool_result，中间不能有其他消息
        // 后置验证：反复扫描直到没有需要处理的孤立块
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < result.size(); i++) {
                JsonNode msg = result.get(i);
                if (!"assistant".equals(msg.get("role").asText())) continue;
                JsonNode content = msg.get("content");
                if (content == null || !content.isArray()) continue;

                // 收集这条assistant消息中的所有tool_use ID
                List<String> toolUseIds = new ArrayList<>();
                for (JsonNode block : content) {
                    if ("tool_use".equals(block.get("type").asText(""))) {
                        toolUseIds.add(block.get("id").asText(""));
                    }
                }
                if (toolUseIds.isEmpty()) continue;

                // 检查下一条消息是否是user且包含对应的tool_result
                if (i + 1 >= result.size()) {
                    log.warning("[AnthropicConverter] Removing tool_use from last assistant msg: " + toolUseIds);
                    removeToolUseBlocks((ObjectNode) msg);
                    changed = true;
                } else {
                    JsonNode nextMsg = result.get(i + 1);
                    if (!"user".equals(nextMsg.get("role").asText())) {
                        log.warning("[AnthropicConverter] Removing tool_use from msg " + i + ": next role=" + nextMsg.get("role").asText());
                        removeToolUseBlocks((ObjectNode) msg);
                        changed = true;
                    } else {
                        Set<String> nextResultIds = collectToolResultIds(nextMsg);
                        List<String> orphaned = new ArrayList<>();
                        for (String tid : toolUseIds) {
                            if (!nextResultIds.contains(tid)) {
                                orphaned.add(tid);
                            }
                        }
                        if (!orphaned.isEmpty()) {
                            log.warning("[AnthropicConverter] Removing orphaned tool_use from msg " + i + ": " + orphaned);
                            removeSpecificToolUseBlocks((ObjectNode) msg, orphaned);
                            changed = true;
                        }
                    }
                }
            }
        } while (changed);

        // 清理剩余的孤立tool_result
        cleanupOrphanedToolResults(result);
        return result;
    }

    public ObjectNode toToolResultBlock(LLMMessage msg) {
        if (msg.getToolCallId() == null || msg.getToolCallId().isEmpty()) {
            throw new IllegalArgumentException("tool_call_id must not be empty");
        }
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "tool_result");
        block.put("tool_use_id", msg.getToolCallId());
        block.put("content", msg.getContent() != null ? msg.getContent() : "");
        return block;
    }

    public ArrayNode toAnthropicTools(List<LLMTool> tools) {
        ArrayNode toolsArray = mapper.createArrayNode();
        if (tools == null) return toolsArray;
        for (LLMTool tool : tools) {
            ObjectNode toolNode = toolsArray.addObject();
            toolNode.put("name", tool.getFunction().getName());
            toolNode.put("description", tool.getFunction().getDescription() != null ? tool.getFunction().getDescription() : "");
            Map<String, Object> params = tool.getFunction().getParameters();
            ObjectNode schema = mapper.createObjectNode();
            schema.put("type", params != null && params.containsKey("type") ? params.get("type").toString() : "object");
            if (params != null && params.containsKey("properties")) {
                schema.set("properties", mapper.valueToTree(params.get("properties")));
            } else {
                schema.set("properties", mapper.createObjectNode());
            }
            if (params != null && params.containsKey("required")) {
                ArrayNode reqArray = schema.putArray("required");
                Object rv = params.get("required");
                if (rv instanceof List) { for (Object item : (List<?>) rv) reqArray.add(item.toString()); }
            }
            toolNode.set("input_schema", schema);
        }
        return toolsArray;
    }

    public LLMResponse parseResponse(String responseBody) throws Exception {
        JsonNode json = mapper.readTree(responseBody);
        if (json.has("type") && "error".equals(json.get("type").asText())) {
            JsonNode error = json.get("error");
            return LLMResponse.error(
                mapAnthropicErrorCode(error.has("type") ? error.get("type").asText() : "api_error"),
                error.has("message") ? error.get("message").asText() : "Unknown Anthropic error");
        }
        LLMResponse.Builder builder = LLMResponse.builder().rawResponse(responseBody);
        if (json.has("model")) builder.model(json.get("model").asText());
        if (json.has("stop_reason")) {
            String sr = json.get("stop_reason").asText();
            builder.finishReason("end_turn".equals(sr) ? "stop" : sr);
        }
        if (json.has("content") && json.get("content").isArray()) {
            StringBuilder textContent = new StringBuilder();
            StringBuilder reasoningContent = new StringBuilder();
            List<LLMMessage.ToolCall> toolCalls = new ArrayList<>();
            for (JsonNode block : json.get("content")) {
                String type = block.has("type") ? block.get("type").asText() : "";
                if ("text".equals(type) && block.has("text")) textContent.append(block.get("text").asText());
                else if ("thinking".equals(type) && block.has("thinking")) reasoningContent.append(block.get("thinking").asText());
                else if ("tool_use".equals(type)) {
                    toolCalls.add(LLMMessage.ToolCall.builder()
                        .id(block.has("id") ? block.get("id").asText() : "")
                        .type("tool_use")
                        .function(block.has("name") ? block.get("name").asText() : "", 
                            block.has("input") ? mapper.writeValueAsString(block.get("input")) : "{}")
                        .build());
                }
            }
            builder.content(textContent.toString());
            if (reasoningContent.length() > 0) builder.reasoningContent(reasoningContent.toString());
            if (!toolCalls.isEmpty()) builder.toolCalls(toolCalls);
        }
        if (json.has("usage")) {
            JsonNode usage = json.get("usage");
            int inTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
            int outTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
            builder.promptTokens(inTokens).completionTokens(outTokens).totalTokens(inTokens + outTokens);
        }
        return builder.build();
    }

    public String mapAnthropicErrorCode(String type) {
        if (type == null) return "UNKNOWN_ERROR";
        switch (type) {
            case "rate_limit_error": return "RATE_LIMIT_HARD";
            case "overloaded_error": return "SERVER_OVERLOADED";
            case "invalid_request_error": return "CLIENT_ERROR";
            case "authentication_error": case "permission_error": return "AUTH_ERROR";
            case "not_found_error": return "CLIENT_ERROR";
            case "api_error": return "SERVER_ERROR";
            default: return "UNKNOWN_ERROR";
        }
    }

    private ObjectNode createUserMessage(ArrayNode content) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "user"); msg.set("content", content);
        return msg;
    }

    private ObjectNode createTextBlock(String text) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "text"); block.put("text", text != null ? text : "");
        return block;
    }

    private ObjectNode createThinkingBlock(String thinking) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "thinking"); block.put("thinking", thinking != null ? thinking : ""); block.put("signature", "");
        return block;
    }

    /**
     * 预验证 tool_use ↔ tool_result 配对，移除孤立的条目。
     * 防止 DeepSeek Anthropic 兼容 API 报错：
     * "tool_use ids were found without tool_result blocks immediately after"
     */
    private List<LLMMessage> preValidateToolPairs(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) return messages;

        // Phase 1: 收集所有被 tool_result 引用的 tool_call_id
        java.util.Set<String> resultIds = new java.util.HashSet<>();
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.TOOL && msg.getToolCallId() != null) {
                resultIds.add(msg.getToolCallId());
            }
        }

        // Phase 2: 遍历并清理
        List<LLMMessage> cleaned = new ArrayList<>();
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                // 只保留有对应 tool_result 的 tool_use
                List<LLMMessage.ToolCall> valid = new ArrayList<>();
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getId() != null && resultIds.contains(tc.getId())) {
                        valid.add(tc);
                    } else {
                        log.warning("[AnthropicConverter] Removing orphaned tool_use: " + tc.getId());
                    }
                }
                if (valid.isEmpty()) {
                    // 所有 tool_use 都是孤儿，转为纯文本消息
                    cleaned.add(LLMMessage.assistant(
                        msg.getContent() != null ? msg.getContent() : "",
                        msg.getReasoningContent()));
                } else if (valid.size() < msg.getToolCalls().size()) {
                    // 部分有效，部分孤儿
                    cleaned.add(LLMMessage.assistantWithTools(
                        msg.getContent(), valid, msg.getReasoningContent()));
                } else {
                    cleaned.add(msg);
                }
            } else if (msg.getRole() == LLMMessage.Role.TOOL) {
                // 移除没有对应 tool_use 的 tool_result
                if (msg.getToolCallId() == null || !hasMatchingToolUse(messages, msg.getToolCallId())) {
                    log.warning("[AnthropicConverter] Removing orphaned tool_result: " + msg.getToolCallId());
                    continue; // 跳过这个 tool_result
                }
                cleaned.add(msg);
            } else {
                cleaned.add(msg);
            }
        }

        // Phase 3: 移除开头孤立的 TOOL 消息（截断后可能残留）
        while (!cleaned.isEmpty() && cleaned.get(0).getRole() == LLMMessage.Role.TOOL) {
            log.warning("[AnthropicConverter] Removing leading TOOL message");
            cleaned.remove(0);
        }

        if (cleaned.size() != messages.size()) {
            log.info("[AnthropicConverter] Pre-validation: " + messages.size() + " -> " + cleaned.size() + " messages");
        }
        return cleaned;
    }

    private boolean hasMatchingToolUse(List<LLMMessage> messages, String toolCallId) {
        if (toolCallId == null) return false;
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (toolCallId.equals(tc.getId())) return true;
                }
            }
        }
        return false;
    }

    private ObjectNode createToolUseBlock(LLMMessage.ToolCall tc) {
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "tool_use"); block.put("id", tc.getId()); block.put("name", tc.getFunction().getName());
        try {
            String args = tc.getFunction().getArguments();
            block.set("input", args != null && !args.isEmpty() ? mapper.readTree(args) : mapper.createObjectNode());
        } catch (Exception e) {
            block.set("input", mapper.createObjectNode());
        }
        return block;
    }

    /**
     * 从assistant消息中移除所有tool_use blocks
     */
    private void removeToolUseBlocks(ObjectNode assistantMsg) {
        ArrayNode content = (ArrayNode) assistantMsg.get("content");
        if (content == null) return;
        ArrayNode cleaned = mapper.createArrayNode();
        for (JsonNode block : content) {
            if (!"tool_use".equals(block.get("type").asText(""))) {
                cleaned.add(block);
            }
        }
        assistantMsg.set("content", cleaned);
    }

    /**
     * 从assistant消息中移除指定的tool_use blocks
     */
    private void removeSpecificToolUseBlocks(ObjectNode assistantMsg, List<String> idsToRemove) {
        Set<String> removeSet = new java.util.HashSet<>(idsToRemove);
        ArrayNode content = (ArrayNode) assistantMsg.get("content");
        if (content == null) return;
        ArrayNode cleaned = mapper.createArrayNode();
        for (JsonNode block : content) {
            if ("tool_use".equals(block.get("type").asText(""))
                    && removeSet.contains(block.get("id").asText(""))) {
                continue;
            }
            cleaned.add(block);
        }
        assistantMsg.set("content", cleaned);
    }

    /**
     * 收集user消息中的所有tool_result ID
     */
    private Set<String> collectToolResultIds(JsonNode userMsg) {
        Set<String> ids = new java.util.HashSet<>();
        JsonNode content = userMsg.get("content");
        if (content == null || !content.isArray()) return ids;
        for (JsonNode block : content) {
            if ("tool_result".equals(block.get("type").asText(""))) {
                String id = block.get("tool_use_id").asText("");
                if (!id.isEmpty()) ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 清理没有前导tool_use的孤立tool_result
     */
    private void cleanupOrphanedToolResults(List<JsonNode> messages) {
        // 收集所有有效的tool_use ID
        Set<String> validToolUseIds = new java.util.HashSet<>();
        for (JsonNode msg : messages) {
            if (!"assistant".equals(msg.get("role").asText())) continue;
            JsonNode content = msg.get("content");
            if (content == null || !content.isArray()) continue;
            for (JsonNode block : content) {
                if ("tool_use".equals(block.get("type").asText(""))) {
                    String id = block.get("id").asText("");
                    if (!id.isEmpty()) validToolUseIds.add(id);
                }
            }
        }

        // 移除没有对应tool_use的tool_result
        for (JsonNode msg : messages) {
            if (!"user".equals(msg.get("role").asText())) continue;
            ArrayNode content = (ArrayNode) msg.get("content");
            if (content == null || content.size() == 0) continue;

            // 检查是否有tool_result没有被前导tool_use引用
            boolean hasOrphanedResult = false;
            for (JsonNode block : content) {
                if ("tool_result".equals(block.get("type").asText(""))) {
                    String id = block.get("tool_use_id").asText("");
                    if (!validToolUseIds.contains(id)) {
                        hasOrphanedResult = true;
                        break;
                    }
                }
            }

            if (!hasOrphanedResult) continue;

            // 移除孤立的tool_result，保留其他内容
            ArrayNode cleaned = mapper.createArrayNode();
            for (JsonNode block : content) {
                if ("tool_result".equals(block.get("type").asText(""))) {
                    String id = block.get("tool_use_id").asText("");
                    if (!validToolUseIds.contains(id)) {
                        log.warning("[AnthropicConverter] Removing orphaned tool_result: " + id);
                        continue;
                    }
                }
                cleaned.add(block);
            }
            ((ObjectNode) msg).set("content", cleaned);
        }
    }
}
