package com.jwcode.core.search;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 搜索引擎接口
 * 
 * 定义搜索引擎的基本操作，所有搜索引擎实现都应实现此接口
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface SearchEngine {
    
    /**
     * 执行搜索查询
     * 
     * @param query 搜索查询词
     * @param maxResults 最大结果数
     * @return 搜索结果列表
     */
    List<SearchResult> search(String query, int maxResults);
    
    /**
     * 异步执行搜索查询
     * 
     * @param query 搜索查询词
     * @param maxResults 最大结果数
     * @return 包含搜索结果列表的 CompletableFuture
     */
    default CompletableFuture<List<SearchResult>> searchAsync(String query, int maxResults) {
        return CompletableFuture.supplyAsync(() -> search(query, maxResults));
    }
    
    /**
     * 获取搜索引擎名称
     * 
     * @return 引擎名称标识符
     */
    String getName();
    
    /**
     * 获取搜索引擎显示名称
     * 
     * @return 引擎显示名称
     */
    default String getDisplayName() {
        return getName();
    }
    
    /**
     * 检查搜索引擎是否可用
     * 
     * @return true 如果引擎配置正确且可用
     */
    default boolean isAvailable() {
        return true;
    }
    
    /**
     * 获取搜索引擎描述
     * 
     * @return 引擎描述信息
     */
    default String getDescription() {
        return "";
    }
    
    /**
     * 检查是否需要 API Key
     * 
     * @return true 如果需要 API Key
     */
    default boolean requiresApiKey() {
        return false;
    }
    
    /**
     * 设置 API Key
     * 
     * @param apiKey API 密钥
     */
    default void setApiKey(String apiKey) {
        // 默认实现为空，需要 API Key 的引擎应覆盖此方法
    }
    
    /**
     * 设置搜索引擎配置
     * 
     * @param config 配置对象
     */
    default void configure(SearchEngineConfig config) {
        // 默认实现为空
    }
    
    /**
     * 搜索引擎配置类
     */
    class SearchEngineConfig {
        private String apiKey;
        private String baseUrl;
        private int timeoutMs = 10000;
        private String userAgent;
        private boolean followRedirects = true;
        
        // Getters and Setters
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        
        public boolean isFollowRedirects() { return followRedirects; }
        public void setFollowRedirects(boolean followRedirects) { this.followRedirects = followRedirects; }
        
        public static SearchEngineConfig builder() {
            return new SearchEngineConfig();
        }
        
        public SearchEngineConfig apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        public SearchEngineConfig baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public SearchEngineConfig timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }
        
        public SearchEngineConfig userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }
        
        public SearchEngineConfig followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }
    }
}
