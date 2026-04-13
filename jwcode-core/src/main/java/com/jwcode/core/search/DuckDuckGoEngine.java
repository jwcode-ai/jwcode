package com.jwcode.core.search;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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
 * DuckDuckGo 搜索引擎实现
 * 
 * 使用 DuckDuckGo HTML 接口进行搜索
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class DuckDuckGoEngine implements SearchEngine {
    
    private static final Logger logger = Logger.getLogger(DuckDuckGoEngine.class.getName());
    
    // DuckDuckGo HTML 搜索 URL
    private static final String DUCKDUCKGO_URL = "https://html.duckduckgo.com/html/?q=";
    private static final String DUCKDUCKGO_BANG_URL = "https://duckduckgo.com/html/?q=";
    
    // 默认 User-Agent
    private static final String DEFAULT_USER_AGENT = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    // 超时配置（毫秒）
    private int connectTimeout = 10000;
    private int readTimeout = 10000;
    private String userAgent = DEFAULT_USER_AGENT;
    private boolean followRedirects = true;
    
    public DuckDuckGoEngine() {
    }
    
    public DuckDuckGoEngine(SearchEngineConfig config) {
        configure(config);
    }
    
    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        
        HttpURLConnection conn = null;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = DUCKDUCKGO_URL + encodedQuery;
            
            URL url = new URL(searchUrl);
            conn = (HttpURLConnection) url.openConnection();
            setupConnection(conn);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("搜索请求失败，HTTP 状态码: " + responseCode);
            }
            
            // 读取响应
            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line).append("\n");
                }
            }
            
            // 解析搜索结果
            results = parseResults(html.toString(), maxResults);
            
        } catch (Exception e) {
            logger.severe("DuckDuckGo 搜索失败: " + e.getMessage());
            // 返回空结果而不是抛出异常，保持稳定性
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        
        return results;
    }
    
    @Override
    public String getName() {
        return "duckduckgo";
    }
    
    @Override
    public String getDisplayName() {
        return "DuckDuckGo";
    }
    
    @Override
    public String getDescription() {
        return "DuckDuckGo 搜索引擎 - 注重隐私保护的搜索服务";
    }
    
    @Override
    public boolean requiresApiKey() {
        return false;
    }
    
    @Override
    public void configure(SearchEngineConfig config) {
        if (config.getTimeoutMs() > 0) {
            this.connectTimeout = config.getTimeoutMs();
            this.readTimeout = config.getTimeoutMs();
        }
        if (config.getUserAgent() != null) {
            this.userAgent = config.getUserAgent();
        }
        this.followRedirects = config.isFollowRedirects();
    }
    
    /**
     * 设置连接超时
     */
    public void setTimeout(int timeoutMs) {
        this.connectTimeout = timeoutMs;
        this.readTimeout = timeoutMs;
    }
    
    /**
     * 设置 User-Agent
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    /**
     * 配置 HTTP 连接
     */
    private void setupConnection(HttpURLConnection conn) {
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        conn.setRequestProperty("DNT", "1");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setInstanceFollowRedirects(followRedirects);
    }
    
    /**
     * 解析搜索结果 HTML
     */
    private List<SearchResult> parseResults(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            Document doc = Jsoup.parse(html);
            
            // DuckDuckGo HTML 结果在 class="result" 的 div 中
            Elements resultDivs = doc.select(".result");
            
            for (Element resultDiv : resultDivs) {
                if (results.size() >= maxResults) {
                    break;
                }
                
                SearchResult result = parseResultElement(resultDiv);
                if (result != null && result.isValid()) {
                    results.add(result);
                }
            }
            
            // 如果没有找到标准结果，尝试其他选择器
            if (results.isEmpty()) {
                results = parseAlternative(doc, maxResults);
            }
            
        } catch (Exception e) {
            logger.warning("解析 DuckDuckGo 结果失败: " + e.getMessage());
        }
        
        return results;
    }
    
    /**
     * 解析单个结果元素
     */
    private SearchResult parseResultElement(Element resultDiv) {
        try {
            // 提取标题和链接
            Element titleLink = resultDiv.selectFirst(".result__a, h2 a, .result-title");
            if (titleLink == null) {
                return null;
            }
            
            String title = titleLink.text().trim();
            String url = extractUrl(titleLink.attr("href"));
            
            // 提取摘要
            Element snippetDiv = resultDiv.selectFirst(".result__snippet, .result-snippet, .snippet");
            String snippet = snippetDiv != null ? snippetDiv.text().trim() : "";
            
            // 尝试提取时间
            Element timeElement = resultDiv.selectFirst(".result__timestamp, .timestamp");
            String publishedTime = timeElement != null ? timeElement.text().trim() : null;
            
            return SearchResult.builder()
                .title(title)
                .url(url)
                .snippet(snippet)
                .source(getName())
                .publishedTime(publishedTime)
                .relevanceScore(0.5)
                .build();
                
        } catch (Exception e) {
            logger.fine("解析单个结果失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 替代解析方法（应对页面结构变化）
     */
    private List<SearchResult> parseAlternative(Document doc, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        
        // 尝试更通用的选择器
        Elements links = doc.select("a[href]");
        
        for (Element link : links) {
            if (results.size() >= maxResults) {
                break;
            }
            
            String href = link.attr("href");
            // 过滤掉 DuckDuckGo 内部链接和无效链接
            if (href.contains("duckduckgo.com") || href.startsWith("/") || 
                href.startsWith("#") || href.isEmpty()) {
                continue;
            }
            
            String title = link.text().trim();
            if (title.length() < 5) {
                continue; // 跳过太短的标题
            }
            
            // 尝试在相邻元素中查找摘要
            String snippet = "";
            Element parent = link.parent();
            if (parent != null) {
                Element sibling = parent.nextElementSibling();
                if (sibling != null) {
                    snippet = sibling.text().trim();
                }
            }
            
            SearchResult result = SearchResult.builder()
                .title(title)
                .url(extractUrl(href))
                .snippet(snippet)
                .source(getName())
                .relevanceScore(0.3)
                .build();
            
            if (result.isValid()) {
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * 提取真实 URL（处理 DuckDuckGo 的重定向 URL）
     */
    private String extractUrl(String href) {
        if (href == null || href.isEmpty()) {
            return "";
        }
        
        try {
            // 处理 DuckDuckGo 的重定向 URL
            if (href.contains("duckduckgo.com/l/?uddg=")) {
                int start = href.indexOf("uddg=") + 5;
                String encoded = href.substring(start);
                return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            }
            
            // 处理相对 URL
            if (href.startsWith("//")) {
                return "https:" + href;
            }
            
            return href;
            
        } catch (Exception e) {
            return href;
        }
    }
}
