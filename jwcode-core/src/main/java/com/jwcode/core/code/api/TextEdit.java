package com.jwcode.core.code.api;

/**
 * 文本编辑操作
 * 用于增量更新
 */
public record TextEdit(
    int startOffset,    // 起始字节偏移
    int endOffset,      // 结束字节偏移
    String newText      // 替换的新文本
) {
    /**
     * 创建插入操作
     */
    public static TextEdit insert(int offset, String text) {
        return new TextEdit(offset, offset, text);
    }
    
    /**
     * 创建删除操作
     */
    public static TextEdit delete(int start, int end) {
        return new TextEdit(start, end, "");
    }
    
    /**
     * 创建替换操作
     */
    public static TextEdit replace(int start, int end, String text) {
        return new TextEdit(start, end, text);
    }
    
    /**
     * 计算编辑后的新偏移量
     */
    public int getOffsetDelta() {
        return newText.length() - (endOffset - startOffset);
    }
}