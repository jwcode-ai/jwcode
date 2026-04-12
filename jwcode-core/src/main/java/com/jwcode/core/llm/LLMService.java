package com.jwcode.core.llm;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
}
