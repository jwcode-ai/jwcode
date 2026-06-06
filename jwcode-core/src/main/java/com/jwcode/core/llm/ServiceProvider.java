package com.jwcode.core.llm;

/**
 * LLM 服务提供者接口
 * 
 * 每个服务提供者对应一种 API 协议格式。
 * - "openai-completions" → OpenAIServiceProvider
 * - "anthropic-messages" → AnthropicServiceProvider
 */
public interface ServiceProvider {
    
    /**
     * 获取 API 类型标识符
     */
    String getApiType();
    
    /**
     * 根据配置创建 LLM 服务实例
     */
    LLMService createService(ServiceConfig config);
}

