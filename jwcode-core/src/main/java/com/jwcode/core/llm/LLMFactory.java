package com.jwcode.core.llm;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.JwcodeConfig.ModelDefinition;
import com.jwcode.core.config.JwcodeConfig.ModelRefParts;
import com.jwcode.core.config.JwcodeConfig.ProviderConfig;
import com.jwcode.core.llm.route.RouteRegistry;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * LLM 组件工厂
 * 用于创建和管理 LLM 相关组件。
 * 支持通过 ServiceRegistry 路由到不同的服务实现。
 *
 * v3.2 增强：支持按 ModelRef 缓存 LLMService，
 * 每个 ModelRef (provider:modelId) 可独立创建并缓存 LLMService 实例。
 */
public class LLMFactory {

    private static final Logger logger = Logger.getLogger(LLMFactory.class.getName());
    private static final String DEFAULT_CONFIG_PATH = ".jwcode/config.yaml";

    private final ServiceRegistry registry;
    private final RouteRegistry routeRegistry;
    private JwcodeConfig config;

    // 向后兼容：单个 LLMService 缓存（默认模型）
    private LLMService llmService;

    // v3.2 增强：按 ModelRef 缓存 LLMService
    private final ConcurrentHashMap<String, LLMService> modelServiceCache = new ConcurrentHashMap<>();

    // 模型解析器
    private ModelResolver modelResolver;

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
        this.config.ensureDefaultsInitialized();
        this.modelResolver = new ModelResolver(this.config);
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
        ProviderConfig provider = new ProviderConfig();
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

    /**
     * 获取默认模型的 LLMService（向后兼容）
     * 使用 default-models.global 指定的全局默认模型。
     * 如果未配置，回退到 default-provider 的第一个启用模型。
     */
    public synchronized LLMService getLLMService() {
        String globalRef = config.getDefaultModelRef("global");
        if (globalRef != null) {
            return getLLMService(globalRef);
        }
        // 极简回退：旧配置完全无默认模型时
        if (llmService == null) {
            llmService = createLLMService(config.getDefaultProviderName());
        }
        return llmService;
    }

    /**
     * 按 ModelRef 获取 LLMService
     * 格式: "provider:modelId"
     * 每个 ModelRef 独立缓存。
     */
    public synchronized LLMService getLLMService(String modelRef) {
        if (modelRef == null || modelRef.isEmpty()) {
            return getLLMService();
        }

        String globalRef = config.getDefaultModelRef("global");

        // 如果是全局默认模型，使用专用缓存（兼容 FallbackLLMService 包装）
        if (globalRef != null && modelRef.equals(globalRef)) {
            if (llmService == null) {
                LLMService primary = getLLMServiceByRef(modelRef);
                String fallbackName = config.getFallbackProvider();
                if (fallbackName != null && !fallbackName.isEmpty()
                    && !fallbackName.equals(config.getDefaultProviderName())) {
                    ProviderConfig fallbackProvider = config.getProvider(fallbackName);
                    if (fallbackProvider != null) {
                        String fallbackRef = fallbackName + ":" + (fallbackProvider.getModels().isEmpty()
                            ? "unknown" : fallbackProvider.getModels().get(0).getId());
                        LLMService fallback = getLLMServiceByRef(fallbackRef);
                        llmService = new FallbackLLMService(primary, modelRef, fallback, fallbackRef);
                        logger.info("[LLMFactory] Created FallbackLLMService with primary=" + modelRef
                            + ", fallback=" + fallbackRef);
                    } else {
                        llmService = primary;
                    }
                } else {
                    llmService = primary;
                }
            }
            return llmService;
        }

        return modelServiceCache.computeIfAbsent(modelRef, ref -> getLLMServiceByRef(ref));
    }

    /**
     * 直接按 ModelRef 创建 LLMService（无缓存，无关闭包装）
     */
    private LLMService getLLMServiceByRef(String modelRef) {
        ModelRefParts parts = JwcodeConfig.parseModelRef(modelRef);
        if (parts == null) {
            logger.warning("[LLMFactory] Invalid modelRef: " + modelRef + ", falling back to default");
            return getLLMService();
        }
        ProviderConfig provider = config.getProvider(parts.getProvider());
        if (provider == null) {
            logger.warning("[LLMFactory] Provider not found for modelRef: " + modelRef + ", falling back to default");
            return getLLMService();
        }
        ServiceConfig serviceConfig = createServiceConfig(parts.getProvider(), provider, parts.getModelId());
        LLMService svc = registry.createService(provider.getApiType(), serviceConfig);
        logger.info("[LLMFactory] Created LLMService for modelRef=" + modelRef
            + " (" + svc.getClass().getSimpleName() + ")");
        return svc;
    }

    /**
     * 按 ModelRef 获取 LLMService（已包含模型参数）
     */
    public synchronized LLMService getLLMServiceForModel(String modelRef) {
        return getLLMService(modelRef);
    }

    /**
     * 获取 ModelResolver
     */
    public ModelResolver getModelResolver() {
        return modelResolver;
    }

    private LLMService createLLMService(String providerName) {
        ProviderConfig provider = config.getProvider(providerName);
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

    /**
     * 根据 Provider 配置创建 ServiceConfig（使用 provider 第一个模型）
     */
    private ServiceConfig createServiceConfig(String providerName, ProviderConfig provider) {
        return createServiceConfig(providerName, provider, null);
    }

    /**
     * 根据 Provider 配置和指定模型 ID 创建 ServiceConfig
     */
    private ServiceConfig createServiceConfig(String providerName, ProviderConfig provider, String modelId) {
        if (provider == null) {
            throw new IllegalStateException("No provider configured: " + providerName);
        }
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com/v1";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");

        // 根据 apiType 进行 URL 规范化
        String apiType = provider.getApiType();
        if ("anthropic-messages".equals(apiType)) {
            if (baseUrl.endsWith("/v1")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
            }
        } else {
            if (baseUrl.contains("moonshot.cn") && !baseUrl.endsWith("/v1")) {
                baseUrl = baseUrl + "/v1";
            }
        }

        ModelDefinition model = null;
        if (modelId != null) {
            // 查找指定模型
            model = provider.findModel(modelId).orElse(null);
        }
        if (model == null && !provider.getModels().isEmpty()) {
            model = provider.getModels().get(0);
        }
        if (model == null) {
            model = config.getDefaultModel();
        }
        String resolvedModelId = model != null ? model.getId() : (modelId != null ? modelId : "kimi-k2.5");
        int timeoutSeconds = 300;
        if (resolvedModelId != null && (resolvedModelId.toLowerCase().contains("deepseek-v4") || resolvedModelId.toLowerCase().contains("deepseek-r1"))) {
            timeoutSeconds = 900;
        }
        return ServiceConfig.builder()
            .baseUrl(baseUrl)
            .model(resolvedModelId)
            .apiKeys(provider.getApiKeys())
            .temperature(model != null ? model.getTemperature() : null)
            .maxTokens(model != null ? model.getMaxTokens() : null)
            .timeoutSeconds(timeoutSeconds)
            .contextWindow(model != null ? model.getContextWindow() : 1000000)
            .apiType(apiType)
            .anthropicVersion(provider.getAnthropicVersion())
            .build();
    }

    // ==================== QueryEngine 工厂方法 ====================

    /**
     * 创建 QueryEngine（使用默认模型）
     */
    public LLMQueryEngine createQueryEngine(com.jwcode.core.session.Session session) {
        return createQueryEngine(session, null);
    }

    /**
     * 创建 QueryEngine，可选指定 ModelRef
     *
     * @param session  会话
     * @param modelRef 可选的模型引用 "provider:modelId"，null 则使用默认模型
     */
    public LLMQueryEngine createQueryEngine(com.jwcode.core.session.Session session, String modelRef) {
        LLMQueryEngine.EngineConfig engineConfig = LLMQueryEngine.EngineConfig.fromJwcodeConfig(config);
        String modelId = session.getModel();
        if (modelId == null || modelId.isEmpty()) {
            if (modelRef != null) {
                ModelRefParts parts = JwcodeConfig.parseModelRef(modelRef);
                if (parts != null) {
                    modelId = parts.getModelId();
                }
            }
            if (modelId == null || modelId.isEmpty()) {
                modelId = config != null && config.getDefaultModel() != null ? config.getDefaultModel().getId() : null;
            }
        }
        engineConfig.applyModelTraits(modelId);
        LLMService service = (modelRef != null) ? getLLMServiceForModel(modelRef) : getLLMService();
        return LLMQueryEngine.builder()
            .session(session)
            .llmService(service)
            .config(engineConfig)
            .build();
    }

    /**
     * 创建 QueryEngine（带工具注册表和执行器）
     */
    public LLMQueryEngine createQueryEngine(
            com.jwcode.core.session.Session session,
            com.jwcode.core.tool.ToolRegistry toolRegistry,
            com.jwcode.core.tool.ToolExecutor toolExecutor) {
        return createQueryEngine(session, toolRegistry, toolExecutor, null, null);
    }

    /**
     * 创建 QueryEngine（带工具注册表、执行器和 Agent 注册表）
     */
    public LLMQueryEngine createQueryEngine(
            com.jwcode.core.session.Session session,
            com.jwcode.core.tool.ToolRegistry toolRegistry,
            com.jwcode.core.tool.ToolExecutor toolExecutor,
            com.jwcode.core.agent.AgentRegistry agentRegistry) {
        return createQueryEngine(session, toolRegistry, toolExecutor, agentRegistry, null);
    }

    /**
     * 创建 QueryEngine（完整参数，支持指定 ModelRef）
     *
     * @param session    会话
     * @param toolRegistry  工具注册表
     * @param toolExecutor  工具执行器
     * @param agentRegistry Agent 注册表
     * @param modelRef      可选模型引用，null 则使用默认模型
     */
    public LLMQueryEngine createQueryEngine(
            com.jwcode.core.session.Session session,
            com.jwcode.core.tool.ToolRegistry toolRegistry,
            com.jwcode.core.tool.ToolExecutor toolExecutor,
            com.jwcode.core.agent.AgentRegistry agentRegistry,
            String modelRef) {
        LLMQueryEngine.EngineConfig engineConfig = LLMQueryEngine.EngineConfig.fromJwcodeConfig(config);
        String modelId = session.getModel();
        if (modelId == null || modelId.isEmpty()) {
            if (modelRef != null) {
                ModelRefParts parts = JwcodeConfig.parseModelRef(modelRef);
                if (parts != null) {
                    modelId = parts.getModelId();
                }
            }
            if (modelId == null || modelId.isEmpty()) {
                modelId = config != null && config.getDefaultModel() != null ? config.getDefaultModel().getId() : null;
            }
        }
        engineConfig.applyModelTraits(modelId);
        LLMService service = (modelRef != null) ? getLLMServiceForModel(modelRef) : getLLMService();
        return LLMQueryEngine.builder()
            .session(session)
            .llmService(service)
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
     * 从配置初始化 RouteRegistry
     */
    private void initRouteRegistry() {
        if (config == null || config.getAllProviders() == null) return;
        int count = 0;
        for (var entry : config.getAllProviders().entrySet()) {
            String name = entry.getKey();
            ProviderConfig provider = entry.getValue();
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
        ProviderConfig provider = config.getDefaultProvider();
        if (provider.getModels().isEmpty()) {
            JwcodeConfig.ModelDefinition newModel = new JwcodeConfig.ModelDefinition();
            newModel.setId(modelId);
            provider.setModels(java.util.Collections.singletonList(newModel));
        } else {
            provider.getModels().get(0).setId(modelId);
        }
        this.llmService = null;
        this.modelServiceCache.clear();
        logger.info("[LLMFactory] Model switched to: " + modelId + " (service will be recreated on next query)");
        setGlobalInstance(this);
    }

    /**
     * 重新加载配置
     * 刷新所有缓存和路由信息。
     */
    public void reloadConfig(String configPath) {
        try {
            this.config = JwcodeConfig.load(configPath);
            this.config.ensureDefaultsInitialized();
            // 清除所有 LLMService 缓存
            this.llmService = null;
            this.modelServiceCache.clear();
            // 刷新模型解析器
            this.modelResolver = new ModelResolver(this.config);
            // 重建路由注册表（provider/model 列表可能变了）
            this.routeRegistry.clear();
            initRouteRegistry();
            // 刷新模型路由器
            this.modelRouter = new ModelRouter(this.config);
            logger.info("[LLMFactory] Config reloaded from: " + configPath
                + " — cache cleared, routes refreshed");
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
