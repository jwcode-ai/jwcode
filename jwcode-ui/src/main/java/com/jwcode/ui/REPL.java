package com.jwcode.ui;

import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.ui.components.*;

import org.jline.reader.*;
import org.jline.terminal.*;
import org.jline.utils.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * REPL - 交互式终端界面
 * 
 * 实现 prompt/transcript 双屏幕模式，参照 Claude Code 的 REPL 架构
 * - prompt 屏幕：活动对话、流式传输、权限对话框
 * - transcript 屏幕：只读历史审查、搜索功能
 */
public class REPL implements AutoCloseable {
    
    // 屏幕类型
    public enum Screen {
        PROMPT,  // 活动对话屏幕
        TRANSCRIPT  // 历史记录屏幕
    }
    
    // 状态
    private final Terminal terminal;
    private final LineReader lineReader;
    private Screen currentScreen;
    private Screen previousScreen;
    
    // 消息历史
    private final List<Message> messages;
    private final List<Message> frozenMessages; // transcript 模式使用的冻结快照
    
    // 组件
    private MessageList messageList;
    private PromptInput promptInput;
    
    // 流式输出
    private StringBuilder streamingContent;
    private boolean isStreaming;
    private CompletableFuture<Void> streamingTask;
    
    // 回调
    private Consumer<String> onSubmit;
    private Consumer<String> onCommand;
    
    // 滚动状态
    private int scrollOffset;
    private boolean autoScroll;
    
    // ANSI 颜色
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    
    public REPL() throws IOException {
        this.terminal = TerminalBuilder.builder()
                .name("JWCode")
                .build();
        
        this.lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
        
        this.currentScreen = Screen.PROMPT;
        this.messages = new ArrayList<>();
        this.frozenMessages = new ArrayList<>();
        this.streamingContent = new StringBuilder();
        this.isStreaming = false;
        this.autoScroll = true;
        this.scrollOffset = 0;
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        messageList = new MessageList()
                .maxWidth(terminal.getSize().getColumns());
        
        promptInput = new PromptInput()
                .prompt("jwcode> ")
                .onSubmit(this::handleSubmit);
    }
    
    /**
     * 设置提交回调
     */
    public REPL onSubmit(Consumer<String> callback) {
        this.onSubmit = callback;
        return this;
    }
    
    /**
     * 设置命令回调
     */
    public REPL onCommand(Consumer<String> callback) {
        this.onCommand = callback;
        return this;
    }
    
    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) {
        messages.add(Message.createUserMessage(content));
        if (autoScroll) {
            scrollOffset = 0;
        }
    }
    
    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String content) {
        messages.add(Message.createAssistantMessage(content));
        if (autoScroll) {
            scrollOffset = 0;
        }
    }
    
    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        messages.add(Message.createSystemMessage(content));
    }
    
    /**
     * 开始流式输出
     */
    public void startStreaming() {
        isStreaming = true;
        streamingContent.setLength(0);
        messages.add(Message.createAssistantMessage("")); // 添加空消息占位
    }
    
    /**
     * 流式输出内容
     */
    public void streamContent(String content) {
        if (!isStreaming) return;
        
        streamingContent.append(content);
        if (!messages.isEmpty()) {
            Message last = messages.get(messages.size() - 1);
            // 更新最后一条消息
        }
        
        // 实时渲染（限流）
        if (streamingContent.length() % 20 == 0) {
            render();
        }
    }
    
    /**
     * 结束流式输出
     */
    public void endStreaming() {
        isStreaming = false;
        if (!streamingContent.isEmpty()) {
            // 更新最后一条消息
        }
    }
    
    /**
     * 设置子代理来源标签
     */
    public void setSourceLabel(String label) {
        messageList.sourceLabel(label);
    }
    
    /**
     * 运行主循环
     */
    public void run() {
        render();
        
        while (true) {
            try {
                String input;
                
                if (currentScreen == Screen.PROMPT) {
                    // PROMPT 屏幕：显示输入框
                    render();
                    input = lineReader.readLine(getPrompt());
                } else {
                    // TRANSCRIPT 屏幕：只读模式，监听导航键
                    renderTranscript();
                    input = lineReader.readLine("");
                }
                
                if (input == null) {
                    // EOF
                    break;
                }
                
                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }
                
                // 处理全局快捷键
                if (handleGlobalKey(input)) {
                    continue;
                }
                
                // 处理命令
                if (currentScreen == Screen.PROMPT) {
                    if (input.startsWith("/")) {
                        if (onCommand != null) {
                            onCommand.accept(input);
                        }
                    } else {
                        if (onSubmit != null) {
                            onSubmit.accept(input);
                        }
                    }
                }
                
            } catch (UserInterruptException e) {
                // Ctrl+C
                if (isStreaming) {
                    endStreaming();
                    terminal.writer().println("\n" + YELLOW + "已取消" + RESET);
                } else {
                    terminal.writer().println("\n" + YELLOW + "使用 /exit 退出" + RESET);
                }
            } catch (EndOfFileException e) {
                break;
            }
        }
    }
    
    /**
     * 处理全局快捷键
     */
    private boolean handleGlobalKey(String input) {
        switch (input) {
            case "ctrl+o":
            case "^o":
                toggleScreen();
                return true;
                
            case "ctrl+c":
                // 取消当前操作
                return false;
                
            case "ctrl+b":
                // 后台任务
                terminal.writer().println(DIM + "后台任务模式 (Ctrl+B)" + RESET);
                return true;
                
            case "ctrl+r":
                // 历史搜索
                String search = lineReader.readLine("搜索历史: ");
                searchHistory(search);
                return true;
                
            case "/exit":
            case "/quit":
                return false; // 退出循环
        }
        
        return false;
    }
    
    /**
     * 切换屏幕
     */
    private void toggleScreen() {
        if (currentScreen == Screen.PROMPT) {
            // 冻结当前消息
            frozenMessages.clear();
            frozenMessages.addAll(messages);
            previousScreen = Screen.PROMPT;
            currentScreen = Screen.TRANSCRIPT;
            terminal.writer().println(DIM + "\n[进入历史记录视图] 按 Ctrl+O 返回对话" + RESET);
        } else {
            currentScreen = Screen.PROMPT;
            terminal.writer().println(DIM + "[返回对话视图]" + RESET);
        }
    }
    
    /**
     * 搜索历史
     */
    private void searchHistory(String query) {
        if (query == null || query.isEmpty()) return;
        
        terminal.writer().println("\n" + CYAN + "搜索结果:" + RESET);
        
        boolean found = false;
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            String content = msg.getTextContent();
            if (content != null && content.toLowerCase().contains(query.toLowerCase())) {
                found = true;
                terminal.writer().println(GREEN + "[" + i + "] " + msg.getRole() + RESET + ":");
                terminal.writer().println("  " + content.substring(0, Math.min(100, content.length())));
                terminal.writer().println();
            }
        }
        
        if (!found) {
            terminal.writer().println(YELLOW + "未找到匹配结果" + RESET);
        }
    }
    
    /**
     * 获取提示符
     */
    private String getPrompt() {
        if (isStreaming) {
            return CYAN + "◐ " + RESET;
        }
        return GREEN + "jwcode" + RESET + "> ";
    }
    
    /**
     * 处理提交
     */
    private void handleSubmit(String input) {
        if (input == null || input.trim().isEmpty()) return;
        
        if (input.startsWith("/")) {
            if (onCommand != null) {
                onCommand.accept(input);
            }
        } else {
            addUserMessage(input);
            if (onSubmit != null) {
                onSubmit.accept(input);
            }
        }
    }
    
    /**
     * 渲染当前屏幕
     */
    private void render() {
        int width = terminal.getSize().getColumns();
        int height = terminal.getSize().getRows();
        
        StringBuilder sb = new StringBuilder();
        
        // 状态栏
        sb.append(renderStatusBar(width));
        sb.append("\n");
        
        // 消息列表
        MessageList ml = new MessageList().maxWidth(width);
        for (Message msg : messages) {
            ml.addMessage(msg);
        }
        if (isStreaming) {
            ml.updateLastMessage(streamingContent.toString());
        }
        sb.append(ml.render());
        
        // 清屏并输出
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
        } catch (Exception e) {
            // 忽略
        }
        terminal.writer().print(sb.toString());
        terminal.flush();
    }
    
    /**
     * 渲染历史记录屏幕
     */
    private void renderTranscript() {
        int width = terminal.getSize().getColumns();
        int height = terminal.getSize().getRows();
        
        StringBuilder sb = new StringBuilder();
        
        // 标题栏
        sb.append(CYAN).append("┌─ 历史记录 ").append(RESET);
        sb.append(DIM).append("(Ctrl+O 返回对话)").append(RESET);
        sb.append(" ".repeat(Math.max(0, width - 30)));
        sb.append(CYAN).append("┐").append(RESET).append("\n");
        
        // 消息列表（只读）
        MessageList ml = new MessageList().maxWidth(width).compact(true);
        for (Message msg : frozenMessages) {
            ml.addMessage(msg);
        }
        sb.append(ml.render());
        
        // 底部导航提示
        sb.append("\n").append(DIM);
        sb.append("j/k 或 ↑↓ 导航，/ 搜索，Ctrl+O 返回").append(RESET);
        
        // 清屏并输出
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
        } catch (Exception e) {
            // 忽略
        }
        terminal.writer().print(sb.toString());
        terminal.flush();
    }
    
    /**
     * 渲染状态栏
     */
    private String renderStatusBar(int width) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(CYAN);
        sb.append("─".repeat(Math.max(0, width)));
        sb.append(RESET).append("\n");
        
        sb.append(DIM).append("[Ctrl+O] 历史").append(RESET).append(" ");
        sb.append(DIM).append("[Ctrl+R] 搜索").append(RESET).append(" ");
        sb.append(DIM).append("[Ctrl+C] 取消").append(RESET).append(" ");
        sb.append(DIM).append("[/help] 帮助").append(RESET);
        
        // 右对齐信息
        String info = "消息: " + messages.size();
        int infoLen = info.length();
        sb.append(" ".repeat(Math.max(0, width - 60 - infoLen)));
        sb.append(DIM).append(info).append(RESET);
        
        sb.append("\n").append(CYAN);
        sb.append("─".repeat(Math.max(0, width)));
        sb.append(RESET);
        
        return sb.toString();
    }
    
    /**
     * 显示权限对话框
     */
    public void showPermissionDialog(String title, String description, String[] options) {
        terminal.writer().println();
        terminal.writer().println(CYAN + "┌─ " + title + " ─" + "─".repeat(Math.max(0, 50 - title.length())) + "┐" + RESET);
        terminal.writer().println(CYAN + "│" + RESET + " " + description);
        
        for (int i = 0; i < options.length; i++) {
            terminal.writer().println(CYAN + "│" + RESET + "   " + GREEN + (i + 1) + ". " + options[i] + RESET);
        }
        
        terminal.writer().println(CYAN + "└" + "─".repeat(60) + "┘" + RESET);
        
        String input = lineReader.readLine("请选择 (1-" + options.length + "): ");
        // 处理输入...
    }
    
    /**
     * 显示确认对话框
     */
    public boolean confirm(String message) {
        terminal.writer().println();
        terminal.writer().println(CYAN + "┌─ 确认 ─" + "─".repeat(50) + "┐" + RESET);
        terminal.writer().println(CYAN + "│" + RESET + " " + message);
        terminal.writer().println(CYAN + "│" + RESET + "   " + GREEN + "1. 是" + RESET + "   " + RED + "2. 否" + RESET);
        terminal.writer().println(CYAN + "└" + "─".repeat(60) + "┘" + RESET);
        
        String input = lineReader.readLine("请选择: ");
        return "1".equals(input) || "是".equals(input);
    }
    
    /**
     * 获取会话
     */
    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }
    
    /**
     * 清空消息
     */
    public void clearMessages() {
        messages.clear();
    }
    
    @Override
    public void close() {
        try {
            if (streamingTask != null && !streamingTask.isDone()) {
                streamingTask.cancel(true);
            }
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException e) {
            // 忽略
        }
    }
}
