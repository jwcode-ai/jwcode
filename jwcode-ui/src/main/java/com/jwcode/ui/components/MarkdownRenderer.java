package com.jwcode.ui.components;

import java.util.*;
import java.util.regex.*;

/**
 * MarkdownRenderer - Markdown 渲染组件
 * 
 * 支持基础 Markdown 语法的高亮渲染
 * 包括：标题、粗体、斜体、代码块、行内代码、列表、链接
 */
public class MarkdownRenderer implements Component {
    
    private String content;
    private int maxWidth;
    private boolean codeHighlight;
    private String codeLanguage;
    
    // ANSI 颜色
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String UNDERLINE = "\u001B[4m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String WHITE = "\u001B[37m";
    
    // LRU 缓存
    private static final int CACHE_SIZE = 500;
    private static final Map<String, ParsedMarkdown> cache = new LinkedHashMap<>(CACHE_SIZE) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > CACHE_SIZE;
        }
    };
    
    public MarkdownRenderer() {
        this.content = "";
        this.maxWidth = 80;
        this.codeHighlight = true;
    }
    
    public MarkdownRenderer content(String content) {
        this.content = content;
        return this;
    }
    
    public MarkdownRenderer maxWidth(int width) {
        this.maxWidth = width;
        return this;
    }
    
    public MarkdownRenderer codeHighlight(boolean highlight) {
        this.codeHighlight = highlight;
        return this;
    }
    
    @Override
    public String render() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // 检查 Markdown 语法（快速路径）
        if (!hasMarkdownSyntax(content)) {
            return wrapText(content);
        }
        
        // 使用缓存
        String cacheKey = content.hashCode() + "_" + maxWidth;
        ParsedMarkdown parsed = cache.get(cacheKey);
        
        if (parsed == null) {
            parsed = parseMarkdown(content);
            cache.put(cacheKey, parsed);
        }
        
        return renderParsed(parsed);
    }
    
    /**
     * 快速检查是否存在 Markdown 语法
     */
    private boolean hasMarkdownSyntax(String text) {
        // 检查前 500 个字符
        String check = text.length() > 500 ? text.substring(0, 500) : text;
        
        // 标题
        if (check.contains("# ")) return true;
        // 粗体
        if (check.contains("**") || check.contains("__")) return true;
        // 斜体
        if (check.contains("*") || check.contains("_")) return true;
        // 代码块
        if (check.contains("```")) return true;
        // 行内代码
        if (check.contains("`")) return true;
        // 列表
        if (check.contains("- ") || check.contains("* ") || check.contains("1. ")) return true;
        // 链接
        if (check.contains("](")) return true;
        
        return false;
    }
    
    private ParsedMarkdown parseMarkdown(String text) {
        ParsedMarkdown result = new ParsedMarkdown();
        String[] lines = text.split("\n");
        boolean inCodeBlock = false;
        StringBuilder codeBlock = new StringBuilder();
        String codeBlockLang = "";
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // 代码块开始/结束
            if (line.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeBlockLang = line.substring(3).trim();
                    codeBlock.setLength(0);
                } else {
                    inCodeBlock = false;
                    result.blocks.add(new RenderBlock(BlockType.CODE_BLOCK, codeBlock.toString(), codeBlockLang));
                }
                continue;
            }
            
            if (inCodeBlock) {
                if (codeBlock.length() > 0) codeBlock.append("\n");
                codeBlock.append(line);
                continue;
            }
            
            // 标题
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                if (level <= 6 && level < line.length() && line.charAt(level) == ' ') {
                    String titleText = line.substring(level + 1);
                    result.blocks.add(new RenderBlock(BlockType.HEADING, titleText, String.valueOf(level)));
                } else {
                    result.blocks.add(new RenderBlock(BlockType.TEXT, line));
                }
                continue;
            }
            
            // 列表项
            if (line.matches("^\\s*[-*+]\\s+.+") || line.matches("^\\s*\\d+\\.\\s+.+")) {
                result.blocks.add(new RenderBlock(BlockType.LIST_ITEM, line.trim()));
                continue;
            }
            
            // 水平线
            if (line.matches("^-{3,}$") || line.matches("^_{3,}$") || line.matches("^*{3,}$")) {
                result.blocks.add(new RenderBlock(BlockType.HR));
                continue;
            }
            
            // 空行
            if (line.trim().isEmpty()) {
                result.blocks.add(new RenderBlock(BlockType.BLANK));
                continue;
            }
            
            // 普通文本 - 解析内联样式
            result.blocks.add(new RenderBlock(BlockType.PARAGRAPH, line));
        }
        
        return result;
    }
    
    private String renderParsed(ParsedMarkdown parsed) {
        StringBuilder sb = new StringBuilder();
        
        for (RenderBlock block : parsed.blocks) {
            switch (block.type) {
                case HEADING:
                    sb.append(renderHeading(block.content, block.extra));
                    break;
                case CODE_BLOCK:
                    sb.append(renderCodeBlock(block.content, block.extra));
                    break;
                case LIST_ITEM:
                    sb.append(renderListItem(block.content));
                    break;
                case HR:
                    sb.append(renderHr());
                    break;
                case BLANK:
                    sb.append("\n");
                    break;
                case PARAGRAPH:
                    sb.append(renderParagraph(block.content));
                    break;
                case TEXT:
                    sb.append(wrapText(block.content)).append("\n");
                    break;
            }
        }
        
        return sb.toString();
    }
    
    private String renderHeading(String text, String level) {
        int lvl = Integer.parseInt(level);
        String color;
        switch (lvl) {
            case 1: color = CYAN; break;
            case 2: color = BLUE; break;
            case 3: color = GREEN; break;
            default: color = RESET;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(color);
        
        // 标题装饰
        int width = Math.min(text.length() + 4, maxWidth);
        sb.append("═".repeat(Math.max(0, width)));
        sb.append(RESET).append("\n");
        
        sb.append(BOLD).append(color).append("#".repeat(lvl)).append(" ");
        sb.append(RESET).append(renderInlineStyles(text));
        sb.append("\n");
        
        sb.append(color);
        sb.append("═".repeat(Math.max(0, width)));
        sb.append(RESET).append("\n");
        
        return sb.toString();
    }
    
    private String renderCodeBlock(String code, String language) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(DIM).append("┌─");
        for (int i = 0; i < maxWidth - 4; i++) sb.append("─");
        sb.append("┐").append(RESET).append("\n");
        
        if (language != null && !language.isEmpty() && codeHighlight) {
            sb.append(DIM).append("│").append(RESET).append(" ");
            sb.append(MAGENTA).append(language).append(RESET);
            sb.append(" ".repeat(Math.max(0, maxWidth - language.length() - 4)));
            sb.append(DIM).append(" │").append(RESET).append("\n");
        }
        
        String[] codeLines = code.split("\n");
        for (String line : codeLines) {
            sb.append(DIM).append("│").append(RESET).append(" ");
            if (codeHighlight) {
                sb.append(highlightSyntax(line));
            } else {
                sb.append(line);
            }
            int padding = maxWidth - line.length() - 3;
            sb.append(" ".repeat(Math.max(0, padding)));
            sb.append(DIM).append(" │").append(RESET).append("\n");
        }
        
        sb.append(DIM).append("└");
        for (int i = 0; i < maxWidth - 2; i++) sb.append("─");
        sb.append("┘").append(RESET);
        
        return sb.toString();
    }
    
    private String highlightSyntax(String line) {
        // 简单语法高亮
        String result = line;
        
        // 注释
        result = result.replaceAll("(?<=//.*)|(//.*$)", YELLOW + "$0" + RESET);
        result = result.replaceAll("#.*$", YELLOW + "$0" + RESET);
        
        // 字符串
        result = result.replaceAll("\"[^\"]*\"", GREEN + "$0" + RESET);
        result = result.replaceAll("'[^']*'", GREEN + "$0" + RESET);
        
        // 关键字
        String[] keywords = {"function", "class", "const", "let", "var", "if", "else", "for", "while", "return", "import", "export", "def", "class", "public", "private", "protected"};
        for (String kw : keywords) {
            result = result.replaceAll("\\b" + kw + "\\b", BLUE + "$0" + RESET);
        }
        
        // 数字
        result = result.replaceAll("\\b\\d+\\b", MAGENTA + "$0" + RESET);
        
        return result;
    }
    
    private String renderListItem(String text) {
        return "  • " + renderInlineStyles(text) + "\n";
    }
    
    private String renderHr() {
        return CYAN + "─".repeat(maxWidth) + RESET + "\n";
    }
    
    private String renderParagraph(String text) {
        return wrapText(renderInlineStyles(text)) + "\n";
    }
    
    private String renderInlineStyles(String text) {
        String result = text;
        
        // 行内代码
        result = result.replaceAll("`([^`]+)`", YELLOW + "`$1`" + RESET);
        
        // 粗体
        result = result.replaceAll("\\*\\*([^*]+)\\*\\*", BOLD + "$1" + RESET);
        result = result.replaceAll("__([^_]+)__", BOLD + "$1" + RESET);
        
        // 斜体
        result = result.replaceAll("\\*([^*]+)\\*", "$1");
        result = result.replaceAll("_([^_]+)_", "$1");
        
        // 链接 [text](url)
        Pattern linkPattern = Pattern.compile("\\[(.+?)\\]\\((.+?)\\)");
        Matcher matcher = linkPattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = UNDERLINE + BLUE + matcher.group(1) + RESET;
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        result = sb.toString();
        
        return result;
    }
    
    private String wrapText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        String[] words = text.split(" ");
        int currentLength = 0;
        
        for (String word : words) {
            if (currentLength + word.length() + 1 > maxWidth && currentLength > 0) {
                sb.append("\n");
                currentLength = 0;
            }
            
            if (currentLength > 0) {
                sb.append(" ");
                currentLength++;
            }
            
            sb.append(word);
            currentLength += word.length();
        }
        
        return sb.toString();
    }
    
    private static class ParsedMarkdown {
        List<RenderBlock> blocks = new ArrayList<>();
    }
    
    private static class RenderBlock {
        BlockType type;
        String content;
        String extra;
        
        RenderBlock(BlockType type) {
            this.type = type;
        }
        
        RenderBlock(BlockType type, String content) {
            this.type = type;
            this.content = content;
        }
        
        RenderBlock(BlockType type, String content, String extra) {
            this.type = type;
            this.content = content;
            this.extra = extra;
        }
    }
    
    private enum BlockType {
        HEADING, CODE_BLOCK, LIST_ITEM, HR, BLANK, PARAGRAPH, TEXT
    }
}
