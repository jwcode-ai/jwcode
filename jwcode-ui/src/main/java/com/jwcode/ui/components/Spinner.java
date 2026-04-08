package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;

/**
 * Spinner - 旋转加载器组件
 * 
 * 功能说明：
 * 显示加载状态的动画组件。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Spinner implements Component {
    
    private static final String[] DEFAULT_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] DOTS_FRAMES = {"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"};
    private static final String[] LINE_FRAMES = {"-", "\\", "|", "/"};
    private static final String[] CLOCK_FRAMES = {"🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗", "🕘", "🕙", "🕚", "🕛"};
    
    private int frame;
    private String[] frames;
    private SpinnerStyle style;
    private TextColor color;
    private String message;
    private boolean show_message;
    private long frameDelay;
    private long lastFrameTime;
    
    public Spinner() {
        this.frame = 0;
        this.frames = DEFAULT_FRAMES;
        this.style = SpinnerStyle.DEFAULT;
        this.color = TextColor.ANSI.CYAN;
        this.message = "加载中...";
        this.show_message = true;
        this.frameDelay = 80;
        this.lastFrameTime = System.currentTimeMillis();
    }
    
    @Override
    public String render() {
        updateFrame();
        
        StringBuilder sb = new StringBuilder();
        sb.append(getColorCode());
        sb.append(frames[frame % frames.length]);
        sb.append(ANSI_RESET);
        
        if (show_message && message != null) {
            sb.append(' ').append(message);
        }
        
        return sb.toString();
    }
    
    /**
     * 更新帧
     */
    private void updateFrame() {
        long now = System.currentTimeMillis();
        if (now - lastFrameTime >= frameDelay) {
            frame++;
            lastFrameTime = now;
        }
    }
    
    /**
     * 获取颜色代码
     */
    private String getColorCode() {
        switch (color) {
            case ANSI.RED: return "\u001B[31m";
            case ANSI.GREEN: return "\u001B[32m";
            case ANSI.YELLOW: return "\u001B[33m";
            case ANSI.BLUE: return "\u001B[34m";
            case ANSI.MAGENTA: return "\u001B[35m";
            case ANSI.CYAN: return "\u001B[36m";
            case ANSI.WHITE: return "\u001B[37m";
            default: return "\u001B[0m";
        }
    }
    
    private static final String ANSI_RESET = "\u001B[0m";
    
    /**
     * 设置样式
     */
    public void setStyle(SpinnerStyle style) {
        this.style = style;
        this.frames = style.frames;
    }
    
    /**
     * 设置颜色
     */
    public void setColor(TextColor color) {
        this.color = color;
    }
    
    /**
     * 设置消息
     */
    public void setMessage(String message) {
        this.message = message;
    }
    
    /**
     * 设置是否显示消息
     */
    public void setShowMessage(boolean show) {
        this.show_message = show;
    }
    
    /**
     * 设置帧延迟（毫秒）
     */
    public void setFrameDelay(long delay) {
        this.frameDelay = Math.max(16, delay);
    }
    
    /**
     * 重置 spinner
     */
    public void reset() {
        this.frame = 0;
        this.lastFrameTime = System.currentTimeMillis();
    }
    
    /**
     * 获取当前帧
     */
    public int getFrame() {
        return frame;
    }
    
    /**
     * Spinner 样式枚举
     */
    public enum SpinnerStyle {
        DEFAULT(DEFAULT_FRAMES),
        DOTS(DOTS_FRAMES),
        LINE(LINE_FRAMES),
        CLOCK(CLOCK_FRAMES);
        
        public final String[] frames;
        
        SpinnerStyle(String[] frames) {
            this.frames = frames;
        }
    }
}