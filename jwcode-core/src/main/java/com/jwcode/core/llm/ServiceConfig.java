package com.jwcode.core.llm;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * LLM 服务配置
 * 
 * 支持 OpenAI 和 Anthropic 格式的通用配置。
 * - apiType: "openai-completions" 或 "anthropic-messages"
 * - anthropicVersion: Anthropic API 版本（默认 "2023-06-01"）
 */
@Data
@Builder
public class ServiceConfig {
    private String baseUrl;
    private String model;
    private List<String> apiKeys;
    private Double temperature;
    private Integer maxTokens;
    @Builder.Default private int timeoutSeconds = 300;
    @Builder.Default private int contextWindow = 1000000;
    @Builder.Default private String apiType = "openai-completions";
    @Builder.Default private String anthropicVersion = "2023-06-01";

    public ServiceConfig() {}

    public ServiceConfig(String baseUrl, String model, List<String> apiKeys,
                         Double temperature, Integer maxTokens,
                         int timeoutSeconds, int contextWindow,
                         String apiType, String anthropicVersion) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKeys = apiKeys;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.contextWindow = contextWindow;
        this.apiType = apiType != null ? apiType : "openai-completions";
        this.anthropicVersion = anthropicVersion != null ? anthropicVersion : "2023-06-01";
    }
}

