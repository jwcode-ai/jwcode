package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * GlobTool 的输出结果
 * 
 * 包含文件搜索和匹配的结果
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GlobOutput(
    @JsonProperty("pattern")
    String pattern,
    
    @JsonProperty("path")
    String path,
    
    @JsonProperty("is_regex")
    Boolean isRegex,
    
    @JsonProperty("exclude")
    String exclude,
    
    @JsonProperty("count")
    Integer count,
    
    @JsonProperty("max_results")
    Integer maxResults,
    
    @JsonProperty("files")
    List<String> files,
    
    @JsonProperty("truncated")
    Boolean truncated,
    
    @JsonProperty("truncation_reason")
    String truncationReason,
    
    @JsonProperty("summary")
    String summary
) {
    
    public GlobOutput {
        if (count == null) {
            count = 0;
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
    public static GlobOutput success(String pattern, String path, boolean isRegex, 
                                     String exclude, int maxResults, List<String> files) {
        int count = files.size();
        boolean truncated = count >= maxResults;
        String truncationReason = truncated ? "达到最大结果限制 " + maxResults : null;
        
        StringBuilder summary = new StringBuilder();
        summary.append("搜索模式：").append(pattern).append("\n");
        summary.append("搜索路径：").append(path).append("\n");
        if (exclude != null) {
            summary.append("排除模式：").append(exclude).append("\n");
        }
        summary.append("找到 ").append(count).append(" 个文件");
        if (truncated) {
            summary.append(" (已达到最大结果限制 ").append(maxResults).append(")");
        }
        
        return new GlobOutput(pattern, path, isRegex, exclude, count, maxResults, 
                             files, truncated, truncationReason, summary.toString());
    }
    
    /**
     * 创建错误输出
     */
    public static GlobOutput error(String pattern, String path, String errorMessage) {
        String summary = "搜索失败: " + errorMessage + "\n" +
                        "搜索模式: " + pattern + "\n" +
                        "搜索路径: " + path;
        return new GlobOutput(pattern, path, false, null, 0, 100, 
                             List.of(), false, null, summary);
    }
    
    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return truncationReason == null || !truncationReason.startsWith("搜索失败");
    }
    
    /**
     * 获取文件列表（如果为空则返回空列表）
     */
    public List<String> getFiles() {
        return files != null ? files : List.of();
    }
    
    /**
     * 检查是否被截断
     */
    public boolean isTruncated() {
        return truncated != null && truncated;
    }
}