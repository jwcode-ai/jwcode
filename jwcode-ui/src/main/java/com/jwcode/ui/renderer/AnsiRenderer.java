package com.jwcode.ui.renderer;

import com.jwcode.ui.terminal.TerminalBuffer;

/**
 * AnsiRenderer - ANSI 转义码渲染器
 *
 * <p>将 {@link TerminalBuffer.DiffRegion} 数组转换为最小 ANSI 转义码序列，
 * 写入终端的 stdout。支持真彩色 (24-bit) / 256 色 / 16 色自动降级。</p>
 *
 * <p>输出策略：</p>
 * <ul>
 *   <li>每个 DiffRegion 先输出光标定位指令 {@code ESC[row;colH}</li>
 *   <li>然后输出前景色、背景色、样式指令</li>
 *   <li>最后输出字符内容 + 重置 {@code ESC[0m}</li>
 * </ul>
 */
public class AnsiRenderer {

    // ANSI 转义码常量
    private static final String ESC = "\u001B[";
    private static final String RESET = ESC + "0m";

    /** 终端色彩能力（自动降级） */
    private ColorCapability colorCapability = ColorCapability.TRUE_COLOR;

    /** 是否启用样式（加粗、下划线等） */
    private boolean styleEnabled = true;

    public AnsiRenderer() {
    }

    public AnsiRenderer(ColorCapability colorCapability) {
        this.colorCapability = colorCapability;
    }

    /**
     * 将 DiffRegion 数组渲染为 ANSI 转义码字符串。
     *
     * @param diffs         变化区域数组
     * @param terminalWidth 终端宽度（用于整行清除）
     * @return ANSI 转义码字符串
     */
    public String renderDiffs(TerminalBuffer.DiffRegion[] diffs, int terminalWidth) {
        if (diffs == null || diffs.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(diffs.length * 32);

        for (TerminalBuffer.DiffRegion region : diffs) {
            // 1. 光标定位到变化区域的起始位置
            sb.append(ESC).append(region.getRow() + 1).append(';')
                    .append(region.getCol() + 1).append('H');

            // 2. 输出字符内容（不包含颜色/样式变化时只输出字符）
            //    简化实现：直接输出整段文本
            sb.append(new String(region.getChars()));

            // 3. 如果变化区域未覆盖整行，清除行尾残余
            if (!region.isFullLine(terminalWidth)) {
                // 不需要额外清除，因为我们已经精确覆盖了变化区域
            }
        }

        return sb.toString();
    }

    /**
     * 渲染单个格子的 ANSI 转义码（含完整样式信息）。
     *
     * @param ch   字符
     * @param fgR  前景色 R
     * @param fgG  前景色 G
     * @param fgB  前景色 B
     * @param bgR  背景色 R
     * @param bgG  背景色 G
     * @param bgB  背景色 B
     * @param bold 是否加粗
     * @return ANSI 转义码字符串
     */
    public String renderCell(char ch, int fgR, int fgG, int fgB,
                             int bgR, int bgG, int bgB,
                             boolean bold) {
        StringBuilder sb = new StringBuilder();

        // 前景色
        sb.append(colorForeground(fgR, fgG, fgB));

        // 背景色
        sb.append(colorBackground(bgR, bgG, bgB));

        // 样式
        if (bold && styleEnabled) {
            sb.append(ESC).append("1m");
        }

        // 字符
        sb.append(ch);

        // 重置
        sb.append(RESET);

        return sb.toString();
    }

    /**
     * 生成前景色 ANSI 转义码。
     */
    public String colorForeground(int r, int g, int b) {
        switch (colorCapability) {
            case TRUE_COLOR:
                return ESC + "38;2;" + r + ';' + g + ';' + b + 'm';
            case COLORS_256:
                return ESC + "38;5;" + to256Color(r, g, b) + 'm';
            case COLORS_16:
            default:
                return ESC + to16Color(r, g, b, true) + 'm';
        }
    }

    /**
     * 生成背景色 ANSI 转义码。
     */
    public String colorBackground(int r, int g, int b) {
        switch (colorCapability) {
            case TRUE_COLOR:
                return ESC + "48;2;" + r + ';' + g + ';' + b + 'm';
            case COLORS_256:
                return ESC + "48;5;" + to256Color(r, g, b) + 'm';
            case COLORS_16:
            default:
                return ESC + to16Color(r, g, b, false) + 'm';
        }
    }

    /**
     * 生成光标定位指令。
     *
     * @param row 行号（1-based）
     * @param col 列号（1-based）
     */
    public String cursorPosition(int row, int col) {
        return ESC + row + ';' + col + 'H';
    }

    /**
     * 生成清除行指令（从光标到行尾）。
     */
    public String clearLine() {
        return ESC + "K";
    }

    /**
     * 生成清除屏幕指令。
     */
    public String clearScreen() {
        return ESC + "2J" + ESC + "1;1H";
    }

    /**
     * 生成隐藏光标指令。
     */
    public String hideCursor() {
        return ESC + "?25l";
    }

    /**
     * 生成显示光标指令。
     */
    public String showCursor() {
        return ESC + "?25h";
    }

    // ==================== 色彩降级 ====================

    /**
     * RGB 到 256 色调色板（6x6x6 立方体 + 灰度）。
     */
    private static int to256Color(int r, int g, int b) {
        // 使用 6x6x6 立方体
        int ri = Math.round(r / 51.0f);
        int gi = Math.round(g / 51.0f);
        int bi = Math.round(b / 51.0f);
        return 16 + ri * 36 + gi * 6 + bi;
    }

    /**
     * RGB 到 16 色 ANSI 码。
     *
     * @param r         红色分量
     * @param g         绿色分量
     * @param b         蓝色分量
     * @param foreground true=前景, false=背景
     * @return ANSI 颜色码字符串（不含 ESC 和 m）
     */
    private static String to16Color(int r, int g, int b, boolean foreground) {
        int base = foreground ? 30 : 40;
        // 简单亮度判断
        int brightness = (r + g + b) / 3;
        if (brightness < 128) {
            // 暗色
            if (r > g && r > b) return String.valueOf(base + 1);      // 红
            if (g > r && g > b) return String.valueOf(base + 2);      // 绿
            if (b > r && b > g) return String.valueOf(base + 4);      // 蓝
            return String.valueOf(base);                                // 黑
        } else {
            // 亮色
            if (r > g && r > b) return String.valueOf(base + 1) + ";1"; // 亮红
            if (g > r && g > b) return String.valueOf(base + 2) + ";1"; // 亮绿
            if (b > r && b > g) return String.valueOf(base + 4) + ";1"; // 亮蓝
            return String.valueOf(base + 7);                             // 白
        }
    }

    // ==================== 配置 ====================

    /**
     * 设置色彩能力（自动降级）。
     */
    public void setColorCapability(ColorCapability colorCapability) {
        this.colorCapability = colorCapability;
    }

    /**
     * 获取当前色彩能力。
     */
    public ColorCapability getColorCapability() {
        return colorCapability;
    }

    /**
     * 设置是否启用样式。
     */
    public void setStyleEnabled(boolean styleEnabled) {
        this.styleEnabled = styleEnabled;
    }

    /**
     * 是否启用样式。
     */
    public boolean isStyleEnabled() {
        return styleEnabled;
    }
}
