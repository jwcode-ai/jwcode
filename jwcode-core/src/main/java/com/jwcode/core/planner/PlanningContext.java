package com.jwcode.core.planner;

import java.util.HashMap;
import java.util.Map;

/**
 * 规划上下文
 */
public class PlanningContext {
    
    /**
     * 项目根目录
     */
    private String projectRoot;
    
    /**
     * 首选 Agent 类型
     */
    private String preferredAgent;
    
    /**
     * 预算限制（token 数）
     */
    private int tokenBudget = 10000;
    
    /**
     * 时间限制（毫秒）
     */
    private long timeBudgetMs = 300000; // 5分钟
    
    /**
     * 是否允许并行执行
     */
    private boolean allowParallel = true;
    
    /**
     * 最大并行度
     */
    private int maxParallelism = 4;
    
    /**
     * 额外上下文数据
     */
    private Map<String, Object> data = new HashMap<>();
    
    public PlanningContext() {}
    
    // Getters
    public String getProjectRoot() { return projectRoot; }
    public String getPreferredAgent() { return preferredAgent; }
    public int getTokenBudget() { return tokenBudget; }
    public long getTimeBudgetMs() { return timeBudgetMs; }
    public boolean isAllowParallel() { return allowParallel; }
    public int getMaxParallelism() { return maxParallelism; }
    public Map<String, Object> getData() { 
        if (data == null) data = new HashMap<>();
        return data; 
    }
    
    // Setters
    public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }
    public void setPreferredAgent(String preferredAgent) { this.preferredAgent = preferredAgent; }
    public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }
    public void setTimeBudgetMs(long timeBudgetMs) { this.timeBudgetMs = timeBudgetMs; }
    public void setAllowParallel(boolean allowParallel) { this.allowParallel = allowParallel; }
    public void setMaxParallelism(int maxParallelism) { this.maxParallelism = maxParallelism; }
    public void setData(Map<String, Object> data) { this.data = data; }
    
    /**
     * 获取上下文值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }
    
    /**
     * 设置上下文值
     */
    public void set(String key, Object value) {
        data.put(key, value);
    }
    
    /**
     * 创建默认上下文
     */
    public static PlanningContext defaultContext() {
        return builder().build();
    }
    
    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("projectRoot", projectRoot);
        map.put("preferredAgent", preferredAgent);
        map.put("tokenBudget", tokenBudget);
        map.put("timeBudgetMs", timeBudgetMs);
        map.put("allowParallel", allowParallel);
        map.put("maxParallelism", maxParallelism);
        if (data != null) {
            map.putAll(data);
        }
        return map;
    }
    
    // Builder
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String projectRoot;
        private String preferredAgent;
        private int tokenBudget = 10000;
        private long timeBudgetMs = 300000;
        private boolean allowParallel = true;
        private int maxParallelism = 4;
        private Map<String, Object> data = new HashMap<>();
        
        public Builder projectRoot(String projectRoot) { this.projectRoot = projectRoot; return this; }
        public Builder preferredAgent(String preferredAgent) { this.preferredAgent = preferredAgent; return this; }
        public Builder tokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; return this; }
        public Builder timeBudgetMs(long timeBudgetMs) { this.timeBudgetMs = timeBudgetMs; return this; }
        public Builder allowParallel(boolean allowParallel) { this.allowParallel = allowParallel; return this; }
        public Builder maxParallelism(int maxParallelism) { this.maxParallelism = maxParallelism; return this; }
        public Builder data(Map<String, Object> data) { this.data = data; return this; }
        
        public PlanningContext build() {
            PlanningContext context = new PlanningContext();
            context.projectRoot = this.projectRoot;
            context.preferredAgent = this.preferredAgent;
            context.tokenBudget = this.tokenBudget;
            context.timeBudgetMs = this.timeBudgetMs;
            context.allowParallel = this.allowParallel;
            context.maxParallelism = this.maxParallelism;
            context.data = this.data;
            return context;
        }
    }
}
