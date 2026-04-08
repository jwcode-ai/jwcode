package com.jwcode.ui.components;

import com.googlecode.lanterna.TextColor;

/**
 * Text - 文本组件
 * 
 * 功能说明：
 * 显示带格式的文本内容。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class Text implements Component {
    
    private String content;
    private TextAlignment alignment;
    private TextColor color;
    private boolean bold;
    private boolean underline;
    private int maxWidth;
    private boolean wordWrap;
    
    public Text() {
        this("");
    }
    
    public Text(String content) {
        this.content = content;
        this.alignment = TextAlignment.LEFT;
        this.color = TextColor.ANSI.DEFAULT;
        this.bold = false;
        this.underline = false;
        this.maxWidth = 0; // 0 表示无限制
        this.wordWrap = true;
    }
    
    @Override
    public String render() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        String[] lines = content.split("\n", -1);
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // 处理自动换行
            if (wordWrap && maxWidth > 0 && line.length() > maxWidth) {
                String[] wrappedLines = wrapLine(line, maxWidth);
                for (String wrappedLine : wrappedLines) {
                    sb.append(applyAlignment(wrappedLine)).append("\n");
                }
            } else {
                sb.append(applyAlignment(line));
                if (i < lines.length - 1) {
                    sb.append("\n");
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 应用对齐方式
     */
    private String applyAlignment(String line) {
        if (maxWidth <= 0 || line.length() >= maxWidth) {
            return line;
        }
        
        StringBuilder sb = new StringBuilder();
        int padding = maxWidth - line.length();
        
        switch (alignment) {
            case LEFT:
                sb.append(line);
                break;
            case CENTER:
                int leftPadding = padding / 2;
                for (int i = 0; i < leftPadding; i++) sb.append(' ');
                sb.append(line);
                for (int i = leftPadding; i < padding; i++) sb.append(' ');
                break;
            case RIGHT:
                for (int i = 0; i < padding; i++) sb.append(' ');
                sb.append(line);
                break;
            case JUSTIFY:
                if (line.trim().length() <= 1) {
                    sb.append(line);
                } else {
                    sb.append(justifyLine(line, maxWidth));
                }
                break;
        }
        
        return sb.toString();
    }
    
    /**
     * 两端对齐
     */
    private String justifyLine(String line, int width) {
        String trimmed = line.trim();
        if (trimmed.length() >= width || trimmed.length() <= 1) {
            return trimmed;
        }
        
        StringBuilder sb = new StringBuilder();
        String[] words = trimmed.split("\\s+");
        int totalSpaces = width - trimmed.length() + words.length - 1;
        int spacesBetweenWords = words.length - 1;
        
        if (spacesBetweenWords <= 0) {
            sb.append(trimmed);
            while (sb.length() < width) sb.append(' ');
            return sb.toString();
        }
        
        int baseSpaces = totalSpaces / spacesBetweenWords;
        int extraSpaces = totalSpaces % spacesBetweenWords;
        
        for (int i = 0; i < words.length; i++) {
            sb.append(words[i]);
            if (i < words.length - 1) {
                for (int j = 0; j < baseSpaces; j++) sb.append(' ');
                if (i < extraSpaces) sb.append(' ');
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 换行处理
     */
    private String[] wrapLine(String line, int maxWidth) {
        if (line.length() <= maxWidth) {
            return new String[]{line};
        }
        
        java.util.List<String> lines = new java.util.ArrayList<>();
        int start = 0;
        
        while (start < line.length()) {
            int end = Math.min(start + maxWidth, line.length());
            
            // 尝试在单词边界处断开
            if (end < line.length() && wordWrap) {
                int lastSpace = line.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            
            lines.add(line.substring(start, end));
            start = end;
            
            // 跳过空格
            while (start < line.length() && line.charAt(start) == ' ') {
                start++;
            }
        }
        
        return lines.toArray(new String[0]);
    }
    
    /**
     * 设置内容
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * 获取内容
     */
    public String getContent() {
        return content;
    }
    
    /**
     * 设置对齐方式
     */
    public void setAlignment(TextAlignment alignment) {
        this.alignment = alignment;
    }
    
    /**
     * 设置颜色
     */
    public void setColor(TextColor color) {
        this.color = color;
    }
    
    /**
     * 设置粗体
     */
    public void setBold(boolean bold) {
        this.bold = bold;
    }
    
    /**
     * 设置下划线
     */
    public void setUnderline(boolean underline) {
        this.underline = underline;
    }
    
    /**
     * 设置最大宽度
     */
    public void setMaxWidth(int width) {
        this.maxWidth = width;
    }
    
    /**
     * 设置是否自动换行
     */
    public void setWordWrap(boolean wrap) {
        this.wordWrap = wrap;
    }
    
    /**
     * 创建标签文本
     */
    public static Text label(String text) {
        Text t = new Text(text);
        t.setBold(true);
        return t;
    }
    
    /**
     * 创建描述文本
     */
    public static Text description(String text) {
        return new Text(text);
    }
    
    /**
     * 创建标题文本
     */
    public static Text title(String text) {
        Text t = new Text(text);
        t.setBold(true);
        t.setAlignment(TextAlignment.CENTER);
        return t;
    }
    
    /**
     * 创建错误文本
     */
    public static Text error(String text) {
        Text t = new Text(text);
        t.setColor(TextColor.ANSI.RED);
        return t;
    }
    
    /**
     * 创建成功文本
     */
    public static Text success(String text) {
        Text t = new Text(text);
        t.setColor(TextColor.ANSI.GREEN);
        return t;
    }
    
    /**
     * 创建警告文本
     */
    public static Text warning(String text) {
        Text t = new Text(text);
        t.setColor(TextColor.ANSI.YELLOW);
        return t;
    }
    
    /**
     * 文本对齐枚举
     */
    public enum TextAlignment {
        LEFT,       // 左对齐
        CENTER,     // 居中对齐
        RIGHT,      // 右对齐
        JUSTIFY     // 两端对齐
    }
}