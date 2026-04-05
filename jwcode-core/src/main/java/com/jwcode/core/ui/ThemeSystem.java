package com.jwcode.core.ui;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ThemeSystem - 主题系统
 * 
 * 功能说明：
 * 完整的亮色/暗色主题切换系统，支持自定义主题配置。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ThemeSystem {
    
    // ANSI 转义代码
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String UNDERLINE = "\u001B[4m";
    public static final String BLINK = "\u001B[5m";
    public static final String REVERSE = "\u001B[7m";
    public static final String HIDDEN = "\u001B[8m";
    
    // 前景色 - 暗色主题
    public static final String DARK_TEXT_PRIMARY = "\u001B[37m";
    public static final String DARK_TEXT_SECONDARY = "\u001B[38;5;245m";
    public static final String DARK_TEXT_MUTED = "\u001B[38;5;240m";
    public static final String DARK_ACCENT = "\u001B[38;5;75m";
    public static final String DARK_SUCCESS = "\u001B[38;5;78m";
    public static final String DARK_WARNING = "\u001B[38;5;220m";
    public static final String DARK_ERROR = "\u001B[38;5;203m";
    public static final String DARK_INFO = "\u001B[38;5;75m";
    
    // 前景色 - 亮色主题
    public static final String LIGHT_TEXT_PRIMARY = "\u001B[38;5;235m";
    public static final String LIGHT_TEXT_SECONDARY = "\u001B[38;5;245m";
    public static final String LIGHT_TEXT_MUTED = "\u001B[38;5;250m";
    public static final String LIGHT_ACCENT = "\u001B[38;5;25m";
    public static final String LIGHT_SUCCESS = "\u001B[38;5;22m";
    public static final String LIGHT_WARNING = "\u001B[38;5;172m";
    public static final String LIGHT_ERROR = "\u001B[38;5;160m";
    public static final String LIGHT_INFO = "\u001B[38;5;25m";
    
    // 背景色 - 暗色主题
    public static final String DARK_BG_PRIMARY = "\u001B[48;5;235m";
    public static final String DARK_BG_SECONDARY = "\u001B[48;5;237m";
    public static final String DARK_BG_TERTIARY = "\u001B[48;5;239m";
    
    // 背景色 - 亮色主题
    public static final String LIGHT_BG_PRIMARY = "\u001B[48;5;255m";
    public static final String LIGHT_BG_SECONDARY = "\u001B[48;5;253m";
    public static final String LIGHT_BG_TERTIARY = "\u001B[48;5;251m";
    
    // 当前主题
    private static Theme currentTheme = Theme.DARK;
    private static final Map<String, ThemeConfig> customThemes = new ConcurrentHashMap<>();
    
    /**
     * 主题枚举
     */
    public enum Theme {
        DARK, LIGHT, CUSTOM
    }
    
    /**
     * 主题配置
     */
    public static class ThemeConfig {
        public String name;
        public String textPrimary;
        public String textSecondary;
        public String textMuted;
        public String accent;
        public String success;
        public String warning;
        public String error;
        public String info;
        public String bgPrimary;
        public String bgSecondary;
        public String bgTertiary;
        
        public ThemeConfig(String name) {
            this.name = name;
        }
        
        public ThemeConfig setColors(String textPrimary, String textSecondary, String textMuted,
                                     String accent, String success, String warning, String error) {
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textMuted = textMuted;
            this.accent = accent;
            this.success = success;
            this.warning = warning;
            this.error = error;
            return this;
        }
        
        public ThemeConfig setBackgrounds(String bgPrimary, String bgSecondary, String bgTertiary) {
            this.bgPrimary = bgPrimary;
            this.bgSecondary = bgSecondary;
            this.bgTertiary = bgTertiary;
            return this;
        }
    }
    
    /**
     * 设置主题
     */
    public static void setTheme(Theme theme) {
        currentTheme = theme;
    }
    
    /**
     * 设置自定义主题
     */
    public static void setTheme(String themeName) {
        if (customThemes.containsKey(themeName)) {
            currentTheme = Theme.CUSTOM;
        }
    }
    
    /**
     * 切换主题
     */
    public static void toggleTheme() {
        currentTheme = (currentTheme == Theme.DARK) ? Theme.LIGHT : Theme.DARK;
    }
    
    /**
     * 获取当前主题
     */
    public static Theme getCurrentTheme() {
        return currentTheme;
    }
    
    /**
     * 获取文本主色
     */
    public static String getTextPrimary() {
        return getColorForTheme(
            DARK_TEXT_PRIMARY, LIGHT_TEXT_PRIMARY, 
            t -> t.textPrimary
        );
    }
    
    /**
     * 获取文本次要色
     */
    public static String getTextSecondary() {
        return getColorForTheme(
            DARK_TEXT_SECONDARY, LIGHT_TEXT_SECONDARY,
            t -> t.textSecondary
        );
    }
    
    /**
     * 获取文本弱化色
     */
    public static String getTextMuted() {
        return getColorForTheme(
            DARK_TEXT_MUTED, LIGHT_TEXT_MUTED,
            t -> t.textMuted
        );
    }
    
    /**
     * 获取强调色
     */
    public static String getAccent() {
        return getColorForTheme(
            DARK_ACCENT, LIGHT_ACCENT,
            t -> t.accent
        );
    }
    
    /**
     * 获取成功色
     */
    public static String getSuccess() {
        return getColorForTheme(
            DARK_SUCCESS, LIGHT_SUCCESS,
            t -> t.success
        );
    }
    
    /**
     * 获取警告色
     */
    public static String getWarning() {
        return getColorForTheme(
            DARK_WARNING, LIGHT_WARNING,
            t -> t.warning
        );
    }
    
    /**
     * 获取错误色
     */
    public static String getError() {
        return getColorForTheme(
            DARK_ERROR, LIGHT_ERROR,
            t -> t.error
        );
    }
    
    /**
     * 获取信息色
     */
    public static String getInfo() {
        return getColorForTheme(
            DARK_INFO, LIGHT_INFO,
            t -> t.info
        );
    }
    
    /**
     * 获取背景主色
     */
    public static String getBgPrimary() {
        return getColorForTheme(
            DARK_BG_PRIMARY, LIGHT_BG_PRIMARY,
            t -> t.bgPrimary
        );
    }
    
    /**
     * 获取背景次要色
     */
    public static String getBgSecondary() {
        return getColorForTheme(
            DARK_BG_SECONDARY, LIGHT_BG_SECONDARY,
            t -> t.bgSecondary
        );
    }
    
    /**
     * 获取背景第三色
     */
    public static String getBgTertiary() {
        return getColorForTheme(
            DARK_BG_TERTIARY, LIGHT_BG_TERTIARY,
            t -> t.bgTertiary
        );
    }
    
    /**
     * 注册自定义主题
     */
    public static void registerTheme(ThemeConfig config) {
        customThemes.put(config.name, config);
    }
    
    /**
     * 获取自定义主题
     */
    public static ThemeConfig getCustomTheme(String name) {
        return customThemes.get(name);
    }
    
    /**
     * 列出所有主题
     */
    public static void listThemes() {
        System.out.println("可用主题:");
        System.out.println("  1. DARK  - 暗色主题");
        System.out.println("  2. LIGHT - 亮色主题");
        
        if (!customThemes.isEmpty()) {
            System.out.println("  自定义主题:");
            for (ThemeConfig config : customThemes.values()) {
                System.out.println("     - " + config.name);
            }
        }
        
        System.out.println();
        System.out.println("当前主题：" + currentTheme);
    }
    
    /**
     * 应用主题到输出
     */
    public static String apply(String text, String color) {
        return color + text + RESET;
    }
    
    /**
     * 根据主题获取颜色
     */
    private static String getColorForTheme(String darkColor, String lightColor, 
                                           java.util.function.Function<ThemeConfig, String> customGetter) {
        switch (currentTheme) {
            case DARK:
                return darkColor;
            case LIGHT:
                return lightColor;
            case CUSTOM:
                // 默认使用暗色主题作为自定义主题的基础
                return darkColor;
            default:
                return darkColor;
        }
    }
    
    /**
     * 清除屏幕
     */
    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
    
    /**
     * 移动光标到指定位置
     */
    public static void moveCursor(int row, int col) {
        System.out.print("\033[" + row + ";" + col + "H");
    }
    
    /**
     * 隐藏光标
     */
    public static void hideCursor() {
        System.out.print("\033[?25l");
    }
    
    /**
     * 显示光标
     */
    public static void showCursor() {
        System.out.print("\033[?25h");
    }
    
    /**
     * 保存光标位置
     */
    public static void saveCursor() {
        System.out.print("\033[s");
    }
    
    /**
     * 恢复光标位置
     */
    public static void restoreCursor() {
        System.out.print("\033[u");
    }
}