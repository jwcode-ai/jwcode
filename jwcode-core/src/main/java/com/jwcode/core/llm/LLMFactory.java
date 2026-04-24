package com.jwcode.core.llm;

import com.jwcode.core.config.JwcodeConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * LLM 组件工厂
 * 
 * 用于创建和管理 LLM 相关组件
 */
public class LLMFactory {
    
    private static final Logger logger = Logger.getLogger(LLMFactory.class.getName());
    private static final String DEFAULT_CONFIG_PATH = ".jwcode/config.yaml";
    
    private JwcodeConfig config;
    private LLMService llmService;
    
    public LLMFactory() {
        this(null);
    }
    
    public LLMFactory(JwcodeConfig config) {
        if (config != null) {
            this.config = config;
        } else {
            loadDefaultConfig();
        }
    }
    
    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        try {
            Path configPath = Paths.get(System.getProperty("user.home"), DEFAULT_CONFIG_PATH);
            if (configPath.toFile().exists()) {
                this.config = JwcodeConfig.load(configPath.toString());
                logger.info("[LLMFactory] Loaded config from: " + configPath);
            } else {
                logger.warning("[LLMFactory] Config file not found: " + configPath);
                this.config = createDefaultConfig();
            }
        } catch (Exception e) {
            logger.severe("[LLMFactory] Failed to load config: " + e.getMessage());
            this.config = createDefaultConfig();
        }
    }
    
    /**
     * 创建默认配置
     */
    private JwcodeConfig createDefaultConfig() {
        logger.info("[LLMFactory] Creating default config");
        
        JwcodeConfig config = new JwcodeConfig();
        config.setDefaultProvider("moonshot");
        
        // 创建默认 provider
        JwcodeConfig.ProviderConfig provider = new JwcodeConfig.ProviderConfig();
        provider.setBaseUrl("https://api.moonshot.cn/v1");
        provider.setApiKeys(java.util.Collections.singletonList("your-api-key-here"));
        
        // 创建默认模型
        JwcodeConfig.ModelConfig model = new JwcodeConfig.ModelConfig();
        model.setId("kimi-k2.5");
        model.setTemperature(1.0);  // kimi-k2.5 要求 temperature 必须为 1
        model.setMaxTokens(4096);
        
        provider.setModels(java.util.Collections.singletonList(model));
        config.setProviders(java.util.Map.of("moonshot", provider));
        
        return config;
    }
    
    /**
     * 获取或创建 LLMService
     */
    public synchronized LLMService getLLMService() {
        if (llmService == null) {
            OpenAILLMService.ServiceConfig serviceConfig = createServiceConfig(config);
            llmService = new OpenAILLMService(serviceConfig);
        }
        return llmService;
    }
    
    /**
     * 从 JwcodeConfig 创建 ServiceConfig
     */
    private OpenAILLMService.ServiceConfig createServiceConfig(JwcodeConfig config) {
        JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
        JwcodeConfig.ModelDefinition model = config.getDefaultModel();
        
        if (provider == null) {
            throw new IllegalStateException("No provider configured");
        }
        
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.moonshot.cn/v1";
        }
        
        // 清理 URL 末尾的斜杠
        baseUrl = baseUrl.replaceAll("/+$", "");
        
        // 对于 moonshot，确保 URL 格式正确
        if (baseUrl.contains("moonshot.cn") && !baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl + "/v1";
        }
        
        String modelId = model != null ? model.getId() : "kimi-k2.5";
        
        // 使用更长的超时时间（300秒=5分钟）以支持复杂任务
        return OpenAILLMService.ServiceConfig.builder()
            .baseUrl(baseUrl)
            .model(modelId)
            .apiKeys(provider.getApiKeys())
            .temperature(model != null ? model.getTemperature() : null)
            .maxTokens(model != null ? model.getMaxTokens() : 4096)
            .timeoutSeconds(300)
            .build();
    }
    
    /**
     * 创建新的 LLMQueryEngine
     */
    public LLMQueryEngine createQueryEngine(com.jwcode.core.session.Session session) {
        LLMQueryEngine.EngineConfig engineConfig = LLMQueryEngine.EngineConfig.fromJwcodeConfig(config);
        // 根据模型特性自动调整空回复阈值等参数
        String modelId = session.getModel();
        if (modelId == null || modelId.isEmpty()) {
            modelId = config != null && config.getDefaultModel() != null ? config.getDefaultModel().getId() : null;
        }
        engineConfig.applyModelTraits(modelId);
        
        return LLMQueryEngine.builder()
            .session(session)
            .llmService(getLLMService())
            .config(engineConfig)
            .build();
    }
    
    /**
     * 创建新的 LLMQueryEngine，带完整配置
     */
    public LLMQueryEngine createQueryEngine(
            com.jwcode.core.session.Session session,
            com.jwcode.core.tool.ToolRegistry toolRegistry,
            com.jwcode.core.tool.ToolExecutor toolExecutor) {
        return LLMQueryEngine.builder()
            .session(session)
            .llmService(getLLMService())
            .toolRegistry(toolRegistry)
            .toolExecutor(toolExecutor)
            .build();
    }
    
    /**
     * 获取配置
     */
    public JwcodeConfig getConfig() {
        return config;
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfig(String configPath) {
        try {
            this.config = JwcodeConfig.load(configPath);
            this.llmService = null;  // 重置服务，下次获取时重新创建
            logger.info("[LLMFactory] Config reloaded from: " + configPath);
        } catch (Exception e) {
            logger.severe("[LLMFactory] Failed to reload config: " + e.getMessage());
            throw new RuntimeException("Failed to reload config", e);
        }
    }
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 快速创建工厂（使用默认配置）
     */
    public static LLMFactory createDefault() {
        return new LLMFactory();
    }
    
    /**
     * 从配置文件创建工厂
     */
    public static LLMFactory fromConfig(String configPath) {
        try {
            JwcodeConfig config = JwcodeConfig.load(configPath);
            return new LLMFactory(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config from: " + configPath, e);
        }
    }
    
    /**
     * 从配置对象创建工厂
     */
    public static LLMFactory fromConfig(JwcodeConfig config) {
        return new LLMFactory(config);
    }
}
