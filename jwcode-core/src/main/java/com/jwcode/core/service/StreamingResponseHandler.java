package com.jwcode.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 流式响应处理器 - 处理 SSE (Server-Sent Events) 格式的流式响应
 * 
 * 参照 Kimi Code 的流式响应处理
 */
public class StreamingResponseHandler {
    
    private static final Logger logger = Logger.getLogger(StreamingResponseHandler.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Consumer<String> onContent;
    private final Consumer<String> onThinking;
    private final Consumer<ToolCallEvent> onToolCall;
    private final Runnable onComplete;
    private final Consumer<Throwable> onError;
    
    private StringBuilder contentBuffer = new StringBuilder();
    private StringBuilder thinkingBuffer = new StringBuilder();
    private boolean isThinking = false;
    
    public StreamingResponseHandler(
            Consumer<String> onContent,
            Consumer<String> onThinking,
            Consumer<ToolCallEvent> onToolCall,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        this.onContent = onContent;
        this.onThinking = onThinking;
        this.onToolCall = onToolCall;
        this.onComplete = onComplete;
        this.onError = onError;
    }
    
    /**
     * 处理流式输入
     */
    public void processStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line);
            }
            
            // 流结束，通知完成
            if (onComplete != null) {
                onComplete.run();
            }
            
        } catch (IOException e) {
            logger.severe("读取流失败: " + e.getMessage());
            if (onError != null) {
                onError.accept(e);
            }
        }
    }
    
    /**
     * 处理单行数据
     */
    private void processLine(String line) {
        // SSE 格式: data: {...}
        if (line.startsWith("data: ")) {
            String data = line.substring(6).trim();
            
            // 流结束标记
            if ("[DONE]".equals(data)) {
                return;
            }
            
            try {
                JsonNode json = objectMapper.readTree(data);
                processEvent(json);
            } catch (Exception e) {
                logger.fine("解析事件失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理 JSON 事件
     */
    private void processEvent(JsonNode json) {
        // 提取内容增量
        JsonNode choices = json.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
            return;
        }
        
        JsonNode choice = choices.get(0);
        JsonNode delta = choice.get("delta");
        
        if (delta == null) {
            return;
        }
        
        // 处理思考过程 (<think> 标签)
        JsonNode reasoningContent = delta.get("reasoning_content");
        if (reasoningContent != null && !reasoningContent.isNull()) {
            String thinking = reasoningContent.asText();
            thinkingBuffer.append(thinking);
            if (onThinking != null) {
                onThinking.accept(thinking);
            }
            return;
        }
        
        // 处理普通内容
        JsonNode content = delta.get("content");
        if (content != null && !content.isNull()) {
            String text = content.asText();
            contentBuffer.append(text);
            if (onContent != null) {
                onContent.accept(text);
            }
        }
        
        // 处理工具调用
        JsonNode toolCalls = delta.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
            processToolCalls(toolCalls);
        }
    }
    
    /**
     * 处理工具调用事件
     */
    private void processToolCalls(JsonNode toolCalls) {
        for (JsonNode toolCall : toolCalls) {
            String id = toolCall.has("id") ? toolCall.get("id").asText() : null;
            String type = toolCall.has("type") ? toolCall.get("type").asText() : "function";
            
            JsonNode function = toolCall.get("function");
            if (function != null) {
                String name = function.has("name") ? function.get("name").asText() : null;
                String arguments = function.has("arguments") ? function.get("arguments").asText() : null;
                
                if (onToolCall != null && name != null) {
                    onToolCall.accept(new ToolCallEvent(id, type, name, arguments));
                }
            }
        }
    }
    
    /**
     * 获取完整内容
     */
    public String getFullContent() {
        return contentBuffer.toString();
    }
    
    /**
     * 获取完整思考过程
     */
    public String getFullThinking() {
        return thinkingBuffer.toString();
    }
    
    /**
     * 工具调用事件
     */
    public static class ToolCallEvent {
        private final String id;
        private final String type;
        private final String name;
        private final String arguments;
        
        public ToolCallEvent(String id, String type, String name, String arguments) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.arguments = arguments;
        }
        
        public String getId() { return id; }
        public String getType() { return type; }
        public String getName() { return name; }
        public String getArguments() { return arguments; }
    }
}
