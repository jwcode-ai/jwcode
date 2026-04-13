package com.jwcode.core.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Google Custom Search 引擎实现
 * 
 * 使用 Google Custom Search JSON API 进行搜索
 * 需要配置 API Key 和 Search Engine ID
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class GoogleCustomSearchEngine implements SearchEngine {
    
    private static final Logger logger = Logger.getLogger(GoogleCustomSearchEngine.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // Google Custom Search API 端点
    private static final String API_BASE_URL = "https://www.googleapis.com/customsearch/v1";
    
    // 默认 User-Agent
    private static final String DEFAULT_USER_AGENT = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    
    private String apiKey;
    private String searchEngineId;
    private int connectTimeout = 10000;
    private int readTimeout = 10000;
    private String userAgent = DEFAULT_USER_AGENT;
    
    public GoogleCustomSearchEngine() {
    }
    
    public GoogleCustomSearchEngine(String apiKey, String searchEngineId) {
        this.apiKey = apiKey;
        this.searchEngineId = searchEngineId;
    }
    
    public GoogleCustomSearchEngine(SearchEngineConfig config) {
        configure(config);
    }
    
    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        
        if (!isAvailable()) {
            logger.warning("Google Custom Search 未配置 API Key 或 Search Engine ID");
            return results;
        }
        
        // Google API 每页最多返回 10 条结果
        int pageSize = Math.min(maxResults, 10);
        int startIndex = 1;
        
        while (results.size() < maxResults) {
            try {
                List<SearchResult> pageResults = fetchPage(query, startIndex, pageSize);
                
                if (pageResults.isEmpty()) {
                    break; // 没有更多结果
                }
                
                results.addAll(pageResults);
                
                // 如果获取的结果少于请求的数量，说明没有更多结果了
                if (pageResults.size() < pageSize) {
                    break;
                }
                
                startIndex += pageSize;
                
                // 避免请求过快
                if (results.size() < maxResults) {
                    Thread.sleep(100);
                }
                
            } catch (Exception e) {
                logger.severe("Google Custom Search 请求失败: " + e.getMessage());
                break;
            }
        }
        
        // 限制返回结果数量
        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }
        
        return results;
    }
    
    @Override
    public String getName() {
        return "google";
    }
    
    @Override
    public String getDisplayName() {
        return "Google Custom Search";
    }
    
    @Override
    public String getDescription() {
        return "Google Custom Search API - 强大的搜索结果，需要 API Key";
    }
    
    @Override
    public boolean requiresApiKey() {
        return true;
    }
    
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() 
            && searchEngineId != null && !searchEngineId.isEmpty();
    }
    
    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    @Override
    public void configure(SearchEngineConfig config) {
        if (config.getApiKey() != null) {
            this.apiKey = config.getApiKey();
        }
        if (config.getBaseUrl() != null) {
            // baseUrl 在这里用作 Search Engine ID
            this.searchEngineId = config.getBaseUrl();
        }
        if (config.getTimeoutMs() > 0) {
            this.connectTimeout = config.getTimeoutMs();
            this.readTimeout = config.getTimeoutMs();
        }
        if (config.getUserAgent() != null) {
            this.userAgent = config.getUserAgent();
        }
    }
    
    /**
     * 设置 Search Engine ID (cx)
     */
    public void setSearchEngineId(String searchEngineId) {
        this.searchEngineId = searchEngineId;
    }
    
    /**
     * 获取 Search Engine ID
     */
    public String getSearchEngineId() {
        return searchEngineId;
    }
    
    /**
     * 设置超时
     */
    public void setTimeout(int timeoutMs) {
        this.connectTimeout = timeoutMs;
        this.readTimeout = timeoutMs;
    }
    
    /**
     * 获取当前 API Key（脱敏显示）
     */
    public String getMaskedApiKey() {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * 获取 API 请求配额信息
     * 
     * @return 配额信息字符串
     */
    public String getQuotaInfo() {
        return "每日免费配额: 100 次搜索查询";
    }
    
    /**
     * 获取单个页面的搜索结果
     */
    private List<SearchResult> fetchPage(String query, int start, int num) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String urlString = String.format("%s?key=%s&cx=%s&q=%s&start=%d&num=%d",
            API_BASE_URL,
            apiKey,
            searchEngineId,
            encodedQuery,
            start,
            Math.min(num, 10) // Google API 限制每页最多 10 条
        );
        
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 403) {
                throw new RuntimeException("API Key 无效或配额已用完");
            } else if (responseCode == 400) {
                throw new RuntimeException("请求参数错误: " + readErrorResponse(conn));
            } else if (responseCode != 200) {
                throw new RuntimeException("请求失败，HTTP 状态码: " + responseCode);
            }
            
            // 读取响应
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // 解析 JSON 响应
            results = parseJsonResponse(response.toString());
            
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        
        return results;
    }
    
    /**
     * 解析 JSON 响应
     */
    private List<SearchResult> parseJsonResponse(String json) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode items = root.get("items");
            
            if (items == null || !items.isArray()) {
                // 检查是否有错误信息
                JsonNode error = root.get("error");
                if (error != null) {
                    String message = error.get("message").asText("Unknown error");
                    logger.warning("Google API 错误: " + message);
                }
                return results;
            }
            
            for (JsonNode item : items) {
                SearchResult result = parseItem(item);
                if (result != null && result.isValid()) {
                    results.add(result);
                }
            }
            
        } catch (Exception e) {
            logger.warning("解析 JSON 响应失败: " + e.getMessage());
        }
        
        return results;
    }
    
    /**
     * 解析单个搜索结果项
     */
    private SearchResult parseItem(JsonNode item) {
        try {
            String title = getTextValue(item, "title", "");
            String link = getTextValue(item, "link", "");
            String snippet = getTextValue(item, "snippet", "");
            
            // 获取页面元数据
            JsonNode pagemap = item.get("pagemap");
            String publishedTime = null;
            
            if (pagemap != null) {
                JsonNode metatags = pagemap.get("metatags");
                if (metatags != null && metatags.isArray() && metatags.size() > 0) {
                    JsonNode firstMeta = metatags.get(0);
                    publishedTime = getTextValue(firstMeta, "article:published_time", null);
                    if (publishedTime == null) {
                        publishedTime = getTextValue(firstMeta, "datePublished", null);
                    }
                }
            }
            
            // 计算相关性评分（Google 返回的结果默认按相关性排序）
            double relevanceScore = 1.0;
            
            return SearchResult.builder()
                .title(title)
                .url(link)
                .snippet(snippet)
                .source(getName())
                .publishedTime(publishedTime)
                .relevanceScore(relevanceScore)
                .build();
                
        } catch (Exception e) {
            logger.fine("解析搜索结果项失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 安全获取文本值
     */
    private String getTextValue(JsonNode node, String field, String defaultValue) {
        JsonNode valueNode = node.get(field);
        if (valueNode != null && !valueNode.isNull()) {
            return valueNode.asText(defaultValue);
        }
        return defaultValue;
    }
    
    /**
     * 读取错误响应
     */
    private String readErrorResponse(HttpURLConnection conn) {
        try {
            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line);
                }
            }
            return error.toString();
        } catch (Exception e) {
            return "无法读取错误详情";
        }
    }
}
