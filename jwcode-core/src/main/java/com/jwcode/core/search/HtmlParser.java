package com.jwcode.core.search;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * HTML 解析器
 * 
 * 使用 jsoup 解析 HTML 内容，提取标题、正文、元数据等
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class HtmlParser {
    
    private static final Logger logger = Logger.getLogger(HtmlParser.class.getName());
    
    private final Document document;
    private final String rawHtml;
    
    /**
     * 创建 HTML 解析器
     * 
     * @param html HTML 字符串
     */
    public HtmlParser(String html) {
        this.rawHtml = html;
        this.document = Jsoup.parse(html);
    }
    
    /**
     * 提取页面标题
     * 
     * @return 标题文本
     */
    public String extractTitle() {
        // 首先尝试 <title> 标签
        String title = document.title();
        if (!title.isEmpty()) {
            return title;
        }
        
        // 尝试 h1 标签
        Elements h1 = document.select("h1");
        if (!h1.isEmpty()) {
            return h1.first().text();
        }
        
        // 尝试 og:title meta 标签
        Element ogTitle = document.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            return ogTitle.attr("content");
        }
        
        return "";
    }
    
    /**
     * 提取页面元描述
     * 
     * @return 描述文本
     */
    public String extractDescription() {
        // 尝试 description meta 标签
        Element desc = document.selectFirst("meta[name=description]");
        if (desc != null) {
            return desc.attr("content");
        }
        
        // 尝试 og:description
        Element ogDesc = document.selectFirst("meta[property=og:description]");
        if (ogDesc != null) {
            return ogDesc.attr("content");
        }
        
        return "";
    }
    
    /**
     * 提取所有元数据
     * 
     * @return 元数据键值对
     */
    public Map<String, String> extractMetadata() {
        Map<String, String> metadata = new HashMap<>();
        
        // 提取所有 meta 标签
        Elements metaTags = document.select("meta");
        for (Element meta : metaTags) {
            String name = meta.attr("name");
            String property = meta.attr("property");
            String content = meta.attr("content");
            
            if (!name.isEmpty() && !content.isEmpty()) {
                metadata.put(name, content);
            } else if (!property.isEmpty() && !content.isEmpty()) {
                metadata.put(property, content);
            }
        }
        
        // 添加标题
        String title = extractTitle();
        if (!title.isEmpty()) {
            metadata.put("title", title);
        }
        
        // 添加 canonical URL
        Element canonical = document.selectFirst("link[rel=canonical]");
        if (canonical != null) {
            metadata.put("canonical", canonical.attr("href"));
        }
        
        return metadata;
    }
    
    /**
     * 提取正文内容（去除脚本、样式等）
     * 
     * @return 清理后的 HTML
     */
    public String extractBody() {
        // 创建文档副本以避免修改原始文档
        Document cleanDoc = Jsoup.parse(rawHtml);
        
        // 移除不需要的元素
        cleanDoc.select("script, style, nav, header, footer, aside, .advertisement, .ads").remove();
        
        // 移除注释
        cleanDoc.select("#comment").remove();
        
        // 获取 body 内容
        Element body = cleanDoc.body();
        if (body != null) {
            return body.html();
        }
        
        return cleanDoc.html();
    }
    
    /**
     * 提取纯文本内容
     * 
     * @return 纯文本
     */
    public String extractText() {
        // 移除脚本和样式
        Document textDoc = Jsoup.parse(rawHtml);
        textDoc.select("script, style").remove();
        
        // 获取文本
        return textDoc.body() != null ? textDoc.body().text() : textDoc.text();
    }
    
    /**
     * 提取主要内容区域
     * 
     * @return 主要内容 HTML
     */
    public String extractMainContent() {
        // 尝试常见的主要内容容器
        String[] selectors = {
            "main",
            "article",
            "[role=main]",
            ".content",
            ".main-content",
            "#content",
            "#main-content",
            ".post",
            ".article",
            ".entry-content"
        };
        
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                return element.html();
            }
        }
        
        // 如果没有找到主要内容区域，返回整个 body
        return extractBody();
    }
    
    /**
     * 提取所有链接
     * 
     * @return URL 到文本的映射
     */
    public Map<String, String> extractLinks() {
        Map<String, String> links = new HashMap<>();
        Elements anchorTags = document.select("a[href]");
        
        for (Element anchor : anchorTags) {
            String href = anchor.attr("href");
            String text = anchor.text().trim();
            if (!href.isEmpty() && !text.isEmpty()) {
                links.put(href, text);
            }
        }
        
        return links;
    }
    
    /**
     * 提取所有图片
     * 
     * @return 图片 URL 列表
     */
    public Map<String, String> extractImages() {
        Map<String, String> images = new HashMap<>();
        Elements imgTags = document.select("img[src]");
        
        for (Element img : imgTags) {
            String src = img.attr("src");
            String alt = img.attr("alt");
            if (!src.isEmpty()) {
                images.put(src, alt);
            }
        }
        
        return images;
    }
    
    /**
     * 获取文档的原始 HTML
     * 
     * @return 原始 HTML
     */
    public String getRawHtml() {
        return rawHtml;
    }
    
    /**
     * 获取解析后的文档对象
     * 
     * @return jsoup Document
     */
    public Document getDocument() {
        return document;
    }
    
    /**
     * 检查是否为有效的 HTML 文档
     * 
     * @return true 如果文档有效
     */
    public boolean isValid() {
        return document != null && document.body() != null;
    }
}
