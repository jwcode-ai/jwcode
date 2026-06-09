package com.jwcode.core.search;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 搜索引擎工厂
 * 
 * 根据配置创建和管理搜索引擎实例
 * 支持引擎切换和配置管理
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SearchEngineFactory {
    
    private static final Logger logger = Logger.getLogger(SearchEngineFactory.class.getName());
    
    // 引擎实例缓存
    private final Map<String, SearchEngine> engines = new ConcurrentHashMap<>();
    
    // 默认引擎名称
    private String defaultEngine = "bing";
    
    // 引擎配置
    private final Map<String, SearchEngine.SearchEngineConfig> engineConfigs = new ConcurrentHashMap<>();
    
    // 单例实例
    private static final SearchEngineFactory INSTANCE = new SearchEngineFactory();
    
    private SearchEngineFactory() {
        // 私有构造函数，注册默认引擎
        registerDefaultEngines();
    }
    
    /**
     * 获取工厂单例实例
     */
    public static SearchEngineFactory getInstance() {
        return INSTANCE;
    }
    
    /**
     * 创建新的工厂实例（用于测试或特殊场景）
     */
    public static SearchEngineFactory createNew() {
        return new SearchEngineFactory();
    }
    
    /**
     * 获取搜索引擎
     * 
     * @param engineName 引擎名称
     * @return 搜索引擎实例
     */
    public SearchEngine getEngine(String engineName) {
        if (engineName == null || engineName.isEmpty()) {
            engineName = defaultEngine;
        }
        
        String normalizedName = normalizeEngineName(engineName);
        
        // 从缓存获取
        SearchEngine engine = engines.get(normalizedName);
        if (engine != null) {
            return engine;
        }
        
        // 创建新实例
        engine = createEngine(normalizedName);
        if (engine != null) {
            engines.put(normalizedName, engine);
        }
        
        return engine;
    }
    
    /**
     * 获取默认搜索引擎
     */
    public SearchEngine getDefaultEngine() {
        return getEngine(defaultEngine);
    }
    
    /**
     * 设置默认搜索引擎
     */
    public void setDefaultEngine(String engineName) {
        this.defaultEngine = normalizeEngineName(engineName);
        logger.info("默认搜索引擎设置为: " + this.defaultEngine);
    }
    
    /**
     * 注册搜索引擎
     * 
     * @param name 引擎名称
     * @param engine 引擎实例
     */
    public void registerEngine(String name, SearchEngine engine) {
        engines.put(normalizeEngineName(name), engine);
        logger.info("注册搜索引擎: " + name);
    }
    
    /**
     * 配置搜索引擎
     * 
     * @param engineName 引擎名称
     * @param config 配置对象
     */
    public void configureEngine(String engineName, SearchEngine.SearchEngineConfig config) {
        String normalizedName = normalizeEngineName(engineName);
        engineConfigs.put(normalizedName, config);
        
        // 如果引擎已创建，更新其配置
        SearchEngine engine = engines.get(normalizedName);
        if (engine != null) {
            engine.configure(config);
        }
        
        logger.info("配置搜索引擎: " + engineName);
    }
    
    /**
     * 配置 Google 搜索引擎
     * 
     * @param apiKey API Key
     * @param searchEngineId Search Engine ID (cx)
     */
    public void configureGoogle(String apiKey, String searchEngineId) {
        SearchEngine.SearchEngineConfig config = SearchEngine.SearchEngineConfig.builder()
            .apiKey(apiKey)
            .baseUrl(searchEngineId)
            .timeoutMs(10000);
        
        configureEngine("google", config);
        
        // 立即创建并配置引擎
        GoogleCustomSearchEngine engine = new GoogleCustomSearchEngine(config);
        registerEngine("google", engine);
    }
    
    /**
     * 配置 Bing 搜索引擎
     *
     * @param timeoutMs 超时时间
     * @param userAgent User-Agent
     */
    public void configureBing(int timeoutMs, String userAgent) {
        SearchEngine.SearchEngineConfig config = SearchEngine.SearchEngineConfig.builder()
            .timeoutMs(timeoutMs)
            .userAgent(userAgent)
            .followRedirects(true);

        configureEngine("bing", config);
    }
    
    /**
     * 检查引擎是否可用
     */
    public boolean isEngineAvailable(String engineName) {
        SearchEngine engine = getEngine(engineName);
        return engine != null && engine.isAvailable();
    }
    
    /**
     * 获取所有可用引擎名称
     */
    public String[] getAvailableEngines() {
        return new String[]{"bing", "baidu", "sogou"};
    }
    
    /**
     * 获取引擎信息
     */
    public String getEngineInfo(String engineName) {
        SearchEngine engine = getEngine(engineName);
        if (engine == null) {
            return "未知引擎: " + engineName;
        }
        
        StringBuilder info = new StringBuilder();
        info.append("名称: ").append(engine.getName()).append("\n");
        info.append("显示名: ").append(engine.getDisplayName()).append("\n");
        info.append("描述: ").append(engine.getDescription()).append("\n");
        info.append("可用: ").append(engine.isAvailable() ? "是" : "否").append("\n");
        info.append("需要 API Key: ").append(engine.requiresApiKey() ? "是" : "否");
        
        return info.toString();
    }
    
    /**
     * 重置所有引擎（清除缓存）
     */
    public void reset() {
        engines.clear();
        engineConfigs.clear();
        registerDefaultEngines();
        logger.info("搜索引擎工厂已重置");
    }
    
    /**
     * 注册默认搜索引擎
     */
    private void registerDefaultEngines() {
        // 注册 Bing（国内可直接访问）
        engines.put("bing", new BingSearchEngine());
        // 注册百度
        engines.put("baidu", new BaiduSearchEngine());
        // 注册搜狗
        engines.put("sogou", new SogouSearchEngine());
    }
    
    /**
     * 创建搜索引擎实例
     */
    private SearchEngine createEngine(String engineName) {
        SearchEngine.SearchEngineConfig config = engineConfigs.get(engineName);

        switch (engineName) {
            case "bing":
                return config != null ? new BingSearchEngine(config) : new BingSearchEngine();

            case "baidu":
                return config != null ? new BaiduSearchEngine(config) : new BaiduSearchEngine();

            case "sogou":
                return config != null ? new SogouSearchEngine(config) : new SogouSearchEngine();

            default:
                logger.warning("未知的搜索引擎: " + engineName);
                return null;
        }
    }
    
    /**
     * 标准化引擎名称
     */
    private String normalizeEngineName(String name) {
        if (name == null) {
            return "bing";
        }
        return name.toLowerCase().trim();
    }
}
