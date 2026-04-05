package com.jwcode.core.skill;

import com.jwcode.core.session.Session;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能执行上下文
 */
@Data
@Builder
public class SkillContext {
    
    /**
     * 用户输入
     */
    private String input;
    
    /**
     * 当前会话
     */
    private Session session;
    
    /**
     * 项目根目录
     */
    private String projectRoot;
    
    /**
     * 上下文参数
     */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();
    
    /**
     * 执行选项
     */
    @Builder.Default
    private ExecutionOptions options = ExecutionOptions.builder().build();
    
    /**
     * 获取参数
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key) {
        return (T) params.get(key);
    }
    
    /**
     * 设置参数
     */
    public void setParam(String key, Object value) {
        params.put(key, value);
    }
    
    /**
     * 创建子上下文
     */
    public SkillContext createChild(String subInput) {
        return SkillContext.builder()
            .input(subInput)
            .session(session)
            .projectRoot(projectRoot)
            .params(new HashMap<>(params))
            .options(options)
            .build();
    }
    
    @Data
    @Builder
    public static class ExecutionOptions {
        @Builder.Default
        private boolean verbose = false;
        
        @Builder.Default
        private int maxTokens = 4000;
        
        @Builder.Default
        private double temperature = 0.7;
        
        @Builder.Default
        private long timeoutMs = 120000;
        
        @Builder.Default
        private boolean streamOutput = false;
    }
}
