package com.jwcode.core.channel;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.llm.LLMFactory;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.llm.LLMTestResult;
import com.jwcode.core.llm.LLMTool;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelMessageDispatcherTest {

    @Test
    void wechatUsesShortToolMessages() {
        RecordingChannelRegistry registry = new RecordingChannelRegistry();
        ToolRegistry toolRegistry = ToolRegistry.createDefault();
        ShortToolLLMService llmService = new ShortToolLLMService();
        ChannelMessageDispatcher dispatcher = new ChannelMessageDispatcher(
            toolRegistry,
            registry,
            new TestFactory(llmService),
            new AgentRegistry(toolRegistry),
            new ToolExecutor(toolRegistry, null, null, null));

        InboundChannelMessage inbound = new InboundChannelMessage();
        inbound.channelId = "wechat-1";
        inbound.channelType = "wechat";
        inbound.senderId = "user-1";
        inbound.text = "run task";

        dispatcher.dispatchForTest(inbound);
        waitForMessages(registry, 2);

        assertTrue(registry.messages.stream().anyMatch(m -> m.startsWith("runTool ") && m.endsWith("ms")));
        assertTrue(registry.messages.stream().anyMatch(m -> m.equals("final answer")));
    }

    private static void waitForMessages(RecordingChannelRegistry registry, int minCount) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (registry.messages.size() >= minCount) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static class RecordingChannelRegistry extends ChannelRegistry {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void send(String channelId, String recipientId, String text) {
            messages.add(text);
        }
    }

    private static class ShortToolLLMService implements LLMService {
        @Override public CompletableFuture<LLMResponse> chat(List<LLMMessage> messages) { return CompletableFuture.completedFuture(LLMResponse.success("final answer")); }
        @Override public CompletableFuture<LLMResponse> chatWithTools(List<LLMMessage> messages, List<LLMTool> tools) { return CompletableFuture.completedFuture(LLMResponse.success("final answer")); }
        @Override public CompletableFuture<LLMResponse> chatStream(List<LLMMessage> messages, Consumer<String> contentConsumer) {
            contentConsumer.accept("final answer");
            return CompletableFuture.completedFuture(LLMResponse.success("final answer"));
        }
        @Override public CompletableFuture<LLMResponse> chatStreamWithTools(List<LLMMessage> messages, List<LLMTool> tools,
                                                                             Consumer<String> contentConsumer, Consumer<String> thinkingConsumer,
                                                                             Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) {
            contentConsumer.accept("final answer");
            return CompletableFuture.completedFuture(LLMResponse.success("final answer"));
        }
        @Override public CompletableFuture<LLMTestResult> test() { return CompletableFuture.completedFuture(LLMTestResult.success("test", 1)); }
        @Override public String getModelName() { return "test-model"; }
        @Override public void close() {}
    }

    private static class TestFactory extends LLMFactory {
        private final LLMService service;

        TestFactory(LLMService service) {
            super(TestFactoryConfig.config());
            this.service = service;
        }

        @Override
        public LLMService getLLMService() {
            return service;
        }

        @Override
        public LLMQueryEngine createQueryEngine(Session session, ToolRegistry toolRegistry, ToolExecutor toolExecutor, AgentRegistry agentRegistry) {
            return new LLMQueryEngine(session, service, toolExecutor,
                LLMQueryEngine.EngineConfig.defaultConfig(), agentRegistry) {
                @Override
                public CompletableFuture<QueryResult> queryStream(String prompt, Consumer<String> contentConsumer,
                                                                  Consumer<String> thinkingConsumer,
                                                                  Consumer<LLMService.StreamToolCallEvent> toolCallConsumer) {
                    StepCallback callback = getStepCallback();
                    if (callback != null) {
                        callback.onToolCallChunk(new LLMService.StreamToolCallEvent("tc-1", "function", "runTool", "{}", true));
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        callback.onToolResult("runTool", "ok", "tc-1");
                    }
                    contentConsumer.accept("final answer");
                    return CompletableFuture.completedFuture(QueryResult.success(
                        com.jwcode.core.model.Message.createAssistantMessage("final answer")));
                }

                private StepCallback getStepCallback() {
                    try {
                        Field field = LLMQueryEngine.class.getDeclaredField("stepCallback");
                        field.setAccessible(true);
                        return (StepCallback) field.get(this);
                    } catch (Exception e) {
                        return null;
                    }
                }
            };
        }
    }

    private static class TestFactoryConfig {
        static JwcodeConfig config() {
            JwcodeConfig config = new JwcodeConfig();
            config.setDefaultProvider("test");
            JwcodeConfig.ProviderConfig provider = new JwcodeConfig.ProviderConfig();
            provider.setApiType("openai-completions");
            provider.setBaseUrl("http://localhost");
            provider.setApiKeys(List.of("test"));
            JwcodeConfig.ModelConfig model = new JwcodeConfig.ModelConfig();
            model.setId("test-model");
            provider.setModels(List.of(model));
            config.setProviders(java.util.Map.of("test", provider));
            return config;
        }
    }
}
