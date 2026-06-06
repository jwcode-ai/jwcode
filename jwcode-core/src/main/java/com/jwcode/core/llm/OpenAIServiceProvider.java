package com.jwcode.core.llm;

/**
 * OpenAI 兼容格式的服务提供者
 * 
 * 对应 api-type = "openai-completions"
 * 创建 OpenAILLMService 实例。
 */
public class OpenAIServiceProvider implements ServiceProvider {

    @Override
    public String getApiType() {
        return "openai-completions";
    }

    @Override
    public LLMService createService(ServiceConfig config) {
        return new OpenAILLMService(config);
    }
}
