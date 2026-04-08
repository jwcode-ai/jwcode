package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * StatusLine - 状态行组件
 * 
 * 功能说明：
 * 显示底部状态栏，支持多段信息显示、动态更新、颜色主题等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class StatusLine implements Component {
    
    private final List<StatusSegment> segments;
    private String separator;
    private TextColor defaultForegroundColor;
    private TextColor defaultBackgroundColor;
    private Position position;
    private boolean visible;
    private int minWidth;
    
    public StatusLine() {
        this.segments = new ArrayList<>();
        this.separator = " │ ";
        this.defaultForegroundColor = TextColor.ANSI.DEFAULT;
        this.defaultBackgroundColor = TextColor.ANSI.DEFAULT;
        this.position = Position.BOTTOM;
        this.visible = true;
        this.minWidth = 40;
    }
    
    @Override
    public String render() {
        if (!visible) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 根据位置添加上边框或下边框
        if (position == Position.TOP) {
            sb.append("┌");
        } else {
            sb.append("└");
        }
        
        // 渲染内容
        sb.append(renderContent());
        
        // 结束边框
        if (position == Position.TOP) {
            sb.append("┐");
        } else {
            sb.append("┘");
        }
        
        return sb.toString();
    }
    
    /**
     * 渲染内容
     */
    private String renderContent() {
        if (segments.isEmpty()) {
            return "─".repeat(Math.max(minWidth, 20));
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            StatusSegment segment = segments.get(i);
            String segmentStr = segment.render();
            
            if (i > 0 && !segmentStr.isEmpty()) {
                sb.append(separator);
            }
            
            sb.append(segmentStr);
        }
        
        // 填充剩余空间
        int contentLength = sb.length();
        int targetLength = Math.max(minWidth, contentLength);
        while (sb.length() < targetLength) {
            sb.append(" ");
        }
        
        return sb.toString();
    }
    
    /**
     * 添加段
     */
    public void addSegment(StatusSegment segment) {
        segments.add(segment);
    }
    
    /**
     * 添加段（快捷方式）
     */
    public void addSegment(String text, TextColor color) {
        segments.add(new StatusSegment(text, color));
    }
    
    /**
     * 添加段（带图标）
     */
    public void addSegment(String icon, String text, TextColor color) {
        segments.add(new StatusSegment(icon, text, color));
    }
    
    /**
     * 移除段
     */
    public void removeSegment(int index) {
        if (index >= 0 && index < segments.size()) {
            segments.remove(index);
        }
    }
    
    /**
     * 清除所有段
     */
    public void clearSegments() {
        segments.clear();
    }
    
    /**
     * 更新段
     */
    public void updateSegment(int index, String text) {
        if (index >= 0 && index < segments.size()) {
            segments.get(index).setText(text);
        }
    }
    
    /**
     * 设置分隔符
     */
    public void setSeparator(String separator) {
        this.separator = separator;
    }
    
    /**
     * 设置默认前景色
     */
    public void setDefaultForegroundColor(TextColor color) {
        this.defaultForegroundColor = color;
    }
    
    /**
     * 设置默认背景色
     */
    public void setDefaultBackgroundColor(TextColor color) {
        this.defaultBackgroundColor = color;
    }
    
    /**
     * 设置位置
     */
    public void setPosition(Position position) {
        this.position = position;
    }
    
    /**
     * 设置是否可见
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * 设置最小宽度
     */
    public void setMinWidth(int width) {
        this.minWidth = Math.max(20, width);
    }
    
    /**
     * 是否可见
     */
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * 获取位置
     */
    public Position getPosition() {
        return position;
    }
    
    /**
     * 切换可见性
     */
    public void toggleVisibility() {
        this.visible = !this.visible;
    }
    
    /**
     * 状态段类
     */
    public static class StatusSegment {
        private String icon;
        private String text;
        private TextColor color;
        private boolean dynamic;
        private SegmentValueProvider valueProvider;
        
        public StatusSegment(String text, TextColor color) {
            this(null, text, color);
        }
        
        public StatusSegment(String icon, String text, TextColor color) {
            this.icon = icon;
            this.text = text;
            this.color = color;
            this.dynamic = false;
        }
        
        /**
         * 创建动态段
         */
        public static StatusSegment dynamic(String icon, SegmentValueProvider provider, TextColor color) {
            StatusSegment segment = new StatusSegment(icon, "", color);
            segment.dynamic = true;
            segment.valueProvider = provider;
            return segment;
        }
        
        /**
         * 渲染段
         */
        public String render() {
            StringBuilder sb = new StringBuilder();
            
            if (icon != null && !icon.isEmpty()) {
                sb.append(icon).append(" ");
            }
            
            if (dynamic && valueProvider != null) {
                sb.append(valueProvider.getValue());
            } else {
                sb.append(text);
            }
            
            return sb.toString();
        }
        
        /**
         * 设置文本
         */
        public void setText(String text) {
            this.text = text;
        }
        
        /**
         * 获取文本
         */
        public String getText() {
            return text;
        }
        
        /**
         * 设置颜色
         */
        public void setColor(TextColor color) {
            this.color = color;
        }
        
        /**
         * 获取颜色
         */
        public TextColor getColor() {
            return color;
        }
        
        /**
         * 设置图标
         */
        public void setIcon(String icon) {
            this.icon = icon;
        }
    }
    
    /**
     * 动态段值提供者接口
     */
    @FunctionalInterface
    public interface SegmentValueProvider {
        String getValue();
    }
    
    /**
     * 位置枚举
     */
    public enum Position {
        TOP,    // 顶部
        BOTTOM  // 底部
    }
    
    /**
     * 预定义段工厂方法
     */
    public static class SegmentFactory {
        
        /**
         * 创建模式段
         */
        public static StatusSegment mode(String mode) {
            TextColor color = getColorForMode(mode);
            return new StatusSegment("📋", mode, color);
        }
        
        /**
         * 创建分支段
         */
        public static StatusSegment branch(String branch) {
            return new StatusSegment("🌿", branch, TextColor.ANSI.CYAN);
        }
        
        /**
         * 创建位置段
         */
        public static StatusSegment location(String location) {
            return new StatusSegment("📁", location, TextColor.ANSI.BLUE);
        }
        
        /**
         * 创建编码格式段
         */
        public static StatusSegment encoding(String encoding) {
            return new StatusSegment("📝", encoding, TextColor.ANSI.DEFAULT);
        }
        
        /**
         * 创建行数列段
         */
        public static StatusSegment lineColumn(int line, int column) {
            return new StatusSegment("📍", line + ":" + column, TextColor.ANSI.DEFAULT);
        }
        
        /**
         * 创建时间
         */
        public static StatusSegment time() {
            return StatusSegment.dynamic("🕐", () -> {
                java.time.LocalTime now = java.time.LocalTime.now();
                return String.format("%02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond());
            }, TextColor.ANSI.DEFAULT);
        }
        
        /**
         * 创建日期段
         */
        public static StatusSegment date() {
            return StatusSegment.dynamic("📅", () -> {
                java.time.LocalDate now = java.time.LocalDate.now();
                return now.toString();
            }, TextColor.ANSI.DEFAULT);
        }
        
        /**
         * 创建进度段
         */
        public static StatusSegment progress(int current, int total) {
            int percent = (total > 0) ? (current * 100 / total) : 0;
            return new StatusSegment("📊", current + "/" + total + " (" + percent + "%)", TextColor.ANSI.GREEN);
        }
        
        /**
         * 根据模式获取颜色
         */
        private static TextColor getColorForMode(String mode) {
            switch (mode.toLowerCase()) {
                case "insert":
                    return TextColor.ANSI.GREEN;
                case "normal":
                    return TextColor.ANSI.BLUE;
                case "visual":
                    return TextColor.ANSI.YELLOW;
                case "command":
                    return TextColor.ANSI.MAGENTA;
                default:
                    return TextColor.ANSI.DEFAULT;
            }
        }
    }
}