package com.jwcode.core.query;

import java.time.Duration;

/**
 * EngineConfig - 查询引擎配置
 * 
 * 功能说明：
 * 配置 QueryEngine 的行为，包括循环限制、超时、预算等。
 * 
 * 关键特性：
 * - 最大迭代次数：可配置或禁用限制
 * - Token 预算：防止过度消耗
 * - 时间预算：防止长时间运行
 * - 智能完成检测：替代硬性限制
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class EngineConfig {
    
    // 默认配置常量
    public static final int UNLIMITED_ITERATIONS = -1;
    public static final int DEFAULT_MAX_ITERATIONS = 100;
    public static final int DEFAULT_TOKEN_BUDGET = 100000;
    public static final Duration DEFAULT_MAX_DURATION = Duration.ofMinutes(30);
    
    private final int maxIterations;
    private final int tokenBudget;
    private final Duration maxDuration;
    private final boolean enableSmartCompletion;
    private final boolean enableLoopDetection;
    private final int loopDetectionThreshold;
    private final boolean debug;
    
    private EngineConfig(Builder builder) {
        this.maxIterations = builder.maxIterations;
        this.tokenBudget = builder.tokenBudget;
        this.maxDuration = builder.maxDuration;
        this.enableSmartCompletion = builder.enableSmartCompletion;
        this.enableLoopDetection = builder.enableLoopDetection;
        this.loopDetectionThreshold = builder.loopDetectionThreshold;
        this.debug = builder.debug;
    }
    
    // Getters
    public int getMaxIterations() { return maxIterations; }
    public int getTokenBudget() { return tokenBudget; }
    public Duration getMaxDuration() { return maxDuration; }
    public boolean isEnableSmartCompletion() { return enableSmartCompletion; }
    public boolean isEnableLoopDetection() { return enableLoopDetection; }
    public int getLoopDetectionThreshold() { return loopDetectionThreshold; }
    public boolean isDebug() { return debug; }
    
    /**
     * 检查是否启用迭代限制
     */
    public boolean isIterationLimitEnabled() {
        return maxIterations != UNLIMITED_ITERATIONS && maxIterations > 0;
    }
    
    /**
     * 检查迭代次数是否超限
     */
    public boolean isIterationExceeded(int currentIteration) {
        if (!isIterationLimitEnabled()) {
            return false;
        }
        return currentIteration >= maxIterations;
    }
    
    /**
     * 获取默认配置
     */
    public static EngineConfig defaultConfig() {
        return new Builder().build();
    }
    
    /**
     * 获取无限制配置（不限制迭代次数）
     */
    public static EngineConfig unlimited() {
        return new Builder()
            .maxIterations(UNLIMITED_ITERATIONS)
            .enableSmartCompletion(true)
            .enableLoopDetection(true)
            .build();
    }
    
    /**
     * 获取严格配置（严格限制）
     */
    public static EngineConfig strict() {
        return new Builder()
            .maxIterations(50)
            .tokenBudget(50000)
            .maxDuration(Duration.ofMinutes(10))
            .enableLoopDetection(true)
            .build();
    }
    
    /**
     * 获取开发配置（调试模式）
     */
    public static EngineConfig development() {
        return new Builder()
            .maxIterations(200)
            .debug(true)
            .enableSmartCompletion(true)
            .enableLoopDetection(true)
            .build();
    }
    
    @Override
    public String toString() {
        return String.format(
            "EngineConfig{maxIterations=%s, tokenBudget=%d, maxDuration=%s, smartCompletion=%b, loopDetection=%b}",
            maxIterations == UNLIMITED_ITERATIONS ? "unlimited" : maxIterations,
            tokenBudget,
            maxDuration,
            enableSmartCompletion,
            enableLoopDetection
        );
    }
    
    /**
     * 配置构建器
     */
    public static class Builder {
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private int tokenBudget = DEFAULT_TOKEN_BUDGET;
        private Duration maxDuration = DEFAULT_MAX_DURATION;
        private boolean enableSmartCompletion = true;
        private boolean enableLoopDetection = true;
        private int loopDetectionThreshold = 3;
        private boolean debug = false;
        
        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }
        
        public Builder unlimitedIterations() {
            this.maxIterations = UNLIMITED_ITERATIONS;
            return this;
        }
        
        public Builder tokenBudget(int tokenBudget) {
            this.tokenBudget = tokenBudget;
            return this;
        }
        
        public Builder maxDuration(Duration duration) {
            this.maxDuration = duration;
            return this;
        }
        
        public Builder enableSmartCompletion(boolean enable) {
            this.enableSmartCompletion = enable;
            return this;
        }
        
        public Builder enableLoopDetection(boolean enable) {
            this.enableLoopDetection = enable;
            return this;
        }
        
        public Builder loopDetectionThreshold(int threshold) {
            this.loopDetectionThreshold = threshold;
            return this;
        }
        
        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }
        
        public EngineConfig build() {
            return new EngineConfig(this);
        }
    }
}
