package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * GrepTool 的输出结果
 * 
 * 包含文本搜索和正则表达式匹配的结果
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrepOutput(
    @JsonProperty("pattern")
    String pattern,
    
    @JsonProperty("path")
    String path,
    
    @JsonProperty("is_regex")
    Boolean isRegex,
    
    @JsonProperty("ignore_case")
    Boolean ignoreCase,
    
    @JsonProperty("file_pattern")
    String filePattern,
    
    @JsonProperty("exclude")
    String exclude,
    
    @JsonProperty("context")
    Integer context,
    
    @JsonProperty("count")
    Integer count,
    
    @JsonProperty("file_count")
    Integer fileCount,
    
    @JsonProperty("max_results")
    Integer maxResults,
    
    @JsonProperty("matches")
    List<GrepMatch> matches,
    
    @JsonProperty("truncated")
    Boolean truncated,
    
    @JsonProperty("truncation_reason")
    String truncationReason,
    
    @JsonProperty("summary")
    String summary
) {
    
    public GrepOutput {
        if (count == null) {
            count = 0;
        }
        if (fileCount == null) {
            fileCount = 0;
        }
        if (maxResults == null) {
            maxResults = 100;
        }
        if (truncated == null) {
            truncated = false;
        }
    }
    
    /**
     * 创建成功输出
     */
    public static GrepOutput success(String pattern, String path, boolean isRegex, 
                                     boolean ignoreCase, String filePattern, String exclude,
                                     int context, int maxResults, List<GrepMatch> matches) {
        int count = matches.stream().mapToInt(GrepMatch::getMatchCount).sum();
        int fileCount = (int) matches.stream().map(GrepMatch::getFilePath).distinct().count();
        boolean truncated = count >= maxResults;
        String truncationReason = truncated ? "达到最大结果限制 " + maxResults : null;
        
        StringBuilder summary = new StringBuilder();
        summary.append("搜索模式：").append(pattern).append("\n");
        summary.append("搜索路径：").append(path).append("\n");
        summary.append("上下文行数：").append(context).append("\n");
        summary.append("找到 ").append(count).append(" 个匹配，分布在 ").append(fileCount).append(" 个文件中");
        if (truncated) {
            summary.append(" (已达到最大结果限制 ").append(maxResults).append(")");
        }
        
        return new GrepOutput(pattern, path, isRegex, ignoreCase, filePattern, exclude,
                             context, count, fileCount, maxResults, matches, truncated,
                             truncationReason, summary.toString());
    }
    
    /**
     * 创建错误输出
     */
    public static GrepOutput error(String pattern, String path, String errorMessage) {
        String summary = "搜索失败: " + errorMessage + "\n" +
                        "搜索模式: " + pattern + "\n" +
                        "搜索路径: " + path;
        return new GrepOutput(pattern, path, false, false, null, null,
                             2, 0, 0, 100, List.of(), false, null, summary);
    }
    
    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return truncationReason == null || !truncationReason.startsWith("搜索失败");
    }
    
    /**
     * 检查是否被截断
     */
    public boolean isTruncated() {
        return truncated != null && truncated;
    }
    
    /**
     * 获取匹配列表（如果为空则返回空列表）
     */
    public List<GrepMatch> getMatches() {
        return matches != null ? matches : List.of();
    }
    
    /**
     * Grep 匹配结果
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static record GrepMatch(
        @JsonProperty("file_path")
        String filePath,
        
        @JsonProperty("line_number")
        Integer lineNumber,
        
        @JsonProperty("line")
        String line,
        
        @JsonProperty("start")
        Integer start,
        
        @JsonProperty("end")
        Integer end,
        
        @JsonProperty("before_context")
        List<String> beforeContext,
        
        @JsonProperty("after_context")
        List<String> afterContext,
        
        @JsonProperty("highlighted_line")
        String highlightedLine
    ) {
        
        public GrepMatch {
            if (beforeContext == null) {
                beforeContext = List.of();
            }
            if (afterContext == null) {
                afterContext = List.of();
            }
        }
        
        /**
         * 获取匹配数量（总是为1，用于统计）
         */
        public int getMatchCount() {
            return 1;
        }
        
        /**
         * 获取高亮显示的行
         */
        public String getHighlightedLine() {
            if (highlightedLine != null) {
                return highlightedLine;
            }
            if (start == null || end == null || start < 0 || end > line.length() || start >= end) {
                return line;
            }
            return line.substring(0, start) + "[[[" + line.substring(start, end) + "]]]" + line.substring(end);
        }
        
        /**
         * 获取文件路径（兼容性方法）
         */
        public String getFilePath() {
            return filePath;
        }
    }
}
