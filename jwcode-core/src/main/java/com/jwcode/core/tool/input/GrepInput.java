package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GrepTool 的输入参数
 * 
 * 用于文本搜索和正则表达式匹配
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrepInput(
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
    
    @JsonProperty("max_results")
    Integer maxResults
) {
    
    public GrepInput {
        if (isRegex == null) {
            isRegex = false;
        }
        if (ignoreCase == null) {
            ignoreCase = false;
        }
        if (context == null) {
            context = 2;
        } else if (context < 0) {
            context = 0;
        } else if (context > 10) {
            context = 10;
        }
        if (maxResults == null) {
            maxResults = 100;
        } else if (maxResults > 500) {
            maxResults = 500;
        } else if (maxResults < 1) {
            maxResults = 1;
        }
    }
    
    public GrepInput(String pattern) {
        this(pattern, null, null, null, null, null, null, null);
    }
    
    public GrepInput(String pattern, String path) {
        this(pattern, path, null, null, null, null, null, null);
    }
    
    /**
     * 获取最大结果数量
     */
    public int getMaxResults() {
        return maxResults != null ? maxResults : 100;
    }
    
    /**
     * 获取上下文行数
     */
    public int getContext() {
        return context != null ? context : 2;
    }
    
    /**
     * 检查是否使用正则表达式
     */
    public Boolean isRegex() {
        return isRegex;
    }
    
    /**
     * 检查是否忽略大小写
     */
    public Boolean isIgnoreCase() {
        return ignoreCase;
    }
    
    /**
     * 检查是否使用正则表达式（布尔值）
     */
    public boolean isRegexBoolean() {
        return isRegex != null && isRegex;
    }
    
    /**
     * 检查是否忽略大小写（布尔值）
     */
    public boolean isIgnoreCaseBoolean() {
        return ignoreCase != null && ignoreCase;
    }
    
    /**
     * 获取搜索路径，如果未指定则返回当前目录
     */
    public String getPathOrDefault() {
        return path != null && !path.isEmpty() ? path : ".";
    }
}