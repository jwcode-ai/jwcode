package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.java.Log;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI 兼容的 LLM 服务实现
 * 
 * 支持：
 * - OpenAI
 * - Moonshot (Kimi)
 * - MiniMax
 * - 任何 OpenAI 兼容的 API
 */
@Log
public class OpenAILLMService implements LLMService {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final ServiceConfig config;
    private int currentKeyIndex = 0;
    
    public OpenAILLMService(ServiceConfig config) {
        this.config = config;
        // 增加连接超时到60秒
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();
        
        log.info("[OpenAILLMService] Initialized with model: " + config.getModel());
        log.info("[OpenAILLMService] Base URL: " + config.getBaseUrl());
        log.info("[OpenAILLMService] Timeout: " + config.getTimeoutSeconds() + "s");
    }
    
    @Override
    public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages) {
        return chatWithTools(messages, null);
    }
    
    @Override
    public CompletableFuture<LLMResponse> chatWithTools(List<LLMMessage> messages, List<LLMTool> tools) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = getNextApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("No API key configured");
            }
            
            String url = normalizeUrl(config.getBaseUrl());
            log.info("[OpenAI] ==========================================");
            log.info("[OpenAI] Request: POST " + url);
            log.info("[OpenAI] Model: " + config.getModel());
            log.info("[OpenAI] Message count: " + messages.size());
            
            int maxRetries = 2;
            int attempt = 0;
            
            while (attempt <= maxRetries) {
                try {
                    // 构建请求体
                    ObjectNode requestBody = buildRequestBody(messages, tools);
                    String requestJson = mapper.writeValueAsString(requestBody);
                    
                    // 打印请求参数
                    log.info("[OpenAI] ---------- Request Body ----------");
                    log.info("[OpenAI] " + requestJson);
                    log.info("[OpenAI] ---------- End Request Body ----------");
                    
                    // 发送请求
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                        .build();
                    
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    log.info("[OpenAI] Response status: " + response.statusCode());
                    
                    if (response.statusCode() == 200) {
                        // 打印响应内容
                        String responseBody = response.body();
                        log.info("[OpenAI] ---------- Response Body ----------");
                        log.info("[OpenAI] " + responseBody);
                        log.info("[OpenAI] ---------- End Response Body ----------");
                        return parseResponse(responseBody);
                    } else if (response.statusCode() == 429) {
                        // Rate limit, retry
                        attempt++;
                        if (attempt <= maxRetries) {
                            log.warning("[OpenAI] Rate limited, retrying (" + attempt + "/" + maxRetries + ")...");
                            Thread.sleep(1000 * attempt);
                            continue;
                        }
                        return LLMResponse.error("Rate limited. Please try again later.");
                    } else {
                        String errorBody = response.body();
                        log.severe("[OpenAI] ---------- Error Response ----------");
                        log.severe("[OpenAI] Status: " + response.statusCode());
                        log.severe("[OpenAI] Body: " + errorBody);
                        log.severe("[OpenAI] ---------- End Error Response ----------");
                        return LLMResponse.error("HTTP " + response.statusCode() + ": " + errorBody);
                    }
                    
                } catch (java.net.http.HttpTimeoutException e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[OpenAI] Request timeout, retrying (" + attempt + "/" + maxRetries + ")...");
                        // 切换 API key
                        apiKey = getNextApiKey();
                    } else {
                        log.severe("[OpenAI] Request timeout after " + (maxRetries + 1) + " attempts");
                        return LLMResponse.error("Request timed out. The model is taking too long to respond. " +
                            "Please try again or use a simpler prompt. " +
                            "Current timeout: " + config.getTimeoutSeconds() + " seconds");
                    }
                } catch (java.net.ConnectException e) {
                    // ConnectException 是 SocketException 的子类，必须先捕获
                    log.severe("[OpenAI] Connection failed: " + e.getMessage());
                    return LLMResponse.error("Connection failed. Please check your network and API endpoint: " + url);
                } catch (java.net.SocketException e) {
                    log.severe("[OpenAI] Connection reset: " + e.getMessage());
                    return LLMResponse.error("Connection reset. Possible causes:\n" +
                        "1. Request too large (too many messages in history) - Try 'clear' command\n" +
                        "2. Network instability\n" +
                        "3. API server closed connection\n" +
                        "Current messages: " + messages.size());
                } catch (Exception e) {
                    log.severe("[OpenAI] Request failed: " + e.getMessage());
                    return LLMResponse.error("Request failed: " + e.getMessage());
                }
            }
            
            return LLMResponse.error("Max retries exceeded");
        });
    }
    
    @Override
    public CompletableFuture<LLMTestResult> test() {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                List<LLMMessage> testMessages = List.of(
                    LLMMessage.system("You are a helpful assistant"),
                    LLMMessage.user("Hello")
                );
                
                LLMResponse response = chat(testMessages).get();
                long latency = System.currentTimeMillis() - startTime;
                
                if (response.isSuccess()) {
                    return LLMTestResult.success("Model available", latency);
                } else {
                    return LLMTestResult.error(response.getErrorMessage(), "Check API key and endpoint");
                }
            } catch (Exception e) {
                return LLMTestResult.error(e.getMessage(), "Check network connection");
            }
        });
    }
    
    @Override
    public String getModelName() {
        return config.getModel();
    }
    
    @Override
    public void close() {
        // HTTP client doesn't need explicit close
    }
    
    /**
     * 构建请求体 - 使用标准的 OpenAIRequestBuilder
     * 
     * 消息截断策略：
     * - 保留所有 assistant 消息（可能包含 tool_calls）
     * - 保留所有 user 消息（安全的）
     * - 只截断 tool 消息（可以丢弃，只要对应的 assistant 消息保留）
     * - 保留系统消息（总是在最前面）
     * 
     * 这样确保消息序列总是 valid 的，不会出现 tool 消息没有对应 assistant 消息的问题
     */
    private ObjectNode buildRequestBody(List<LLMMessage> messages, List<LLMTool> tools) {
        List<LLMMessage> filteredMessages = messages;
        
        // 检查是否有需要截断的 tool 消息
        if (messages.size() > 100) {
            log.warning("[OpenAI] Too many messages (" + messages.size() + "), filtering tool messages");
            
            filteredMessages = new ArrayList<>();
            int toolCount = 0;
            
            for (LLMMessage msg : messages) {
                // 保留系统消息
                if (msg.getRole() == LLMMessage.Role.SYSTEM) {
                    filteredMessages.add(msg);
                }
                // 保留 assistant 和 user 消息
                else if (msg.getRole() == LLMMessage.Role.ASSISTANT || 
                         msg.getRole() == LLMMessage.Role.USER) {
                    filteredMessages.add(msg);
                }
                // 处理 tool 消息：只保留最近的 100 条
                else if (msg.getRole() == LLMMessage.Role.TOOL) {
                    toolCount++;
                    // 只保留最近的 100 条 tool 消息（通常是最后一轮调用的结果）
                    if (toolCount > 100) {
                        log.fine("[OpenAI] Skipping old tool message: " + msg.getToolCallId());
                        continue;
                    }
                    filteredMessages.add(msg);
                }
            }
            
            log.info("[OpenAI] Filtered to " + filteredMessages.size() + " messages (skipped " + 
                     (messages.size() - filteredMessages.size()) + " old tool messages)");
        }
        
        OpenAIRequestBuilder builder = new OpenAIRequestBuilder(config.getModel())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens());
        
        // 添加过滤后的消息
        for (LLMMessage msg : filteredMessages) {
            builder.addMessage(msg);
        }
        
        // 添加工具
        if (tools != null) {
            builder.addTools(tools);
        }
        
        return builder.build();
    }
    
    /**
     * 修复工具消息序列：确保每个 tool 消息都有对应的 assistant 消息（包含 tool_calls）
     * 
     * 如果截断后的消息以 tool 开头，需要向前查找对应的 assistant 消息
     */
    private List<LLMMessage> fixToolMessageSequence(
            List<LLMMessage> truncatedMessages, 
            List<LLMMessage> allMessages,
            int originalStartIndex) {
        
        if (truncatedMessages.isEmpty()) {
            return truncatedMessages;
        }
        
        // 收集所有需要保留的 tool_call_id
        java.util.Set<String> requiredToolCallIds = new java.util.HashSet<>();
        for (LLMMessage msg : truncatedMessages) {
            if (msg.getRole() == LLMMessage.Role.TOOL && msg.getToolCallId() != null) {
                requiredToolCallIds.add(msg.getToolCallId());
            }
        }
        
        // 向前查找包含这些 tool_calls 的 assistant 消息
        List<LLMMessage> additionalMessages = new ArrayList<>();
        int searchIndex = originalStartIndex - 1;
        java.util.Set<String> foundToolCallIds = new java.util.HashSet<>();
        
        while (searchIndex >= 0 && !requiredToolCallIds.isEmpty()) {
            LLMMessage msg = allMessages.get(searchIndex);
            
            // 如果是 assistant 消息且有 tool_calls，检查是否包含我们需要的 id
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                boolean hasMatchingToolCall = false;
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (requiredToolCallIds.contains(tc.getId())) {
                        hasMatchingToolCall = true;
                        foundToolCallIds.add(tc.getId());
                    }
                }
                
                if (hasMatchingToolCall) {
                    // 插入到最前面（保持原有顺序）
                    additionalMessages.add(0, msg);
                    requiredToolCallIds.removeAll(foundToolCallIds);
                }
            }
            
            searchIndex--;
        }
        
        // 如果有未找到的 tool_call_id，需要移除对应的 tool 消息
        if (!requiredToolCallIds.isEmpty()) {
            log.warning("[OpenAI] Some tool_call_ids not found in history: " + requiredToolCallIds);
            truncatedMessages.removeIf(msg -> 
                msg.getRole() == LLMMessage.Role.TOOL && 
                requiredToolCallIds.contains(msg.getToolCallId())
            );
        }
        
        // 合并：additionalMessages 插入到 truncatedMessages 前面
        if (!additionalMessages.isEmpty()) {
            log.info("[OpenAI] Added " + additionalMessages.size() + " assistant messages for tool context");
            List<LLMMessage> result = new ArrayList<>(additionalMessages);
            result.addAll(truncatedMessages);
            return result;
        }
        
        return truncatedMessages;
    }
    
    /**
     * 解析响应
     */
    private LLMResponse parseResponse(String responseBody) throws Exception {
        JsonNode json = mapper.readTree(responseBody);
        
        LLMResponse.Builder builder = LLMResponse.builder()
            .rawResponse(responseBody);
        
        if (json.has("choices") && json.get("choices").isArray() && json.get("choices").size() > 0) {
            JsonNode choice = json.get("choices").get(0);
            JsonNode message = choice.get("message");
            
            // 内容
            if (message.has("content") && !message.get("content").isNull()) {
                String content = message.get("content").asText();
                builder.content(content);
                log.info("[OpenAI] Parsed content length: " + content.length());
            } else {
                builder.content("");
                log.info("[OpenAI] No content in response");
            }
            
            // 工具调用
            if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                List<LLMMessage.ToolCall> toolCalls = new ArrayList<>();
                log.info("[OpenAI] Tool calls found: " + message.get("tool_calls").size());
                for (JsonNode tc : message.get("tool_calls")) {
                    String toolName = tc.get("function").get("name").asText();
                    log.info("[OpenAI] Tool: " + toolName);
                    LLMMessage.ToolCall toolCall = LLMMessage.ToolCall.builder()
                        .id(tc.get("id").asText())
                        .type(tc.get("type").asText())
                        .function(toolName, tc.get("function").get("arguments").asText())
                        .build();
                    toolCalls.add(toolCall);
                }
                builder.toolCalls(toolCalls);
            }
            
            // 完成原因
            if (choice.has("finish_reason")) {
                String finishReason = choice.get("finish_reason").asText();
                builder.finishReason(finishReason);
                log.info("[OpenAI] Finish reason: " + finishReason);
            }
        }
        
        // 模型
        if (json.has("model")) {
            builder.model(json.get("model").asText());
        }
        
        // 用量
        if (json.has("usage")) {
            JsonNode usage = json.get("usage");
            int promptTokens = usage.get("prompt_tokens").asInt();
            int completionTokens = usage.get("completion_tokens").asInt();
            int totalTokens = usage.get("total_tokens").asInt();
            builder.promptTokens(promptTokens);
            builder.completionTokens(completionTokens);
            builder.totalTokens(totalTokens);
            log.info("[OpenAI] Tokens - Prompt: " + promptTokens + 
                     ", Completion: " + completionTokens + 
                     ", Total: " + totalTokens);
        }
        
        LLMResponse response = builder.build();
        log.info("[OpenAI] ==========================================");
        return response;
    }
    
    /**
     * 获取下一个 API Key（轮询）
     */
    private String getNextApiKey() {
        List<String> keys = config.getApiKeys();
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        String key = keys.get(currentKeyIndex);
        currentKeyIndex = (currentKeyIndex + 1) % keys.size();
        return key;
    }
    
    /**
     * 规范化 URL
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            log.warning("[OpenAILLMService] URL is empty, using default");
            return "https://api.openai.com/v1/chat/completions";
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        log.fine("[OpenAILLMService] Normalizing URL: " + url);
        
        if (url.contains("/chat/completions")) {
            return url;
        }
        if (url.endsWith("/v1")) {
            return url + "/chat/completions";
        }
        
        // 处理特定提供商的 URL 格式
        if (url.contains("moonshot.cn")) {
            return url + "/chat/completions";
        }
        
        return url + "/v1/chat/completions";
    }
    
    /**
     * 服务配置
     */
    @lombok.Data
    @lombok.Builder
    public static class ServiceConfig {
        private String baseUrl;
        private String model;
        private List<String> apiKeys;
        private Double temperature;
        private Integer maxTokens;
        private int timeoutSeconds = 300;  // 默认5分钟超时
    }
}
