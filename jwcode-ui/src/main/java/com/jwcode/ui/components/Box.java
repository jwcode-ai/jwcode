package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;

/**
 * Box - 容器组件
 * 
 * 功能说明：
 * 基础容器组件，用于包裹其他组件。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Box implements Component {
    
    private Component content;
    private int width;
    private int height;
    private BorderStyle borderStyle;
    private TextColor borderColor;
    private String title;
    private boolean showBorder;
    private int paddingLeft;
    private int paddingRight;
    private int paddingTop;
    private int paddingBottom;
    
    public Box() {
        this(null, 40, 10);
    }
    
    public Box(Component content, int width, int height) {
        this.content = content;
        this.width = width;
        this.height = height;
        this.borderStyle = BorderStyle.SINGLE;
        this.borderColor = TextColor.ANSI.DEFAULT;
        this.title = null;
        this.showBorder = true;
        this.paddingLeft = 1;
        this.paddingRight = 1;
        this.paddingTop = 0;
        this.paddingBottom = 0;
    }
    
    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        
        if (showBorder) {
            // 上边框（带标题）
            sb.append(renderTopBorder()).append("\n");
        }
        
        // 内容区域
        for (int i = 0; i < paddingTop; i++) {
            if (showBorder) sb.append('│');
            sb.append(repeat(' ', width - (showBorder ? 2 : 0)));
            if (showBorder) sb.append('│');
            sb.append("\n");
        }
        
        if (content != null) {
            String contentStr = content.render();
            String[] lines = contentStr.split("\n", -1);
            for (String line : lines) {
                if (showBorder) sb.append('│');
                sb.append(padOrTruncate(line, width - (showBorder ? 2 : 0)));
                if (showBorder) sb.append('│');
                sb.append("\n");
            }
        }
        
        for (int i = 0; i < paddingBottom; i++) {
            if (showBorder) sb.append('│');
            sb.append(repeat(' ', width - (showBorder ? 2 : 0)));
            if (showBorder) sb.append('│');
            sb.append("\n");
        }
        
        if (showBorder) {
            // 下边框
            sb.append(renderBottomBorder());
        }
        
        return sb.toString();
    }
    
    /**
     * 渲染上边框
     */
    private String renderTopBorder() {
        BorderChars chars = borderStyle.chars;
        StringBuilder sb = new StringBuilder();
        sb.append(chars.topLeft);
        
        if (title != null && !title.isEmpty()) {
            String titleText = " " + title + " ";
            int titleLen = titleText.length();
            int remainingWidth = width - 2 - titleLen;
            int leftLen = remainingWidth / 2;
            int rightLen = remainingWidth - leftLen;
            
            for (int i = 0; i < leftLen; i++) sb.append(chars.horizontal);
            sb.append(titleText);
            for (int i = 0; i < rightLen; i++) sb.append(chars.horizontal);
        } else {
            for (int i = 0; i < width - 2; i++) sb.append(chars.horizontal);
        }
        
        sb.append(chars.topRight);
        return sb.toString();
    }
    
    /**
     * 渲染下边框
     */
    private String renderBottomBorder() {
        BorderChars chars = borderStyle.chars;
        StringBuilder sb = new StringBuilder();
        sb.append(chars.bottomLeft);
        for (int i = 0; i < width - 2; i++) sb.append(chars.horizontal);
        sb.append(chars.bottomRight);
        return sb.toString();
    }
    
    /**
     * 填充或截断字符串
     */
    private String padOrTruncate(String str, int length) {
        if (str == null) str = "";
        if (str.length() >= length) {
            return str.substring(0, length - 3) + "...";
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < length) {
            sb.append(' ');
        }
        return sb.toString();
    }
    
    /**
     * 重复字符
     */
    private String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }
    
    /**
     * 设置内容组件
     */
    public void setContent(Component content) {
        this.content = content;
    }
    
    /**
     * 获取内容组件
     */
    public Component getContent() {
        return content;
    }
    
    /**
     * 设置宽度
     */
    public void setWidth(int width) {
        this.width = Math.max(4, width);
    }
    
    /**
     * 设置高度
     */
    public void setHeight(int height) {
        this.height = Math.max(2, height);
    }
    
    /**
     * 设置边框样式
     */
    public void setBorderStyle(BorderStyle style) {
        this.borderStyle = style;
    }
    
    /**
     * 设置边框颜色
     */
    public void setBorderColor(TextColor color) {
        this.borderColor = color;
    }
    
    /**
     * 设置标题
     */
    public void setTitle(String title) {
        this.title = title;
    }
    
    /**
     * 设置是否显示边框
     */
    public void setShowBorder(boolean show) {
        this.showBorder = show;
    }
    
    /**
     * 设置内边距
     */
    public void setPadding(int left, int right, int top, int bottom) {
        this.paddingLeft = left;
        this.paddingRight = right;
        this.paddingTop = top;
        this.paddingBottom = bottom;
    }
    
    /**
     * 边框样式枚举
     */
    public enum BorderStyle {
        SINGLE(new BorderChars('┌', '┐', '└', '┘', '─', '│')),
        DOUBLE(new BorderChars('╔', '╗', '╚', '╝', '═', '║')),
        ROUNDED(new BorderChars('╭', '╮', '╰', '╯', '─', '│')),
        NONE(new BorderChars(' ', ' ', ' ', ' ', ' ', ' '));
        
        public final BorderChars chars;
        
        BorderStyle(BorderChars chars) {
            this.chars = chars;
        }
    }
    
    /**
     * 边框字符类
     */
    public static class BorderChars {
        public final char topLeft;
        public final char topRight;
        public final char bottomLeft;
        public final char bottomRight;
        public final char horizontal;
        public final char vertical;
        
        public BorderChars(char topLeft, char topRight, char bottomLeft, 
                          char bottomRight, char horizontal, char vertical) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomLeft = bottomLeft;
            this.bottomRight = bottomRight;
            this.horizontal = horizontal;
            this.vertical = vertical;
        }
    }
}