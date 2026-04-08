package com.jwcode.ui.terminal;

import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;

import java.util.HashMap;
import java.util.Map;

/**
 * TerminalBuffer - 终端缓冲区
 * 
 * 功能说明：
 * 管理终端屏幕的缓冲区，支持高效的局部更新和脏区域检测。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TerminalBuffer {
    
    private int width;
    private int height;
    private final Map<String, Cell> cells;
    private final DirtyRegion dirtyRegion;
    
    public TerminalBuffer() {
        this(80, 24);
    }
    
    public TerminalBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.cells = new HashMap<>();
        this.dirtyRegion = new DirtyRegion();
    }
    
    /**
     * 设置缓冲区大小
     */
    public void resize(int width, int height) {
        if (width != this.width || height != this.height) {
            this.width = width;
            this.height = height;
            dirtyRegion.setFullDirty();
        }
    }
    
    /**
     * 清空缓冲区
     */
    public void clear() {
        cells.clear();
        dirtyRegion.setFullDirty();
    }
    
    /**
     * 在指定位置写入字符
     */
    public void writeCharacter(int x, int y, TextCharacter character) {
        if (isValidPosition(x, y)) {
            String key = getKey(x, y);
            cells.put(key, new Cell(character));
            dirtyRegion.expand(x, y);
        }
    }
    
    /**
     * 在指定位置写入字符串
     */
    public void writeString(int x, int y, String text) {
        writeString(x, y, text, TextColor.ANSI.DEFAULT);
    }
    
    /**
     * 在指定位置写入带颜色的字符串
     */
    public void writeString(int x, int y, String text, TextColor color) {
        writeString(x, y, text, color, TextColor.ANSI.DEFAULT);
    }
    
    /**
     * 在指定位置写入带前景色和背景色的字符串
     */
    public void writeString(int x, int y, String text, TextColor fg, TextColor bg) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                y++;
                continue;
            }
            if (c == '\r') {
                continue;
            }
            writeCharacter(x + i, y, new TextCharacter(c, fg, bg));
        }
    }
    
    /**
     * 获取指定位置的字符
     */
    public TextCharacter getCharacter(int x, int y) {
        String key = getKey(x, y);
        Cell cell = cells.get(key);
        if (cell != null) {
            return cell.character;
        }
        return new TextCharacter(' ', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT);
    }
    
    /**
     * 获取指定位置的字符（不带颜色信息）
     */
    public char getCharAt(int x, int y) {
        return getCharacter(x, y).getCharacter();
    }
    
    /**
     * 获取行的内容
     */
    public String getLine(int y) {
        StringBuilder sb = new StringBuilder(width);
        for (int x = 0; x < width; x++) {
            sb.append(getCharAt(x, y));
        }
        return sb.toString();
    }
    
    /**
     * 获取矩形区域的内容
     */
    public String getRegion(int x1, int y1, int x2, int y2) {
        StringBuilder sb = new StringBuilder();
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                sb.append(getCharAt(x, y));
            }
            if (y < y2) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
    
    /**
     * 填充矩形区域
     */
    public void fill(int x1, int y1, int x2, int y2, char character) {
        fill(x1, y1, x2, y2, new TextCharacter(character));
    }
    
    /**
     * 用指定字符填充矩形区域
     */
    public void fill(int x1, int y1, int x2, int y2, TextCharacter character) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                writeCharacter(x, y, character);
            }
        }
    }
    
    /**
     * 检查位置是否有效
     */
    public boolean isValidPosition(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }
    
    /**
     * 获取缓冲区宽度
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * 获取缓冲区高度
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * 获取脏区域
     */
    public DirtyRegion getDirtyRegion() {
        return dirtyRegion;
    }
    
    /**
     * 标记指定位置为脏
     */
    public void markDirty(int x, int y) {
        dirtyRegion.expand(x, y);
    }
    
    /**
     * 清除脏区域标记
     */
    public void clearDirty() {
        dirtyRegion.clear();
    }
    
    /**
     * 获取位置键
     */
    private String getKey(int x, int y) {
        return x + "," + y;
    }
    
    /**
     * 单元格类
     */
    private static class Cell {
        final TextCharacter character;
        
        Cell(TextCharacter character) {
            this.character = character;
        }
    }
    
    /**
     * 脏区域类 - 用于跟踪需要更新的区域
     */
    public static class DirtyRegion {
        private int minX;
        private int minY;
        private int maxX;
        private int maxY;
        private boolean fullDirty;
        
        public DirtyRegion() {
            this.minX = Integer.MAX_VALUE;
            this.minY = Integer.MAX_VALUE;
            this.maxX = Integer.MIN_VALUE;
            this.maxY = Integer.MIN_VALUE;
            this.fullDirty = false;
        }
        
        /**
         * 扩展脏区域
         */
        public void expand(int x, int y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        
        /**
         * 设置全屏脏
         */
        public void setFullDirty() {
            fullDirty = true;
        }
        
        /**
         * 清除脏区域
         */
        public void clear() {
            minX = Integer.MAX_VALUE;
            minY = Integer.MAX_VALUE;
            maxX = Integer.MIN_VALUE;
            maxY = Integer.MIN_VALUE;
            fullDirty = false;
        }
        
        /**
         * 是否是全屏脏
         */
        public boolean isFullDirty() {
            return fullDirty;
        }
        
        /**
         * 是否有脏区域
         */
        public boolean hasDirty() {
            return fullDirty || (minX <= maxX && minY <= maxY);
        }
        
        /**
         * 获取最小 X
         */
        public int getMinX() {
            return fullDirty ? 0 : minX;
        }
        
        /**
         * 获取最小 Y
         */
        public int getMinY() {
            return fullDirty ? 0 : minY;
        }
        
        /**
         * 获取最大 X
         */
        public int getMaxX() {
            return fullDirty ? Integer.MAX_VALUE : maxX;
        }
        
        /**
         * 获取最大 Y
         */
        public int getMaxY() {
            return fullDirty ? Integer.MAX_VALUE : maxY;
        }
        
        /**
         * 获取脏区域宽度
         */
        public int getWidth() {
            if (fullDirty) return Integer.MAX_VALUE;
            return maxX - minX + 1;
        }
        
        /**
         * 获取脏区域高度
         */
        public int getHeight() {
            if (fullDirty) return Integer.MAX_VALUE;
            return maxY - minY + 1;
        }
    }
}