package com.jwcode.core.skill;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 技能执行结果
 */
@Data
@Builder
public class SkillResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 输出内容
     */
    private String output;
    
    /**
     * 结构化数据
     */
    private Map<String, Object> data;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 使用的 token 数
     */
    private TokenUsage tokenUsage;
    
    /**
     * 执行耗时（毫秒）
     */
    private long executionTimeMs;
    
    /**
     * 创建成功结果
     */
    public static SkillResult success(String output) {
        return SkillResult.builder()
            .success(true)
            .output(output)
            .build();
    }
    
    /**
     * 创建失败结果
     */
    public static SkillResult error(String error) {
        return SkillResult.builder()
            .success(false)
            .error(error)
            .build();
    }
    
    /**
     * 创建带数据的成功结果
     */
    public static SkillResult success(String output, Map<String, Object> data) {
        return SkillResult.builder()
            .success(true)
            .output(output)
            .data(data)
            .build();
    }
    
    @Data
    @Builder
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
