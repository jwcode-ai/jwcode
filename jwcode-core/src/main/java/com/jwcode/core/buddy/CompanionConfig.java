package com.jwcode.core.buddy;

import java.util.*;

/**
 * CompanionConfig - 伙伴配置
 * 
 * 功能说明：
 * 管理 Companion 伙伴系统的配置选项。
 * 支持外观、行为、通知等各方面的自定义设置。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class CompanionConfig {
    
    /**
     * 伙伴外观主题
     */
    public enum CompanionTheme {
        /** 默认 */
        DEFAULT,
        /** 可爱 */
        CUTE,
        /** 专业 */
        PROFESSIONAL,
        /** 极简 */
        MINIMAL,
        /** 复古 */
        RETRO,
        /** 赛博朋克 */
        CYBERPUNK
    }
    
    /**
     * 伙伴尺寸
     */
    public enum CompanionSize {
        SMALL(32),
        MEDIUM(64),
        LARGE(96),
        XLARGE(128);
        
        private final int pixels;
        
        CompanionSize(int pixels) {
            this.pixels = pixels;
        }
        
        public int getPixels() {
            return pixels;
        }
    }
    
    // 外观配置
    private CompanionTheme theme;
    private CompanionSize size;
    private String customSprite;
    private boolean showEmojis;
    
    // 行为配置
    private boolean enabled;
    private boolean autoSleep;
    private long sleepTimeoutMs;
    private boolean showCelebrations;
    private boolean showEncouragements;
    
    // 通知配置
    private boolean showPrompts;
    private int maxPromptDuration;
    private boolean soundEnabled;
    private boolean vibrationEnabled;
    
    // 成就配置
    private boolean achievementsEnabled;
    private boolean showAchievementNotifications;
    
    /**
     * 默认构造函数
     */
    public CompanionConfig() {
        // 外观默认值
        this.theme = CompanionTheme.DEFAULT;
        this.size = CompanionSize.MEDIUM;
        this.customSprite = null;
        this.showEmojis = true;
        
        // 行为默认值
        this.enabled = true;
        this.autoSleep = true;
        this.sleepTimeoutMs = 300000; // 5 分钟
        this.showCelebrations = true;
        this.showEncouragements = true;
        
        // 通知默认值
        this.showPrompts = true;
        this.maxPromptDuration = 5000; // 5 秒
        this.soundEnabled = false;
        this.vibrationEnabled = false;
        
        // 成就默认值
        this.achievementsEnabled = true;
        this.showAchievementNotifications = true;
    }
    
    /**
     * 创建默认配置
     */
    public static CompanionConfig defaultConfig() {
        return new CompanionConfig();
    }
    
    /**
     * 创建禁用配置
     */
    public static CompanionConfig disabledConfig() {
        CompanionConfig config = new CompanionConfig();
        config.setEnabled(false);
        return config;
    }
    
    // ==================== Getter 和 Setter ====================
    
    public CompanionTheme getTheme() {
        return theme;
    }
    
    public void setTheme(CompanionTheme theme) {
        this.theme = theme;
    }
    
    public CompanionSize getSize() {
        return size;
    }
    
    public void setSize(CompanionSize size) {
        this.size = size;
    }
    
    public String getCustomSprite() {
        return customSprite;
    }
    
    public void setCustomSprite(String customSprite) {
        this.customSprite = customSprite;
    }
    
    public boolean isShowEmojis() {
        return showEmojis;
    }
    
    public void setShowEmojis(boolean showEmojis) {
        this.showEmojis = showEmojis;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isAutoSleep() {
        return autoSleep;
    }
    
    public void setAutoSleep(boolean autoSleep) {
        this.autoSleep = autoSleep;
    }
    
    public long getSleepTimeoutMs() {
        return sleepTimeoutMs;
    }
    
    public void setSleepTimeoutMs(long sleepTimeoutMs) {
        this.sleepTimeoutMs = sleepTimeoutMs;
    }
    
    public boolean isShowCelebrations() {
        return showCelebrations;
    }
    
    public void setShowCelebrations(boolean showCelebrations) {
        this.showCelebrations = showCelebrations;
    }
    
    public boolean isShowEncouragements() {
        return showEncouragements;
    }
    
    public void setShowEncouragements(boolean showEncouragements) {
        this.showEncouragements = showEncouragements;
    }
    
    public boolean isShowPrompts() {
        return showPrompts;
    }
    
    public void setShowPrompts(boolean showPrompts) {
        this.showPrompts = showPrompts;
    }
    
    public int getMaxPromptDuration() {
        return maxPromptDuration;
    }
    
    public void setMaxPromptDuration(int maxPromptDuration) {
        this.maxPromptDuration = maxPromptDuration;
    }
    
    public boolean isSoundEnabled() {
        return soundEnabled;
    }
    
    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }
    
    public boolean isVibrationEnabled() {
        return vibrationEnabled;
    }
    
    public void setVibrationEnabled(boolean vibrationEnabled) {
        this.vibrationEnabled = vibrationEnabled;
    }
    
    public boolean isAchievementsEnabled() {
        return achievementsEnabled;
    }
    
    public void setAchievementsEnabled(boolean achievementsEnabled) {
        this.achievementsEnabled = achievementsEnabled;
    }
    
    public boolean isShowAchievementNotifications() {
        return showAchievementNotifications;
    }
    
    public void setShowAchievementNotifications(boolean showAchievementNotifications) {
        this.showAchievementNotifications = showAchievementNotifications;
    }
    
    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("theme", theme.name());
        map.put("size", size.name());
        map.put("customSprite", customSprite);
        map.put("showEmojis", showEmojis);
        map.put("enabled", enabled);
        map.put("autoSleep", autoSleep);
        map.put("sleepTimeoutMs", sleepTimeoutMs);
        map.put("showCelebrations", showCelebrations);
        map.put("showEncouragements", showEncouragements);
        map.put("showPrompts", showPrompts);
        map.put("maxPromptDuration", maxPromptDuration);
        map.put("soundEnabled", soundEnabled);
        map.put("vibrationEnabled", vibrationEnabled);
        map.put("achievementsEnabled", achievementsEnabled);
        map.put("showAchievementNotifications", showAchievementNotifications);
        return map;
    }
    
    /**
     * 从 Map 创建配置
     */
    @SuppressWarnings("unchecked")
    public static CompanionConfig fromMap(Map<String, Object> map) {
        CompanionConfig config = new CompanionConfig();
        
        if (map.containsKey("theme")) {
            config.setTheme(CompanionTheme.valueOf((String) map.get("theme")));
        }
        if (map.containsKey("size")) {
            config.setSize(CompanionSize.valueOf((String) map.get("size")));
        }
        if (map.containsKey("customSprite")) {
            config.setCustomSprite((String) map.get("customSprite"));
        }
        if (map.containsKey("showEmojis")) {
            config.setShowEmojis((Boolean) map.get("showEmojis"));
        }
        if (map.containsKey("enabled")) {
            config.setEnabled((Boolean) map.get("enabled"));
        }
        if (map.containsKey("autoSleep")) {
            config.setAutoSleep((Boolean) map.get("autoSleep"));
        }
        if (map.containsKey("sleepTimeoutMs")) {
            config.setSleepTimeoutMs(((Number) map.get("sleepTimeoutMs")).longValue());
        }
        if (map.containsKey("showCelebrations")) {
            config.setShowCelebrations((Boolean) map.get("showCelebrations"));
        }
        if (map.containsKey("showEncouragements")) {
            config.setShowEncouragements((Boolean) map.get("showEncouragements"));
        }
        if (map.containsKey("showPrompts")) {
            config.setShowPrompts((Boolean) map.get("showPrompts"));
        }
        if (map.containsKey("maxPromptDuration")) {
            config.setMaxPromptDuration(((Number) map.get("maxPromptDuration")).intValue());
        }
        if (map.containsKey("soundEnabled")) {
            config.setSoundEnabled((Boolean) map.get("soundEnabled"));
        }
        if (map.containsKey("vibrationEnabled")) {
            config.setVibrationEnabled((Boolean) map.get("vibrationEnabled"));
        }
        if (map.containsKey("achievementsEnabled")) {
            config.setAchievementsEnabled((Boolean) map.get("achievementsEnabled"));
        }
        if (map.containsKey("showAchievementNotifications")) {
            config.setShowAchievementNotifications((Boolean) map.get("showAchievementNotifications"));
        }
        
        return config;
    }
    
    @Override
    public String toString() {
        return String.format("CompanionConfig{theme=%s, size=%s, enabled=%s}",
                theme, size, enabled);
    }
}