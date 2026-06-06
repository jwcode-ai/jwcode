package com.jwcode.core.llm;

public class AnthropicServiceProvider implements ServiceProvider {
    @Override
    public String getApiType() {
        return "anthropic-messages";
    }
    @Override
    public LLMService createService(ServiceConfig config) {
        return new AnthropicLLMService(config);
    }
}
