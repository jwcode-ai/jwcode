package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.java.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

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
    
    // 消息数量限制：降低到 50（原 100 导致上下文窗口超限）
    private static final int MAX_MESSAGE_COUNT = 500;
    
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
            
            int maxRetries = 3;  // 增加重试次数到 3
            int attempt = 0;
            int retryDelay = 2000; // 初始重试延迟 2 秒
            
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
                        // Rate limit, retry with exponential backoff
                        attempt++;
                        if (attempt <= maxRetries) {
                            log.warning("[OpenAI] Rate limited, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                            Thread.sleep(retryDelay);
                            retryDelay *= 2; // 指数退避
                            // 切换 API key
                            apiKey = getNextApiKey();
                            continue;
                        }
                        return LLMResponse.error("Rate limited. Please try again later.");
                    } else if (response.statusCode() >= 500) {
                        // 服务器错误 (500, 502, 503, 504)，重试
                        attempt++;
                        if (attempt <= maxRetries) {
                            log.warning("[OpenAI] Server error " + response.statusCode() + ", retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                            String errorBody = response.body();
                            log.info("[OpenAI] Server error body: " + errorBody);
                            Thread.sleep(retryDelay);
                            retryDelay *= 2; // 指数退避
                            // 切换 API key
                            apiKey = getNextApiKey();
                            continue;
                        }
                        String errorBody = response.body();
                        log.severe("[OpenAI] ---------- Server Error Response ----------");
                        log.severe("[OpenAI] Status: " + response.statusCode());
                        log.severe("[OpenAI] Body: " + errorBody);
                        log.severe("[OpenAI] ---------- End Server Error Response ----------");
                        return LLMResponse.error("Server error (HTTP " + response.statusCode() + "). The API server encountered an internal error. " +
                            "This is usually temporary. Please try again in a few moments. " +
                            "Error: " + errorBody);
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
                        log.warning("[OpenAI] Request timeout, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2; // 指数退避
                        // 切换 API key
                        apiKey = getNextApiKey();
                    } else {
                        log.severe("[OpenAI] Request timeout after " + (maxRetries + 1) + " attempts");
                        return LLMResponse.error("Request timed out. The model is taking too long to respond. " +
                            "Please try again or use a simpler prompt. " +
                            "Current timeout: " + config.getTimeoutSeconds() + " seconds");
                    }
                } catch (java.net.ConnectException e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[OpenAI] Connection failed, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2;
                        continue;
                    }
                    log.severe("[OpenAI] Connection failed after " + (maxRetries + 1) + " attempts: " + e.getMessage());
                    return LLMResponse.error("Connection failed. Please check your network and API endpoint: " + url);
                } catch (java.net.SocketException e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[OpenAI] Connection reset, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2;
                        continue;
                    }
                    log.severe("[OpenAI] Connection reset after " + (maxRetries + 1) + " attempts: " + e.getMessage());
                    return LLMResponse.error("Connection reset. Possible causes:\n" +
                        "1. Request too large (too many messages in history) - Try 'clear' command\n" +
                        "2. Network instability\n" +
                        "3. API server closed connection\n" +
                        "Current messages: " + messages.size());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.severe("[OpenAI] Request interrupted");
                    return LLMResponse.error("Request was interrupted. Please try again.");
                } catch (Exception e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[OpenAI] Request failed, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2;
                        continue;
                    }
                    log.severe("[OpenAI] Request failed after " + (maxRetries + 1) + " attempts: " + e.getMessage());
                    return LLMResponse.error("Request failed: " + e.getMessage());
                }
            }
            
            return LLMResponse.error("Max retries exceeded");
        });
    }
    
    @Override
    public CompletableFuture<LLMResponse> chatStream(
            List<LLMMessage> messages,
            Consumer<String> contentConsumer) {
        return chatStreamWithTools(messages, null, contentConsumer, null, null);
    }
    
    @Override
    public CompletableFuture<LLMResponse> chatStreamWithTools(
            List<LLMMessage> messages,
            List<LLMTool> tools,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<StreamToolCallEvent> toolCallConsumer) {
        
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = getNextApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("No API key configured");
            }
            
            String url = normalizeUrl(config.getBaseUrl());
            log.info("[OpenAI Stream] Request: POST " + url);
            log.info("[OpenAI Stream] Model: " + config.getModel());
            log.info("[OpenAI Stream] Message count: " + messages.size());
            
            try {
                // 构建流式请求体
                ObjectNode requestBody = buildRequestBody(messages, tools);
                requestBody.put("stream", true);
                String requestJson = mapper.writeValueAsString(requestBody);
                
                // 发送请求
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .build();
                
                // 使用 InputStream 获取流式响应
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                
                if (response.statusCode() != 200) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    log.severe("[OpenAI Stream] Error: " + errorBody);
                    return LLMResponse.error("HTTP " + response.statusCode() + ": " + errorBody);
                }
                
                // 处理流式响应
                return processStreamResponse(
                    response.body(), 
                    contentConsumer, 
                    thinkingConsumer, 
                    toolCallConsumer
                );
                
            } catch (Exception e) {
                log.log(Level.SEVERE, "[OpenAI Stream] Request failed: " + e.getMessage(), e);
                return LLMResponse.error("Stream request failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * 处理流式响应
     */
    private LLMResponse processStreamResponse(
            InputStream inputStream,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<StreamToolCallEvent> toolCallConsumer) throws Exception {
        
        StringBuilder contentBuffer = new StringBuilder();
        StringBuilder thinkingBuffer = new StringBuilder();
        StringBuilder reasoningBuffer = new StringBuilder();
        List<LLMMessage.ToolCall> toolCalls = new ArrayList<>();
        String finishReason = null;
        String responseModel = null;
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        
        // 工具调用缓冲区（用于累积流式工具调用）
        java.util.Map<String, StreamToolCallAccumulator> toolCallAccumulators = new java.util.HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                // SSE 格式: data: {...}
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    
                    // 流结束标记
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    
                    try {
                        JsonNode json = mapper.readTree(data);
                        
                        // 提取模型信息
                        if (json.has("model") && responseModel == null) {
                            responseModel = json.get("model").asText();
                        }
                        
                        // 提取用量（通常在最后一个 chunk）
                        if (json.has("usage")) {
                            JsonNode usage = json.get("usage");
                            if (usage.has("prompt_tokens")) {
                                promptTokens = usage.get("prompt_tokens").asInt();
                            }
                            if (usage.has("completion_tokens")) {
                                completionTokens = usage.get("completion_tokens").asInt();
                            }
                            if (usage.has("total_tokens")) {
                                totalTokens = usage.get("total_tokens").asInt();
                            }
                        }
                        
                        // 处理 choices
                        JsonNode choices = json.get("choices");
                        if (choices == null || !choices.isArray() || choices.size() == 0) {
                            continue;
                        }
                        
                        JsonNode choice = choices.get(0);
                        JsonNode delta = choice.get("delta");
                        
                        if (delta == null) {
                            continue;
                        }
                        
                        // 提取完成原因
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                            finishReason = choice.get("finish_reason").asText();
                        }
                        
                        // 处理思考过程 (reasoning_content)
                        JsonNode reasoningContent = delta.get("reasoning_content");
                        if (reasoningContent != null && !reasoningContent.isNull()) {
                            String thinking = reasoningContent.asText();
                            thinkingBuffer.append(thinking);
                            reasoningBuffer.append(thinking);
                            if (thinkingConsumer != null) {
                                thinkingConsumer.accept(thinking);
                            }
                            continue;
                        }
                        
                        // 处理普通内容
                        JsonNode content = delta.get("content");
                        if (content != null && !content.isNull()) {
                            String text = content.asText();
                            contentBuffer.append(text);
                            if (contentConsumer != null) {
                                contentConsumer.accept(text);
                            }
                        }
                        
                        // 处理工具调用
                        JsonNode toolCallsDelta = delta.get("tool_calls");
                        if (toolCallsDelta != null && toolCallsDelta.isArray()) {
                            processStreamToolCalls(toolCallsDelta, toolCallAccumulators, toolCallConsumer);
                        }
                        
                    } catch (Exception e) {
                        log.fine("[OpenAI Stream] Failed to parse event: " + e.getMessage());
                    }
                }
            }
        }
        
        // 转换累积的工具调用为最终格式
        for (StreamToolCallAccumulator acc : toolCallAccumulators.values()) {
            if (acc.isComplete()) {
                toolCalls.add(LLMMessage.ToolCall.builder()
                    .id(acc.getId())
                    .type(acc.getType())
                    .function(acc.getName(), acc.getArguments())
                    .build());
            }
        }
        
        log.info("[OpenAI Stream] Completed. Content length: " + contentBuffer.length());
        log.info("[OpenAI Stream] Finish reason: " + finishReason);
        
        // 构建响应
        LLMResponse.Builder builder = LLMResponse.builder()
            .content(contentBuffer.toString())
            .reasoningContent(reasoningBuffer.toString())
            .rawResponse(contentBuffer.toString());
        
        if (!toolCalls.isEmpty()) {
            builder.toolCalls(toolCalls);
        }
        if (finishReason != null) {
            builder.finishReason(finishReason);
        }
        if (responseModel != null) {
            builder.model(responseModel);
        }
        builder.promptTokens(promptTokens)
               .completionTokens(completionTokens)
               .totalTokens(totalTokens);
        
        return builder.build();
    }
    
    /**
     * 处理流式工具调用
     */
    private void processStreamToolCalls(
            JsonNode toolCallsDelta,
            java.util.Map<String, StreamToolCallAccumulator> accumulators,
            Consumer<StreamToolCallEvent> toolCallConsumer) {
        
        for (JsonNode toolCall : toolCallsDelta) {
            String id = toolCall.has("id") ? toolCall.get("id").asText() : null;
            int index = toolCall.has("index") ? toolCall.get("index").asInt() : 0;
            
            // 使用索引作为临时 ID
            String key = id != null ? id : String.valueOf(index);
            
            StreamToolCallAccumulator acc = accumulators.computeIfAbsent(
                key, 
                k -> new StreamToolCallAccumulator(id, index)
            );
            
            // 更新类型
            if (toolCall.has("type")) {
                acc.setType(toolCall.get("type").asText());
            }
            
            // 更新函数信息
            JsonNode function = toolCall.get("function");
            if (function != null) {
                if (function.has("name")) {
                    acc.appendName(function.get("name").asText());
                }
                if (function.has("arguments")) {
                    acc.appendArguments(function.get("arguments").asText());
                }
            }
            
            // 通知消费者
            if (toolCallConsumer != null) {
                toolCallConsumer.accept(new StreamToolCallEvent(
                    acc.getId(),
                    acc.getType(),
                    acc.getName(),
                    acc.getArguments(),
                    false
                ));
            }
        }
    }
    
    /**
     * 工具调用累积器（用于流式工具调用）
     */
    private static class StreamToolCallAccumulator {
        private String id;
        private final int index;
        private String type = "function";
        private final StringBuilder nameBuilder = new StringBuilder();
        private final StringBuilder argumentsBuilder = new StringBuilder();
        
        StreamToolCallAccumulator(String id, int index) {
            this.id = id;
            this.index = index;
        }
        
        void setType(String type) { this.type = type; }
        void appendName(String name) { nameBuilder.append(name); }
        void appendArguments(String args) { argumentsBuilder.append(args); }
        
        String getId() { return id != null ? id : String.valueOf(index); }
        String getType() { return type; }
        String getName() { return nameBuilder.toString(); }
        String getArguments() { return argumentsBuilder.toString(); }
        boolean isComplete() { 
            return nameBuilder.length() > 0 && id != null; 
        }
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
     * 消息截断策略（修复 TOOL -> USER 无效序列问题）：
     * - 按组截断：USER -> ASSISTANT -> TOOL* 为一组，不可拆分
     * - TOOL 消息必须保留其对应的 ASSISTANT 消息（包含 tool_calls）
     * - 如果截断会破坏序列，则整组丢弃
     * - 保留系统消息（总是在最前面）
     * 
     * 这样确保消息序列总是 valid 的，不会出现 TOOL -> USER 无效序列
     */
    private ObjectNode buildRequestBody(List<LLMMessage> messages, List<LLMTool> tools) {
        List<LLMMessage> filteredMessages = messages;
        
        // 检查是否有需要截断的消息
        if (messages.size() > MAX_MESSAGE_COUNT) {
            log.warning("[OpenAI] Too many messages (" + messages.size() + "), filtering to " + MAX_MESSAGE_COUNT);
            
            filteredMessages = smartFilterMessages(messages, MAX_MESSAGE_COUNT);
            
            log.info("[OpenAI] Filtered to " + filteredMessages.size() + " messages (skipped " + 
                     (messages.size() - filteredMessages.size()) + " old messages)");
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
     * 智能过滤消息 - 确保不会出现 TOOL -> USER 无效序列
     * 
     * 策略：按组截断，每组 USER -> ASSISTANT -> TOOL* 为一组
     * 从后向前保留最近的消息
     */
    private List<LLMMessage> smartFilterMessages(List<LLMMessage> messages, int maxCount) {
        if (messages.size() <= maxCount) {
            return messages;
        }
        
        // 提取系统消息
        LLMMessage systemMsg = null;
        List<LLMMessage> nonSystemMessages = new ArrayList<>();
        
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.SYSTEM) {
                systemMsg = msg;
            } else {
                nonSystemMessages.add(msg);
            }
        }
        
        // 按组划分消息
        List<List<LLMMessage>> groups = new ArrayList<>();
        List<LLMMessage> currentGroup = new ArrayList<>();
        LLMMessage.Role lastRole = null;
        
        for (LLMMessage msg : nonSystemMessages) {
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
        
        // 从后向前选择要保留的组
        List<LLMMessage> result = new ArrayList<>();
        java.util.Set<String> validToolCallIds = new java.util.HashSet<>();
        
        // 收集所有有效的 tool_call_ids（来自保留的 ASSISTANT 消息）
        for (int i = groups.size() - 1; i >= 0 && result.size() < maxCount; i--) {
            List<LLMMessage> group = groups.get(i);
            
            // 检查这组是否会导致消息数量超限
            int neededSlots = group.size();
            int availableSlots = maxCount - result.size();
            
            if (neededSlots <= availableSlots) {
                // 可以完整添加这组
                for (LLMMessage msg : group) {
                    result.add(0, msg);
                    // 更新有效的 tool_call_ids
                    if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                        for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                            if (tc.getId() != null) {
                                validToolCallIds.add(tc.getId());
                            }
                        }
                    }
                }
            } else {
                // 需要部分添加 - 优先保留 USER + ASSISTANT
                for (LLMMessage msg : group) {
                    if (result.size() >= maxCount) break;
                    
                    // 跳过孤立的 TOOL 消息
                    if (msg.getRole() == LLMMessage.Role.TOOL) {
                        String toolCallId = msg.getToolCallId();
                        if (toolCallId == null || !validToolCallIds.contains(toolCallId)) {
                            log.fine("[OpenAI] Skipping orphaned TOOL in filter: " + toolCallId);
                            continue;
                        }
                    }
                    
                    result.add(0, msg);
                    
                    // 更新有效的 tool_call_ids
                    if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                        for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                            if (tc.getId() != null) {
                                validToolCallIds.add(tc.getId());
                            }
                        }
                    }
                }
            }
        }
        
        // 验证最终序列：如果以 TOOL 开头，需要移除
        while (!result.isEmpty() && result.get(0).getRole() == LLMMessage.Role.TOOL) {
            log.warning("[OpenAI] Removing leading TOOL message to fix sequence");
            result.remove(0);
        }
        
        // 添加系统消息到最前面
        if (systemMsg != null) {
            result.add(0, systemMsg);
        }
        
        return result;
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
            
            // 思考内容 (reasoning_content)
            if (message.has("reasoning_content") && !message.get("reasoning_content").isNull()) {
                String reasoningContent = message.get("reasoning_content").asText();
                builder.reasoningContent(reasoningContent);
                log.info("[OpenAI] Parsed reasoning_content length: " + reasoningContent.length());
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
