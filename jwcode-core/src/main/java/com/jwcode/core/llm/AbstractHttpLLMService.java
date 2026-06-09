package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * 閹跺€熻杽閻?HTTP LLM 閺堝秴濮熼崺铏硅
 * 
 * 鐏忎浇顥婇崗鍙橀煩閻?HTTP 閸╄櫣顢呯拋鐐煢閿?
 * - HttpClient 閸掓繂顫愰崠鏍电礄鏉╃偞甯寸搾鍛 60s閿涘瓛TTP/1.1閿涘苯褰查柊宥囩枂閿?
 * - 鐢箓鍣哥拠鏇犳畱 POST 鐠囬攱鐪伴敍鍫熷瘹閺佷即鈧偓闁?2s閳?s閳?s閿涘本娓舵径?3 濞嗏槄绱濋弨顖涘瘮閹?API key閿?
 * - 闁氨鏁ら惃鍕晩鐠囶垳鐖滈弰鐘茬殸
 * - 绾剟妾哄ù浣告彥闁喎銇戠拹銉礄isHardRateLimit key health 閺堝搫鍩楅敍?
 * - SSE 鐞涘矁顕伴崣鏍ф嫲鏉╃偞甯寸粻锛勬倞
 * - 閺冦儱绻旈崪宀冣偓妤佹鐠佹澘缍?
 * 
 * 鐎涙劗琚崣顏堟付鐎圭偟骞?5 娑?hook 閺傝纭堕弶銉ョ暚閹存劕宕楃拋顕€鈧倿鍘ら妴?
 */


public abstract class AbstractHttpLLMService implements LLMService {

    protected static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(AbstractHttpLLMService.class.getName());

    protected static final ObjectMapper mapper = new ObjectMapper();
    protected final HttpClient httpClient;
    protected final ServiceConfig config;
    protected int currentKeyIndex = 0;
    protected final String providerTag;

    protected AbstractHttpLLMService(ServiceConfig config, String providerTag) {
        this.config = config;
        this.providerTag = providerTag;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

        log.info("[" + providerTag + "Service] Initialized with model: " + config.getModel());
        log.info("[" + providerTag + "Service] Base URL: " + config.getBaseUrl());
        log.info("[" + providerTag + "Service] Timeout: " + config.getTimeoutSeconds() + "s");
    }

    // ======================== Subclass Hooks ========================

    /**
     * 閺嬪嫬缂撶拠閿嬬湴娴?JSON
     */
    protected abstract ObjectNode buildRequestBody(List<LLMMessage> msgs, List<LLMTool> tools);

    /**
     * 閺嬪嫬缂撶拠閿嬬湴 URI
     */
    protected abstract URI buildRequestUri();

    /**
     * 閺嬪嫬缂撴０婵嗩樆閻ㄥ嫯顕Ч鍌氥仈閿涘牐顓荤拠浣搞仈缁涘绱?
     */
    protected abstract Map<String, String> buildExtraHeaders(String apiKey);

    /**
     * 鐟欙絾鐎介棃鐐寸ウ瀵繐鎼锋惔鏂剧秼
     */
    protected abstract LLMResponse parseResponse(String responseBody) throws Exception;

    /**
     * 婢跺嫮鎮婂ù浣哥础 SSE 娴滃娆㈤妴?
     * 
     * @param eventType SSE 娴滃娆㈢猾璇茬€烽敍鍫濐洤 "data"閵?message_start" 缁涘绱?
     * @param data      SSE 娴滃娆㈤弫鐗堝祦閿涘湞SON 閼哄倻鍋ｉ敍?
     * @param acc       濞翠礁绱＄槐顖溞濋崳顭掔礉閻劋绨穱婵嗙摠娑擃參妫块悩鑸碘偓?
     * @return StreamResult 鐞涖劎銇氳ぐ鎾冲娴滃娆㈡径鍕倞閸氬海娈戠紒鎾寸亯
     */
    protected abstract StreamResult processStreamEvent(
            String eventType, JsonNode data, StreamAccumulator acc);

    /**
     * 婢跺嫮鎮婂ù浣哥础鐞涘矉绱橭penAI 閺嶇厧绱￠敍姝瀉ta: {...}閿?
     */
    protected abstract LLMResponse processStreamResponse(
            InputStream body,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) throws Exception;

    // ======================== Shared HTTP Utilities ========================

    /**
     * 閼惧嘲褰囨稉瀣╃娑?API key閿涘牐鐤嗙拠顫礆
     */
    protected String getNextApiKey() {
        List<String> keys = config.getApiKeys();
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        String key = keys.get(currentKeyIndex);
        currentKeyIndex = (currentKeyIndex + 1) % keys.size();
        return key;
    }

    /**
     * 閺嬪嫬缂?HttpRequest
     */
    protected HttpRequest buildHttpRequest(String apiKey, String requestJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(buildRequestUri())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()));

        // 濞ｈ濮炵拋銈堢槈婢?
        Map<String, String> extraHeaders = buildExtraHeaders(apiKey);
        for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }

    /**
     * 鐟欏嫯瀵栭崠?URL閿涘牏些闂勩倕鐔柈銊︽灘閺夌媴绱濇潻钘夊鐠侯垰绶炵粵澶涚礆
     */
    protected String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            log.warning("[" + providerTag + "] URL is empty, using default");
            return getDefaultBaseUrl();
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 鐎涙劗琚崣顖濐洬閻╂牔浜掗幓鎰返姒涙顓?base URL
     */
    protected String getDefaultBaseUrl() {
        return "https://api.openai.com/v1/chat/completions";
    }

    /**
     * 婢跺嫮鎮婇棃鐐寸ウ瀵?HTTP 闁挎瑨顕ら崫宥呯安閿涘本褰侀崣鏍晩鐠囶垳琚崹瀣嫲濞戝牊浼?
     */
    protected String extractErrorType(String errorBody) {
        try {
            JsonNode json = mapper.readTree(errorBody);
            if (json.has("error")) {
                JsonNode error = json.get("error");
                if (error.has("type")) {
                    return error.get("type").asText("");
                }
                if (error.has("code")) {
                    return error.get("code").asText("");
                }
            }
            // 閸忕厧顔愬ù浣哥础闁挎瑨顕ょ悰?
            if (json.has("type")) {
                return json.get("type").asText("");
            }
        } catch (Exception e) {
            // ignore parse errors
        }
        return "unknown_error";
    }

    /**
     * 娴犲酣鏁婄拠顖氭惙鎼存柧缍嬫稉顓熷絹閸欐牠鏁婄拠顖涚Х閹?
     */
    protected String extractErrorMessage(String errorBody) {
        try {
            JsonNode json = mapper.readTree(errorBody);
            if (json.has("error")) {
                JsonNode error = json.get("error");
                if (error.has("message")) {
                    return error.get("message").asText("");
                }
            }
            // Try top-level message
            if (json.has("message")) {
                return json.get("message").asText("");
            }
        } catch (Exception e) {
            // ignore parse errors
        }
        return errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody;
    }

    /**
     * 閸掋倖鏌囬弰顖氭儊閺勵垳鈥栭梽鎰ウ閿涘湵PD閵嗕巩nsufficient_quota 缁涘绗夐崣顖炲櫢鐠囨洜娈戦梽鎰ウ閿?
     */
    protected boolean isHardRateLimit(String errorType) {
        if (errorType == null) return false;
        // OpenAI: "insufficient_quota", "tokens", "requests" (TPD/TPM exceeded)
        // Anthropic: "rate_limit_error" with is_hard_limit=true is handled separately
        return errorType.contains("insufficient_quota")
            || errorType.contains("tokens")
            || "rate_limit_error".equals(errorType);
    }

    /**
     * 濞撳懐鎮婇梽鎰ウ濞戝牊浼呴敍鍫濆箵闂勩倕鍟戞担娆庝繆閹垽绱?
     */
    protected String cleanRateLimitMessage(String message) {
        if (message == null) return "";
        // 閹搭亝鏌囨潻鍥毐濞戝牊浼?
        if (message.length() > 200) {
            return message.substring(0, 200) + "...";
        }
        return message;
    }

    /**
     * 閸掋倖鏌?HTTP 閻樿埖鈧胶鐖滈弰顖氭儊閸欘垶鍣哥拠?
     */
    protected boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500 || statusCode == 529;
    }

    /**
     * 鐏?HTTP 閻樿埖鈧胶鐖滈弰鐘茬殸閸掓澘鍞撮柈銊╂晩鐠囶垳鐖?
     */
    protected String mapHttpStatusToErrorCode(int statusCode, String errorType) {
        if (statusCode == 429) {
            if (isHardRateLimit(errorType)) {
                return "RATE_LIMIT_HARD";
            }
            return "RATE_LIMIT_RETRYABLE";
        }
        if (statusCode == 529) {
            return "SERVER_OVERLOADED";
        }
        if (statusCode == 400) return "CLIENT_ERROR";
        if (statusCode == 401 || statusCode == 403) return "AUTH_ERROR";
        if (statusCode == 404) return "CLIENT_ERROR";
        if (statusCode >= 500) return "SERVER_ERROR";
        return "UNKNOWN_ERROR";
    }

    /**
     * 鐏忓棗绱撶敮鍝ヨ閸ㄥ妲х亸鍕煂閸愬懘鍎撮柨娆掝嚖閻?
     */
    protected String mapExceptionToErrorCode(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException) {
            return "TIMEOUT";
        }
        if (e instanceof java.net.ConnectException) {
            return "CONNECTION_ERROR";
        }
        if (e instanceof java.net.SocketException) {
            return "CONNECTION_ERROR";
        }
        if (e instanceof InterruptedException) {
            return "INTERRUPTED";
        }
        return "REQUEST_FAILED";
    }

    // ======================== SSE Utilities ========================

    /**
     * 闁劘顢戠拠璇插絿 SSE 濞翠緤绱濈亸鍡樼槨鐞涘苯鍨庡ú鎯у煂鐎涙劗琚惃?stream event 婢跺嫮鎮?
     */
    protected LLMResponse processSSEStream(
            InputStream body,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) throws Exception {

        StreamAccumulator acc = new StreamAccumulator();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            String currentEventType = null;
            StringBuilder currentData = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // 缁岄缚顢?= 娴滃娆㈢紒鎾存将閿涘苯顦╅悶鍡欑柈缁夘垳娈戞禍瀣╂
                    if (currentData.length() > 0) {
                        String dataStr = currentData.toString().trim();
                        if (!dataStr.isEmpty()) {
                            try {
                                JsonNode data = mapper.readTree(dataStr);
                                StreamResult result = processStreamEvent(
                                    currentEventType != null ? currentEventType : "data",
                                    data, acc);
                                // 鐏?StreamResult 閸掑棙娣崇紒?consumer
                                if (result != null) {
                                    if (result.content != null && contentConsumer != null) {
                                        contentConsumer.accept(result.content);
                                    }
                                    if (result.thinkingContent != null && thinkingConsumer != null) {
                                        thinkingConsumer.accept(result.thinkingContent);
                                    }
                                    if (result.toolCallEvent != null && toolCallConsumer != null) {
                                        toolCallConsumer.accept(result.toolCallEvent);
                                    }
                                }
                            } catch (Exception e) {
                                log.warning("[" + providerTag + " Stream] Failed to parse SSE data: " + e.getMessage());
                            }
                        }
                        currentData = new StringBuilder();
                        currentEventType = null;
                    }
                } else if (line.startsWith("event: ")) {
                    currentEventType = line.substring("event: ".length()).trim();
                } else if (line.startsWith("data: ")) {
                    currentData.append(line.substring("data: ".length()));
                } else if (line.startsWith("data:")) {
                    currentData.append(line.substring("data:".length()));
                } else if (line.startsWith(":")) {
                    // Comment/keepalive line, ignore
                } else {
                    // Non-SSE line, might be part of data
                    currentData.append(line);
                }
            }

            // 婢跺嫮鎮婇張鈧崥搴濈娑擃亙绨ㄦ禒璁圭礄婵″倹鐏夐張澶涚礆
            if (currentData.length() > 0) {
                String dataStr = currentData.toString().trim();
                if (!dataStr.isEmpty()) {
                    try {
                        JsonNode data = mapper.readTree(dataStr);
                        processStreamEvent(
                            currentEventType != null ? currentEventType : "data",
                            data, acc);
                    } catch (Exception e) {
                        log.warning("[" + providerTag + " Stream] Failed to parse final SSE data: " + e.getMessage());
                    }
                }
            }
        }

        return acc.buildResponse();
    }

    // ======================== LLMService Implementation ========================

    @Override
    public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages) {
        return chatWithTools(messages, null);
    }

    @Override
    public CompletableFuture<LLMResponse> chatStream(
            List<LLMMessage> messages,
            Consumer<String> contentConsumer) {
        return chatStreamWithTools(messages, null, contentConsumer, null, null);
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
    public void close() {
        // HttpClient is shared, nothing to close
    }

    @Override
    public CompletableFuture<LLMTestResult> test() {
        return chat(List.of(LLMMessage.user("test"))).thenApply(response -> {
            if (response.isSuccess()) {
                return LLMTestResult.success(providerTag + " service is available", 0);
            }
            return LLMTestResult.error(response.getErrorMessage(), "Check your API key and configuration");
        });
    }

    // ======================== Inner Types ========================

    /**
     * 濞翠礁绱℃禍瀣╂婢跺嫮鎮婄紒鎾寸亯
     */
    protected static class StreamResult {
        final String content;
        final String thinkingContent;
        final LLMService.StreamToolCallEvent toolCallEvent;

        public StreamResult(String content, String thinkingContent, LLMService.StreamToolCallEvent toolCallEvent) {
            this.content = content;
            this.thinkingContent = thinkingContent;
            this.toolCallEvent = toolCallEvent;
        }

        public static StreamResult content(String content) {
            return new StreamResult(content, null, null);
        }

        public static StreamResult thinking(String thinkingContent) {
            return new StreamResult(null, thinkingContent, null);
        }

        public static StreamResult toolCall(LLMService.StreamToolCallEvent event) {
            return new StreamResult(null, null, event);
        }

        public static StreamResult empty() {
            return new StreamResult(null, null, null);
        }
    }

    /**
     * 濞翠礁绱＄槐顖溞濋崳?- 閻劋绨崷銊︾ウ瀵繐顦╅悶鍡氱箖缁嬪鑵戠槐顖溞濋悩鑸碘偓?
     * 鐎涙劗琚崣顖欎簰閹碘晛鐫嶅銈囩柈缁夘垰娅掓禒銉﹀潑閸旂姴宕楃拋顔惧鐎规氨娈戦悩鑸碘偓浣哥摟濞?
     */
    protected static class StreamAccumulator {
        protected StringBuilder contentBuilder = new StringBuilder();
        protected StringBuilder reasoningBuilder = new StringBuilder();
        protected List<LLMMessage.ToolCall> toolCalls = new ArrayList<>();
        protected int promptTokens = 0;
        protected int completionTokens = 0;
        protected String finishReason;
        protected String model;
        protected String rawResponse;
        protected String errorMessage;
        protected String errorCode;

        public StreamAccumulator() {}

        public LLMResponse buildResponse() {
            if (errorMessage != null) {
                return errorCode != null
                    ? LLMResponse.error(errorCode, errorMessage)
                    : LLMResponse.error(errorMessage);
            }

            LLMResponse.Builder builder = LLMResponse.builder()
                .content(contentBuilder.toString())
                .reasoningContent(reasoningBuilder.length() > 0 ? reasoningBuilder.toString() : null)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .finishReason(finishReason)
                .model(model)
                .rawResponse(rawResponse);

            if (!toolCalls.isEmpty()) {
                builder.toolCalls(toolCalls);
            }

            return builder.build();
        }
    }
}



