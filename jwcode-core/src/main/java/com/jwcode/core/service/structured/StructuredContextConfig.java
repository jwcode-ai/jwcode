package com.jwcode.core.service.structured;

/**
 * 结构化上下文管理器配置。
 * v1.1 新增 AICL 协议配置项。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class StructuredContextConfig {
    
    private String sessionId = "default";
    private int maxActiveSize = 50;
    private int minRetainCount = 5;
    private boolean enableArchive = true;
    private int periodicEvalInterval = 10;
    
    // 安全配置
    private boolean enableIntentProtection = true;
    private boolean enableRefCountProtection = true;
    private boolean enableRecentProtection = true;
    private int recentMessageProtection = 5;
    
    // AI 评估配置
    private boolean enableAiEvaluation = false;
    private String aiEvaluatorClass;

    // ===== AICL 协议配置（v1.1） =====
    private boolean aiclEnabled = true;
    private long aiclTotalBudget = 8000;
    private double aiclEvictionThreshold = 0.8;
    private double aiclStopThreshold = 0.75;
    private int aiclDefaultTtl = 3;
    private int aiclMaxGeneration = 2;
    
    public static StructuredContextConfig defaultConfig() {
        return new StructuredContextConfig();
    }
    
    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public int getMaxActiveSize() { return maxActiveSize; }
    public void setMaxActiveSize(int maxActiveSize) { this.maxActiveSize = maxActiveSize; }
    
    public int getMinRetainCount() { return minRetainCount; }
    public void setMinRetainCount(int minRetainCount) { this.minRetainCount = minRetainCount; }
    
    public boolean isEnableArchive() { return enableArchive; }
    public void setEnableArchive(boolean enableArchive) { this.enableArchive = enableArchive; }
    
    public int getPeriodicEvalInterval() { return periodicEvalInterval; }
    public void setPeriodicEvalInterval(int periodicEvalInterval) { this.periodicEvalInterval = periodicEvalInterval; }
    
    public boolean isEnableIntentProtection() { return enableIntentProtection; }
    public void setEnableIntentProtection(boolean enableIntentProtection) { this.enableIntentProtection = enableIntentProtection; }
    
    public boolean isEnableRefCountProtection() { return enableRefCountProtection; }
    public void setEnableRefCountProtection(boolean enableRefCountProtection) { this.enableRefCountProtection = enableRefCountProtection; }
    
    public boolean isEnableRecentProtection() { return enableRecentProtection; }
    public void setEnableRecentProtection(boolean enableRecentProtection) { this.enableRecentProtection = enableRecentProtection; }
    
    public int getRecentMessageProtection() { return recentMessageProtection; }
    public void setRecentMessageProtection(int recentMessageProtection) { this.recentMessageProtection = recentMessageProtection; }
    
    public boolean isEnableAiEvaluation() { return enableAiEvaluation; }
    public void setEnableAiEvaluation(boolean enableAiEvaluation) { this.enableAiEvaluation = enableAiEvaluation; }
    
    public String getAiEvaluatorClass() { return aiEvaluatorClass; }
    public void setAiEvaluatorClass(String aiEvaluatorClass) { this.aiEvaluatorClass = aiEvaluatorClass; }

    // ===== AICL Getters/Setters =====
    public boolean isAiclEnabled() { return aiclEnabled; }
    public void setAiclEnabled(boolean aiclEnabled) { this.aiclEnabled = aiclEnabled; }

    public long getAiclTotalBudget() { return aiclTotalBudget; }
    public void setAiclTotalBudget(long aiclTotalBudget) { this.aiclTotalBudget = aiclTotalBudget; }

    public double getAiclEvictionThreshold() { return aiclEvictionThreshold; }
    public void setAiclEvictionThreshold(double aiclEvictionThreshold) { this.aiclEvictionThreshold = aiclEvictionThreshold; }

    public double getAiclStopThreshold() { return aiclStopThreshold; }
    public void setAiclStopThreshold(double aiclStopThreshold) { this.aiclStopThreshold = aiclStopThreshold; }

    public int getAiclDefaultTtl() { return aiclDefaultTtl; }
    public void setAiclDefaultTtl(int aiclDefaultTtl) { this.aiclDefaultTtl = aiclDefaultTtl; }

    public int getAiclMaxGeneration() { return aiclMaxGeneration; }
    public void setAiclMaxGeneration(int aiclMaxGeneration) { this.aiclMaxGeneration = aiclMaxGeneration; }
}