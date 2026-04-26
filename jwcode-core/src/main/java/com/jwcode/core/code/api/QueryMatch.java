package com.jwcode.core.code.api;

import java.util.Map;

/**
 * 查询匹配结果
 */
public class QueryMatch {
    private final String file;
    private final Range range;
    private final Map<String, SyntaxNode> captures;
    private final SyntaxNode rootMatch;
    private final double score; // 匹配评分（用于排序）
    
    public QueryMatch(String file, Range range, Map<String, SyntaxNode> captures, 
                      SyntaxNode rootMatch, double score) {
        this.file = file;
        this.range = range;
        this.captures = captures;
        this.rootMatch = rootMatch;
        this.score = score;
    }
    
    /**
     * 获取匹配的文件路径
     */
    public String getFile() {
        return file;
    }
    
    /**
     * 获取匹配的代码范围
     */
    public Range getRange() {
        return range;
    }
    
    /**
     * 获取所有捕获的节点
     */
    public Map<String, SyntaxNode> getCaptures() {
        return captures;
    }
    
    /**
     * 获取指定名称的捕获节点
     * @param name 捕获名称（不含 @）
     */
    public SyntaxNode getCapture(String name) {
        return captures.get(name);
    }
    
    /**
     * 获取根匹配节点（整个匹配范围）
     */
    public SyntaxNode getRootMatch() {
        return rootMatch;
    }
    
    /**
     * 获取匹配的源代码文本
     */
    public String getText() {
        return rootMatch.getText();
    }
    
    /**
     * 获取匹配评分
     */
    public double getScore() {
        return score;
    }
    
    /**
     * 转换为预览字符串（用于展示）
     */
    public String toPreview(int maxLength) {
        String text = getText();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    @Override
    public String toString() {
        return String.format("Match[%s@%s captures=%s]", file, range, captures.keySet());
    }
}