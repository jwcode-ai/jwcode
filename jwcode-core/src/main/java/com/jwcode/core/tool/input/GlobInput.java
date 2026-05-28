package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GlobTool 的输入参数
 * 
 * 用于文件搜索和匹配
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GlobInput(
    @JsonProperty("pattern")
    String pattern,
    
    @JsonProperty("path")
    String path,
    
    @JsonProperty("is_regex")
    Boolean isRegex,
    
    @JsonProperty("exclude")
    String exclude,
    
    @JsonProperty("max_results")
    Integer maxResults
) {
    
    public GlobInput {
        if (isRegex == null) {
            isRegex = false;
        }
        if (maxResults == null) {
            maxResults = 500;
        } else if (maxResults > 5000) {
            maxResults = 5000;
        } else if (maxResults < 1) {
            maxResults = 1;
        }
    }
    
    public GlobInput(String pattern) {
        this(pattern, null, null, null, null);
    }
    
    public GlobInput(String pattern, String path) {
        this(pattern, path, null, null, null);
    }
    
    /**
     * 获取最大结果数量
     */
    public int getMaxResults() {
        return maxResults != null ? maxResults : 500;
    }
    
    /**
     * 检查是否使用正则表达式
     */
    public Boolean isRegex() {
        return isRegex;
    }
    
    /**
     * 检查是否使用正则表达式（布尔值）
     */
    public boolean isRegexBoolean() {
        return isRegex != null && isRegex;
    }
    
    /**
     * 获取搜索路径，如果未指定则返回当前目录
     */
    public String getPathOrDefault() {
        return path != null && !path.isEmpty() ? path : ".";
    }
}
