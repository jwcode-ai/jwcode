package com.jwcode.core.llm;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.llm.route.RouteRegistry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * LLM 组件工厂
 * 用于创建和管理 LLM 相关组件。
 * 支持通过 ServiceRegistry 路由到不同的服务实现。
 */
public class LLMFactory {
    
    private static final Logger logger = Logger.getLogger(LLMFactory.class.getName());
    private static final String DEFAULT_CONFIG_PATH = ".jwcode/config.yaml";
    
    private final ServiceRegistry registry;
    private final RouteRegistry routeRegistry;
    private JwcodeConfig config;
    private LLMService llmService;

    private com.jwcode.core.agent.CompactorAgent sharedCompactorAgent;
    private com.jwcode.core.agent.BudgetExhaustedHandler sharedBudgetHandler;
    private ModelRouter modelRouter;
    
    public LLMFactory() {
        this(null);
    }
    
    public LLMFactory(JwcodeConfig config) {
        this.registry = createDefaultRegistry();
        this.routeRegistry = new RouteRegistry();
        if (config != null) {
            this.config = config;
        } else {
            loadDefaultConfig();
        }
        this.modelRouter = new ModelRouter(this.config);
        initRouteRegistry();
    }
    
    private ServiceRegistry createDefaultRegistry() {
        ServiceRegistry reg = new ServiceRegistry();
        reg.register(new OpenAIServiceProvider());
        reg.register(new AnthropicServiceProvider());
        logger.info("[LLMFactory] Initialized ServiceRegistry with default providers");
        return reg;
    }
    
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
    
    private JwcodeConfig createDefaultConfig() {
        logger.info("[LLMFactory] Creating default config");
        JwcodeConfig config = new JwcodeConfig();
        config.setDefaultProvider("moonshot");
        JwcodeConfig.ProviderConfig provider = new JwcodeConfig.ProviderConfig();
        provider.setBaseUrl("https://api.moonshot.cn/v1");
        provider.setApiType("openai-completions");
        provider.setApiKeys(new java.util.ArrayList<>(java.util.Collections.singletonList("your-api-key-here")));
        JwcodeConfig.ModelConfig model = new JwcodeConfig.ModelConfig();
        model.setId("kimi-k2.5");
        model.setTemperature(1.0);
        model.setMaxTokens(null);
        provider.setModels(java.util.Collections.singletonList(model));
        config.setProviders(new java.util.HashMap<>(java.util.Map.of("moonshot", provider)));
        return config;
    }
    
    public synchronized LLMService getLLMService() {
        if (llmService == null) {
            LLMService primary = createLLMService(config.getDefaultProviderName());
            String fallbackName = config.getFallbackProvider();
            if (fallbackName != null && !fallbackName.isEmpty() && !fallbackName.equals(config.getDefaultProviderName())) {
                JwcodeConfig.ProviderConfig fallbackProvider = config.getProvider(fallbackName);
                if (fallbackProvider != null) {
                    LLMService fallback = createLLMService(fallbackName);
                    llmService = new FallbackLLMService(primary, config.getDefaultProviderName(), fallback, fallbackName);
                    logger.info("[LLMFactory] Created FallbackLLMService with primary=" + config.getDefaultProviderName() + ", fallback=" + fallbackName);
                } else {
                    logger.warning("[LLMFactory] Fallback provider not found. Using primary only.");
                    llmService = primary;
                }
            } else {
                llmService = primary;
            }
        }
        return llmService;
    }
    
    private LLMService createLLMService(String providerName) {
        JwcodeConfig.ProviderConfig provider = config.getProvider(providerName);
        if (provider == null) {
            throw new IllegalStateException("Provider not found: " + providerName);
        }
        String apiType = provider.getApiType();
        logger.info("[LLMFactory] createLLMService: providerName=" + providerName
            + ", apiType=" + apiType
            + ", baseUrl=" + provider.getBaseUrl()
            + ", defaultProviderName=" + config.getDefaultProviderName());
        ServiceConfig serviceConfig = createServiceConfig(providerName, provider);
        LLMService svc = registry.createService(apiType, serviceConfig);
        logger.info("[LLMFactory] Created service: " + svc.getClass().getSimpleName()
            + " for apiType=" + apiType);
        return svc;
    }
    
    private ServiceConfig createServiceConfig(String providerName, JwcodeConfig.ProviderConfig provider) {
        if (provider == null) {
            throw new IllegalStateException("No provider configured: " + providerName);
        }
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com/v1";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");

        // 根据 apiType 进行 URL 规范化，避免后缀重复拼接
        String apiType = provider.getApiType();
        if ("anthropic-messages".equals(apiType)) {
            // AnthropicLLMService.buildRequestUri() 自动追加 /v1/messages
            // 防止用户配置了带后缀的 baseUrl 导致 /v1/v1/messages 这样的双拼接
            if (baseUrl.endsWith("/v1")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
            }
        } else {
            // openai-completions (default): OpenAILLMService.normalizeUrl 会处理
            // 对 moonshot.cn 保留向后兼容
            if (baseUrl.contains("moonshot.cn") && !baseUrl.endsWith("/v1")) {
                baseUrl = baseUrl + "/v1";
            }
        }

        JwcodeConfig.ModelDefinition model = null;
        if (!provider.getModels().isEmpty()) {
            model = provider.getModels().get(0);
        }
        if (model == null) {
            model = config.getDefaultModel();
        }
        String modelId = model != null ? model.getId() : "kimi-k2.5";
        int timeoutSeconds = 300;
        if (modelId.toLowerCase().contains("deepseek-v4") || modelId.toLowerCase().contains("deepseek-r1")) {
            timeoutSeconds = 900;
        }
        return ServiceConfig.builder()
            .baseUrl(baseUrl)
            .model(modelId)
            .apiKeys(provider.getApiKeys())
            .temperature(model != null ? model.getTemperature() : null)
            .maxTokens(model != null ? model.getMaxTokens() : null)
            .timeoutSeconds(timeoutSeconds)
            .contextWindow(model != null ? model.getContextWindow() : 1000000)
            .apiType(apiType)
            .anthropicVersion(provider.getAnthropicVersion())
            .build();
    }
    
    public LLMQueryEngine createQueryEngine(com.jwcode.core.session.Session session) {
        LLMQueryEngine.EngineConfig engineConfig = LLMQueryEngine.EngineConfig.fromJwcodeConfig(config);
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
    
    public LLMQueryEngine createQueryEngine(
            com.jwcode.core.session.Session session,
            com.jwcode.core.tool.ToolRegistry toolRegistry,
            com.jwcode.core.tool.ToolExecutor toolExecutor) {
        LLMQueryEngine.EngineConfig engineConfig = LLMQueryEngine.EngineConfig.fromJwcodeConfig(config);
        return LLMQueryEngine.builder()
            .session(session)
            .llmService(getLLMService())
            .toolRegistry(toolRegistry)
            .toolExecutor(toolExecutor)
            .config(engineConfig)
            .build();
    }
    
    public LLMQueryEngine createQueryEngine(
            com.jwcode.core.session.Session session,
            com.jwcode.core.tool.ToolRegistry toolRegistry,
            com.jwcode.core.tool.ToolExecutor toolExecutor,
            com.jwcode.core.agent.AgentRegistry agentRegistry) {
        LLMQueryEngine.EngineConfig engineConfig = LLMQueryEngine.EngineConfig.fromJwcodeConfig(config);
        String modelId = session.getModel();
        if (modelId == null || modelId.isEmpty()) {
            modelId = config != null && config.getDefaultModel() != null ? config.getDefaultModel().getId() : null;
        }
        engineConfig.applyModelTraits(modelId);
        return LLMQueryEngine.builder()
            .session(session)
            .llmService(getLLMService())
            .toolRegistry(toolRegistry)
            .toolExecutor(toolExecutor)
            .config(engineConfig)
            .agentRegistry(agentRegistry)
            .build();
    }
    
    private String compactionModelId;
    
    public void setCompactionModelId(String modelId) {
        this.compactionModelId = modelId;
        if (sharedCompactorAgent != null) {
            sharedCompactorAgent.setCompactionModel(modelId);
        }
    }
    
    public String getCompactionModelId() {
        return compactionModelId;
    }
    
    public synchronized com.jwcode.core.agent.CompactorAgent getSharedCompactorAgent() {
        if (sharedCompactorAgent == null) {
            sharedCompactorAgent = new com.jwcode.core.agent.CompactorAgent(
                getLLMService(), new com.jwcode.core.service.SimpleCompactionStrategy(getLLMService()));
            if (compactionModelId != null) {
                sharedCompactorAgent.setCompactionModel(compactionModelId);
            }
            logger.info("[LLMFactory] Created shared CompactorAgent"
                + (compactionModelId != null ? " (compaction model: " + compactionModelId + ")" : ""));
        }
        return sharedCompactorAgent;
    }
    
    public synchronized com.jwcode.core.agent.BudgetExhaustedHandler getSharedBudgetHandler() {
        if (sharedBudgetHandler == null) {
            sharedBudgetHandler = com.jwcode.core.agent.BudgetExhaustedHandler.createDefault();
            logger.info("[LLMFactory] Created shared BudgetExhaustedHandler");
        }
        return sharedBudgetHandler;
    }
    
    public JwcodeConfig getConfig() { return config; }
    public ModelRouter getModelRouter() { return modelRouter; }
    public RouteRegistry getRouteRegistry() { return routeRegistry; }

    /**
     * 从配置初始化 RouteRegistry：为每个 provider 注册路由和模型映射。
     */
    private void initRouteRegistry() {
        if (config == null || config.getAllProviders() == null) return;
        int count = 0;
        for (var entry : config.getAllProviders().entrySet()) {
            String name = entry.getKey();
            JwcodeConfig.ProviderConfig provider = entry.getValue();
            ServiceConfig serviceConfig = createServiceConfig(name, provider);
            routeRegistry.registerFromConfig(name, serviceConfig);
            count++;
        }
        logger.info("[LLMFactory] RouteRegistry initialized with " + count + " provider routes");
    }

    private static volatile LLMFactory globalInstance;
    
    public static void setGlobalInstance(LLMFactory factory) { globalInstance = factory; }
    public static LLMFactory getGlobalInstance() { return globalInstance; }
    
    public synchronized void switchModel(String modelId) {
        if (config == null || config.getDefaultProvider() == null) {
            logger.warning("[LLMFactory] Cannot switch model: no config loaded");
            return;
        }
        JwcodeConfig.ProviderConfig provider = config.getDefaultProvider();
        if (provider.getModels().isEmpty()) {
            JwcodeConfig.ModelDefinition newModel = new JwcodeConfig.ModelDefinition();
            newModel.setId(modelId);
            provider.setModels(java.util.Collections.singletonList(newModel));
        } else {
            provider.getModels().get(0).setId(modelId);
        }
        this.llmService = null;
        logger.info("[LLMFactory] Model switched to: " + modelId + " (service will be recreated on next query)");
        setGlobalInstance(this);
    }
    
    public void reloadConfig(String configPath) {
        try {
            this.config = JwcodeConfig.load(configPath);
            this.llmService = null;
            logger.info("[LLMFactory] Config reloaded from: " + configPath);
        } catch (Exception e) {
            logger.severe("[LLMFactory] Failed to reload config: " + e.getMessage());
            throw new RuntimeException("Failed to reload config", e);
        }
    }
    
    public static LLMFactory createDefault() {
        return new LLMFactory();
    }
    
    public static LLMFactory fromConfig(String configPath) {
        try {
            JwcodeConfig config = JwcodeConfig.load(configPath);
            return new LLMFactory(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config from: " + configPath, e);
        }
    }
    
    public static LLMFactory fromConfig(JwcodeConfig config) {
        return new LLMFactory(config);
    }
}
