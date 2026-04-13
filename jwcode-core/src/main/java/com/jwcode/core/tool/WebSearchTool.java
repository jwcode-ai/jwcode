package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.search.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * WebSearch 工具 - 网页搜索（增强版，Phase 8）
 * 
 * 使用 SearchEngineFactory 支持多搜索引擎
 * 支持 DuckDuckGo 和 Google Custom Search
 * 集成搜索结果缓存和结果处理管道
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class WebSearchTool implements Tool<WebSearchTool.Input, WebSearchTool.Output, WebSearchTool.Progress> {
    
    private static final Logger logger = Logger.getLogger(WebSearchTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // 搜索引擎工厂
    private final SearchEngineFactory engineFactory;
    
    // 搜索结果缓存
    private final SearchCache cache;
    
    // 结果处理器
    private final SearchResultProcessor processor;
    
    // 默认配置
    private static final String DEFAULT_ENGINE = "duckduckgo";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int MAX_ALLOWED_RESULTS = 20;
    
    public WebSearchTool() {
        this.engineFactory = SearchEngineFactory.getInstance();
        this.cache = new SearchCache();
        this.processor = new SearchResultProcessor();
    }
    
    /**
     * 创建带自定义配置的 WebSearchTool
     */
    public WebSearchTool(SearchCache cache, SearchResultProcessor processor) {
        this.engineFactory = SearchEngineFactory.getInstance();
        this.cache = cache != null ? cache : new SearchCache();
        this.processor = processor != null ? processor : new SearchResultProcessor();
    }
    
    @Override
    public String getName() {
        return "WebSearch";
    }
    
    @Override
    public String getDescription() {
        return "搜索网页内容。支持多搜索引擎（DuckDuckGo、Google），支持搜索结果缓存和智能处理。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 WebSearch 工具搜索网页内容。
               
               参数:
               - query: 搜索查询词（必需）
               - max_results: 最大结果数（可选，默认 5，最大 20）
               - engine: 搜索引擎（可选，默认 duckduckgo，可选值: duckduckgo, google）
               - use_cache: 是否使用缓存（可选，默认 true）
               
               示例:
               - {"query": "Java 21 新特性"} - 使用默认引擎搜索
               - {"query": "Spring Boot 教程", "max_results": 10} - 返回 10 条结果
               - {"query": "OpenAI API 文档", "engine": "google"} - 使用 Google 搜索
               
               注意:
               - 使用简洁明确的搜索词
               - 可以包含版本号、技术栈名称等关键词
               - Google 引擎需要配置 API Key
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string", "description": "搜索查询词"},
                        "max_results": {"type": "integer", "description": "最大结果数", "default": 5},
                        "engine": {"type": "string", "description": "搜索引擎", "default": "duckduckgo", "enum": ["duckduckgo", "google"]},
                        "use_cache": {"type": "boolean", "description": "是否使用缓存", "default": true}
                    },
                    "required": ["query"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<Input>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<Output>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                if (input == null || input.query == null || input.query.trim().isEmpty()) {
                    return ToolResult.error("搜索查询词不能为空");
                }
                
                String engineName = input.engine != null ? input.engine.toLowerCase() : DEFAULT_ENGINE;
                int maxResults = input.max_results != null ? input.max_results : DEFAULT_MAX_RESULTS;
                boolean useCache = input.use_cache != null ? input.use_cache : true;
                
                // 限制最大结果数
                if (maxResults < 1) maxResults = 1;
                if (maxResults > MAX_ALLOWED_RESULTS) maxResults = MAX_ALLOWED_RESULTS;
                
                // 检查缓存
                if (useCache) {
                    List<SearchResult> cachedResults = cache.get(input.query, engineName);
                    if (cachedResults != null && !cachedResults.isEmpty()) {
                        logger.fine("使用缓存的搜索结果: " + input.query);
                        Output cachedOutput = createOutput(input.query, cachedResults, engineName, true);
                        return ToolResult.success(cachedOutput);
                    }
                }
                
                // 获取搜索引擎
                SearchEngine engine = engineFactory.getEngine(engineName);
                if (engine == null) {
                    return ToolResult.error("未知的搜索引擎: " + engineName);
                }
                
                // 检查引擎是否可用
                if (!engine.isAvailable()) {
                    if (engine.requiresApiKey()) {
                        return ToolResult.error("搜索引擎 '" + engineName + "' 需要配置 API Key");
                    }
                    return ToolResult.error("搜索引擎 '" + engineName + "' 当前不可用");
                }
                
                // 执行搜索
                List<SearchResult> rawResults = engine.search(input.query, maxResults);
                
                // 处理结果
                List<SearchResult> processedResults = processor.process(rawResults, maxResults, true);
                
                // 缓存结果
                if (useCache && !processedResults.isEmpty()) {
                    cache.put(input.query, engineName, processedResults);
                }
                
                Output output = createOutput(input.query, processedResults, engineName, false);
                return ToolResult.success(output);
                
            } catch (Exception e) {
                logger.severe("搜索失败: " + e.getMessage());
                return ToolResult.error("搜索失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 创建输出对象
     */
    private Output createOutput(String query, List<SearchResult> results, String engine, boolean fromCache) {
        Output output = new Output();
        output.success = true;
        output.query = query;
        output.engine = engine;
        output.from_cache = fromCache;
        output.result_count = results.size();
        output.results = convertResults(results);
        return output;
    }
    
    /**
     * 转换搜索结果为工具输出格式
     */
    private List<SearchResultItem> convertResults(List<SearchResult> results) {
        List<SearchResultItem> items = new ArrayList<>();
        for (SearchResult result : results) {
            SearchResultItem item = new SearchResultItem();
            item.title = result.getTitle();
            item.url = result.getUrl();
            item.snippet = result.getSnippet();
            item.source = result.getSource();
            item.published_time = result.getPublishedTime();
            items.add(item);
        }
        return items;
    }
    
    /**
     * 配置 Google 搜索引擎
     */
    public void configureGoogle(String apiKey, String searchEngineId) {
        engineFactory.configureGoogle(apiKey, searchEngineId);
        logger.info("已配置 Google 搜索引擎");
    }
    
    /**
     * 获取搜索缓存统计
     */
    public SearchCache.CacheStats getCacheStats() {
        return cache.getStats();
    }
    
    /**
     * 清空搜索缓存
     */
    public void clearCache() {
        cache.clear();
        logger.info("搜索缓存已清空");
    }
    
    /**
     * 获取可用搜索引擎列表
     */
    public String[] getAvailableEngines() {
        return engineFactory.getAvailableEngines();
    }
    
    /**
     * 设置默认搜索引擎
     */
    public void setDefaultEngine(String engine) {
        engineFactory.setDefaultEngine(engine);
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (input == null || input.query == null || input.query.trim().isEmpty()) {
            builder.addError("query 是必需的");
        }
        
        if (input.max_results != null && (input.max_results < 1 || input.max_results > MAX_ALLOWED_RESULTS)) {
            builder.addError("max_results 必须在 1-" + MAX_ALLOWED_RESULTS + " 之间");
        }
        
        if (input.engine != null) {
            String engine = input.engine.toLowerCase();
            boolean valid = false;
            for (String available : engineFactory.getAvailableEngines()) {
                if (available.equals(engine)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                builder.addError("无效的搜索引擎: " + input.engine);
            }
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return true;
    }
    
    @Override
    public boolean isConcurrencySafe(Input input) {
        return true;
    }
    
    /**
     * 关闭工具（清理资源）
     */
    public void shutdown() {
        cache.shutdown();
    }
    
    /**
     * 输入类型
     */
    public static class Input {
        public String query;
        public Integer max_results;
        public String engine;
        public Boolean use_cache;
        
        public Input() {}
        
        public Input(String query) {
            this.query = query;
        }
        
        public Input(String query, Integer max_results) {
            this.query = query;
            this.max_results = max_results;
        }
        
        public Input(String query, Integer max_results, String engine) {
            this.query = query;
            this.max_results = max_results;
            this.engine = engine;
        }
    }
    
    /**
     * 搜索结果项（工具输出格式）
     */
    public static class SearchResultItem {
        public String title;
        public String url;
        public String snippet;
        public String source;
        public String published_time;
    }
    
    /**
     * 输出类型
     */
    public static class Output {
        public boolean success;
        public String query;
        public String engine;
        public boolean from_cache;
        public int result_count;
        public List<SearchResultItem> results;
        
        public Output() {}
    }
    
    /**
     * 进度类型
     */
    public static class Progress {
        private final String query;
        private final int progress;
        private final String stage;
        
        public Progress(String query, int progress, String stage) {
            this.query = query;
            this.progress = progress;
            this.stage = stage;
        }
        
        public String getQuery() { return query; }
        public int getProgress() { return progress; }
        public String getStage() { return stage; }
    }
}
