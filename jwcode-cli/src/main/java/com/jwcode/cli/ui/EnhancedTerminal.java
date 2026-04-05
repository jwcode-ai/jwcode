package com.jwcode.cli.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

import java.io.IOException;

/**
 * 增强型终端 UI - 参照 Kimi Code 的终端交互
 */
public class EnhancedTerminal {
    
    private Terminal terminal;
    private LineReader lineReader;
    private boolean supportsColor;
    
    // ANSI 颜色代码
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
    }
    
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
