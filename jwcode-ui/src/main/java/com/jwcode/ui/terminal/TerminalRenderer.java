package com.jwcode.ui.terminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TerminalRenderer - 终端渲染器（v2.0 Buffer + Diff 驱动）
 *
 * <p>不再直接写 Lanterna TextGraphics，改为写入 {@link TerminalBuffer}，
 * 通过 {@link #render()} 调用 {@link TerminalBuffer#endFrame()} 获取 Diff，
 * 然后将 Diff 输出到终端。</p>
 *
 * <p>与 {@code AnsiRenderer} 配合使用时，可将 Diff 转换为 ANSI 转义码输出
 * 到 JLine Terminal writer。</p>
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class TerminalRenderer {

    private final Screen screen;
    private final Terminal terminal;
    private final TerminalBuffer buffer;
    private int cursorX;
    private int cursorY;
    private TextColor currentForegroundColor;
    private TextColor currentBackgroundColor;

    /** 是否使用 Buffer+Diff 模式（否则回退到 Lanterna 直接绘制） */
    private boolean diffMode = true;

    public TerminalRenderer() throws IOException {
        this(new DefaultTerminalFactory().createScreen());
    }

    public TerminalRenderer(Screen screen) throws IOException {
        this.screen = screen;
        this.terminal = new DefaultTerminalFactory().createTerminal();
        this.buffer = new TerminalBuffer();
        this.cursorX = 0;
        this.cursorY = 0;
        this.currentForegroundColor = TextColor.ANSI.DEFAULT;
        this.currentBackgroundColor = TextColor.ANSI.DEFAULT;

        init();
    }

    /**
     * 初始化终端
     */
    private void init() throws IOException {
        screen.startScreen();
        screen.setCursorPosition(null);
    }

    /**
     * 清屏
     */
    public void clear() {
        buffer.clear();
        screen.clear();
    }

    /**
     * 在指定位置绘制文本（写入 Buffer）。
     */
    public void drawText(int x, int y, String text) {
        drawText(x, y, text, currentForegroundColor);
    }

    /**
     * 在指定位置绘制带颜色的文本（写入 Buffer）。
     */
    public void drawText(int x, int y, String text, TextColor color) {
        buffer.writeString(x, y, text, color);
        if (!diffMode) {
            TextGraphics graphics = screen.newTextGraphics();
            graphics.setForegroundColor(color);
            graphics.putString(x, y, text);
        }
    }

    /**
     * 绘制带背景色的文本（写入 Buffer）。
     */
    public void drawText(int x, int y, String text, TextColor fg, TextColor bg) {
        buffer.writeString(x, y, text, fg, bg);
        if (!diffMode) {
            TextGraphics graphics = screen.newTextGraphics();
            graphics.setForegroundColor(fg);
            graphics.setBackgroundColor(bg);
            graphics.putString(x, y, text);
        }
    }

    /**
     * 绘制盒子（边框）- 写入 Buffer。
     */
    public void drawBox(int x, int y, int width, int height) {
        drawBox(x, y, width, height, TextColor.ANSI.DEFAULT);
    }

    /**
     * 绘制带颜色的盒子 - 写入 Buffer。
     */
    public void drawBox(int x, int y, int width, int height, TextColor color) {
        // 写入 Buffer
        for (int i = 0; i < width; i++) {
            buffer.writeCharacter(x + i, y, new com.googlecode.lanterna.TextCharacter('─', color, TextColor.ANSI.DEFAULT));
            buffer.writeCharacter(x + i, y + height - 1, new com.googlecode.lanterna.TextCharacter('─', color, TextColor.ANSI.DEFAULT));
        }
        for (int i = 0; i < height; i++) {
            buffer.writeCharacter(x, y + i, new com.googlecode.lanterna.TextCharacter('│', color, TextColor.ANSI.DEFAULT));
            buffer.writeCharacter(x + width - 1, y + i, new com.googlecode.lanterna.TextCharacter('│', color, TextColor.ANSI.DEFAULT));
        }
        buffer.writeCharacter(x, y, new com.googlecode.lanterna.TextCharacter('┌', color, TextColor.ANSI.DEFAULT));
        buffer.writeCharacter(x + width - 1, y, new com.googlecode.lanterna.TextCharacter('┐', color, TextColor.ANSI.DEFAULT));
        buffer.writeCharacter(x, y + height - 1, new com.googlecode.lanterna.TextCharacter('└', color, TextColor.ANSI.DEFAULT));
        buffer.writeCharacter(x + width - 1, y + height - 1, new com.googlecode.lanterna.TextCharacter('┘', color, TextColor.ANSI.DEFAULT));

        if (!diffMode) {
            TextGraphics graphics = screen.newTextGraphics();
            graphics.setForegroundColor(color);
            for (int i = 0; i < width; i++) {
                graphics.setCharacter(x + i, y, '─');
                graphics.setCharacter(x + i, y + height - 1, '─');
            }
            for (int i = 0; i < height; i++) {
                graphics.setCharacter(x, y + i, '│');
                graphics.setCharacter(x + width - 1, y + i, '│');
            }
            graphics.setCharacter(x, y, '┌');
            graphics.setCharacter(x + width - 1, y, '┐');
            graphics.setCharacter(x, y + height - 1, '└');
            graphics.setCharacter(x + width - 1, y + height - 1, '┘');
        }
    }

    /**
     * 绘制进度条 - 写入 Buffer。
     */
    public void drawProgressBar(int x, int y, int width, int progress) {
        drawProgressBar(x, y, width, progress, TextColor.ANSI.GREEN, TextColor.ANSI.DEFAULT);
    }

    /**
     * 绘制带颜色的进度条 - 写入 Buffer。
     */
    public void drawProgressBar(int x, int y, int width, int progress, TextColor color, TextColor bgColor) {
        int filledWidth = (progress * (width - 2)) / 100;

        // 写入 Buffer
        for (int i = 0; i < width - 2; i++) {
            buffer.writeCharacter(x + 1 + i, y,
                    new com.googlecode.lanterna.TextCharacter(i < filledWidth ? '█' : '░',
                            i < filledWidth ? color : bgColor, bgColor));
        }
        buffer.writeCharacter(x, y, new com.googlecode.lanterna.TextCharacter('[', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
        buffer.writeCharacter(x + width - 1, y, new com.googlecode.lanterna.TextCharacter(']', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));

        if (!diffMode) {
            TextGraphics graphics = screen.newTextGraphics();
            graphics.setBackgroundColor(bgColor);
            for (int i = 0; i < width - 2; i++) {
                graphics.setCharacter(x + 1 + i, y, '░');
            }
            graphics.setForegroundColor(color);
            for (int i = 0; i < filledWidth; i++) {
                graphics.setCharacter(x + 1 + i, y, '█');
            }
            graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
            graphics.setCharacter(x, y, '[');
            graphics.setCharacter(x + width - 1, y, ']');
        }
    }

    /**
     * 绘制旋转加载器 - 写入 Buffer。
     */
    public void drawSpinner(int x, int y, int frame) {
        char[] frames = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};
        char current = frames[frame % frames.length];
        buffer.writeCharacter(x, y, new com.googlecode.lanterna.TextCharacter(current, TextColor.ANSI.CYAN, TextColor.ANSI.DEFAULT));

        if (!diffMode) {
            TextGraphics graphics = screen.newTextGraphics();
            graphics.setForegroundColor(TextColor.ANSI.CYAN);
            graphics.setCharacter(x, y, current);
        }
    }

    /**
     * 设置光标位置。
     */
    public void setCursorPosition(int x, int y) {
        this.cursorX = x;
        this.cursorY = y;
        screen.setCursorPosition(new TerminalPosition(y, x));
    }

    /**
     * 隐藏光标。
     */
    public void hideCursor() {
        screen.setCursorPosition(null);
    }

    /**
     * 显示光标。
     */
    public void showCursor() {
        screen.setCursorPosition(new TerminalPosition(cursorY, cursorX));
    }

    /**
     * 刷新屏幕 - 执行 Diff 并输出到 Lanterna Screen。
     * 在 diffMode=true 时，通过 Buffer Diff 实现增量更新。
     */
    public void refresh() {
        try {
            if (diffMode) {
                TerminalBuffer.DiffRegion[] diffs = buffer.endFrame();
                if (diffs.length > 0) {
                    TextGraphics graphics = screen.newTextGraphics();
                    for (TerminalBuffer.DiffRegion region : diffs) {
                        String text = new String(region.getChars());
                        graphics.putString(region.getX1(), region.getY1(), text);
                    }
                }
            }
            screen.refresh();
        } catch (IOException e) {
            // 忽略刷新异常
        }
    }

    /**
     * 执行 Buffer Diff 并返回变化区域（供 AnsiRenderer 使用）。
     *
     * @return DiffRegion 数组
     */
    public TerminalBuffer.DiffRegion[] flushDiff() {
        return buffer.endFrame();
    }

    /**
     * 设置是否使用 Diff 模式。
     */
    public void setDiffMode(boolean diffMode) {
        this.diffMode = diffMode;
    }

    /**
     * 是否使用 Diff 模式。
     */
    public boolean isDiffMode() {
        return diffMode;
    }
    
    /**
     * 获取终端宽度
     */
    public int getWidth() {
        try {
            return terminal.getTerminalSize().getColumns();
        } catch (IOException e) {
            return 80;
        }
    }
    
    /**
     * 获取终端高度
     */
    public int getHeight() {
        try {
            return terminal.getTerminalSize().getRows();
        } catch (IOException e) {
            return 24;
        }
    }
    
    /**
     * 获取缓冲区
     */
    public TerminalBuffer getBuffer() {
        return buffer;
    }
    
    /**
     * 设置前景色
     */
    public void setForegroundColor(TextColor color) {
        this.currentForegroundColor = color;
    }
    
    /**
     * 设置背景色
     */
    public void setBackgroundColor(TextColor color) {
        this.currentBackgroundColor = color;
    }
    
    /**
     * 关闭终端
     */
    public void close() throws IOException {
        screen.stopScreen();
        terminal.close();
    }
    
    /**
     * 创建终端渲染器构建器
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 构建器类
     */
    public static class Builder {
        private boolean fullScreen = true;
        private boolean cursorVisible = false;
        private TextColor backgroundColor = TextColor.ANSI.DEFAULT;
        
        public Builder fullScreen(boolean fullScreen) {
            this.fullScreen = fullScreen;
            return this;
        }
        
        public Builder cursorVisible(boolean visible) {
            this.cursorVisible = visible;
            return this;
        }
        
        public Builder backgroundColor(TextColor color) {
            this.backgroundColor = color;
            return this;
        }
        
        public TerminalRenderer build() throws IOException {
            DefaultTerminalFactory factory = new DefaultTerminalFactory()
                .setInitialTerminalSize(new TerminalSize(80, 24));
            
            Screen screen = factory.createScreen();
            TerminalRenderer renderer = new TerminalRenderer(screen);
            
            if (!cursorVisible) {
                renderer.hideCursor();
            }
            
            return renderer;
        }
    }
}