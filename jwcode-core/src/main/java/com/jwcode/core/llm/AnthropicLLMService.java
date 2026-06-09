package com.jwcode.core.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class AnthropicLLMService extends AbstractHttpLLMService {

    private final AnthropicMessageConverter converter;

    public AnthropicLLMService(ServiceConfig config) {
        super(config, "Anthropic");
        this.converter = new AnthropicMessageConverter();
    }

    @Override
    protected ObjectNode buildRequestBody(List<LLMMessage> messages, List<LLMTool> tools) {
        return converter.toRequestBody(messages, tools, config);
    }

    @Override
    protected URI buildRequestUri() {
        return URI.create(normalizeUrl(config.getBaseUrl()) + "/v1/messages");
    }

    @Override
    protected Map<String, String> buildExtraHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", config.getAnthropicVersion());
        return headers;
    }

    @Override
    protected LLMResponse parseResponse(String responseBody) throws Exception {
        return converter.parseResponse(responseBody);
    }

    @Override
    protected StreamResult processStreamEvent(String eventType, JsonNode data, StreamAccumulator acc) {
        return StreamResult.empty();
    }

    @Override
    protected LLMResponse processStreamResponse(
            InputStream body, Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) throws Exception {

        AnthropicStreamAccumulator acc = new AnthropicStreamAccumulator();
        processAnthropicStream(body, contentConsumer, thinkingConsumer, toolCallConsumer);
        return acc.buildResponse();
    }

    @Override
    public CompletableFuture<LLMResponse> chatWithTools(List<LLMMessage> messages, List<LLMTool> tools) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = getNextApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("No API key configured");
            }

            String uri = buildRequestUri().toString();
            log.info("[Anthropic] ==========================================");
            log.info("[Anthropic] Request: POST " + uri);
            log.info("[Anthropic] Model: " + config.getModel());
            log.info("[Anthropic] Message count: " + messages.size());

            int maxRetries = 3;
            int attempt = 0;
            int retryDelay = 2000;

            while (attempt <= maxRetries) {
                try {
                    ObjectNode requestBody = buildRequestBody(messages, tools);
                    String requestJson = mapper.writeValueAsString(requestBody);

                    log.info("[Anthropic] ---------- Request Body ----------");
                    log.info("[Anthropic] " + requestJson);
                    log.info("[Anthropic] ---------- End Request Body ----------");

                    HttpRequest request = buildHttpRequest(apiKey, requestJson);
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    log.info("[Anthropic] Response status: " + response.statusCode());

                    if (response.statusCode() == 200) {
                        String responseBody = response.body();
                        log.info("[Anthropic] ---------- Response Body ----------");
                        log.info("[Anthropic] " + responseBody);
                        log.info("[Anthropic] ---------- End Response Body ----------");
                        return parseResponse(responseBody);
                    } else {
                        String errorBody = response.body();
                        String errorType = extractAnthropicErrorType(errorBody);
                        String errorMsg = extractAnthropicErrorMessage(errorBody);
                        String internalCode = converter.mapAnthropicErrorCode(errorType);

                        // Guard against empty error messages
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = "HTTP " + response.statusCode()
                                + (errorBody != null && !errorBody.isEmpty()
                                    ? " body=" + errorBody.substring(0, Math.min(errorBody.length(), 200))
                                    : " (empty body)");
                        }

                        if ("RATE_LIMIT_HARD".equals(internalCode)) {
                            log.severe("[Anthropic] Hard rate limit (" + errorType + "): " + errorMsg);
                            return LLMResponse.error("RATE_LIMIT_HARD",
                                "Rate limit reached: " + errorMsg + " [Provider: " + config.getModel() + "]");
                        }

                        boolean retryable = "SERVER_OVERLOADED".equals(internalCode)
                            || "SERVER_ERROR".equals(internalCode)
                            || "UNKNOWN_ERROR".equals(internalCode);

                        if (retryable && attempt < maxRetries) {
                            attempt++;
                            log.warning("[Anthropic] " + errorType + ", retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                            try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            retryDelay *= 2;
                            apiKey = getNextApiKey();
                            continue;
                        }

                        log.severe("[Anthropic] Error " + response.statusCode() + ": " + errorBody);
                        return LLMResponse.error(internalCode, errorMsg);
                    }

                } catch (java.net.http.HttpTimeoutException e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[Anthropic] Timeout, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2; apiKey = getNextApiKey();
                    } else {
                        return LLMResponse.error("TIMEOUT", "Request timed out after " + (maxRetries+1) + " attempts.");
                    }
                } catch (java.net.ConnectException e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[Anthropic] Connection failed, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2; continue;
                    }
                    return LLMResponse.error("CONNECTION_ERROR", "Connection failed: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return LLMResponse.error("INTERRUPTED", "Request interrupted.");
                } catch (Exception e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[Anthropic] Failed, retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2; continue;
                    }
                    return LLMResponse.error("REQUEST_FAILED", "Request failed: " + e.getMessage());
                }
            }
            return LLMResponse.error("MAX_RETRIES", "Max retries exceeded");
        });
    }

    @Override
    public CompletableFuture<LLMResponse> chatStreamWithTools(
            List<LLMMessage> messages, List<LLMTool> tools,
            Consumer<String> contentConsumer, Consumer<String> thinkingConsumer,
            Consumer<StreamToolCallEvent> toolCallConsumer) {

        return CompletableFuture.supplyAsync(() -> {
            String apiKey = getNextApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("No API key configured");
            }

            String uri = buildRequestUri().toString();
            log.info("[Anthropic Stream] ==========================================");
            log.info("[Anthropic Stream] Request: POST " + uri);
            log.info("[Anthropic Stream] Model: " + config.getModel());
            log.info("[Anthropic Stream] Message count: " + messages.size());

            int maxRetries = 3;
            int attempt = 0;
            int retryDelay = 2000;

            while (attempt <= maxRetries) {
                try {
                    ObjectNode requestBody = buildRequestBody(messages, tools);
                    requestBody.put("stream", true);
                    String requestJson = mapper.writeValueAsString(requestBody);

                    log.info("[Anthropic Stream] ---------- Request Body ----------");
                    log.info("[Anthropic Stream] " + requestJson);
                    log.info("[Anthropic Stream] ---------- End Request Body ----------");

                    HttpRequest request = buildHttpRequest(apiKey, requestJson);
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                    log.info("[Anthropic Stream] Response status: " + response.statusCode());

                    if (response.statusCode() != 200) {
                        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                        String errorType = extractAnthropicErrorType(errorBody);
                        String errorMsg = extractAnthropicErrorMessage(errorBody);
                        String internalCode = converter.mapAnthropicErrorCode(errorType);

                        // Guard against empty error messages
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = "HTTP " + response.statusCode()
                                + (errorBody != null && !errorBody.isEmpty()
                                    ? " body=" + errorBody.substring(0, Math.min(errorBody.length(), 200))
                                    : " (empty body)");
                        }

                        log.severe("[Anthropic Stream] Error " + response.statusCode() + " type=" + errorType + " msg=" + errorMsg);

                        if ("RATE_LIMIT_HARD".equals(internalCode)) {
                            return LLMResponse.error("RATE_LIMIT_HARD",
                                "Rate limit: " + errorMsg + " [Provider: " + config.getModel() + "]");
                        }

                        boolean retryable = "SERVER_OVERLOADED".equals(internalCode)
                            || "SERVER_ERROR".equals(internalCode);
                        if (retryable && attempt < maxRetries) {
                            attempt++;
                            log.warning("[Anthropic Stream] " + errorType + ", retrying (" + attempt + "/" + maxRetries + ") after " + retryDelay + "ms...");
                            try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            retryDelay *= 2; apiKey = getNextApiKey(); continue;
                        }
                        return LLMResponse.error(internalCode, errorMsg);
                    }

                    return processAnthropicStream(response.body(), contentConsumer, thinkingConsumer, toolCallConsumer);

                } catch (java.io.IOException e) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        log.warning("[Anthropic Stream] IO error, retrying (" + attempt + "/" + maxRetries + ")...");
                        try { Thread.sleep(retryDelay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        retryDelay *= 2; apiKey = getNextApiKey(); continue;
                    }
                    return LLMResponse.error("STREAM_FAILED", "Stream failed: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return LLMResponse.error("INTERRUPTED", "Stream interrupted.");
                } catch (Exception e) {
                    log.log(Level.SEVERE, "[Anthropic Stream] Error", e);
                    return LLMResponse.error("STREAM_FAILED", "Stream failed: " + e.getMessage());
                }
            }
            return LLMResponse.error("MAX_RETRIES", "Max retries exceeded");
        });
    }

    // ======================== Anthropic SSE Stream Processor ========================

    private LLMResponse processAnthropicStream(
            InputStream body, Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<StreamToolCallEvent> toolCallConsumer) throws Exception {

        AnthropicStreamAccumulator acc = new AnthropicStreamAccumulator();
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(body, StandardCharsets.UTF_8));

        String line;
        String currentEventType = null;
        StringBuilder currentData = new StringBuilder();
        boolean streamComplete = false;

        while (!streamComplete && (line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (currentData.length() > 0) {
                    String dataStr = currentData.toString().trim();
                    if (!dataStr.isEmpty()) {
                        try {
                            JsonNode data = mapper.readTree(dataStr);
                            if (processAnthropicStreamEvent(currentEventType, data, acc,
                                contentConsumer, thinkingConsumer, toolCallConsumer)) {
                                streamComplete = true;
                            }
                        } catch (Exception e) {
                            log.warning("[Anthropic Stream] Parse error: " + e.getMessage());
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
            }
        }

        if (!streamComplete && currentData.length() > 0) {
            String dataStr = currentData.toString().trim();
            if (!dataStr.isEmpty()) {
                try {
                    processAnthropicStreamEvent(currentEventType, mapper.readTree(dataStr), acc,
                        contentConsumer, thinkingConsumer, toolCallConsumer);
                } catch (Exception e) {
                    log.warning("[Anthropic Stream] Parse final error: " + e.getMessage());
                }
            }
        }

        return acc.buildResponse();
    }

    private boolean processAnthropicStreamEvent(
            String eventType, JsonNode data, AnthropicStreamAccumulator acc,
            Consumer<String> contentConsumer, Consumer<String> thinkingConsumer,
            Consumer<StreamToolCallEvent> toolCallConsumer) {

        if (eventType == null) eventType = "data";

        switch (eventType) {
            case "message_start":
                if (data.has("message")) {
                    JsonNode message = data.get("message");
                    if (message.has("model")) acc.model = message.get("model").asText();
                    if (message.has("usage")) {
                        JsonNode usage = message.get("usage");
                        acc.promptTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
                    }
                }
                break;

            case "content_block_start":
                int blockIndex = data.has("index") ? data.get("index").asInt() : 0;
                if (data.has("content_block")) {
                    JsonNode block = data.get("content_block");
                    String type = block.has("type") ? block.get("type").asText() : "";
                    acc.currentBlockType = type;
                    acc.currentBlockIndex = blockIndex;

                    if ("tool_use".equals(type)) {
                        String toolId = block.has("id") ? block.get("id").asText() : "";
                        String toolName = block.has("name") ? block.get("name").asText() : "";
                        acc.toolInputBuffers.put(blockIndex, new StringBuilder());
                        acc.pendingToolName.put(blockIndex, toolName);
                        acc.pendingToolId.put(blockIndex, toolId);
                        if (toolCallConsumer != null) {
                            toolCallConsumer.accept(new StreamToolCallEvent(toolId, "tool_use", toolName, "", false, blockIndex));
                        }
                    }
                }
                break;

            case "content_block_delta":
                if (data.has("delta")) {
                    JsonNode delta = data.get("delta");
                    String type = delta.has("type") ? delta.get("type").asText() : "";
                    int idx = data.has("index") ? data.get("index").asInt() : acc.currentBlockIndex;

                    if ("text_delta".equals(type)) {
                        String text = delta.has("text") ? delta.get("text").asText() : "";
                        acc.contentBuilder.append(text);
                        if (contentConsumer != null) contentConsumer.accept(text);
                    } else if ("thinking_delta".equals(type)) {
                        String thinking = delta.has("thinking") ? delta.get("thinking").asText() : "";
                        acc.reasoningBuilder.append(thinking);
                        if (thinkingConsumer != null) thinkingConsumer.accept(thinking);
                    } else if ("input_json_delta".equals(type)) {
                        String partialJson = delta.has("partial_json") ? delta.get("partial_json").asText() : "";
                        StringBuilder buf = acc.toolInputBuffers.get(idx);
                        if (buf != null) {
                            buf.append(partialJson);
                        }
                        String fullArgs = buf != null ? buf.toString() : partialJson;
                        String toolId = acc.pendingToolId.getOrDefault(idx, "");
                        String toolName = acc.pendingToolName.getOrDefault(idx, "");
                        if (toolCallConsumer != null) {
                            toolCallConsumer.accept(new StreamToolCallEvent(toolId, "tool_use", toolName, fullArgs, false, idx));
                        }
                    }
                }
                break;

            case "content_block_stop":
                int stopIdx = data.has("index") ? data.get("index").asInt() : acc.currentBlockIndex;
                String currentType = acc.currentBlockType;
                if ("tool_use".equals(currentType)) {
                    StringBuilder buf = acc.toolInputBuffers.get(stopIdx);
                    if (buf != null) {
                        String jsonArgs = buf.toString();
                        String toolId = acc.pendingToolId.getOrDefault(stopIdx, "");
                        String toolName = acc.pendingToolName.getOrDefault(stopIdx, "");
                        acc.toolCalls.add(LLMMessage.ToolCall.builder()
                            .id(toolId).type("tool_use").function(toolName, jsonArgs).build());
                        if (toolCallConsumer != null) {
                            toolCallConsumer.accept(new StreamToolCallEvent(toolId, "tool_use", toolName, jsonArgs, true, stopIdx));
                        }
                    }
                }
                acc.currentBlockType = null;
                break;

            case "message_delta":
                if (data.has("delta")) {
                    JsonNode delta = data.get("delta");
                    if (delta.has("stop_reason")) {
                        String sr = delta.get("stop_reason").asText();
                        acc.finishReason = "end_turn".equals(sr) ? "stop" : sr;
                    }
                }
                if (data.has("usage")) {
                    JsonNode usage = data.get("usage");
                    acc.completionTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
                }
                break;

            case "message_stop":
                // Stream complete — signal caller to stop reading
                return true;

            case "ping":
                // Keepalive, no action needed
                break;

            case "error":
                log.severe("[Anthropic Stream] Error event: " + data.toString());
                acc.errorCode = data.has("error") && data.get("error").has("type")
                    ? data.get("error").get("type").asText("stream_error")
                    : "stream_error";
                acc.errorMessage = data.has("error") && data.get("error").has("message")
                    ? data.get("error").get("message").asText(data.toString())
                    : data.toString();
                break;
        }
        return false;
    }

    private String extractAnthropicErrorType(String errorBody) {
        try {
            JsonNode json = mapper.readTree(errorBody);
            if (json.has("error")) {
                return json.get("error").has("type") ? json.get("error").get("type").asText("") : "";
            }
            if (json.has("type")) return json.get("type").asText("");
        } catch (Exception e) { /* ignore */ }
        return "api_error";
    }

    private String extractAnthropicErrorMessage(String errorBody) {
        try {
            JsonNode json = mapper.readTree(errorBody);
            if (json.has("error")) {
                JsonNode error = json.get("error");
                if (error.has("message")) {
                    String msg = error.get("message").asText("");
                    if (msg != null && !msg.isEmpty()) {
                        return msg;
                    }
                }
                // Fallback: construct from error type
                String type = error.has("type") ? error.get("type").asText("") : "";
                return type.isEmpty() ? errorBody : "[" + type + "] " + errorBody;
            }
        } catch (Exception e) { /* ignore */ }
        return errorBody != null && !errorBody.isEmpty()
            ? (errorBody.length() > 500 ? errorBody.substring(0, 500) + "..." : errorBody)
            : "Unknown error (empty response body)";
    }

    private static class AnthropicStreamAccumulator extends StreamAccumulator {
        String currentBlockType;
        int currentBlockIndex;
        Map<Integer, StringBuilder> toolInputBuffers = new HashMap<>();
        Map<Integer, String> pendingToolId = new HashMap<>();
        Map<Integer, String> pendingToolName = new HashMap<>();
    }
}
