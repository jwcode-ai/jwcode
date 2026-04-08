package com.jwcode.ui.terminal;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TerminalRenderer - 终端渲染器
 * 
 * 功能说明：
 * 负责终端的渲染输出，提供高效的屏幕更新和绘制功能。
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
    
    public TerminalRenderer() throws IOException {
        this(new DefaultTerminalFactory().createScreen());
    }
    
    public TerminalRenderer(Screen screen) throws IOException {
        this.screen = screen;
        this.terminal = screen.getTerminal();
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
     * 在指定位置绘制文本
     */
    public void drawText(int x, int y, String text) {
        drawText(x, y, text, currentForegroundColor);
    }
    
    /**
     * 在指定位置绘制带颜色的文本
     */
    public void drawText(int x, int y, String text, TextColor color) {
        buffer.writeString(x, y, text, color);
        TextGraphics graphics = screen.newTextGraphics();
        graphics.setForegroundColor(color);
        graphics.putString(x, y, text);
    }
    
    /**
     * 绘制带背景色的文本
     */
    public void drawText(int x, int y, String text, TextColor fg, TextColor bg) {
        buffer.writeString(x, y, text, fg, bg);
        TextGraphics graphics = screen.newTextGraphics();
        graphics.setForegroundColor(fg);
        graphics.setBackgroundColor(bg);
        graphics.putString(x, y, text);
    }
    
    /**
     * 绘制盒子（边框）
     */
    public void drawBox(int x, int y, int width, int height) {
        drawBox(x, y, width, height, TextColor.ANSI.DEFAULT);
    }
    
    /**
     * 绘制带颜色的盒子
     */
    public void drawBox(int x, int y, int width, int height, TextColor color) {
        TextGraphics graphics = screen.newTextGraphics();
        graphics.setForegroundColor(color);
        
        // 绘制上下边框
        for (int i = 0; i < width; i++) {
            graphics.setCharacter(x + i, y, '─');
            graphics.setCharacter(x + i, y + height - 1, '─');
        }
        
        // 绘制左右边框
        for (int i = 0; i < height; i++) {
            graphics.setCharacter(x, y + i, '│');
            graphics.setCharacter(x + width - 1, y + i, '│');
        }
        
        // 绘制四个角
        graphics.setCharacter(x, y, '┌');
        graphics.setCharacter(x + width - 1, y, '┐');
        graphics.setCharacter(x, y + height - 1, '└');
        graphics.setCharacter(x + width - 1, y + height - 1, '┘');
    }
    
    /**
     * 绘制进度条
     */
    public void drawProgressBar(int x, int y, int width, int progress) {
        drawProgressBar(x, y, width, progress, TextColor.ANSI.GREEN, TextColor.ANSI.DEFAULT);
    }
    
    /**
     * 绘制带颜色的进度条
     */
    public void drawProgressBar(int x, int y, int width, int progress, TextColor color, TextColor bgColor) {
        TextGraphics graphics = screen.newTextGraphics();
        
        int filledWidth = (progress * (width - 2)) / 100;
        
        graphics.setBackgroundColor(bgColor);
        
        // 绘制背景
        for (int i = 0; i < width - 2; i++) {
            graphics.setCharacter(x + 1 + i, y, '░');
        }
        
        // 绘制填充部分
        graphics.setForegroundColor(color);
        for (int i = 0; i < filledWidth; i++) {
            graphics.setCharacter(x + 1 + i, y, '█');
        }
        
        // 绘制边框
        graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        graphics.setCharacter(x, y, '[');
        graphics.setCharacter(x + width - 1, y, ']');
    }
    
    /**
     * 绘制旋转加载器
     */
    public void drawSpinner(int x, int y, int frame) {
        char[] frames = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};
        char current = frames[frame % frames.length];
        
        TextGraphics graphics = screen.newTextGraphics();
        graphics.setForegroundColor(TextColor.ANSI.CYAN);
        graphics.setCharacter(x, y, current);
    }
    
    /**
     * 设置光标位置
     */
    public void setCursorPosition(int x, int y) {
        this.cursorX = x;
        this.cursorY = y;
        screen.setCursorPosition(new TerminalPosition(y, x));
    }
    
    /**
     * 隐藏光标
     */
    public void hideCursor() {
        screen.setCursorPosition(null);
    }
    
    /**
     * 显示光标
     */
    public void showCursor() {
        screen.setCursorPosition(new TerminalPosition(cursorY, cursorX));
    }
    
    /**
     * 刷新屏幕
     */
    public void refresh() {
        screen.refresh();
    }
    
    /**
     * 获取终端宽度
     */
    public int getWidth() {
        return terminal.getTerminalSize().getColumns();
    }
    
    /**
     * 获取终端高度
     */
    public int getHeight() {
        return terminal.getTerminalSize().getRows();
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
                .setInitialTerminalSize(80, 24);
            
            Screen screen = factory.createScreen();
            TerminalRenderer renderer = new TerminalRenderer(screen);
            
            if (!cursorVisible) {
                renderer.hideCursor();
            }
            
            return renderer;
        }
    }
}