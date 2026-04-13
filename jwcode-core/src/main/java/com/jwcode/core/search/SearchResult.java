package com.jwcode.core.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 搜索结果数据类
 * 
 * 表示单个搜索结果的完整信息
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    
    /** 结果标题 */
    private String title;
    
    /** 结果 URL */
    private String url;
    
    /** 结果摘要/描述 */
    private String snippet;
    
    /** 来源搜索引擎 */
    private String source;
    
    /** 搜索结果时间 */
    private String publishedTime;
    
    /** 内容类型（如 text/html, pdf 等） */
    private String contentType;
    
    /** 相关性评分（0.0 - 1.0） */
    private double relevanceScore;
    
    /** 搜索结果获取时间 */
    private Instant fetchedAt;
    
    /**
     * 创建基础搜索结果
     */
    public SearchResult(String title, String url, String snippet) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.fetchedAt = Instant.now();
        this.relevanceScore = 0.5;
    }
    
    /**
     * 创建带来源的搜索结果
     */
    public SearchResult(String title, String url, String snippet, String source) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.source = source;
        this.fetchedAt = Instant.now();
        this.relevanceScore = 0.5;
    }
    
    /**
     * 检查是否为有效结果
     */
    public boolean isValid() {
        return title != null && !title.isBlank() 
            && url != null && !url.isBlank();
    }
    
    /**
     * 获取标准化 URL（用于去重）
     */
    public String getNormalizedUrl() {
        if (url == null) return "";
        String normalized = url.toLowerCase()
            .replaceAll("^https?://", "")
            .replaceAll("^www\\.", "")
            .replaceAll("/$", "");
        return normalized;
    }
    
    /**
     * 创建简略副本（用于摘要）
     */
    public SearchResult createSummary(int maxSnippetLength) {
        SearchResult summary = new SearchResult();
        summary.title = this.title;
        summary.url = this.url;
        summary.source = this.source;
        summary.relevanceScore = this.relevanceScore;
        summary.fetchedAt = this.fetchedAt;
        
        if (snippet != null && snippet.length() > maxSnippetLength) {
            summary.snippet = snippet.substring(0, maxSnippetLength) + "...";
        } else {
            summary.snippet = this.snippet;
        }
        
        return summary;
    }
}
