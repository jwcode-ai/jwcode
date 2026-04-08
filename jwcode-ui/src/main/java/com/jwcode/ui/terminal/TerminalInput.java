package com.jwcode.ui.terminal;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * TerminalInput - 终端输入处理
 * 
 * 功能说明：
 * 处理终端的用户输入，支持行输入、单字符输入和快捷键处理。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TerminalInput {
    
    private final Terminal terminal;
    private final LineReader lineReader;
    private InputMode inputMode;
    private boolean echoEnabled;
    private Consumer<KeyEvent> keyHandler;
    
    public TerminalInput() throws IOException {
        this(TerminalBuilder.builder().system(true).build());
    }
    
    public TerminalInput(Terminal terminal) {
        this.terminal = terminal;
        this.lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .build();
        this.inputMode = InputMode.NORMAL;
        this.echoEnabled = true;
    }
    
    /**
     * 读取一行输入
     */
    public String readLine() {
        return readLine("");
    }
    
    /**
     * 读取一行输入（带提示）
     */
    public String readLine(String prompt) {
        try {
            return lineReader.readLine(prompt);
        } catch (UserInterruptException e) {
            return null;
        } catch (EndOfFileException e) {
            return null;
        }
    }
    
    /**
     * 读取一行输入（带掩码，用于密码输入）
     */
    public String readLineMasked(String prompt) {
        try {
            return lineReader.readLine(prompt, '*');
        } catch (UserInterruptException e) {
            return null;
        } catch (EndOfFileException e) {
            return null;
        }
    }
    
    /**
     * 读取单个字符
     */
    public char readCharacter() throws IOException {
        terminal.enterRawMode();
        try {
            int ch = terminal.reader().read();
            return (char) ch;
        } finally {
            terminal.leaveRawMode();
        }
    }
    
    /**
     * 读取按键（不等待回车）
     */
    public int readKey() throws IOException {
        terminal.enterRawMode();
        try {
            return terminal.reader().read();
        } finally {
            terminal.leaveRawMode();
        }
    }
    
    /**
     * 检查是否有输入可用
     */
    public boolean hasInput() throws IOException {
        return terminal.reader().peek() != -1;
    }
    
    /**
     * 设置输入模式
     */
    public void setInputMode(InputMode mode) {
        this.inputMode = mode;
        updateTerminalSettings();
    }
    
    /**
     * 获取输入模式
     */
    public InputMode getInputMode() {
        return inputMode;
    }
    
    /**
     * 启用/禁用回显
     */
    public void setEchoEnabled(boolean enabled) {
        this.echoEnabled = enabled;
    }
    
    /**
     * 是否启用回显
     */
    public boolean isEchoEnabled() {
        return echoEnabled;
    }
    
    /**
     * 设置按键处理器
     */
    public void setKeyHandler(Consumer<KeyEvent> handler) {
        this.keyHandler = handler;
    }
    
    /**
     * 开始监听按键
     */
    public void startListening() {
        if (keyHandler != null) {
            new Thread(this::keyListeningLoop).start();
        }
    }
    
    /**
     * 停止监听
     */
    public void stopListening() {
        // 设置标志位停止监听
    }
    
    /**
     * 按键监听循环
     */
    private void keyListeningLoop() {
        try {
            while (true) {
                int key = readKey();
                if (key == -1) {
                    Thread.sleep(10);
                    continue;
                }
                
                KeyEvent event = new KeyEvent(key);
                if (keyHandler != null) {
                    keyHandler.accept(event);
                }
                
                if (event.isConsumed()) {
                    break;
                }
            }
        } catch (IOException e) {
            // 忽略错误
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 更新终端设置
     */
    private void updateTerminalSettings() {
        // 根据输入模式更新终端设置
        switch (inputMode) {
            case RAW:
                // 原始模式：禁用所有处理
                break;
            case NORMAL:
                // 正常模式：启用标准处理
                break;
            case HIDE_INPUT:
                // 隐藏输入模式
                setEchoEnabled(false);
                break;
        }
    }
    
    /**
     * 获取终端
     */
    public Terminal getTerminal() {
        return terminal;
    }
    
    /**
     * 关闭输入处理
     */
    public void close() throws IOException {
        terminal.close();
    }
    
    /**
     * 输入模式枚举
     */
    public enum InputMode {
        NORMAL,         // 正常模式
        RAW,            // 原始模式
        HIDE_INPUT      // 隐藏输入（密码模式）
    }
    
    /**
     * 按键事件类
     */
    public static class KeyEvent {
        private final int keyCode;
        private boolean consumed;
        private boolean ctrlPressed;
        private boolean altPressed;
        private boolean shiftPressed;
        
        public KeyEvent(int keyCode) {
            this.keyCode = keyCode;
            this.consumed = false;
            
            // 检测修饰键
            detectModifiers();
        }
        
        /**
         * 检测修饰键
         */
        private void detectModifiers() {
            // 简单实现，实际应该根据终端的按键编码来判断
            ctrlPressed = (keyCode & 0x1F) == keyCode && keyCode < 0x1F;
        }
        
        /**
         * 获取按键码
         */
        public int getKeyCode() {
            return keyCode;
        }
        
        /**
         * 获取字符表示
         */
        public char getChar() {
            return (char) keyCode;
        }
        
        /**
         * 是否按下 Ctrl
         */
        public boolean isCtrlPressed() {
            return ctrlPressed;
        }
        
        /**
         * 是否按下 Alt
         */
        public boolean isAltPressed() {
            return altPressed;
        }
        
        /**
         * 是否按下 Shift
         */
        public boolean isShiftPressed() {
            return shiftPressed;
        }
        
        /**
         * 标记事件已消费
         */
        public void consume() {
            this.consumed = true;
        }
        
        /**
         * 是否已消费
         */
        public boolean isConsumed() {
            return consumed;
        }
        
        /**
         * 是否是特殊键
         */
        public boolean isSpecialKey() {
            return keyCode == 3 ||  // Ctrl+C
                   keyCode == 4 ||  // Ctrl+D
                   keyCode == 27 || // Escape
                   keyCode == 127 || // Backspace
                   keyCode == 9 ||  // Tab
                   keyCode == 13 || // Enter
                   keyCode == 10;   // Enter (LF)
        }
        
        /**
         * 是否是 Ctrl+C
         */
        public boolean isCtrlC() {
            return keyCode == 3;
        }
        
        /**
         * 是否是 Ctrl+D
         */
        public boolean isCtrlD() {
            return keyCode == 4;
        }
        
        /**
         * 是否是 Escape
         */
        public boolean isEscape() {
            return keyCode == 27;
        }
        
        /**
         * 是否是 Enter
         */
        public boolean isEnter() {
            return keyCode == 13 || keyCode == 10;
        }
    }
}