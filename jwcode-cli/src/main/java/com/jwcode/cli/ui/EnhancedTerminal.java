package com.jwcode.cli.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

import java.io.IOException;

/**
 * 增强型终端 UI - 参照 Kimi Code 的终端交互
 *
 * <p>v2.0 新增 ANSI 转义码渲染能力，支持格子级 Diff 增量渲染。
 * 与 jwcode-ui 模块的 InkPipeline 配合使用时，通过
 * {@link #renderFrame(String)} 接收 ANSI 字符串直接输出。</p>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class EnhancedTerminal {

    private Terminal terminal;
    private LineReader lineReader;
    private boolean supportsColor;

    /** 终端色彩能力级别 */
    public enum ColorLevel {
        COLORS_16, COLORS_256, TRUE_COLOR
    }

    /** 当前色彩能力 */
    private ColorLevel colorLevel = ColorLevel.COLORS_16;

    /** 终端宽度缓存 */
    private int terminalWidth = 80;
    /** 终端高度缓存 */
    private int terminalHeight = 24;

    // ANSI 颜色代码（兼容旧版 API）
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";

    public EnhancedTerminal() throws IOException {
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(true)
                .build();

        this.lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        this.supportsColor = terminal.getType() != null &&
                           !terminal.getType().equals(Terminal.TYPE_DUMB);

        this.colorLevel = detectColorLevel();

        // 获取终端尺寸
        try {
            org.jline.terminal.Size size = terminal.getSize();
            if (size != null) {
                this.terminalWidth = size.getColumns();
                this.terminalHeight = size.getRows();
            }
        } catch (Exception e) {
            // 使用默认值
        }
    }

    // ==================== 新渲染管线 API ====================

    /**
     * 渲染一帧：将预生成的 ANSI 转义码字符串输出到终端。
     *
     * <p>与 jwcode-ui 的 InkPipeline 配合使用，InkPipeline 负责
     * 将 DiffRegion 转换为 ANSI 字符串，此方法负责输出。</p>
     *
     * @param ansiOutput ANSI 转义码字符串
     */
    public void renderFrame(String ansiOutput) {
        if (ansiOutput == null || ansiOutput.isEmpty() || !supportsColor) return;
        terminal.writer().write(ansiOutput);
        terminal.writer().flush();
    }

    /**
     * 全屏刷新（清屏 + 重置光标位置）。
     */
    public void clearScreen() {
        terminal.writer().write(ESC + "2J" + ESC + "1;1H");
        terminal.writer().flush();
    }

    /**
     * 隐藏光标。
     */
    public void hideCursor() {
        terminal.writer().write(ESC + "?25l");
        terminal.writer().flush();
    }

    /**
     * 显示光标。
     */
    public void showCursor() {
        terminal.writer().write(ESC + "?25h");
        terminal.writer().flush();
    }

    /**
     * 获取终端宽度。
     */
    public int getTerminalWidth() {
        return terminalWidth;
    }

    /**
     * 获取终端高度。
     */
    public int getTerminalHeight() {
        return terminalHeight;
    }

    /**
     * 获取色彩能力级别。
     */
    public ColorLevel getColorLevel() {
        return colorLevel;
    }

    /**
     * 检测终端色彩能力。
     */
    private ColorLevel detectColorLevel() {
        if (!supportsColor) return ColorLevel.COLORS_16;

        String termEnv = System.getenv("COLORTERM");
        if (termEnv != null && (termEnv.contains("truecolor") || termEnv.contains("24bit"))) {
            return ColorLevel.TRUE_COLOR;
        }

        String term = System.getenv("TERM");
        if (term != null) {
            if (term.contains("truecolor") || term.contains("24bit")) {
                return ColorLevel.TRUE_COLOR;
            }
            if (term.contains("256")) {
                return ColorLevel.COLORS_256;
            }
        }

        String type = terminal.getType();
        if (type != null && (type.contains("xterm") || type.contains("256"))) {
            return ColorLevel.COLORS_256;
        }

        return ColorLevel.COLORS_16;
    }

    // ANSI 转义码常量
    private static final String ESC = "\u001B[";

    // ==================== 旧版兼容 API ====================

    public String readLine(String prompt) {
        return lineReader.readLine(colorize(prompt, CYAN));
    }

    public void println(String message) {
        terminal.writer().println(message);
        terminal.writer().flush();
    }

    public void printColored(String message, String color) {
        println(colorize(message, color));
    }

    public void printSuccess(String message) {
        println(colorize("✓ " + message, GREEN));
    }

    public void printError(String message) {
        println(colorize("✗ " + message, RED));
    }

    public void printToolCall(String toolName, String params) {
        println(colorize("┌─ Tool Call: " + toolName + " ─", CYAN));
        if (params != null && !params.isEmpty()) {
            println(colorize("│ " + params.substring(0, Math.min(100, params.length())), DIM));
        }
        println(colorize("└", CYAN));
    }

    public void printToolResult(String toolName, boolean success, String result) {
        String color = success ? GREEN : RED;
        String symbol = success ? "✓" : "✗";
        println(colorize("┌─ " + symbol + " " + toolName + " Result ─", color));
        if (result != null && !result.isEmpty()) {
            String[] lines = result.split("\\n");
            for (int i = 0; i < Math.min(lines.length, 3); i++) {
                println(colorize("│ " + lines[i], DIM));
            }
            if (lines.length > 3) {
                println(colorize("│ ... (" + (lines.length - 3) + " more lines)", DIM));
            }
        }
        println(colorize("└", color));
    }

    private String colorize(String text, String color) {
        if (!supportsColor) return text;
        return color + text + RESET;
    }

    public void close() {
        try {
            terminal.close();
        } catch (Exception e) {
            // 忽略
        }
    }
}
