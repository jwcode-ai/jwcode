package com.jwcode.core.code.api;

/**
 * 源代码位置范围
 * 
 * @param file 文件路径
 * @param startLine 起始行（从 0 开始）
 * @param startColumn 起始列（从 0 开始）
 * @param endLine 结束行
 * @param endColumn 结束列
 */
public record Range(
    String file,
    int startLine,
    int startColumn,
    int endLine,
    int endColumn
) {
    
    /**
     * 创建单行范围
     */
    public static Range of(String file, int line, int startCol, int endCol) {
        return new Range(file, line, startCol, line, endCol);
    }
    
    /**
     * 创建单行单点范围
     */
    public static Range point(String file, int line, int column) {
        return new Range(file, line, column, line, column);
    }
    
    /**
     * 获取行数
     */
    public int getLineCount() {
        return endLine - startLine + 1;
    }
    
    /**
     * 检查是否包含指定位置
     */
    public boolean contains(int line, int column) {
        if (line < startLine || line > endLine) return false;
        if (line == startLine && column < startColumn) return false;
        if (line == endLine && column > endColumn) return false;
        return true;
    }
    
    /**
     * 检查是否包含另一个范围
     */
    public boolean contains(Range other) {
        return contains(other.startLine, other.startColumn) 
            && contains(other.endLine, other.endColumn);
    }
    
    /**
     * 检查是否与另一个范围重叠
     */
    public boolean overlaps(Range other) {
        return !(other.endLine < startLine || other.startLine > endLine);
    }
    
    /**
     * 合并两个范围
     */
    public Range merge(Range other) {
        int newStartLine = Math.min(this.startLine, other.startLine);
        int newStartCol = (this.startLine == newStartLine) 
            ? Math.min(this.startColumn, other.startColumn) 
            : (this.startLine < other.startLine ? this.startColumn : other.startColumn);
        
        int newEndLine = Math.max(this.endLine, other.endLine);
        int newEndCol = (this.endLine == newEndLine) 
            ? Math.max(this.endColumn, other.endColumn)
            : (this.endLine > other.endLine ? this.endColumn : other.endColumn);
        
        return new Range(this.file, newStartLine, newStartCol, newEndLine, newEndCol);
    }
    
    @Override
    public String toString() {
        if (startLine == endLine) {
            return String.format("%s:%d:%d-%d", file, startLine + 1, startColumn, endColumn);
        }
        return String.format("%s:%d:%d-%d:%d", file, 
            startLine + 1, startColumn, endLine + 1, endColumn);
    }
}
