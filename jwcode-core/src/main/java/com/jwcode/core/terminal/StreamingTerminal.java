package com.jwcode.core.terminal;

import org.jline.reader.*;
import org.jline.terminal.*;
import org.jline.utils.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 流式终端渲染器
 * 支持双缓冲、脏区域跟踪和增量渲染
 */
public class StreamingTerminal implements AutoCloseable {
    
    private final Terminal terminal;
    private final LineReader lineReader;
    private final History history;
    
    // 双缓冲：前台缓冲区和后台缓冲区
    private char[][] frontBuffer;
    private char[][] backBuffer;
    private AttributedStyle[][] frontStyles;
    private AttributedStyle[][] backStyles;
    private boolean[][] dirty;
    
    // 脏区域跟踪
    private int dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY;
    
    // 流式输出状态
    private final StringBuilder streamingLine;
    private int streamingCursorX, streamingCursorY;
    private boolean isStreaming;
    
    // 回调函数
    private Consumer<String> onStreamingUpdate;
    
    public StreamingTerminal() throws IOException {
        this.terminal = TerminalBuilder.builder()
                .name("JWCode")
                .build();
        
        this.lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new JwCodeCompleter())
                .build();
        
        this.history = lineReader.getHistory();
        this.streamingLine = new StringBuilder();
        
        initializeBuffers();
    }
    
    private void initializeBuffers() {
        int width = terminal.getSize().getColumns();
        int height = terminal.getSize().getRows();
        
        frontBuffer = new char[height][width];
        backBuffer = new char[height][width];
        frontStyles = new AttributedStyle[height][width];
        backStyles = new AttributedStyle[height][width];
        dirty = new boolean[height][width];
        
        // 初始化为空格
        for (int y = 0; y < height; y++) {
            Arrays.fill(frontBuffer[y], ' ');
            Arrays.fill(backBuffer[y], ' ');
        }
        
        clearDirty();
    }
    
    // ==================== 流式输出支持 ====================
    
    /**
     * 开始流式输出
     */
    public void startStreaming(int x, int y) {
        this.streamingCursorX = x;
        this.streamingCursorY = y;
        this.isStreaming = true;
        this.streamingLine.setLength(0);
    }
    
    /**
     * 流式输出单个字符
     */
    public void streamChar(char c) {
        if (!isStreaming) return;
        
        if (c == '\n') {
            // 换行
            flushStreamingLine();
            streamingCursorY++;
            streamingCursorX = 0;
        } else if (c == '\r') {
            // 回车
            streamingCursorX = 0;
        } else {
            // 普通字符
            if (streamingCursorX < terminal.getSize().getColumns()) {
                streamingLine.append(c);
                
                // 实时显示（最小化更新）
                if (streamingLine.length() % 10 == 0) {
                    flushStreamingLine();
                }
            }
        }
    }
    
    /**
     * 流式输出字符串
     */
    public void streamString(String text) {
        for (char c : text.toCharArray()) {
            streamChar(c);
        }
    }
    
    /**
     * 刷新流式行
     */
    private void flushStreamingLine() {
        String line = streamingLine.toString();
        if (!line.isEmpty()) {
            // 移动光标到起始位置
            terminal.writer().print("\r");
            terminal.writer().print(line);
            
            // 清除行尾
            int clearCount = 50; // 保守估计
            for (int i = 0; i < clearCount; i++) {
                terminal.writer().print(' ');
            }
            terminal.writer().print("\r");
            terminal.writer().print(line);
            terminal.flush();
        }
    }
    
    /**
     * 结束流式输出
     */
    public void endStreaming() {
        flushStreamingLine();
        this.isStreaming = false;
    }
    
    /**
     * 设置流式更新回调
     */
    public void setOnStreamingUpdate(Consumer<String> callback) {
        this.onStreamingUpdate = callback;
    }
    
    // ==================== 双缓冲渲染 ====================
    
    /**
     * 标记脏区域
     */
    private void markDirty(int x, int y) {
        dirty[y][x] = true;
        dirtyMinX = Math.min(dirtyMinX, x);
        dirtyMinY = Math.min(dirtyMinY, y);
        dirtyMaxX = Math.max(dirtyMaxX, x);
        dirtyMaxY = Math.max(dirtyMaxY, y);
    }
    
    /**
     * 清除脏标记
     */
    private void clearDirty() {
        dirtyMinX = Integer.MAX_VALUE;
        dirtyMinY = Integer.MAX_VALUE;
        dirtyMaxX = Integer.MIN_VALUE;
        dirtyMaxY = Integer.MIN_VALUE;
    }
    
    /**
     * 交换缓冲区和渲染
     */
    public void swapBuffers() {
        // 复制后台缓冲区到前台
        int height = terminal.getSize().getRows();
        int width = terminal.getSize().getColumns();
        
        for (int y = 0; y < height; y++) {
            System.arraycopy(backBuffer[y], 0, frontBuffer[y], 0, width);
            System.arraycopy(backStyles[y], 0, frontStyles[y], 0, width);
        }
        
        // 渲染脏区域
        renderDirtyRegion();
        
        // 交换引用
        char[][] tempBuffer = frontBuffer;
        frontBuffer = backBuffer;
        backBuffer = tempBuffer;
        
        AttributedStyle[][] tempStyles = frontStyles;
        frontStyles = backStyles;
        backStyles = tempStyles;
        
        // 清除脏标记
        for (int y = 0; y < height; y++) {
            Arrays.fill(dirty[y], false);
        }
        clearDirty();
    }
    
    /**
     * 渲染脏区域
     */
    private void renderDirtyRegion() {
        if (dirtyMinX > dirtyMaxX || dirtyMinY > dirtyMaxY) {
            return; // 没有脏区域
        }
        
        int height = terminal.getSize().getRows();
        int width = terminal.getSize().getColumns();
        
        // 限制范围
        int startY = Math.max(0, dirtyMinY);
        int endY = Math.min(height - 1, dirtyMaxY);
        int startX = Math.max(0, dirtyMinX);
        int endX = Math.min(width - 1, dirtyMaxX);
        
        // 使用 ANSI 转义序列进行局部更新
        StringBuilder sb = new StringBuilder();
        
        for (int y = startY; y <= endY; y++) {
            // 移动到行首
            sb.append("\r");
            
            // 跳过未修改的前导字符
            int renderStartX = startX;
            while (renderStartX <= endX && !dirty[y][renderStartX]) {
                renderStartX++;
            }
            
            if (renderStartX > endX) continue;
            
            // 构建行内容
            for (int x = renderStartX; x <= endX; x++) {
                char c = frontBuffer[y][x];
                if (dirty[y][x]) {
                    sb.append(c);
                } else {
                    sb.append(c);
                }
            }
        }
        
        if (sb.length() > 0) {
            terminal.writer().print(sb.toString());
            terminal.flush();
        }
    }
    
    /**
     * 在后台缓冲区写入字符
     */
    public void writeToBackBuffer(int x, int y, char c) {
        if (isValidPosition(x, y)) {
            backBuffer[y][x] = c;
            markDirty(x, y);
        }
    }
    
    /**
     * 在后台缓冲区写入字符串
     */
    public void writeStringToBackBuffer(int x, int y, String text) {
        int currentX = x;
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                y++;
                currentX = x;
            } else if (c == '\r') {
                currentX = x;
            } else {
                writeToBackBuffer(currentX++, y, c);
            }
        }
    }
    
    /**
     * 设置后台缓冲区样式
     */
    public void setBackBufferStyle(int x, int y, AttributedStyle style) {
        if (isValidPosition(x, y)) {
            backStyles[y][x] = style;
        }
    }
    
    /**
     * 检查位置是否有效
     */
    private boolean isValidPosition(int x, int y) {
        return x >= 0 && x < terminal.getSize().getColumns() 
            && y >= 0 && y < terminal.getSize().getRows();
    }
    
    // ==================== 基础终端操作 ====================
    
    public String readLine(String prompt) {
        try {
            return lineReader.readLine(prompt);
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }
    
    public String readMultiline(String prompt) {
        StringBuilder input = new StringBuilder();
        String continuationPrompt = "      │ ";
        
        String line = readLine(prompt);
        if (line == null) return null;
        
        input.append(line);
        
        while (line.endsWith("\\")) {
            if (input.length() > 0 && input.charAt(input.length() - 1) == '\\') {
                input.deleteCharAt(input.length() - 1);
            }
            line = readLine(continuationPrompt);
            if (line == null) break;
            input.append("\n").append(line);
        }
        
        return input.toString();
    }
    
    public void print(String message) {
        terminal.writer().print(message);
        terminal.flush();
    }
    
    public void println(String message) {
        terminal.writer().println(message);
        terminal.flush();
    }
    
    public void printColored(String message, AttributedStyle style) {
        AttributedString as = new AttributedStringBuilder(message.length())
            .style(style)
            .append(message)
            .toAttributedString();
        terminal.writer().print(as.toString());
        terminal.flush();
    }
    
    public void printError(String message) {
        printColored(message, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
    }
    
    public void printSuccess(String message) {
        printColored(message, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
    }
    
    public void printWarning(String message) {
        printColored(message, AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
    }
    
    public void clear() {
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();
            clearDirty();
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) {
                terminal.writer().println();
            }
        }
    }
    
    public int getWidth() {
        return terminal.getSize().getColumns();
    }
    
    public int getHeight() {
        return terminal.getSize().getRows();
    }
    
    public void addToHistory(String line) {
        if (line != null && !line.trim().isEmpty()) {
            history.add(line);
        }
    }
    
    public List<String> getHistory() {
        List<String> result = new ArrayList<>();
        history.forEach(entry -> result.add(entry.line()));
        return result;
    }
    
    public List<String> searchHistory(String prefix) {
        List<String> result = new ArrayList<>();
        history.forEach(entry -> {
            if (entry.line().startsWith(prefix)) {
                result.add(entry.line());
            }
        });
        return result;
    }
    
    public void moveCursor(int x, int y) {
        try {
            terminal.puts(InfoCmp.Capability.cursor_address, y, x);
            terminal.flush();
        } catch (Exception e) {
            // 忽略
        }
    }
    
    public void hideCursor() {
        try {
            terminal.puts(InfoCmp.Capability.cursor_invisible);
            terminal.flush();
        } catch (Exception e) {
            // 忽略
        }
    }
    
    public void showCursor() {
        try {
            terminal.puts(InfoCmp.Capability.cursor_visible);
            terminal.flush();
        } catch (Exception e) {
            // 忽略
        }
    }
    
    @Override
    public void close() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException e) {
            // 忽略
        }
    }
    
    private class JwCodeCompleter implements Completer {
        private final Map<String, List<String>> completions = new HashMap<>();
        
        public JwCodeCompleter() {
            completions.put("", Arrays.asList(
                "help", "exit", "clear", "config", "plan", "todo", "theme",
                "diff", "files", "summary", "usage", "cost", "doctor",
                "export", "copy"
            ));
        }
        
        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word();
            for (String cmd : completions.getOrDefault("", Collections.emptyList())) {
                if (cmd.startsWith(word)) {
                    candidates.add(new Candidate(cmd, cmd, "Command", null, null, null, true));
                }
            }
        }
    }
}
