package com.jwcode.ui.terminal;

import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * TerminalBuffer - 终端缓冲区（v2.0 双缓冲 + 格子级 Diff）
 *
 * <p>管理终端屏幕的字符网格缓冲区，支持双缓冲帧间 Diff 检测。
 * 每次渲染写入 currentFrame，调用 {@link #endFrame()} 后
 * 与 previousFrame 逐格比较，输出最小变化集 {@link DiffRegion}。</p>
 *
 * <p>优化策略：连续变化格合并为水平区间，减少 ANSI 光标移动指令。</p>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class TerminalBuffer {

    private int width;
    private int height;

    /** 当前帧缓冲区 */
    private Cell[][] currentFrame;
    /** 上一帧缓冲区（用于 Diff） */
    private Cell[][] previousFrame;

    /** 是否强制全量刷新（resize 或 clear 后） */
    private boolean fullDirty;

    public TerminalBuffer() {
        this(80, 24);
    }

    public TerminalBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.currentFrame = newCellGrid(width, height);
        this.previousFrame = newCellGrid(width, height);
        this.fullDirty = true;
    }

    /**
     * 设置缓冲区大小。尺寸变化时触发全量刷新。
     */
    public void resize(int width, int height) {
        if (width != this.width || height != this.height) {
            this.width = width;
            this.height = height;
            this.currentFrame = newCellGrid(width, height);
            this.previousFrame = newCellGrid(width, height);
            this.fullDirty = true;
        }
    }

    /**
     * 清空当前帧（所有格子重置为空格 + 默认颜色）。
     */
    public void clear() {
        for (int y = 0; y < height; y++) {
            Arrays.fill(currentFrame[y], Cell.EMPTY);
        }
        fullDirty = true;
    }

    /**
     * 在指定位置写入字符。
     */
    public void writeCharacter(int x, int y, TextCharacter character) {
        if (!isValidPosition(x, y)) return;
        currentFrame[y][x] = new Cell(character);
    }

    /**
     * 在指定位置写入字符串（默认前景色）。
     */
    public void writeString(int x, int y, String text) {
        writeString(x, y, text, TextColor.ANSI.DEFAULT);
    }

    /**
     * 在指定位置写入带前景色的字符串。
     */
    public void writeString(int x, int y, String text, TextColor color) {
        writeString(x, y, text, color, TextColor.ANSI.DEFAULT);
    }

    /**
     * 在指定位置写入带前景色和背景色的字符串。
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
     * 获取指定位置的字符。
     */
    public TextCharacter getCharacter(int x, int y) {
        if (!isValidPosition(x, y)) {
            return new TextCharacter(' ', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT);
        }
        Cell cell = currentFrame[y][x];
        return cell != null ? cell.toTextCharacter() : Cell.EMPTY.toTextCharacter();
    }

    /**
     * 获取指定位置的字符（不带颜色信息）。
     */
    public char getCharAt(int x, int y) {
        return getCharacter(x, y).getCharacter();
    }

    /**
     * 获取行的内容。
     */
    public String getLine(int y) {
        StringBuilder sb = new StringBuilder(width);
        for (int x = 0; x < width; x++) {
            sb.append(getCharAt(x, y));
        }
        return sb.toString();
    }

    /**
     * 获取矩形区域的内容。
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
     * 用字符填充矩形区域。
     */
    public void fill(int x1, int y1, int x2, int y2, char character) {
        fill(x1, y1, x2, y2, new TextCharacter(character));
    }

    /**
     * 用指定 TextCharacter 填充矩形区域。
     */
    public void fill(int x1, int y1, int x2, int y2, TextCharacter character) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                writeCharacter(x, y, character);
            }
        }
    }

    /**
     * 检查位置是否有效。
     */
    public boolean isValidPosition(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /**
     * 获取缓冲区宽度。
     */
    public int getWidth() {
        return width;
    }

    /**
     * 获取缓冲区高度。
     */
    public int getHeight() {
        return height;
    }

    // ==================== 核心 Diff 引擎 ====================

    /**
     * 结束当前帧，与上一帧进行格子级 Diff，返回变化区域列表。
     *
     * <p>调用此方法后，当前帧变为上一帧，内部创建新的当前帧供下一轮写入。</p>
     *
     * @return 变化区域数组。若无变化返回空数组。
     */
    public DiffRegion[] endFrame() {
        List<DiffRegion> diffs = new ArrayList<>();

        if (fullDirty) {
            // 全量刷新：整个屏幕作为单个 DiffRegion
            fullDirty = false;
            diffs.add(new DiffRegion(0, 0, width - 1, height - 1, captureCurrentFrame()));
            swapBuffers();
            return diffs.toArray(new DiffRegion[0]);
        }

        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                // 跳过无变化的格子
                while (x < width && cellsEqual(currentFrame[y][x], previousFrame[y][x])) {
                    x++;
                }
                if (x >= width) break;

                // 找到连续变化区间的终点
                int startX = x;
                while (x < width && !cellsEqual(currentFrame[y][x], previousFrame[y][x])) {
                    x++;
                }
                int endX = x - 1;

                // 提取该行的变化字符
                char[] chars = new char[endX - startX + 1];
                for (int i = 0; i < chars.length; i++) {
                    chars[i] = currentFrame[y][startX + i] != null
                            ? currentFrame[y][startX + i].ch
                            : ' ';
                }
                diffs.add(new DiffRegion(startX, y, endX, y, chars));
            }
        }

        swapBuffers();
        return diffs.toArray(new DiffRegion[0]);
    }

    /**
     * 强制下一帧执行全量刷新。
     */
    public void markFullDirty() {
        this.fullDirty = true;
    }

    // ==================== 内部方法 ====================

    /**
     * 捕获当前帧的全部字符（用于全量刷新）。
     */
    private char[] captureCurrentFrame() {
        char[] all = new char[width * height];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Cell cell = currentFrame[y][x];
                all[idx++] = cell != null ? cell.ch : ' ';
            }
        }
        return all;
    }

    /**
     * 交换缓冲区：当前帧 → 上一帧，新建空当前帧。
     */
    private void swapBuffers() {
        previousFrame = currentFrame;
        currentFrame = newCellGrid(width, height);
    }

    /**
     * 比较两个格子是否相等（字符 + 前景色 + 背景色 + 样式）。
     */
    private boolean cellsEqual(Cell a, Cell b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.ch == b.ch
                && a.fgRed == b.fgRed && a.fgGreen == b.fgGreen && a.fgBlue == b.fgBlue
                && a.bgRed == b.bgRed && a.bgGreen == b.bgGreen && a.bgBlue == b.bgBlue
                && a.bold == b.bold && a.italic == b.italic && a.underline == b.underline;
    }

    /**
     * 创建新的空字符网格。
     */
    private Cell[][] newCellGrid(int w, int h) {
        Cell[][] grid = new Cell[h][w];
        for (int y = 0; y < h; y++) {
            Arrays.fill(grid[y], Cell.EMPTY);
        }
        return grid;
    }

    // ==================== 内部类 ====================

    /**
     * 单元格数据（不变对象风格，但允许复用 EMPTY 实例）。
     */
    public static class Cell {
        public static final Cell EMPTY = new Cell(' ',
                TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT,
                false, false, false);

        final char ch;
        final int fgRed, fgGreen, fgBlue;
        final int bgRed, bgGreen, bgBlue;
        final boolean bold;
        final boolean italic;
        final boolean underline;

        public Cell(TextCharacter tc) {
            this.ch = tc.getCharacter();
            TextColor fg = tc.getForegroundColor();
            TextColor bg = tc.getBackgroundColor();
            this.fgRed = getRed(fg);
            this.fgGreen = getGreen(fg);
            this.fgBlue = getBlue(fg);
            this.bgRed = getRed(bg);
            this.bgGreen = getGreen(bg);
            this.bgBlue = getBlue(bg);
            this.bold = false;  // TextCharacter 不直接支持，后续扩展
            this.italic = false;
            this.underline = false;
        }

        public Cell(char ch, TextColor fg, TextColor bg,
                    boolean bold, boolean italic, boolean underline) {
            this.ch = ch;
            this.fgRed = getRed(fg);
            this.fgGreen = getGreen(fg);
            this.fgBlue = getBlue(fg);
            this.bgRed = getRed(bg);
            this.bgGreen = getGreen(bg);
            this.bgBlue = getBlue(bg);
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
        }

        public TextCharacter toTextCharacter() {
            return new TextCharacter(ch,
                    TextColor.Indexed.fromRGB(fgRed, fgGreen, fgBlue),
                    TextColor.Indexed.fromRGB(bgRed, bgGreen, bgBlue));
        }

        private static int getRed(TextColor c) {
            if (c instanceof TextColor.Indexed) return ((TextColor.Indexed) c).getRed();
            if (c instanceof TextColor.RGB) return ((TextColor.RGB) c).getRed();
            return 0;
        }

        private static int getGreen(TextColor c) {
            if (c instanceof TextColor.Indexed) return ((TextColor.Indexed) c).getGreen();
            if (c instanceof TextColor.RGB) return ((TextColor.RGB) c).getGreen();
            return 0;
        }

        private static int getBlue(TextColor c) {
            if (c instanceof TextColor.Indexed) return ((TextColor.Indexed) c).getBlue();
            if (c instanceof TextColor.RGB) return ((TextColor.RGB) c).getBlue();
            return 0;
        }
    }

    /**
     * DiffRegion - 变化区域描述
     *
     * <p>表示终端中一个连续的水平变化区间，包含该行的起始列、结束列和字符数据。</p>
     */
    public static class DiffRegion {
        private final int x1;
        private final int y1;
        private final int x2;
        private final int y2;
        private final char[] chars;

        public DiffRegion(int x1, int y1, int x2, int y2, char[] chars) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.chars = chars;
        }

        public int getX1() { return x1; }
        public int getY1() { return y1; }
        public int getX2() { return x2; }
        public int getY2() { return y2; }
        public int getRow() { return y1; }
        public int getCol() { return x1; }
        public int getLength() { return x2 - x1 + 1; }
        public char[] getChars() { return chars; }

        /**
         * 是否为整行变化（从 0 到 width-1）。
         */
        public boolean isFullLine(int bufferWidth) {
            return x1 == 0 && x2 >= bufferWidth - 1;
        }

        @Override
        public String toString() {
            return "DiffRegion{y=" + y1 + ", x=" + x1 + "-" + x2 + ", len=" + getLength() + "}";
        }
    }
}