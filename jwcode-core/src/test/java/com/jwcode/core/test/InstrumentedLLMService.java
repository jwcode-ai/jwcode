package com.jwcode.core.test;

import com.jwcode.core.llm.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 可编程响应的模拟 LLM 服务 — 用于测试。
 *
 * <p>支持预设响应序列和回调，无需真实 API 调用。
 */
public class InstrumentedLLMService implements LLMService {

    private final List<LLMResponse> programmedResponses = new ArrayList<>();
    private final List<List<LLMMessage>> receivedRequests = new ArrayList<>();
    private int responseIndex = 0;
    private boolean failOnEmpty = false;
    private String modelName = "test-model";

    /** 预设下一个响应 */
    public InstrumentedLLMService withResponse(LLMResponse response) {
        programmedResponses.add(response);
        return this;
    }

    /** 预设文本响应 */
    public InstrumentedLLMService withTextResponse(String text) {
        programmedResponses.add(LLMResponse.success(text));
        return this;
    }

    /** 预设错误响应 */
    public InstrumentedLLMService withErrorResponse(String error) {
        programmedResponses.add(LLMResponse.error(error));
        return this;
    }

    /** 当响应用尽时抛出异常 */
    public InstrumentedLLMService failOnEmpty() {
        this.failOnEmpty = true;
        return this;
    }

    /** 设置模型名称 */
    public InstrumentedLLMService withModelName(String name) {
        this.modelName = name;
        return this;
    }

    /** 获取接收到的所有请求 */
    public List<List<LLMMessage>> getReceivedRequests() {
        return Collections.unmodifiableList(receivedRequests);
    }

    /** 获取最后一次请求 */
    public List<LLMMessage> getLastRequest() {
        return receivedRequests.isEmpty() ? List.of() : receivedRequests.get(receivedRequests.size() - 1);
    }

    /** 重置状态 */
    public void reset() {
        responseIndex = 0;
        receivedRequests.clear();
    }

    private LLMResponse nextResponse() {
        if (responseIndex < programmedResponses.size()) {
            return programmedResponses.get(responseIndex++);
        }
        if (failOnEmpty) {
            throw new IllegalStateException("No more programmed responses");
        }
        return LLMResponse.success("[FINISH]");
    }

    @Override
    public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages) {
        receivedRequests.add(new ArrayList<>(messages));
        return CompletableFuture.completedFuture(nextResponse());
    }

    @Override
    public CompletableFuture<LLMResponse> chatWithTools(List<LLMMessage> messages, List<LLMTool> tools) {
        receivedRequests.add(new ArrayList<>(messages));
        return CompletableFuture.completedFuture(nextResponse());
    }

    @Override
    public CompletableFuture<LLMResponse> chatStream(List<LLMMessage> messages,
                                                      Consumer<String> contentConsumer) {
        receivedRequests.add(new ArrayList<>(messages));
        LLMResponse response = nextResponse();
        if (response.getContent() != null) {
            contentConsumer.accept(response.getContent());
        }
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<LLMResponse> chatStreamWithTools(
            List<LLMMessage> messages, List<LLMTool> tools,
            Consumer<String> contentConsumer, Consumer<String> thinkingConsumer,
            Consumer<StreamToolCallEvent> toolCallConsumer) {
        receivedRequests.add(new ArrayList<>(messages));
        return CompletableFuture.completedFuture(nextResponse());
    }

    @Override
    public CompletableFuture<LLMTestResult> test() {
        return CompletableFuture.completedFuture(LLMTestResult.success("test-model", 1));
    }

    @Override
    public String getModelName() { return modelName; }

    @Override
    public void close() {
        programmedResponses.clear();
        receivedRequests.clear();
    }
}
