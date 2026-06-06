package com.jwcode.core.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * LLM 服务注册表
 * 
 * 根据 apiType 将请求路由到对应的 ServiceProvider。
 * 支持运行时注册新的 Provider。
 */
public class ServiceRegistry {
    
    private static final Logger logger = Logger.getLogger(ServiceRegistry.class.getName());
    
    private final Map<String, ServiceProvider> providers = new HashMap<>();
    
    /**
     * 注册一个服务提供者
     */
    public void register(ServiceProvider provider) {
        String apiType = provider.getApiType();
        if (providers.containsKey(apiType)) {
            logger.warning("[ServiceRegistry] Overwriting existing provider for type: " + apiType);
        }
        providers.put(apiType, provider);
        logger.info("[ServiceRegistry] Registered provider: " + apiType);
    }
    
    /**
     * 根据 apiType 创建 LLM 服务
     * 
     * @param apiType  API 类型（如 "openai-completions"、"anthropic-messages"）
     * @param config   服务配置
     * @return LLM 服务实例
     * @throws IllegalArgumentException 如果 apiType 未注册
     */
    public LLMService createService(String apiType, ServiceConfig config) {
        ServiceProvider provider = providers.get(apiType);
        if (provider == null) {
            throw new IllegalArgumentException(
                "Unsupported API type: " + apiType + 
                ". Supported types: " + getSupportedTypes());
        }
        logger.info("[ServiceRegistry] Creating service for apiType=" + apiType + 
                    ", model=" + config.getModel() + 
                    ", baseUrl=" + config.getBaseUrl());
        return provider.createService(config);
    }
    
    /**
     * 获取所有已注册的 API 类型列表
     */
    public List<String> getSupportedTypes() {
        return new ArrayList<>(providers.keySet());
    }
    
    /**
     * 检查指定类型是否已注册
     */
    public boolean hasProvider(String apiType) {
        return providers.containsKey(apiType);
    }
}

