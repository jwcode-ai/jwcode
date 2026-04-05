package com.jwcode.core.compact;

import java.util.*;

/**
 * CompactConfig - 会话压缩配置
 * 
 * 功能说明：
 * 管理会话压缩服务的配置选项。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompactConfig {
    
    /**
     * 压缩策略
     */
    private CompactStrategy strategy;
    
    /**
     * 目标 Token 数量（触发压缩的阈值）
     */
    private int targetTokens;
    
    /**
     * 最大 Token 数量（必须压缩的阈值）
     */
    private int maxTokens;
    
    /**
     * 最小保留消息数量
     */
    private int minMessages;
    
    /**
     * 最大保留消息数量
     */
    private int maxMessages;
    
    /**
     * 是否启用自动压缩
     */
    private boolean autoCompactEnabled;
    
    /**
     * 自动压缩检查间隔（秒）
     */
    private int checkIntervalSeconds;
    
    /**
     * 压缩前是否提示用户
     */
    private boolean promptBeforeCompact;
    
    /**
     * 是否保留压缩历史
     */
    private boolean keepCompactHistory;
    
    /**
     * 默认构造函数
     */
    public CompactConfig() {
        this.strategy = CompactStrategy.HYBRID;
        this.targetTokens = 80000;
        this.maxTokens = 100000;
        this.minMessages = 5;
        this.maxMessages = 50;
        this.autoCompactEnabled = true;
        this.checkIntervalSeconds = 30;
        this.promptBeforeCompact = false;
        this.keepCompactHistory = true;
    }
    
    /**
     * 创建默认配置
     */
    public static CompactConfig defaultConfig() {
        return new CompactConfig();
    }
    
    /**
     * 创建宽松配置
     */
    public static CompactConfig relaxedConfig() {
        CompactConfig config = new CompactConfig();
        config.setTargetTokens(100000);
        config.setMaxTokens(120000);
        config.setMaxMessages(80);
        return config;
    }
    
    /**
     * 创建严格配置
     */
    public static CompactConfig strictConfig() {
        CompactConfig config = new CompactConfig();
        config.setTargetTokens(50000);
        config.setMaxTokens(60000);
        config.setMaxMessages(30);
        return config;
    }
    
    // ==================== Getter 和 Setter ====================
    
    public CompactStrategy getStrategy() {
        return strategy;
    }
    
    public void setStrategy(CompactStrategy strategy) {
        this.strategy = strategy;
    }
    
    public int getTargetTokens() {
        return targetTokens;
    }
    
    public void setTargetTokens(int targetTokens) {
        this.targetTokens = targetTokens;
    }
    
    public int getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public int getMinMessages() {
        return minMessages;
    }
    
    public void setMinMessages(int minMessages) {
        this.minMessages = minMessages;
    }
    
    public int getMaxMessages() {
        return maxMessages;
    }
    
    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }
    
    public boolean isAutoCompactEnabled() {
        return autoCompactEnabled;
    }
    
    public void setAutoCompactEnabled(boolean autoCompactEnabled) {
        this.autoCompactEnabled = autoCompactEnabled;
    }
    
    public int getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }
    
    public void setCheckIntervalSeconds(int checkIntervalSeconds) {
        this.checkIntervalSeconds = checkIntervalSeconds;
    }
    
    public boolean isPromptBeforeCompact() {
        return promptBeforeCompact;
    }
    
    public void setPromptBeforeCompact(boolean promptBeforeCompact) {
        this.promptBeforeCompact = promptBeforeCompact;
    }
    
    public boolean isKeepCompactHistory() {
        return keepCompactHistory;
    }
    
    public void setKeepCompactHistory(boolean keepCompactHistory) {
        this.keepCompactHistory = keepCompactHistory;
    }
    
    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("strategy", strategy.name());
        map.put("targetTokens", targetTokens);
        map.put("maxTokens", maxTokens);
        map.put("minMessages", minMessages);
        map.put("maxMessages", maxMessages);
        map.put("autoCompactEnabled", autoCompactEnabled);
        map.put("checkIntervalSeconds", checkIntervalSeconds);
        map.put("promptBeforeCompact", promptBeforeCompact);
        map.put("keepCompactHistory", keepCompactHistory);
        return map;
    }
    
    /**
     * 从 Map 创建配置
     */
    @SuppressWarnings("unchecked")
    public static CompactConfig fromMap(Map<String, Object> map) {
        CompactConfig config = new CompactConfig();
        
        if (map.containsKey("strategy")) {
            config.setStrategy(CompactStrategy.valueOf((String) map.get("strategy")));
        }
        if (map.containsKey("targetTokens")) {
            config.setTargetTokens(((Number) map.get("targetTokens")).intValue());
        }
        if (map.containsKey("maxTokens")) {
            config.setMaxTokens(((Number) map.get("maxTokens")).intValue());
        }
        if (map.containsKey("minMessages")) {
            config.setMinMessages(((Number) map.get("minMessages")).intValue());
        }
        if (map.containsKey("maxMessages")) {
            config.setMaxMessages(((Number) map.get("maxMessages")).intValue());
        }
        if (map.containsKey("autoCompactEnabled")) {
            config.setAutoCompactEnabled((Boolean) map.get("autoCompactEnabled"));
        }
        if (map.containsKey("checkIntervalSeconds")) {
            config.setCheckIntervalSeconds(((Number) map.get("checkIntervalSeconds")).intValue());
        }
        if (map.containsKey("promptBeforeCompact")) {
            config.setPromptBeforeCompact((Boolean) map.get("promptBeforeCompact"));
        }
        if (map.containsKey("keepCompactHistory")) {
            config.setKeepCompactHistory((Boolean) map.get("keepCompactHistory"));
        }
        
        return config;
    }
    
    @Override
    public String toString() {
        return String.format("CompactConfig{strategy=%s, targetTokens=%d, maxTokens=%d}",
                strategy, targetTokens, maxTokens);
    }
}