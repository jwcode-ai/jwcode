package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Pattern 工具输出结果
 */
public record PatternOutput(
    
    /** 操作是否成功 */
    @JsonProperty("success")
    boolean success,
    
    /** 匹配结果列表 */
    @JsonProperty("matches")
    List<MatchInfo> matches,
    
    /** 替换后的内容（如果执行替换） */
    @JsonProperty("replacedContent")
    String replacedContent,
    
    /** 匹配数量 */
    @JsonProperty("count")
    int count,
    
    /** 操作消息 */
    @JsonProperty("message")
    String message
) {
    public static PatternOutput success(List<MatchInfo> matches, int count) {
        return new PatternOutput(true, matches, null, count, "找到 " + count + " 个匹配");
    }
    
    public static PatternOutput success(String replacedContent, int count) {
        return new PatternOutput(true, null, replacedContent, count, "已完成替换");
    }
    
    public static PatternOutput error(String message) {
        return new PatternOutput(false, null, null, 0, message);
    }
    
    /**
     * 匹配信息
     */
    public record MatchInfo(
        @JsonProperty("file")
        String file,
        
        @JsonProperty("lineNumber")
        int lineNumber,
        
        @JsonProperty("line")
        String line,
        
        @JsonProperty("startIndex")
        int startIndex,
        
        @JsonProperty("endIndex")
        int endIndex,
        
        @JsonProperty("matchedText")
        String matchedText
    ) {}
}