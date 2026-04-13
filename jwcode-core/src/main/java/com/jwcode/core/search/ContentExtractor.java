package com.jwcode.core.search;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 内容提取器
 * 
 * 智能提取文章正文，去除广告和导航，生成内容摘要
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ContentExtractor {
    
    private static final Logger logger = Logger.getLogger(ContentExtractor.class.getName());
    
    // 广告和导航相关的 CSS 选择器
    private static final String[] NOISE_SELECTORS = {
        "script", "style", "nav", "header", "footer", "aside",
        ".advertisement", ".ads", ".ad", ".banner", ".sponsor",
        ".sidebar", ".widget", ".comment", ".social", ".share",
        "#comments", ".related", ".recommend", ".popular",
        ".menu", ".navigation", ".breadcrumb", ".pagination",
        ".cookie-notice", ".newsletter", ".subscribe"
    };
    
    // 内容相关的高权重标签和类名
    private static final String[] CONTENT_INDICATORS = {
        "article", "main", "[role=main]",
        ".content", ".article-content", ".post-content", ".entry-content",
        ".article-body", ".post-body", ".story-body",
        "#content", "#main-content", "#article-body"
    };
    
    // 段落最小长度阈值
    private static final int MIN_PARAGRAPH_LENGTH = 50;
    
    // 内容密度阈值
    private static final double CONTENT_DENSITY_THRESHOLD = 0.3;
    
    /**
     * 提取文章正文
     * 
     * @param html HTML 内容
     * @return 提取的正文文本
     */
    public String extractArticle(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        try {
            Document doc = Jsoup.parse(html);
            
            // 首先尝试通过语义化标签定位主要内容
            Element mainContent = findMainContent(doc);
            
            if (mainContent != null) {
                return extractTextFromElement(mainContent);
            }
            
            // 如果找不到主要内容，使用基于密度的算法
            return extractByDensity(doc);
            
        } catch (Exception e) {
            logger.warning("提取文章正文失败: " + e.getMessage());
            return fallbackExtract(html);
        }
    }
    
    /**
     * 智能段落合并
     * 
     * @param paragraphs 段落列表
     * @return 合并后的文本
     */
    public String mergeParagraphs(List<String> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return "";
        }
        
        StringBuilder merged = new StringBuilder();
        StringBuilder currentBlock = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // 如果段落很短，可能是一个列表项或标题，单独成行
            if (trimmed.length() < 30 && !trimmed.endsWith("。") && !trimmed.endsWith(".")) {
                if (currentBlock.length() > 0) {
                    merged.append(currentBlock.toString().trim()).append("\n\n");
                    currentBlock = new StringBuilder();
                }
                merged.append(trimmed).append("\n");
                continue;
            }
            
            // 正常段落，合并到当前块
            if (currentBlock.length() > 0) {
                currentBlock.append(" ");
            }
            currentBlock.append(trimmed);
            
            // 如果段落以句号结束，完成当前块
            if (trimmed.endsWith("。") || trimmed.endsWith(".") || 
                trimmed.endsWith("?") || trimmed.endsWith("?")) {
                merged.append(currentBlock.toString().trim()).append("\n\n");
                currentBlock = new StringBuilder();
            }
        }
        
        // 添加剩余内容
        if (currentBlock.length() > 0) {
            merged.append(currentBlock.toString().trim());
        }
        
        return merged.toString().trim();
    }
    
    /**
     * 生成内容摘要
     * 
     * @param content 原文内容
     * @param maxLength 摘要最大长度
     * @return 生成的摘要
     */
    public String generateSummary(String content, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        if (content.length() <= maxLength) {
            return content;
        }
        
        // 尝试提取第一段有意义的文本
        String[] paragraphs = content.split("\n\n");
        StringBuilder summary = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.length() < MIN_PARAGRAPH_LENGTH) {
                continue;
            }
            
            if (summary.length() + trimmed.length() + 3 <= maxLength) {
                if (summary.length() > 0) {
                    summary.append("... ");
                }
                summary.append(trimmed);
            } else {
                // 添加剩余可添加的部分
                int remaining = maxLength - summary.length() - 3;
                if (remaining > 20) {
                    summary.append("... ")
                           .append(trimmed, 0, remaining)
                           .append("...");
                }
                break;
            }
        }
        
        String result = summary.toString();
        if (result.isEmpty()) {
            // 如果没有提取到有效段落，截取开头
            return content.substring(0, Math.min(content.length(), maxLength)) + "...";
        }
        
        return result;
    }
    
    /**
     * 去除广告和导航内容
     * 
     * @param doc jsoup 文档
     */
    public void removeNoise(Document doc) {
        for (String selector : NOISE_SELECTORS) {
            try {
                doc.select(selector).remove();
            } catch (Exception e) {
                // 忽略选择器错误
            }
        }
    }
    
    /**
     * 查找主要内容元素
     */
    private Element findMainContent(Document doc) {
        // 先清理噪音元素
        removeNoise(doc);
        
        // 尝试标准选择器
        for (String selector : CONTENT_INDICATORS) {
            Element element = doc.selectFirst(selector);
            if (element != null && hasEnoughContent(element)) {
                return element;
            }
        }
        
        return null;
    }
    
    /**
     * 基于文本密度的内容提取
     */
    private String extractByDensity(Document doc) {
        removeNoise(doc);
        
        Elements divs = doc.select("div, section");
        Element bestElement = null;
        double bestScore = 0;
        
        for (Element div : divs) {
            double score = calculateContentScore(div);
            if (score > bestScore) {
                bestScore = score;
                bestElement = div;
            }
        }
        
        if (bestElement != null && bestScore > CONTENT_DENSITY_THRESHOLD) {
            return extractTextFromElement(bestElement);
        }
        
        // 如果找不到高密度区域，返回 body 文本
        return doc.body() != null ? doc.body().text() : doc.text();
    }
    
    /**
     * 计算元素的内容评分
     */
    private double calculateContentScore(Element element) {
        String text = element.text();
        String html = element.html();
        
        if (text.isEmpty()) {
            return 0;
        }
        
        // 文本长度比例
        double textRatio = (double) text.length() / Math.max(html.length(), 1);
        
        // 段落数量
        int paragraphCount = element.select("p").size();
        
        // 链接密度（链接过多可能是导航）
        int linkCount = element.select("a").size();
        double linkDensity = (double) linkCount / Math.max(text.length() / 100, 1);
        
        // 计算综合得分
        double score = textRatio * 10 + paragraphCount * 0.5 - linkDensity * 2;
        
        // 根据类名和 id 调整得分
        String className = element.className().toLowerCase();
        String id = element.id().toLowerCase();
        
        if (className.contains("content") || id.contains("content") ||
            className.contains("article") || id.contains("article") ||
            className.contains("post") || id.contains("post")) {
            score += 5;
        }
        
        return score;
    }
    
    /**
     * 检查元素是否有足够内容
     */
    private boolean hasEnoughContent(Element element) {
        String text = element.text();
        return text.length() >= MIN_PARAGRAPH_LENGTH;
    }
    
    /**
     * 从元素中提取文本
     */
    private String extractTextFromElement(Element element) {
        // 提取所有段落
        Elements paragraphs = element.select("p, h1, h2, h3, h4, h5, h6, li");
        List<String> paragraphTexts = new ArrayList<>();
        
        for (Element p : paragraphs) {
            String text = p.text().trim();
            if (text.length() >= MIN_PARAGRAPH_LENGTH / 2) {
                paragraphTexts.add(text);
            }
        }
        
        // 如果没有找到段落，直接获取元素文本
        if (paragraphTexts.isEmpty()) {
            return element.text();
        }
        
        return mergeParagraphs(paragraphTexts);
    }
    
    /**
     * 备用提取方法（使用正则表达式）
     */
    private String fallbackExtract(String html) {
        // 移除脚本和样式
        String text = html.replaceAll("(?is)<script.*?>.*?</script>", " ");
        text = text.replaceAll("(?is)<style.*?>.*?</style>", " ");
        
        // 移除 HTML 标签
        text = text.replaceAll("<[^>]+>", " ");
        
        // 解码 HTML 实体
        text = text.replace("&nbsp;", " ");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&amp;", "&");
        text = text.replace("&quot;", "\"");
        
        // 规范化空白
        text = text.replaceAll("\\s+", " ");
        
        return text.trim();
    }
    
    // 广告关键词正则
    private static final Pattern AD_PATTERN = Pattern.compile(
        "(?i)(advertisement|sponsored|promoted|ad-|ads-|banner|popup)"
    );
    
    /**
     * 检查文本是否包含广告内容
     */
    public boolean containsAds(String text) {
        return AD_PATTERN.matcher(text).find();
    }
}
