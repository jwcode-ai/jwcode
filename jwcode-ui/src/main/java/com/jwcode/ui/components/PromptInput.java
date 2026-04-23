package com.jwcode.ui.components;

import org.jline.reader.*;
import org.jline.terminal.*;

import java.util.*;

/**
 * PromptInput - 提示输入组件
 * 
 * 支持多行输入、命令补全、斜杠命令、Vim 风格编辑
 * 参照 Claude Code/Kimi Code 的输入组件设计
 */
public class PromptInput implements Component {
    
    private String prompt;
    private StringBuffer buffer;
    private int cursorPosition;
    private int historyIndex;
    private List<String> history;
    private List<String> completions;
    private boolean multiLineMode;
    private List<StringBuffer> lines;
    private int currentLine;
    private int scrollOffset;
    private Consumer<String> onSubmit;
    private Consumer<String> onCancel;
    
    // ANSI 颜色
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    
    public PromptInput() {
        this.prompt = "jwcode> ";
        this.buffer = new StringBuffer();
        this.cursorPosition = 0;
        this.history = new ArrayList<>();
        this.historyIndex = -1;
        this.completions = new ArrayList<>();
        this.multiLineMode = false;
        this.lines = new ArrayList<>();
        this.lines.add(new StringBuffer());
        this.currentLine = 0;
        this.scrollOffset = 0;
    }
    
    public PromptInput prompt(String prompt) {
        this.prompt = prompt;
        return this;
    }
    
    public PromptInput history(List<String> history) {
        this.history = new ArrayList<>(history);
        this.historyIndex = -1;
        return this;
    }
    
    public PromptInput completions(List<String> completions) {
        this.completions = new ArrayList<>(completions);
        return this;
    }
    
    public PromptInput onSubmit(Consumer<String> callback) {
        this.onSubmit = callback;
        return this;
    }
    
    public PromptInput onCancel(Consumer<String> callback) {
        this.onCancel = callback;
        return this;
    }
    
    /**
     * 获取当前输入内容
     */
    public String getValue() {
        StringBuilder sb = new StringBuffer();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(lines.get(i).toString());
        }
        return sb.toString();
    }
    
    /**
     * 获取当前行内容
     */
    public String getCurrentLine() {
        return lines.get(currentLine).toString();
    }
    
    /**
     * 获取光标在当前行的位置
     */
    public int getCursorInLine() {
        return cursorPosition;
    }
    
    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        
        // 渲染每一行
        for (int i = scrollOffset; i < lines.size(); i++) {
            if (i > scrollOffset) {
                sb.append("\n");
            }
            
            String line = lines.get(i).toString();
            
            // 当前行显示提示符
            if (i == currentLine) {
                // 渲染提示符
                sb.append(CYAN).append(prompt).append(RESET);
                
                // 渲染行内容
                int lineEnd = line.length();
                if (cursorPosition <= lineEnd) {
                    sb.append(line.substring(0, cursorPosition));
                    sb.append(GREEN).append("█").append(RESET); // 光标
                    if (cursorPosition < lineEnd) {
                        sb.append(line.substring(cursorPosition + 1));
                    }
                } else {
                    sb.append(line);
                    sb.append(GREEN).append("█").append(RESET);
                }
            } else {
                // 非当前行：显示续行提示
                sb.append(DIM).append("      │ ").append(RESET);
                sb.append(line);
            }
        }
        
        // 多行模式提示
        if (multiLineMode) {
            sb.append("\n").append(DIM);
            sb.append("多行模式: \\ 续行, Enter 发送, Ctrl+C 取消");
            sb.append(RESET);
        }
        
        // 斜杠命令提示
        if (buffer.length() > 0 && buffer.charAt(0) == '/') {
            sb.append("\n").append(YELLOW);
            sb.append("斜杠命令: /help /config /plan /todo /theme /exit");
            sb.append(RESET);
        }
        
        return sb.toString();
    }
    
    /**
     * 处理按键事件
     * @param key 按键
     * @return true 如果事件被处理
     */
    public boolean handleKey(Key key) {
        int charValue = key.getChar();
        
        // 控制键处理
        if (charValue == -1) {
            return handleControlKey(key);
        }
        
        // 普通字符
        return handleChar((char) charValue);
    }
    
    private boolean handleControlKey(Key key) {
        switch (key.getType()) {
            case Key.Type.UP:
                moveHistoryUp();
                return true;
                
            case Key.Type.DOWN:
                moveHistoryDown();
                return true;
                
            case Key.Type.LEFT:
                moveCursorLeft();
                return true;
                
            case Key.Type.RIGHT:
                moveCursorRight();
                return true;
                
            case Key.Type.HOME:
                moveCursorToLineStart();
                return true;
                
            case Key.Type.END:
                moveCursorToLineEnd();
                return true;
                
            case Key.Type.DELETE:
                deleteCharAfter();
                return true;
                
            case Key.Type.BACKSPACE:
                deleteCharBefore();
                return true;
                
            case Key.Type.ENTER:
                handleEnter();
                return true;
                
            case Key.Type.TAB:
                // 触发补全
                return true;
                
            default:
                return false;
        }
    }
    
    private boolean handleChar(char c) {
        StringBuffer current = lines.get(currentLine);
        
        // 插入字符
        if (cursorPosition <= current.length()) {
            current.insert(cursorPosition, c);
        } else {
            current.append(c);
        }
        cursorPosition++;
        
        // 检查是否是多行模式的触发
        if (c == '\\') {
            multiLineMode = true;
        }
        
        return true;
    }
    
    private void moveCursorLeft() {
        if (cursorPosition > 0) {
            cursorPosition--;
        } else if (currentLine > 0) {
            // 移动到上一行的末尾
            currentLine--;
            cursorPosition = lines.get(currentLine).length();
        }
    }
    
    private void moveCursorRight() {
        StringBuffer current = lines.get(currentLine);
        if (cursorPosition < current.length()) {
            cursorPosition++;
        } else if (currentLine < lines.size() - 1) {
            // 移动到下一行的开头
            currentLine++;
            cursorPosition = 0;
        }
    }
    
    private void moveCursorToLineStart() {
        cursorPosition = 0;
    }
    
    private void moveCursorToLineEnd() {
        cursorPosition = lines.get(currentLine).length();
    }
    
    private void moveHistoryUp() {
        if (history.isEmpty() || historyIndex >= history.size() - 1) {
            return;
        }
        
        historyIndex++;
        restoreFromHistory();
    }
    
    private void moveHistoryDown() {
        if (historyIndex <= 0) {
            historyIndex = -1;
            lines.clear();
            lines.add(new StringBuffer());
            currentLine = 0;
            cursorPosition = 0;
            return;
        }
        
        historyIndex--;
        restoreFromHistory();
    }
    
    private void restoreFromHistory() {
        String histLine = history.get(history.size() - 1 - historyIndex);
        String[] histLines = histLine.split("\n", -1);
        
        lines.clear();
        for (String line : histLines) {
            lines.add(new StringBuffer(line));
        }
        currentLine = lines.size() - 1;
        cursorPosition = lines.get(currentLine).length();
    }
    
    private void deleteCharBefore() {
        StringBuffer current = lines.get(currentLine);
        
        if (cursorPosition > 0) {
            current.deleteCharAt(cursorPosition - 1);
            cursorPosition--;
        } else if (currentLine > 0) {
            // 合并到上一行
            StringBuffer prevLine = lines.get(currentLine - 1);
            cursorPosition = prevLine.length();
            prevLine.append(current.toString());
            lines.remove(currentLine);
            currentLine--;
        }
    }
    
    private void deleteCharAfter() {
        StringBuffer current = lines.get(currentLine);
        
        if (cursorPosition < current.length()) {
            current.deleteCharAt(cursorPosition);
        } else if (currentLine < lines.size() - 1) {
            // 合并下一行
            StringBuffer nextLine = lines.get(currentLine + 1);
            current.append(nextLine.toString());
            lines.remove(currentLine + 1);
        }
    }
    
    private void handleEnter() {
        String content = getValue();
        
        if (multiLineMode) {
            // 多行模式：创建新行
            StringBuffer current = lines.get(currentLine);
            
            // 检查行末是否有反斜杠
            if (current.length() > 0 && current.charAt(current.length() - 1) == '\\') {
                current.deleteCharAt(current.length() - 1);
                lines.add(currentLine + 1, new StringBuffer());
                currentLine++;
                cursorPosition = 0;
                multiLineMode = true;
                return;
            }
        }
        
        // 提交输入
        if (!content.trim().isEmpty()) {
            history.add(content);
            historyIndex = -1;
        }
        
        if (onSubmit != null) {
            onSubmit.accept(content);
        }
        
        // 重置状态
        reset();
    }
    
    /**
     * 重置输入状态
     */
    public void reset() {
        lines.clear();
        lines.add(new StringBuffer());
        currentLine = 0;
        cursorPosition = 0;
        multiLineMode = false;
        scrollOffset = 0;
    }
    
    /**
     * 取消输入
     */
    public void cancel() {
        if (onCancel != null) {
            onCancel.accept(getValue());
        }
        reset();
    }
    
    /**
     * 设置内容
     */
    public void setValue(String value) {
        reset();
        if (value != null && !value.isEmpty()) {
            String[] parts = value.split("\n", -1);
            lines.clear();
            for (String part : parts) {
                lines.add(new StringBuffer(part));
            }
            currentLine = lines.size() - 1;
            cursorPosition = lines.get(currentLine).length();
        }
    }
    
    /**
     * 清空输入
     */
    public void clear() {
        reset();
    }
}
