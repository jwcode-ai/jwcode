package com.jwcode.core.search;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 搜索结果处理器
 * 
 * 处理搜索结果：去重、评分排序、生成摘要、安全过滤
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SearchResultProcessor {
    
    private static final Logger logger = Logger.getLogger(SearchResultProcessor.class.getName());
    
    // URL 黑名单（正则表达式模式）
    private final Set<Pattern> urlBlacklist = new HashSet<>();
    
    // 域名黑名单
    private final Set<String> domainBlacklist = new HashSet<>();
    
    // 内容提取器（用于生成摘要）
    private final ContentExtractor contentExtractor;
    
    // 默认摘要长度
    private int defaultSummaryLength = 300;
    
    // 相似度阈值（用于去重）
    private double similarityThreshold = 0.8;
    
    public SearchResultProcessor() {
        this.contentExtractor = new ContentExtractor();
        initDefaultBlacklist();
    }
    
    public SearchResultProcessor(ContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor != null ? contentExtractor : new ContentExtractor();
        initDefaultBlacklist();
    }
    
    /**
     * 处理搜索结果
     * 
     * @param results 原始搜索结果
     * @param maxResults 最大返回结果数
     * @return 处理后的结果
     */
    public List<SearchResult> process(List<SearchResult> results, int maxResults) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1. 安全过滤（黑名单）
        List<SearchResult> filtered = filterByBlacklist(results);
        
        // 2. URL 去重
        List<SearchResult> deduplicated = deduplicateByUrl(filtered);
        
        // 3. 计算相关性评分
        calculateRelevanceScores(deduplicated);
        
        // 4. 排序
        List<SearchResult> sorted = sortByRelevance(deduplicated);
        
        // 5. 限制数量
        if (sorted.size() > maxResults) {
            sorted = sorted.subList(0, maxResults);
        }
        
        return sorted;
    }
    
    /**
     * 处理搜索结果（带摘要生成）
     * 
     * @param results 原始搜索结果
     * @param maxResults 最大返回结果数
     * @param generateSummaries 是否生成摘要
     * @return 处理后的结果
     */
    public List<SearchResult> process(List<SearchResult> results, int maxResults, boolean generateSummaries) {
        List<SearchResult> processed = process(results, maxResults);
        
        if (generateSummaries) {
            for (SearchResult result : processed) {
                if (result.getSnippet() != null && result.getSnippet().length() > defaultSummaryLength) {
                    String summary = contentExtractor.generateSummary(
                        result.getSnippet(), 
                        defaultSummaryLength
                    );
                    result.setSnippet(summary);
                }
            }
        }
        
        return processed;
    }
    
    /**
     * URL 去重
     * 
     * 基于标准化 URL 进行去重
     */
    public List<SearchResult> deduplicateByUrl(List<SearchResult> results) {
        Set<String> seenUrls = new HashSet<>();
        List<SearchResult> unique = new ArrayList<>();
        
        for (SearchResult result : results) {
            String normalizedUrl = normalizeUrl(result.getUrl());
            
            if (!seenUrls.contains(normalizedUrl)) {
                seenUrls.add(normalizedUrl);
                unique.add(result);
            } else {
                logger.fine("去重移除重复 URL: " + result.getUrl());
            }
        }
        
        return unique;
    }
    
    /**
     * 基于内容的相似度去重
     */
    public List<SearchResult> deduplicateByContent(List<SearchResult> results) {
        List<SearchResult> unique = new ArrayList<>();
        
        for (SearchResult result : results) {
            boolean isDuplicate = false;
            
            for (SearchResult existing : unique) {
                double similarity = calculateSimilarity(result, existing);
                if (similarity >= similarityThreshold) {
                    isDuplicate = true;
                    logger.fine("内容相似去重: " + result.getTitle() + " ~ " + existing.getTitle());
                    break;
                }
            }
            
            if (!isDuplicate) {
                unique.add(result);
            }
        }
        
        return unique;
    }
    
    /**
     * 结果评分排序
     */
    public List<SearchResult> sortByRelevance(List<SearchResult> results) {
        return results.stream()
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .collect(Collectors.toList());
    }
    
    /**
     * 生成内容摘要
     */
    public String generateSummary(String content, int maxLength) {
        return contentExtractor.generateSummary(content, maxLength);
    }
    
    /**
     * 安全过滤（黑名单 URL）
     */
    public List<SearchResult> filterByBlacklist(List<SearchResult> results) {
        return results.stream()
            .filter(this::isAllowed)
            .collect(Collectors.toList());
    }
    
    /**
     * 检查 URL 是否允许
     */
    public boolean isAllowed(SearchResult result) {
        if (result == null || result.getUrl() == null) {
            return false;
        }
        
        String url = result.getUrl().toLowerCase();
        
        // 检查正则黑名单
        for (Pattern pattern : urlBlacklist) {
            if (pattern.matcher(url).matches()) {
                logger.fine("黑名单过滤（正则）: " + url);
                return false;
            }
        }
        
        // 检查域名黑名单
        try {
            String domain = extractDomain(url);
            if (domain != null) {
                for (String blacklistedDomain : domainBlacklist) {
                    if (domain.equals(blacklistedDomain) || domain.endsWith("." + blacklistedDomain)) {
                        logger.fine("黑名单过滤（域名）: " + url);
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("域名解析失败: " + url);
        }
        
        return true;
    }
    
    /**
     * 添加 URL 到黑名单
     */
    public void addToBlacklist(String pattern) {
        try {
            urlBlacklist.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        } catch (Exception e) {
            logger.warning("无效的黑名单模式: " + pattern);
        }
    }
    
    /**
     * 添加域名到黑名单
     */
    public void addDomainToBlacklist(String domain) {
        domainBlacklist.add(domain.toLowerCase());
    }
    
    /**
     * 移除黑名单规则
     */
    public void removeFromBlacklist(String pattern) {
        urlBlacklist.removeIf(p -> p.pattern().equals(pattern));
    }
    
    /**
     * 移除域名黑名单
     */
    public void removeDomainFromBlacklist(String domain) {
        domainBlacklist.remove(domain.toLowerCase());
    }
    
    /**
     * 清空黑名单
     */
    public void clearBlacklist() {
        urlBlacklist.clear();
        domainBlacklist.clear();
        initDefaultBlacklist();
    }
    
    /**
     * 获取黑名单信息
     */
    public String getBlacklistInfo() {
        StringBuilder info = new StringBuilder();
        info.append("URL 模式黑名单: ").append(urlBlacklist.size()).append(" 条\n");
        info.append("域名黑名单: ").append(domainBlacklist.size()).append(" 条\n");
        info.append("域名列表: ").append(String.join(", ", domainBlacklist));
        return info.toString();
    }
    
    /**
     * 设置摘要长度
     */
    public void setDefaultSummaryLength(int length) {
        this.defaultSummaryLength = length;
    }
    
    /**
     * 设置相似度阈值
     */
    public void setSimilarityThreshold(double threshold) {
        this.similarityThreshold = Math.max(0.0, Math.min(1.0, threshold));
    }
    
    /**
     * 计算相关性评分
     */
    private void calculateRelevanceScores(List<SearchResult> results) {
        // 基于位置计算基础评分（位置越靠前越相关）
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            double baseScore = 1.0 - (i * 0.05); // 每位置递减 0.05
            
            // 标题长度因子
            String title = result.getTitle();
            if (title != null) {
                if (title.length() > 10 && title.length() < 100) {
                    baseScore += 0.1; // 适中长度的标题加分
                }
            }
            
            // 摘要长度因子
            String snippet = result.getSnippet();
            if (snippet != null && snippet.length() > 50) {
                baseScore += 0.05;
            }
            
            // 来源权重
            String source = result.getSource();
            if ("google".equals(source)) {
                baseScore += 0.05; // Google 结果略微优先
            }
            
            result.setRelevanceScore(Math.min(1.0, Math.max(0.0, baseScore)));
        }
    }
    
    /**
     * 计算两个结果的相似度
     */
    private double calculateSimilarity(SearchResult a, SearchResult b) {
        // 基于标题和摘要的相似度计算
        String titleA = a.getTitle() != null ? a.getTitle().toLowerCase() : "";
        String titleB = b.getTitle() != null ? b.getTitle().toLowerCase() : "";
        
        // 如果标题完全相同，认为是重复
        if (titleA.equals(titleB) && !titleA.isEmpty()) {
            return 1.0;
        }
        
        // 基于 URL 的相似度
        String urlA = normalizeUrl(a.getUrl());
        String urlB = normalizeUrl(b.getUrl());
        
        if (urlA.equals(urlB)) {
            return 1.0;
        }
        
        // 基于标题的 Jaccard 相似度
        return calculateJaccardSimilarity(titleA, titleB);
    }
    
    /**
     * 计算 Jaccard 相似度
     */
    private double calculateJaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        
        if (union.isEmpty()) {
            return 0.0;
        }
        
        return (double) intersection.size() / union.size();
    }
    
    /**
     * 标准化 URL
     */
    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        
        return url.toLowerCase()
            .replaceAll("^https?://", "")
            .replaceAll("^www\\.", "")
            .replaceAll("/$", "")
            .replaceAll("\\?.*$", ""); // 移除查询参数
    }
    
    /**
     * 提取域名
     */
    private String extractDomain(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost().toLowerCase().replaceFirst("^www\\.", "");
        } catch (MalformedURLException e) {
            // 尝试简单解析
            String cleaned = url.replaceAll("^https?://", "");
            int slashIndex = cleaned.indexOf('/');
            if (slashIndex > 0) {
                return cleaned.substring(0, slashIndex).replaceFirst("^www\\.", "");
            }
            return cleaned.replaceFirst("^www\\.", "");
        }
    }
    
    /**
     * 初始化默认黑名单
     */
    private void initDefaultBlacklist() {
        // 添加一些常见的恶意域名模式
        addDomainToBlacklist("malware.example");
        addDomainToBlacklist("phishing.example");
        
        // 添加一些需要过滤的 URL 模式
        addToBlacklist(".*\\.(exe|dll|bat|cmd|sh|bin)$"); // 可执行文件
    }
}
