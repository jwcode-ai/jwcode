package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;

/**
 * ProgressBar - 进度条组件
 * 
 * 功能说明：
 * 显示操作进度的可视化组件。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ProgressBar implements Component {
    
    private int progress;
    private int width;
    private TextColor color;
    private TextColor backgroundColor;
    private boolean showPercentage;
    private boolean showBrackets;
    private char fillChar;
    private char emptyChar;
    
    public ProgressBar() {
        this(20);
    }
    
    public ProgressBar(int width) {
        this.progress = 0;
        this.width = width;
        this.color = TextColor.ANSI.GREEN;
        this.backgroundColor = TextColor.ANSI.DEFAULT;
        this.showPercentage = true;
        this.showBrackets = true;
        this.fillChar = '█';
        this.emptyChar = '░';
    }
    
    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        
        if (showBrackets) {
            sb.append('[');
        }
        
        int filledWidth = (progress * (width - 2)) / 100;
        
        // 填充部分
        sb.append(ANSI_COLORS.get(color));
        for (int i = 0; i < filledWidth; i++) {
            sb.append(fillChar);
        }
        
        // 空白部分
        sb.append(ANSI_COLORS.get(backgroundColor));
        for (int i = filledWidth; i < width - 2; i++) {
            sb.append(emptyChar);
        }
        
        sb.append(ANSI_COLORS.get(TextColor.ANSI.DEFAULT));
        
        if (showBrackets) {
            sb.append(']');
        }
        
        if (showPercentage) {
            sb.append(' ').append(progress).append('%');
        }
        
        return sb.toString();
    }
    
    /**
     * 设置进度值
     */
    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }
    
    /**
     * 获取进度值
     */
    public int getProgress() {
        return progress;
    }
    
    /**
     * 增加进度
     */
    public void increment(int amount) {
        setProgress(progress + amount);
    }
    
    /**
     * 设置进度条宽度
     */
    public void setWidth(int width) {
        this.width = Math.max(4, width);
    }
    
    /**
     * 获取进度条宽度
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * 设置颜色
     */
    public void setColor(TextColor color) {
        this.color = color;
    }
    
    /**
     * 设置背景色
     */
    public void setBackgroundColor(TextColor color) {
        this.backgroundColor = color;
    }
    
    /**
     * 设置是否显示百分比
     */
    public void setShowPercentage(boolean show) {
        this.showPercentage = show;
    }
    
    /**
     * 设置是否显示括号
     */
    public void setShowBrackets(boolean show) {
        this.showBrackets = show;
    }
    
    /**
     * 设置填充字符
     */
    public void setFillChar(char c) {
        this.fillChar = c;
    }
    
    /**
     * 设置空白字符
     */
    public void setEmptyChar(char c) {
        this.emptyChar = c;
    }
    
    /**
     * 重置进度条
     */
    public void reset() {
        this.progress = 0;
    }
    
    /**
     * 完成进度条
     */
    public void complete() {
        this.progress = 100;
    }
    
    /**
     * ANSI 颜色代码映射
     */
    private static final java.util.Map<TextColor.ANSI, String> ANSI_COLORS = new java.util.HashMap<>();
    static {
        ANSI_COLORS.put(TextColor.ANSI.DEFAULT, "\u001B[0m");
        ANSI_COLORS.put(TextColor.ANSI.BLACK, "\u001B[30m");
        ANSI_COLORS.put(TextColor.ANSI.RED, "\u001B[31m");
        ANSI_COLORS.put(TextColor.ANSI.GREEN, "\u001B[32m");
        ANSI_COLORS.put(TextColor.ANSI.YELLOW, "\u001B[33m");
        ANSI_COLORS.put(TextColor.ANSI.BLUE, "\u001B[34m");
        ANSI_COLORS.put(TextColor.ANSI.MAGENTA, "\u001B[35m");
        ANSI_COLORS.put(TextColor.ANSI.CYAN, "\u001B[36m");
        ANSI_COLORS.put(TextColor.ANSI.WHITE, "\u001B[37m");
    }
}