package com.jwcode.core.llm;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LLM 服务接口 - 统一的大语言模型服务抽象
 */
public interface LLMService {
    
    /**
     * 发送聊天请求
     */
    CompletableFuture<LLMResponse> chat(List<LLMMessage> messages);
    
    /**
     * 发送聊天请求（带工具）
     */
    CompletableFuture<LLMResponse> chatWithTools(List<LLMMessage> messages, List<LLMTool> tools);
    
    /**
     * 发送流式聊天请求
     * 
     * @param messages 消息列表
     * @param contentConsumer 内容消费回调（每收到一块内容就调用）
     * @return 完整的响应结果
     */
    CompletableFuture<LLMResponse> chatStream(
            List<LLMMessage> messages,
            Consumer<String> contentConsumer);
    
    /**
     * 发送流式聊天请求（带工具）
     * 
     * @param messages 消息列表
     * @param tools 工具列表
     * @param contentConsumer 内容消费回调
     * @param thinkingConsumer 思考过程消费回调（可选，用于 reasoning 模型）
     * @param toolCallConsumer 工具调用消费回调
     * @return 完整的响应结果
     */
    CompletableFuture<LLMResponse> chatStreamWithTools(
            List<LLMMessage> messages,
            List<LLMTool> tools,
            Consumer<String> contentConsumer,
            Consumer<String> thinkingConsumer,
            Consumer<StreamToolCallEvent> toolCallConsumer);
    
    /**
     * 测试服务是否可用
     */
    CompletableFuture<LLMTestResult> test();
    
    /**
     * 获取模型名称
     */
    String getModelName();
    
    /**
     * 关闭服务
     */
    void close();
    
    /**
     * 流式工具调用事件
     */
    class StreamToolCallEvent {
        private final String id;
        private final String type;
        private final String name;
        private final String arguments;
        private final boolean isComplete;
        private final int index;
        
        public StreamToolCallEvent(String id, String type, String name, String arguments, boolean isComplete, int index) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.arguments = arguments;
            this.isComplete = isComplete;
            this.index = index;
        }
        
        public StreamToolCallEvent(String id, String type, String name, String arguments, boolean isComplete) {
            this(id, type, name, arguments, isComplete, 0);
        }
        
        public String getId() { return id; }
        public String getType() { return type; }
        public String getName() { return name; }
        public String getArguments() { return arguments; }
        public boolean isComplete() { return isComplete; }
        public int getIndex() { return index; }
        
        @Override
        public String toString() {
            return "StreamToolCallEvent{id='" + id + "', name='" + name + "', isComplete=" + isComplete + ", index=" + index + "}";
        }
    }
}
