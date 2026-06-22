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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * OpenAI 鍏煎鐨?LLM 鏈嶅姟瀹炵幇
 * 
 * 鏀寔锛?
 * - OpenAI
 * - Moonshot (Kimi)
 * - MiniMax
 * - 浠讳綍 OpenAI 鍏煎鐨?API
 */
@Log
public class OpenAILLMService implements LLMService {
    
    private static final ObjectMapper mapper = new ObjectMapper();
    /** IO 线程池 — 替代 ForkJoinPool.commonPool()，避免线程饥饿导致请求排队 */
    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "openai-io-" + r.hashCode());
        t.setDaemon(true);
        return t;
    });
    private final HttpClient httpClient;
    private final ServiceConfig config;
    private int currentKeyIndex = 0;
    
    // 娑堟伅鏁伴噺闄愬埗锛氫笉闄愬埗鏁伴噺锛岀敱涓婁笅鏂囩獥鍙ｇ鐞嗗櫒澶勭悊 token 瓒呴檺
    private static final int MAX_MESSAGE_COUNT = Integer.MAX_VALUE;
    
    public OpenAILLMService(ServiceConfig config) {
        this.config = config;
        // 澧炲姞杩炴帴瓒呮椂鍒?0绉?
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .version(HttpClient.Version.HTTP_1_1)
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
            
            int maxRetries = 3;  // 澧炲姞閲嶈瘯娆℃暟鍒?3
            int attempt = 0;
            int retryDelay = 2000; // 鍒濆閲嶈瘯寤惰繜 2 绉?
            
            while (attempt <= maxRetries) {
                try {
                    // 鏋勫缓璇锋眰浣?
                    ObjectNode requestBody = buildRequestBody(messages, tools);
                    String requestJson = mapper.writeValueAsString(requestBody);
                    
                    // 鎵撳嵃璇锋眰鍙傛暟锛圛NFO 绾у埆锛屽紑鍚?debug 妯″紡锛?
                    log.fine("[OpenAI] ---------- Request Body ----------");
                    log.fine("[OpenAI] " + requestJson);
                    log.fine("[OpenAI] ---------- End Request Body ----------");
                    log.info("[OpenAI] Request body length: " + requestJson.length() + " bytes");
                    
                    // 鍙戦€佽姹?
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
                        String responseBody = response.body();
                        log.info("[OpenAI] Response body length: " + responseBody.length() + " bytes");
                        log.fine("[OpenAI] ---------- Response Body ----------");
                        log.fine("[OpenAI] " + responseBody);
                        log.fine("[OpenAI] ---------- End Response Body ----------");
                        return parseResponse(responseBody);
                    } else if (response.statusCode() == 429) {
                        String errorBody = response.body();
                        String errorType = extractErrorType(errorBody);
                        String errorMsg = extractErrorMessage(errorBody);
                        
                        // Hard quota limits (TPD, insufficient_quota): do not retry, fail fast
                        if (isHardRateLimit(errorType)) {
                            String cleanMsg = cleanRateLimitMessage(errorMsg);
                            log.severe("[OpenAI] Hard rate limit (" + errorType + "): " + cleanMsg);
                            return LLMResponse.error("RATE_LIMIT_HARD",
                                "Rate limit reached: " + cleanMsg +
                                " [Provider: " + config.getModel() + "]");
                        }
                        
                        // Transient overload: retry with exponential backoff
                        attempt++;
                        if (attempt <= maxRetries) {
                            log.warning("[OpenAI] Rate limited (" + errorType + "), retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                            Thread.sleep(retryDelay);
                            retryDelay *= 2; // 鎸囨暟閫€閬?
                            // 鍒囨崲 API key
                            apiKey = getNextApiKey();
                            continue;
                        }
                        return LLMResponse.error("RATE_LIMIT_RETRYABLE", "Rate limited. Please try again later.");
                    } else if (response.statusCode() >= 500) {
                        // 鏈嶅姟鍣ㄩ敊璇?(500, 502, 503, 504)锛岄噸璇?
                        attempt++;
                        if (attempt <= maxRetries) {
                            log.warning("[OpenAI] Server error " + response.statusCode() + ", retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                            String errorBody = response.body();
                            log.fine("[OpenAI] Server error body: " + errorBody);
                            Thread.sleep(retryDelay);
                            retryDelay *= 2; // 鎸囨暟閫€閬?
                            // 鍒囨崲 API key
                            apiKey = getNextApiKey();
                            continue;
                        }
                        String errorBody = response.body();
                        String truncatedError = errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody;
                        log.severe("[OpenAI] Server error (HTTP " + response.statusCode() + "): " + truncatedError);
                        log.fine("[OpenAI] Full server error body: " + errorBody);
                        return LLMResponse.error("SERVER_ERROR", "Server error (HTTP " + response.statusCode() + "). The API server encountered an internal error. " +
                            "This is usually temporary. Please try again in a few moments. " +
                            "Error: " + errorBody);
                    } else {
                        String errorBody = response.body();
                        String truncatedError = errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody;
                        log.severe("[OpenAI] Client error (HTTP " + response.statusCode() + "): " + truncatedError);
                        log.fine("[OpenAI] Full client error body: " + errorBody);
                        return LLMResponse.error("CLIENT_ERROR", "HTTP " + response.statusCode() + ": " + errorBody);
                    }
                    
                } catch (java.net.http.HttpTimeoutException e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[OpenAI] Request timeout, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2; // 鎸囨暟閫€閬?
                        // 鍒囨崲 API key
                        apiKey = getNextApiKey();
                    } else {
                        log.severe("[OpenAI] Request timeout after " + (maxRetries + 1) + " attempts");
                        return LLMResponse.error("TIMEOUT", "Request timed out. The model is taking too long to respond. " +
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
                    return LLMResponse.error("CONNECTION_ERROR", "Connection failed. Please check your network and API endpoint: " + url);
                } catch (java.net.SocketException e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[OpenAI] Connection reset, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2;
                        continue;
                    }
                    log.severe("[OpenAI] Connection reset after " + (maxRetries + 1) + " attempts: " + e.getMessage());
                    return LLMResponse.error("CONNECTION_ERROR", "Connection reset. Possible causes:\n" +
                        "1. Request too large (too many messages in history) - Try 'clear' command\n" +
                        "2. Network instability\n" +
                        "3. API server closed connection\n" +
                        "Current messages: " + messages.size());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.severe("[OpenAI] Request interrupted");
                    return LLMResponse.error("INTERRUPTED", "Request was interrupted. Please try again.");
                } catch (Exception e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[OpenAI] Request failed, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2;
                        continue;
                    }
                    log.severe("[OpenAI] Request failed after " + (maxRetries + 1) + " attempts: " + e.getMessage());
                    return LLMResponse.error("REQUEST_FAILED", "Request failed: " + e.getMessage());
                }
            }
            
            return LLMResponse.error("MAX_RETRIES", "Max retries exceeded");
        }, IO_EXECUTOR);
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
            log.info("[OpenAI Stream] ==========================================");
            log.info("[OpenAI Stream] Request: POST " + url);
            log.info("[OpenAI Stream] Model: " + config.getModel());
            log.info("[OpenAI Stream] Message count: " + messages.size());
            
            int maxRetries = 3;
            int attempt = 0;
            int retryDelay = 2000;
            
            while (attempt <= maxRetries) {
                try {
                    // 鏋勫缓娴佸紡璇锋眰浣?
                    ObjectNode requestBody = buildRequestBody(messages, tools);
                    requestBody.put("stream", true);
                    String requestJson = mapper.writeValueAsString(requestBody);
                    
                    // 完整请求体改为 fine 级别；INFO 只打印长度摘要
                    log.fine("[OpenAI Stream] ---------- Request Body ----------");
                    log.fine("[OpenAI Stream] " + requestJson);
                    log.fine("[OpenAI Stream] ---------- End Request Body ----------");
                    log.info("[OpenAI Stream] Request body length: " + requestJson.length() + " bytes");
                    
                    // 鍙戦€佽姹?
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                        .build();
                    
                    // 浣跨敤 InputStream 鑾峰彇娴佸紡鍝嶅簲
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    
                    if (response.statusCode() != 200) {
                        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                        log.severe("[OpenAI Stream] Error: " + errorBody);
                        
                        String errorType = extractErrorType(errorBody);
                        String errorMsg = extractErrorMessage(errorBody);
                        
                        // Hard quota limits: do not retry, fail fast
                        if (response.statusCode() == 429 && isHardRateLimit(errorType)) {
                            String cleanMsg = cleanRateLimitMessage(errorMsg);
                            log.severe("[OpenAI Stream] Hard rate limit (" + errorType + "): " + cleanMsg);
                            return LLMResponse.error("RATE_LIMIT_HARD",
                                "Rate limit reached: " + cleanMsg +
                                " [Provider: " + config.getModel() + "]");
                        }
                        
                        // 瀵瑰彲閲嶈瘯閿欒鎵ц閫€閬块噸璇曪紙涓?chatWithTools 淇濇寔涓€鑷达級
                        if (response.statusCode() == 429 || response.statusCode() >= 500) {
                            attempt++;
                            if (attempt <= maxRetries) {
                                String retryReason = response.statusCode() == 429 ? "Rate limited (" + errorType + ")" : "Server error " + response.statusCode();
                                log.warning("[OpenAI Stream] " + retryReason + ", retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                                try {
                                    Thread.sleep(retryDelay);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                                retryDelay *= 2;
                                apiKey = getNextApiKey();
                                continue;
                            }
                        }
                        String code = response.statusCode() >= 500 ? "SERVER_ERROR" : "CLIENT_ERROR";
                        return LLMResponse.error(code, "HTTP " + response.statusCode() + ": " + errorBody);
                    }
                    
                    // 澶勭悊娴佸紡鍝嶅簲
                    return processStreamResponse(
                        response.body(), 
                        contentConsumer, 
                        thinkingConsumer, 
                        toolCallConsumer
                    );
                    
                } catch (java.io.IOException e) {
                    String errMsg = e.getMessage();
                    if (errMsg != null && (errMsg.contains("header parser received no bytes") ||
                                        errMsg.contains("connection abort") ||
                                        errMsg.contains("Software caused connection abort") ||
                                        errMsg.contains("Connection reset"))) {
                        attempt++;
                        if (attempt <= maxRetries) {
                            log.warning("[OpenAI Stream] Connection interrupted: " + errMsg + ", retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                            try {
                                Thread.sleep(retryDelay);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            retryDelay *= 2;
                            apiKey = getNextApiKey();
                            continue;
                        }
                    }
                    log.log(Level.SEVERE, "[OpenAI Stream] Request failed: " + e.getMessage(), e);
                    return LLMResponse.error("STREAM_FAILED", "Stream request failed: " + e.getMessage());
                } catch (Exception e) {
                    log.log(Level.SEVERE, "[OpenAI Stream] Request failed: " + e.getMessage(), e);
                    return LLMResponse.error("Stream request failed: " + e.getMessage());
                }
            }
            
            return LLMResponse.error("MAX_RETRIES", "Stream request failed after " + maxRetries + " retries");
        }, IO_EXECUTOR);
    }
    
    /**
     * 澶勭悊娴佸紡鍝嶅簲
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
        boolean inThinkingTag = false;
        
        // 宸ュ叿璋冪敤缂撳啿鍖猴紙鐢ㄤ簬绱Н娴佸紡宸ュ叿璋冪敤锛?
        java.util.Map<String, StreamToolCallAccumulator> toolCallAccumulators = new java.util.HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            // SSE 浜嬩欢绱Н缂撳啿鍖猴細涓€涓?SSE 浜嬩欢鍙兘璺ㄥ涓?data: 琛?
            StringBuilder eventBuffer = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                // SSE 鏍煎紡: data: {...}
                if (line.startsWith("data: ")) {
                    // 绱Н data: 鍚庨潰鐨勫唴瀹癸紝淇濈暀鎹㈣浠ユ敮鎸佸琛屼簨浠?
                    eventBuffer.append(line.substring(6)).append("\n");
                    continue;
                }
                
                // 绌鸿鎴栭潪 data: 琛岃〃绀轰竴涓?SSE 浜嬩欢缁撴潫
                if (eventBuffer.length() > 0) {
                    String eventData = eventBuffer.toString().trim();
                    eventBuffer.setLength(0);
                    
                    // 娴佺粨鏉熸爣璁?
                    if ("[DONE]".equals(eventData)) {
                        break;
                    }
                    
                    // 妫€鏌ユ槸鍚︿负 JSON 鏍煎紡锛堜互 { 鎴?[ 寮€澶达級
                    if (!eventData.startsWith("{") && !eventData.startsWith("[")) {
                        log.warning("[OpenAI Stream] Non-JSON event received (treating as stream termination): " + eventData);
                        break;
                    }
                    
                    try {
                        JsonNode json = mapper.readTree(eventData);
                        
                        // 鎻愬彇妯″瀷淇℃伅
                        if (json.has("model") && responseModel == null) {
                            responseModel = json.get("model").asText();
                        }
                        
                        // 鎻愬彇鐢ㄩ噺锛堥€氬父鍦ㄦ渶鍚庝竴涓?chunk锛?
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
                        
                        // 澶勭悊 choices
                        JsonNode choices = json.get("choices");
                        if (choices == null || !choices.isArray() || choices.size() == 0) {
                            continue;
                        }
                        
                        JsonNode choice = choices.get(0);
                        JsonNode delta = choice.get("delta");
                        
                        if (delta == null) {
                            continue;
                        }
                        
                        // 鎻愬彇瀹屾垚鍘熷洜
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                            finishReason = choice.get("finish_reason").asText();
                        }
                        
                        // 澶勭悊鎬濊€冭繃绋?(reasoning_content)
                        JsonNode reasoningContent = delta.get("reasoning_content");
                        if (reasoningContent != null && !reasoningContent.isNull()) {
                            String thinking = reasoningContent.asText();
                            if (!thinking.isEmpty()) {
                                thinkingBuffer.append(thinking);
                                reasoningBuffer.append(thinking);
                                if (thinkingConsumer != null) {
                                    thinkingConsumer.accept(thinking);
                                }
                            }
                        }
                        
                        // 澶勭悊鏅€氬唴瀹癸紙鍚?<think> 鏍囩鎻愬彇锛?
                        JsonNode content = delta.get("content");
                        if (content != null && !content.isNull()) {
                            String text = content.asText();
                            inThinkingTag = processThinkTagChunk(text, inThinkingTag,
                                contentBuffer, thinkingBuffer, reasoningBuffer,
                                contentConsumer, thinkingConsumer);
                        }
                        
                        // 澶勭悊宸ュ叿璋冪敤
                        JsonNode toolCallsDelta = delta.get("tool_calls");
                        if (toolCallsDelta != null && toolCallsDelta.isArray()) {
                            processStreamToolCalls(toolCallsDelta, toolCallAccumulators, toolCallConsumer);
                        }
                        
                    } catch (java.util.concurrent.CancellationException e) {
                        // 娴佹秷璐硅鍙栨秷锛堝 WebSocket 鏂紑锛夛紝绔嬪嵆缁堟
                        log.info("[OpenAI Stream] Stream cancelled: " + e.getMessage());
                        break;
                    } catch (Exception e) {
                        log.warning("[OpenAI Stream] Failed to parse JSON event. Data=" + eventData + ", error=" + e.toString());
                    }
                }
            }
            
            // 澶勭悊缂撳啿鍖轰腑娈嬬暀鐨勪簨浠讹紙娴佸彲鑳芥病鏈夊熬闅忕┖琛岋級
            if (eventBuffer.length() > 0) {
                String eventData = eventBuffer.toString().trim();
                if (!eventData.isEmpty()) {
                    if ("[DONE]".equals(eventData)) {
                        // 姝ｅ父缁撴潫锛屼笉鍋氬鐞?
                    } else if (eventData.startsWith("{") || eventData.startsWith("[")) {
                        try {
                            mapper.readTree(eventData);
                        } catch (Exception e) {
                            log.warning("[OpenAI Stream] Failed to parse trailing event: " + e.toString());
                        }
                    } else {
                        log.warning("[OpenAI Stream] Non-JSON trailing event ignored: " + eventData);
                    }
                }
            }
        }
        
        // 杞崲绱Н鐨勫伐鍏疯皟鐢ㄤ负鏈€缁堟牸寮?
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
        
        // 鏋勫缓鍝嶅簲
        LLMResponse.Builder builder = LLMResponse.builder()
            .content(contentBuffer.toString())
            .rawResponse(contentBuffer.toString());
        
        if (reasoningBuffer.length() > 0) {
            builder.reasoningContent(reasoningBuffer.toString());
        }
        
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
     * 澶勭悊娴佸紡宸ュ叿璋冪敤
     */
    private void processStreamToolCalls(
            JsonNode toolCallsDelta,
            java.util.Map<String, StreamToolCallAccumulator> accumulators,
            Consumer<StreamToolCallEvent> toolCallConsumer) {
        
        for (JsonNode toolCall : toolCallsDelta) {
            String id = toolCall.has("id") ? toolCall.get("id").asText() : null;
            int index = toolCall.has("index") ? toolCall.get("index").asInt() : 0;
            
            // FIX: 濮嬬粓浣跨敤 index 浣滀负 key锛岄伩鍏嶅悓涓€ tool call 鍥?id 浠庢棤鍒版湁鑰岃鎷嗘垚涓や釜 accumulator
            String key = String.valueOf(index);
            
            StreamToolCallAccumulator acc = accumulators.computeIfAbsent(
                key, 
                k -> new StreamToolCallAccumulator(id, index)
            );
            
            // 濡傛灉鍚庣画 delta 鎻愪緵浜嗙湡瀹?id锛屾洿鏂?accumulator
            if (id != null && acc.getRawId() == null) {
                acc.setId(id);
            }
            
            // 鏇存柊绫诲瀷
            if (toolCall.has("type")) {
                acc.setType(toolCall.get("type").asText());
            }
            
            // 鏇存柊鍑芥暟淇℃伅
            JsonNode function = toolCall.get("function");
            if (function != null) {
                if (function.has("name")) {
                    acc.appendName(function.get("name").asText());
                }
                if (function.has("arguments")) {
                    acc.appendArguments(function.get("arguments").asText());
                }
            }
            
            // 閫氱煡娑堣垂鑰咃紙isComplete 鍙嶆槧褰撳墠 accumulator 鐨勫畬鏁村害锛?
            if (toolCallConsumer != null) {
                toolCallConsumer.accept(new StreamToolCallEvent(
                    acc.getId(),
                    acc.getType(),
                    acc.getName(),
                    acc.getArguments(),
                    acc.isComplete(),
                    index
                ));
            }
        }
    }
    
    /**
     * 宸ュ叿璋冪敤绱Н鍣紙鐢ㄤ簬娴佸紡宸ュ叿璋冪敤锛?
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
        
        void setId(String id) { this.id = id; }
        void setType(String type) { this.type = type; }
        void appendName(String name) { nameBuilder.append(name); }
        void appendArguments(String args) { argumentsBuilder.append(args); }
        
        String getRawId() { return id; }
        String getId() { return id != null ? id : String.valueOf(index); }
        int getIndex() { return index; }
        String getType() { return type; }
        String getName() { return nameBuilder.toString(); }
        String getArguments() { return argumentsBuilder.toString(); }
        boolean isComplete() { 
            // FIX: 鏀惧瀹屾垚鏉′欢锛屼笉寮哄埗瑕佹眰 id 闈炵┖锛堟煇浜?provider 鍙兘涓嶇粰 id锛?
            return nameBuilder.length() > 0 && argumentsBuilder.length() > 0; 
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
        }, IO_EXECUTOR);
    }
    
    @Override
    public String getModelName() {
        return config.getModel();
    }
    
    @Override
    public int getContextWindow() {
        return config.getContextWindow();
    }

    @Override
    public void reconfigure(String modelId) {
        config.setModel(modelId);
        log.info("[OpenAI] Model reconfigured to: " + modelId);
    }

    @Override
    public CompletableFuture<float[]> embed(String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = config.getBaseUrl() + "/embeddings";
                String json = mapper.writeValueAsString(Map.of(
                    "model", "text-embedding-ada-002",
                    "input", text
                ));
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getNextApiKey())
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();
                java.net.http.HttpResponse<String> resp = httpClient.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                JsonNode data = mapper.readTree(resp.body()).get("data");
                if (data != null && data.isArray() && data.size() > 0) {
                    JsonNode embedding = data.get(0).get("embedding");
                    float[] vec = new float[embedding.size()];
                    for (int i = 0; i < vec.length; i++) vec[i] = embedding.get(i).floatValue();
                    return vec;
                }
            } catch (Exception e) {
                log.warning("[OpenAI] Embed failed: " + e.getMessage());
            }
            return new float[0];
        }, IO_EXECUTOR);
    }

    @Override
    public void close() {
        // HTTP client doesn't need explicit close
    }
    
    // ==================== 閿欒瑙ｆ瀽杈呭姪鏂规硶 ====================
    
    /**
     * 浠庨敊璇搷搴?JSON 涓彁鍙?error.type
     */
    private String extractErrorType(String errorBody) {
        try {
            JsonNode json = mapper.readTree(errorBody);
            if (json.has("error") && json.get("error").has("type")) {
                return json.get("error").get("type").asText();
            }
        } catch (Exception e) {
            // ignore parse failure
        }
        return null;
    }
    
    /**
     * 浠庨敊璇搷搴?JSON 涓彁鍙?error.message锛屽け璐ユ椂杩斿洖鍘熷 body
     */
    private String extractErrorMessage(String errorBody) {
        try {
            JsonNode json = mapper.readTree(errorBody);
            if (json.has("error") && json.get("error").has("message")) {
                return json.get("error").get("message").asText();
            }
        } catch (Exception e) {
            // ignore parse failure
        }
        return errorBody;
    }
    
    /**
     * 鍒ゆ柇鏄惁涓虹‖鎬ч厤棰濋檺鍒讹紙涓嶅彲閫氳繃閲嶈瘯鎭㈠锛?
     */
    private boolean isHardRateLimit(String errorType) {
        return "rate_limit_reached_error".equals(errorType)
            || "insufficient_quota".equals(errorType);
    }
    
    /**
     * 缇庡寲閫熺巼闄愬埗閿欒娑堟伅
     */
    private String cleanRateLimitMessage(String rawMessage) {
        if (rawMessage == null) return "Unknown quota limit";
        // 绉婚櫎鏁忔劅淇℃伅锛圓PI key 绛夛級
        String cleaned = rawMessage.replaceAll("<ak-[a-zA-Z0-9]+>", "<api-key>");
        return cleaned;
    }
    
    /**
     * 鏋勫缓璇锋眰浣?- 浣跨敤鏍囧噯鐨?OpenAIRequestBuilder
     * 
     * 娑堟伅鎴柇绛栫暐锛堜慨澶?TOOL -> USER 鏃犳晥搴忓垪闂锛夛細
     * - 鎸夌粍鎴柇锛歎SER -> ASSISTANT -> TOOL* 涓轰竴缁勶紝涓嶅彲鎷嗗垎
     * - TOOL 娑堟伅蹇呴』淇濈暀鍏跺搴旂殑 ASSISTANT 娑堟伅锛堝寘鍚?tool_calls锛?
     * - 濡傛灉鎴柇浼氱牬鍧忓簭鍒楋紝鍒欐暣缁勪涪寮?
     * - 淇濈暀绯荤粺娑堟伅锛堟€绘槸鍦ㄦ渶鍓嶉潰锛?
     * 
     * 杩欐牱纭繚娑堟伅搴忓垪鎬绘槸 valid 鐨勶紝涓嶄細鍑虹幇 TOOL -> USER 鏃犳晥搴忓垪
     */
    private ObjectNode buildRequestBody(List<LLMMessage> messages, List<LLMTool> tools) {
        List<LLMMessage> filteredMessages = messages;
        
        // 涓诲姩楠岃瘉骞朵慨澶?tool_calls 閰嶅瀹屾暣鎬?
        filteredMessages = preValidateToolCalls(messages);
        
        OpenAIRequestBuilder builder = new OpenAIRequestBuilder(config.getModel())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens());
        
        // 娣诲姞杩囨护鍚庣殑娑堟伅
        for (LLMMessage msg : filteredMessages) {
            builder.addMessage(msg);
        }
        
        // 娣诲姞宸ュ叿
        if (tools != null) {
            builder.addTools(tools);
        }
        
        return builder.build();
    }
    
    /**
     * 淇宸ュ叿娑堟伅搴忓垪锛氱‘淇濇瘡涓?tool 娑堟伅閮芥湁瀵瑰簲鐨?assistant 娑堟伅锛堝寘鍚?tool_calls锛?
     * 
     * 濡傛灉鎴柇鍚庣殑娑堟伅浠?tool 寮€澶达紝闇€瑕佸悜鍓嶆煡鎵惧搴旂殑 assistant 娑堟伅
     */
    /**
     * 鍙戦€佸墠涓诲姩楠岃瘉宸ュ叿璋冪敤閰嶅瀹屾暣鎬?
     * 鍙屽悜楠岃瘉锛氱Щ闄ゅ绔嬬殑TOOL娑堟伅锛屼互鍙婄Щ闄SSISTANT涓病鏈夊搴擳OOL鐨則ool_calls
     */
    private List<LLMMessage> preValidateToolCalls(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) return messages;
        java.util.Set<String> assistantIds = new java.util.HashSet<>();
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getId() != null) assistantIds.add(tc.getId());
                }
            }
        }
        java.util.List<LLMMessage> systemMsgs = new java.util.ArrayList<>();
        java.util.List<LLMMessage> nonSystem = new java.util.ArrayList<>();
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.SYSTEM) systemMsgs.add(msg);
            else nonSystem.add(msg);
        }
        java.util.List<LLMMessage> cleaned = new java.util.ArrayList<>();
        for (LLMMessage msg : nonSystem) {
            if (msg.getRole() == LLMMessage.Role.TOOL) {
                String tid = msg.getToolCallId();
                if (tid == null || !assistantIds.contains(tid)) {
                    log.warning("[OpenAI] Pre-validation: removing orphaned TOOL: tool_call_id=" + tid);
                    continue;
                }
            }
            cleaned.add(msg);
        }
        java.util.Set<String> validToolIds = new java.util.HashSet<>();
        for (LLMMessage msg : cleaned) {
            if (msg.getRole() == LLMMessage.Role.TOOL && msg.getToolCallId() != null)
                validToolIds.add(msg.getToolCallId());
        }
        java.util.List<LLMMessage> finalized = new java.util.ArrayList<>();
        for (int i = 0; i < cleaned.size(); i++) {
            LLMMessage msg = cleaned.get(i);
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                java.util.Set<String> following = new java.util.HashSet<>();
                for (int j = i + 1; j < cleaned.size(); j++) {
                    LLMMessage n = cleaned.get(j);
                    if (n.getRole() == LLMMessage.Role.TOOL) {
                        if (n.getToolCallId() != null) following.add(n.getToolCallId());
                    } else break;
                }
                java.util.List<LLMMessage.ToolCall> valid = new java.util.ArrayList<>();
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getId() != null && following.contains(tc.getId())) valid.add(tc);
                    else log.warning("[OpenAI] Pre-validation: removing orphaned tool_call " + tc.getId());
                }
                if (valid.isEmpty()) {
                    log.warning("[OpenAI] Pre-validation: all orphaned, converting to text-only");
                    finalized.add(LLMMessage.assistant(msg.getContent() != null ? msg.getContent() : "", msg.getReasoningContent()));
                } else if (valid.size() < msg.getToolCalls().size()) {
                    log.info("[OpenAI] Pre-validation: trimmed orphaned tool_calls");
                    finalized.add(LLMMessage.assistantWithTools(msg.getContent(), valid, msg.getReasoningContent()));
                } else { finalized.add(msg); }
            } else { finalized.add(msg); }
        }
        java.util.List<LLMMessage> result = new java.util.ArrayList<>(systemMsgs);
        result.addAll(finalized);
        if (result.size() != messages.size())
            log.info("[OpenAI] Pre-validation: " + messages.size() + " -> " + result.size() + " messages");
        return result;
    }

    private List<LLMMessage> fixToolMessageSequence(
            List<LLMMessage> truncatedMessages, 
            List<LLMMessage> allMessages,
            int originalStartIndex) {
        
        if (truncatedMessages.isEmpty()) {
            return truncatedMessages;
        }
        
        // 鏀堕泦鎵€鏈夐渶瑕佷繚鐣欑殑 tool_call_id
        java.util.Set<String> requiredToolCallIds = new java.util.HashSet<>();
        for (LLMMessage msg : truncatedMessages) {
            if (msg.getRole() == LLMMessage.Role.TOOL && msg.getToolCallId() != null) {
                requiredToolCallIds.add(msg.getToolCallId());
            }
        }
        
        // 鍚戝墠鏌ユ壘鍖呭惈杩欎簺 tool_calls 鐨?assistant 娑堟伅
        List<LLMMessage> additionalMessages = new ArrayList<>();
        int searchIndex = originalStartIndex - 1;
        java.util.Set<String> foundToolCallIds = new java.util.HashSet<>();
        
        while (searchIndex >= 0 && !requiredToolCallIds.isEmpty()) {
            LLMMessage msg = allMessages.get(searchIndex);
            
            // 濡傛灉鏄?assistant 娑堟伅涓旀湁 tool_calls锛屾鏌ユ槸鍚﹀寘鍚垜浠渶瑕佺殑 id
            if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                boolean hasMatchingToolCall = false;
                for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                    if (requiredToolCallIds.contains(tc.getId())) {
                        hasMatchingToolCall = true;
                        foundToolCallIds.add(tc.getId());
                    }
                }
                
                if (hasMatchingToolCall) {
                    // 鎻掑叆鍒版渶鍓嶉潰锛堜繚鎸佸師鏈夐『搴忥級
                    additionalMessages.add(0, msg);
                    requiredToolCallIds.removeAll(foundToolCallIds);
                }
            }
            
            searchIndex--;
        }
        
        // 濡傛灉鏈夋湭鎵惧埌鐨?tool_call_id锛岄渶瑕佺Щ闄ゅ搴旂殑 tool 娑堟伅
        if (!requiredToolCallIds.isEmpty()) {
            log.warning("[OpenAI] Some tool_call_ids not found in history: " + requiredToolCallIds);
            truncatedMessages.removeIf(msg -> 
                msg.getRole() == LLMMessage.Role.TOOL && 
                requiredToolCallIds.contains(msg.getToolCallId())
            );
        }
        
        // 鍚堝苟锛歛dditionalMessages 鎻掑叆鍒?truncatedMessages 鍓嶉潰
        if (!additionalMessages.isEmpty()) {
            log.info("[OpenAI] Added " + additionalMessages.size() + " assistant messages for tool context");
            List<LLMMessage> result = new ArrayList<>(additionalMessages);
            result.addAll(truncatedMessages);
            return result;
        }
        
        return truncatedMessages;
    }
    
    /**
     * 鏅鸿兘杩囨护娑堟伅 - 纭繚涓嶄細鍑虹幇 TOOL -> USER 鏃犳晥搴忓垪
     * 
     * 绛栫暐锛氭寜缁勬埅鏂紝姣忕粍 USER -> ASSISTANT -> TOOL* 涓轰竴缁?
     * 浠庡悗鍚戝墠淇濈暀鏈€杩戠殑娑堟伅
     */
    private List<LLMMessage> smartFilterMessages(List<LLMMessage> messages, int maxCount) {
        if (messages.size() <= maxCount) {
            return messages;
        }
        
        // 鎻愬彇绯荤粺娑堟伅
        LLMMessage systemMsg = null;
        List<LLMMessage> nonSystemMessages = new ArrayList<>();
        
        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.SYSTEM) {
                systemMsg = msg;
            } else {
                nonSystemMessages.add(msg);
            }
        }
        
        // 鎸夌粍鍒掑垎娑堟伅
        List<List<LLMMessage>> groups = new ArrayList<>();
        List<LLMMessage> currentGroup = new ArrayList<>();
        LLMMessage.Role lastRole = null;
        
        for (LLMMessage msg : nonSystemMessages) {
            // 鏂扮殑 USER 娑堟伅寮€濮嬫柊缁?
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
        
        // 浠庡悗鍚戝墠閫夋嫨瑕佷繚鐣欑殑缁?
        List<LLMMessage> result = new ArrayList<>();
        java.util.Set<String> validToolCallIds = new java.util.HashSet<>();
        
        // 鏀堕泦鎵€鏈夋湁鏁堢殑 tool_call_ids锛堟潵鑷繚鐣欑殑 ASSISTANT 娑堟伅锛?
        for (int i = groups.size() - 1; i >= 0 && result.size() < maxCount; i--) {
            List<LLMMessage> group = groups.get(i);
            
            // 妫€鏌ヨ繖缁勬槸鍚︿細瀵艰嚧娑堟伅鏁伴噺瓒呴檺
            int neededSlots = group.size();
            int availableSlots = maxCount - result.size();
            
            if (neededSlots <= availableSlots) {
                // 鍙互瀹屾暣娣诲姞杩欑粍
                List<LLMMessage> groupToAdd = new ArrayList<>();
                for (LLMMessage msg : group) {
                    groupToAdd.add(msg);
                    // 鏇存柊鏈夋晥鐨?tool_call_ids
                    if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                        for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                            if (tc.getId() != null) {
                                validToolCallIds.add(tc.getId());
                            }
                        }
                    }
                }
                result.addAll(0, groupToAdd);
            } else {
                // 闇€瑕侀儴鍒嗘坊鍔?- 浼樺厛淇濈暀 USER + ASSISTANT
                List<LLMMessage> groupToAdd = new ArrayList<>();
                for (LLMMessage msg : group) {
                    if (result.size() + groupToAdd.size() >= maxCount) break;
                    
                    // 璺宠繃瀛ょ珛鐨?TOOL 娑堟伅
                    if (msg.getRole() == LLMMessage.Role.TOOL) {
                        String toolCallId = msg.getToolCallId();
                        if (toolCallId == null || !validToolCallIds.contains(toolCallId)) {
                            log.info("[OpenAI] Skipping orphaned TOOL in filter: " + toolCallId);
                            continue;
                        }
                    }
                    
                    groupToAdd.add(msg);
                    
                    // 鏇存柊鏈夋晥鐨?tool_call_ids
                    if (msg.getRole() == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                        for (LLMMessage.ToolCall tc : msg.getToolCalls()) {
                            if (tc.getId() != null) {
                                validToolCallIds.add(tc.getId());
                            }
                        }
                    }
                }
                result.addAll(0, groupToAdd);
            }
        }
        
        // 楠岃瘉鏈€缁堝簭鍒楋細濡傛灉浠?TOOL 寮€澶达紝闇€瑕佺Щ闄?
        while (!result.isEmpty() && result.get(0).getRole() == LLMMessage.Role.TOOL) {
            log.warning("[OpenAI] Removing leading TOOL message to fix sequence");
            result.remove(0);
        }
        
        // 娣诲姞绯荤粺娑堟伅鍒版渶鍓嶉潰
        if (systemMsg != null) {
            result.add(0, systemMsg);
        }
        
        return result;
    }
    
    /**
     * 瑙ｆ瀽鍝嶅簲
     */
    private LLMResponse parseResponse(String responseBody) throws Exception {
        JsonNode json = mapper.readTree(responseBody);
        
        LLMResponse.Builder builder = LLMResponse.builder()
            .rawResponse(responseBody);
        
        if (json.has("choices") && json.get("choices").isArray() && json.get("choices").size() > 0) {
            JsonNode choice = json.get("choices").get(0);
            JsonNode message = choice.get("message");
            
            // 鍐呭锛堝惈 <think> 鏍囩鎻愬彇锛?
            String reasoningContentFromTag = null;
            if (message.has("content") && !message.get("content").isNull()) {
                String content = message.get("content").asText();
                reasoningContentFromTag = extractThinkingFromContent(content);
                if (reasoningContentFromTag != null) {
                    content = content.replace("<think>" + reasoningContentFromTag + "</think>", "").trim();
                }
                builder.content(content);
                log.info("[OpenAI] Parsed content length: " + content.length());
            } else {
                builder.content("");
                log.info("[OpenAI] No content in response");
            }
            
            // 鎬濊€冨唴瀹?(reasoning_content)
            if (message.has("reasoning_content") && !message.get("reasoning_content").isNull()) {
                String reasoningContent = message.get("reasoning_content").asText();
                builder.reasoningContent(reasoningContent);
                log.info("[OpenAI] Parsed reasoning_content length: " + reasoningContent.length());
            } else if (reasoningContentFromTag != null) {
                builder.reasoningContent(reasoningContentFromTag);
                log.info("[OpenAI] Parsed thinking from <think> tag length: " + reasoningContentFromTag.length());
            }
            
            // 宸ュ叿璋冪敤
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
            
            // 瀹屾垚鍘熷洜
            if (choice.has("finish_reason")) {
                String finishReason = choice.get("finish_reason").asText();
                builder.finishReason(finishReason);
                log.info("[OpenAI] Finish reason: " + finishReason);
            }
        }
        
        // 妯″瀷
        if (json.has("model")) {
            builder.model(json.get("model").asText());
        }
        
        // 鐢ㄩ噺
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
     * 浠?content 涓彁鍙?<think> 鏍囩鍐呯殑鎬濊€冨唴瀹癸紙闈炴祦寮忥級
     */
    private String extractThinkingFromContent(String content) {
        if (content == null) return null;
        int start = content.indexOf("<think>");
        int end = content.indexOf("</think>");
        if (start != -1 && end != -1 && end > start) {
            return content.substring(start + "<think>".length(), end).trim();
        }
        return null;
    }
    
    /**
     * 澶勭悊娴佸紡 content chunk 涓殑 <think> 鏍囩鎻愬彇
     * 杩斿洖鏇存柊鍚庣殑 inThinkingTag 鐘舵€?
     */
    private boolean processThinkTagChunk(String text, boolean inThinkingTag,
            StringBuilder contentBuffer, StringBuilder thinkingBuffer, StringBuilder reasoningBuffer,
            java.util.function.Consumer<String> contentConsumer,
            java.util.function.Consumer<String> thinkingConsumer) {
        if (inThinkingTag) {
            int endIndex = text.indexOf("</think>");
            if (endIndex != -1) {
                String thinkingPart = text.substring(0, endIndex);
                thinkingBuffer.append(thinkingPart);
                reasoningBuffer.append(thinkingPart);
                if (thinkingConsumer != null) {
                    thinkingConsumer.accept(thinkingPart);
                }
                String remaining = text.substring(endIndex + "</think>".length());
                contentBuffer.append(remaining);
                if (contentConsumer != null && !remaining.isEmpty()) {
                    contentConsumer.accept(remaining);
                }
                return false;
            } else {
                thinkingBuffer.append(text);
                reasoningBuffer.append(text);
                if (thinkingConsumer != null) {
                    thinkingConsumer.accept(text);
                }
                return true;
            }
        } else {
            int startIndex = text.indexOf("<think>");
            if (startIndex != -1) {
                String contentPart = text.substring(0, startIndex);
                contentBuffer.append(contentPart);
                if (contentConsumer != null && !contentPart.isEmpty()) {
                    contentConsumer.accept(contentPart);
                }
                String afterThink = text.substring(startIndex + "<think>".length());
                int endIndex = afterThink.indexOf("</think>");
                if (endIndex != -1) {
                    String thinkingPart = afterThink.substring(0, endIndex);
                    thinkingBuffer.append(thinkingPart);
                    reasoningBuffer.append(thinkingPart);
                    if (thinkingConsumer != null) {
                        thinkingConsumer.accept(thinkingPart);
                    }
                    String remaining = afterThink.substring(endIndex + "</think>".length());
                    contentBuffer.append(remaining);
                    if (contentConsumer != null && !remaining.isEmpty()) {
                        contentConsumer.accept(remaining);
                    }
                    return false;
                } else {
                    thinkingBuffer.append(afterThink);
                    reasoningBuffer.append(afterThink);
                    if (thinkingConsumer != null) {
                        thinkingConsumer.accept(afterThink);
                    }
                    return true;
                }
            } else {
                contentBuffer.append(text);
                if (contentConsumer != null) {
                    contentConsumer.accept(text);
                }
                return false;
            }
        }
    }
    
    /**
     * 鑾峰彇涓嬩竴涓?API Key锛堣疆璇級
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
     * 瑙勮寖鍖?URL
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
        
        log.info("[OpenAILLMService] Normalizing URL: " + url);
        
        if (url.contains("/chat/completions")) {
            return url;
        }
        if (url.endsWith("/v1")) {
            return url + "/chat/completions";
        }
        
        // 澶勭悊鐗瑰畾鎻愪緵鍟嗙殑 URL 鏍煎紡
        if (url.contains("moonshot.cn")) {
            return url + "/chat/completions";
        }
        
        return url + "/v1/chat/completions";
    }
    
    /**
     * 鏈嶅姟閰嶇疆
     */
}
