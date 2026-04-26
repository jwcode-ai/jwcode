package com.jwcode.core.llm;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 带 Provider 自动故障转移的 LLM 服务包装器
 *
 * 当主 Provider 返回硬性配额限制（RATE_LIMIT_HARD）时，
 * 自动切换到备用 Provider 重试请求。
 */
public class FallbackLLMService implements LLMService {

    private static final Logger logger = Logger.getLogger(FallbackLLMService.class.getName());

    private final LLMService primary;
    private final LLMService fallback;
    private final String primaryName;
    private final String fallbackName;

    public FallbackLLMService(LLMService primary, String primaryName,
                              LLMService fallback, String fallbackName) {
        this.primary = primary;
        this.fallback = fallback;
        this.primaryName = primaryName;
        this.fallbackName = fallbackName;
    }

    @Override
    public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages) {
        return primary.chat(messages).thenCompose(response -> {
            if (isHardLimitError(response)) {
                logger.warning("[FallbackLLM] Primary provider (" + primaryName + ") hit hard limit. Switching to fallback (" + fallbackName + ")...");
                return fallback.chat(messages);
            }
            return CompletableFuture.completedFuture(response);
        });
    }

    @Override
    public CompletableFuture<LLMResponse> chatWithTools(List<LLMMessage> messages, List<LLMTool> tools) {
        return primary.chatWithTools(messages, tools).thenCompose(response -> {
            if (isHardLimitError(response)) {
                logger.warning("[FallbackLLM] Primary provider (" + primaryName + ") hit hard limit. Switching to fallback (" + fallbackName + ")...");
                return fallback.chatWithTools(messages, tools);
            }
            return CompletableFuture.completedFuture(response);
        });
    }

    @Override
    public CompletableFuture<LLMResponse> chatStream(List<LLMMessage> messages, Consumer<String> contentConsumer) {
        return primary.chatStream(messages, contentConsumer).thenCompose(response -> {
            if (isHardLimitError(response)) {
                logger.warning("[FallbackLLM] Primary provider (" + primaryName + ") hit hard limit. Switching to fallback (" + fallbackName + ")...");
                return fallback.chatStream(messages, contentConsumer);
            }
            return CompletableFuture.completedFuture(response);
        });
    }

    @Override
    public CompletableFuture<LLMResponse> chatStreamWithTools(
            List<LLMMessage> messages,
            List<LLMTool> tools,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<StreamToolCallEvent> toolCallConsumer) {

        return primary.chatStreamWithTools(messages, tools, contentConsumer, thinkingConsumer, toolCallConsumer)
            .thenCompose(response -> {
                if (isHardLimitError(response)) {
                    logger.warning("[FallbackLLM] Primary provider (" + primaryName + ") hit hard limit. Switching to fallback (" + fallbackName + ")...");
                    return fallback.chatStreamWithTools(messages, tools, contentConsumer, thinkingConsumer, toolCallConsumer);
                }
                return CompletableFuture.completedFuture(response);
            });
    }

    @Override
    public CompletableFuture<LLMTestResult> test() {
        return primary.test().thenCompose(result -> {
            if (!result.isAvailable() && isHardLimitErrorCode(result.getErrorMessage())) {
                logger.warning("[FallbackLLM] Primary provider test failed with hard limit. Testing fallback...");
                return fallback.test();
            }
            return CompletableFuture.completedFuture(result);
        });
    }

    @Override
    public String getModelName() {
        return primary.getModelName();
    }

    @Override
    public int getContextWindow() {
        return primary.getContextWindow();
    }

    @Override
    public void close() {
        try { primary.close(); } catch (Exception e) { /* ignore */ }
        try { fallback.close(); } catch (Exception e) { /* ignore */ }
    }

    private boolean isHardLimitError(LLMResponse response) {
        return response != null && response.hasError()
            && "RATE_LIMIT_HARD".equals(response.getErrorCode());
    }

    private boolean isHardLimitErrorCode(String errorMessage) {
        return errorMessage != null && errorMessage.contains("RATE_LIMIT_HARD");
    }
}
