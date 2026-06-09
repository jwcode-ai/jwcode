package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
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
        
        List<LLMMessage> nonSystem = new ArrayList<>();
        for (LLMMessage msg : messages) {
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
}
