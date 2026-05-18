package com.jwcode.ui;

import com.jwcode.cli.log.ActivityEntry;
import com.jwcode.cli.log.ActivityLogger;
import com.jwcode.cli.ui.EnhancedTerminal;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;
import com.jwcode.ui.components.*;
import com.jwcode.ui.terminal.TerminalBuffer;

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
 * - sessions 屏幕：会话管理
 */
public class REPL implements AutoCloseable {
    
    // 屏幕类型
    public enum Screen {
        PROMPT,     // 活动对话屏幕
        TRANSCRIPT, // 历史记录屏幕
        SESSIONS    // 会话管理屏幕
    }
    
    // 状态
    private final Terminal terminal;
    private final LineReader lineReader;
    private Screen currentScreen;
    private Screen previousScreen;
    
    // 会话管理
    private final SessionManager sessionManager;
    private Session currentSession;
    
    // 消息历史
    private final List<Message> messages;
    private final List<Message> frozenMessages;
    
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
    
    // 活动日志
    private final ActivityLogger activityLogger;
    
    // ========== Ink 风格渲染管线 ==========
    
    /** InkPipeline 实例（可选，启用后替代传统 render()） */
    private InkPipeline inkPipeline;
    /** EnhancedTerminal 实例（用于 InkPipeline） */
    private EnhancedTerminal enhancedTerminal;
    /** 是否使用 InkPipeline 渲染模式 */
    private boolean useInkPipeline = false;
    
    // ANSI 颜色
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String MAGENTA = "\u001B[35m";
    
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
        
        // 初始化会话管理
        this.sessionManager = SessionManager.getInstance();
        this.currentSession = sessionManager.getActiveSession();
        if (currentSession == null) {
            currentSession = sessionManager.createSession(System.getProperty("user.dir"));
            currentSession.setTitle("新会话");
        }
        
        // 自动初始化 InkPipeline（EnhancedTerminal + 增量渲染）
        autoInitInkPipeline();
        
        // 初始化活动日志
        this.activityLogger = ActivityLogger.getInstance();
        
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
        Message msg = Message.createUserMessage(content);
        messages.add(msg);
        if (currentSession != null) {
            currentSession.addMessage(msg);
            sessionManager.saveSession(currentSession);
        }
        if (autoScroll) {
            scrollOffset = 0;
        }
    }
    
    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String content) {
        Message msg = Message.createAssistantMessage(content);
        messages.add(msg);
        if (currentSession != null) {
            currentSession.addMessage(msg);
            sessionManager.saveSession(currentSession);
        }
        if (autoScroll) {
            scrollOffset = 0;
        }
    }
    
    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        Message msg = Message.createSystemMessage(content);
        messages.add(msg);
        if (currentSession != null) {
            currentSession.addMessage(msg);
        }
    }
    
    /**
     * 开始流式输出
     */
    public void startStreaming() {
        isStreaming = true;
        streamingContent.setLength(0);
        messages.add(Message.createAssistantMessage(""));
    }
    
    /**
     * 流式输出内容
     */
    public void streamContent(String content) {
        if (!isStreaming) return;
        
        streamingContent.append(content);
        if (streamingContent.length() % 20 == 0) {
            render();
        }
    }
    
    /**
     * 结束流式输出
     */
    public void endStreaming() {
        isStreaming = false;
    }
    
    /**
     * 设置子代理来源标签
     */
    public void setSourceLabel(String label) {
        messageList.sourceLabel(label);
    }
    
    /**
     * 自动初始化 InkPipeline（如果尚未启用且 EnhancedTerminal 可用）。
     */
    private void autoInitInkPipeline() {
        if (useInkPipeline) return;
        try {
            EnhancedTerminal et = new EnhancedTerminal();
            enableInkPipeline(et);
            System.err.println("[InkPipeline] ✅ 已启用增量渲染模式");
        } catch (Exception e) {
            System.err.println("[InkPipeline] ⚠️ 初始化失败，回退到传统模式: " + e.getMessage());
        }
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
                    render();
                    input = lineReader.readLine(getPrompt());
                } else {
                    renderTranscript();
                    input = lineReader.readLine("");
                }
                
                if (input == null) {
                    break;
                }
                
                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }
                
                if (handleGlobalKey(input)) {
                    continue;
                }
                
                // 检查退出命令
                if (input.equals("/exit") || input.equals("/quit") || input.equals("exit") || input.equals("quit")) {
                    break;
                }
                
                if (currentScreen == Screen.PROMPT) {
                    // 先尝试命令回调（无论是否带 / 前缀）
                    if (onCommand != null) {
                        onCommand.accept(input);
                    } else if (onSubmit != null) {
                        // 无命令回调时，走自然语言回调
                        onSubmit.accept(input);
                    }
                }
                
            } catch (UserInterruptException e) {
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
                
            case "ctrl+s":
            case "^s":
                showSessionSelector();
                return true;
                
            case "ctrl+c":
                return false;
                
            case "ctrl+b":
                terminal.writer().println(DIM + "后台任务模式 (Ctrl+B)" + RESET);
                return true;
                
            case "ctrl+r":
                String search = lineReader.readLine("搜索历史: ");
                searchHistory(search);
                return true;
                
            case "/exit":
            case "/quit":
                return false;
        }
        
        return false;
    }
    
    /**
     * 切换屏幕
     */
    private void toggleScreen() {
        if (currentScreen == Screen.PROMPT) {
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
     * 显示会话选择器
     */
    private void showSessionSelector() {
        List<Session> sessions = sessionManager.getAllSessions();
        
        if (sessions.isEmpty()) {
            terminal.writer().println(YELLOW + "暂无会话" + RESET);
            return;
        }
        
        terminal.writer().println();
        terminal.writer().println(CYAN + "┌─ 选择会话 (Ctrl+S) " + "─".repeat(40) + "┐" + RESET);
        
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            boolean isActive = currentSession != null && s.getId().equals(currentSession.getId());
            String marker = isActive ? GREEN + "●" + RESET : "○";
            String title = s.getTitle() != null ? s.getTitle() : "未命名";
            String shortId = s.getId().substring(0, Math.min(8, s.getId().length()));
            
            terminal.writer().println(CYAN + "│" + RESET + " " + marker + " " + (i + 1) + ". " + 
                BOLD + title + RESET + " [" + shortId + "] (" + s.getMessageCount() + " 条消息)");
        }
        
        terminal.writer().println(CYAN + "└" + "─".repeat(60) + "┘" + RESET);
        
        String input = lineReader.readLine("选择会话 (输入编号，或按 Enter 取消): ");
        
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        
        try {
            int index = Integer.parseInt(input.trim()) - 1;
            if (index >= 0 && index < sessions.size()) {
                switchToSession(sessions.get(index));
            } else {
                terminal.writer().println(RED + "无效选择" + RESET);
            }
        } catch (NumberFormatException e) {
            terminal.writer().println(RED + "请输入有效编号" + RESET);
        }
    }
    
    /**
     * 切换到指定会话
     */
    private void switchToSession(Session session) {
        if (currentSession != null) {
            sessionManager.saveSession(currentSession);
        }
        
        sessionManager.setActiveSession(session.getId());
        this.currentSession = session;
        
        messages.clear();
        messages.addAll(currentSession.getMessages());
        
        terminal.writer().println(GREEN + "✓ 已切换到会话: " + session.getTitle() + RESET);
    }
    
    /**
     * 创建新会话
     */
    public void createNewSession() {
        if (currentSession != null) {
            sessionManager.saveSession(currentSession);
        }
        
        currentSession = sessionManager.createSession(System.getProperty("user.dir"));
        currentSession.setTitle("会话 " + currentSession.getId().substring(0, 8));
        
        messages.clear();
        
        terminal.writer().println(GREEN + "✓ 已创建新会话" + RESET);
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
     * 启用 InkPipeline 渲染模式。
     *
     * <p>启用后，render() 方法将使用 InkPipeline 进行增量渲染，
     * 而非传统的全屏清空重绘。</p>
     *
     * @param enhancedTerminal EnhancedTerminal 实例
     */
    public void enableInkPipeline(EnhancedTerminal enhancedTerminal) {
        this.enhancedTerminal = enhancedTerminal;
        this.inkPipeline = new InkPipeline(enhancedTerminal);
        this.useInkPipeline = true;
        inkPipeline.init();
    }

    /**
     * 渲染当前屏幕。
     *
     * <p>在 InkPipeline 模式下，使用格子级 Diff 增量渲染；
     * 否则回退到传统的全屏清空重绘。</p>
     */
    private void render() {
        if (useInkPipeline && inkPipeline != null) {
            renderWithPipeline();
            return;
        }

        int width = terminal.getSize().getColumns();
        int height = terminal.getSize().getRows();
        
        StringBuilder sb = new StringBuilder();
        
        // 简约分隔线
        sb.append(CYAN).append(" ").append("─".repeat(Math.max(0, width - 2))).append(RESET).append("\n");
        sb.append(renderStatusBar(width)).append("\n");
        sb.append(CYAN).append(" ").append("─".repeat(Math.max(0, width - 2))).append(RESET).append("\n");
        
        MessageList ml = new MessageList().maxWidth(width);
        for (Message msg : messages) {
            ml.addMessage(msg);
        }
        if (isStreaming) {
            ml.updateLastMessage(streamingContent.toString());
        }
        sb.append(ml.render());
        
        // 输入提示行
        sb.append("\n").append(GREEN).append(" jwcode> ").append(RESET).append("\n");
        
        // 底部分隔线
        sb.append(CYAN).append(" ").append("─".repeat(Math.max(0, width - 2))).append(RESET).append("\n");
        sb.append(renderFooterText(width));
        
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
        } catch (Exception e) {
            // 忽略
        }
        terminal.writer().print(sb.toString());
        terminal.flush();
    }
    
    /**
     * 渲染底部状态行（传统模式）。
     */
    private String renderFooterText(int width) {
        int msgCount = messages.size();
        String status = isStreaming ? "◐ 流式输出中..." : "● 就绪";
        return "  " + DIM + "消息: " + msgCount + "  │  状态: " + status + RESET;
    }

    /**
     * 使用 InkPipeline 进行增量渲染。
     *
     * <p>构建组件树 → InkPipeline.render() → 格子级 Diff → ANSI 输出。
     * 只输出变化区域，不进行全屏重绘。</p>
     *
     * <p>布局结构（参照 Claude Code 风格）：</p>
     * <pre>
     * ─── JWCode ──────────────────────────────────────
     *  [Ctrl+O] 历史  [Ctrl+S] 会话  ...  会话名 | N条
     * ────────────────────────────────────────────────
     *  消息内容...
     *  更多消息...
     * ────────────────────────────────────────────────
     *  jwcode> _                                      ← 集成在管线内的输入行
     * ────────────────────────────────────────────────
     *  消息: N  |  就绪  |  Ctrl+C 取消  |  /help
     * </pre>
     */
    private void renderWithPipeline() {
        int width = enhancedTerminal.getTerminalWidth();
        int height = enhancedTerminal.getTerminalHeight();

        // ===== 根容器（无边框，全屏宽） =====
        Box rootBox = new Box();
        rootBox.setShowBorder(false);
        rootBox.setWidth(width);
        rootBox.setFlexDirection(com.jwcode.ui.layout.FlexDirection.COLUMN);

        // ===== 顶部状态栏（简约分隔线风格，无边框盒） =====
        Text headerSeparator = new Text(buildHeaderSeparator(width));
        rootBox.addChild(headerSeparator);

        Text statusText = new Text(buildStatusText(width));
        statusText.setColor(com.googlecode.lanterna.TextColor.ANSI.CYAN);
        rootBox.addChild(statusText);

        Text headerSeparator2 = new Text(buildHeaderSeparator(width));
        rootBox.addChild(headerSeparator2);

        // ===== 消息列表区域（无边框，简约风格） =====
        MessageList ml = new MessageList().maxWidth(width - 2);
        for (Message msg : messages) {
            ml.addMessage(msg);
        }
        if (isStreaming) {
            ml.updateLastMessage(streamingContent.toString());
        }
        Text messagesText = new Text(ml.render());
        rootBox.addChild(messagesText);

        // ===== 输入提示行（集成在管线内，模拟 prompt 区域） =====
        Text promptLine = new Text(buildPromptLine(width));
        promptLine.setColor(com.googlecode.lanterna.TextColor.ANSI.GREEN);
        rootBox.addChild(promptLine);

        // ===== 底部状态行（简约风格） =====
        Text footerSeparator = new Text(buildFooterSeparator(width));
        rootBox.addChild(footerSeparator);

        Text footerText = new Text(buildFooterText());
        footerText.setColor(com.googlecode.lanterna.TextColor.ANSI.WHITE);
        rootBox.addChild(footerText);

        inkPipeline.setRoot(rootBox);
        inkPipeline.render();
    }

    /**
     * 构建输入提示行内容（在 InkPipeline 组件树中显示）。
     * 实际输入仍由 JLine 处理，这里只做视觉提示。
     */
    private String buildPromptLine(int width) {
        String prompt = isStreaming ? "◐ " : "jwcode> ";
        return " " + prompt;
    }

    /**
     * 构建简约风格的分隔线。
     */
    private String buildHeaderSeparator(int width) {
        return " " + "─".repeat(Math.max(0, width - 2));
    }

    /**
     * 构建底部分隔线。
     */
    private String buildFooterSeparator(int width) {
        return " " + "─".repeat(Math.max(0, width - 2));
    }

    /**
     * 构建底部状态文本（简约风格）。
     */
    private String buildFooterText() {
        int msgCount = messages.size();
        String status = isStreaming ? "◐ 流式输出中..." : "● 就绪";
        return "  " + DIM + "消息: " + msgCount + "  │  状态: " + status + RESET;
    }

    /**
     * 构建状态栏文本（简约风格，参照 Claude Code）。
     */
    private String buildStatusText(int width) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(DIM).append("Ctrl+O").append(RESET).append(" 历史 ");
        sb.append(DIM).append("Ctrl+S").append(RESET).append(" 会话 ");
        sb.append(DIM).append("Ctrl+R").append(RESET).append(" 搜索 ");
        sb.append(DIM).append("Ctrl+C").append(RESET).append(" 取消 ");
        sb.append(DIM).append("/help").append(RESET).append(" 帮助");

        String sessionTitle = currentSession != null && currentSession.getTitle() != null
            ? currentSession.getTitle().substring(0, Math.min(15, currentSession.getTitle().length()))
            : "新会话";
        String info = DIM + sessionTitle + RESET + " │ " + messages.size() + "条";
        int padding = Math.max(0, width - sb.length() - info.length() - 2);
        if (padding > 0) {
            sb.append(" ".repeat(padding));
        }
        sb.append(info);
        return sb.toString();
    }
    
    /**
     * 渲染历史记录屏幕
     */
    private void renderTranscript() {
        int width = terminal.getSize().getColumns();
        int height = terminal.getSize().getRows();
        
        StringBuilder sb = new StringBuilder();
        
        sb.append(CYAN).append("┌─ 历史记录 ").append(RESET);
        sb.append(DIM).append("(Ctrl+O 返回对话)").append(RESET);
        sb.append(" ".repeat(Math.max(0, width - 30)));
        sb.append(CYAN).append("┐").append(RESET).append("\n");
        
        MessageList ml = new MessageList().maxWidth(width).compact(true);
        for (Message msg : frozenMessages) {
            ml.addMessage(msg);
        }
        sb.append(ml.render());
        
        sb.append("\n").append(DIM);
        sb.append("j/k 或 ↑↓ 导航，/ 搜索，Ctrl+O 返回").append(RESET);
        
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
        } catch (Exception e) {
            // 忽略
        }
        terminal.writer().print(sb.toString());
        terminal.flush();
    }
    
    /**
     * 渲染状态栏（简约风格，参照 Claude Code）。
     */
    private String renderStatusBar(int width) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("  ").append(DIM).append("Ctrl+O").append(RESET).append(" 历史 ");
        sb.append(DIM).append("Ctrl+S").append(RESET).append(" 会话 ");
        sb.append(DIM).append("Ctrl+R").append(RESET).append(" 搜索 ");
        sb.append(DIM).append("Ctrl+C").append(RESET).append(" 取消 ");
        sb.append(DIM).append("/help").append(RESET).append(" 帮助");
        
        String sessionTitle = currentSession != null && currentSession.getTitle() != null 
            ? currentSession.getTitle().substring(0, Math.min(15, currentSession.getTitle().length())) 
            : "新会话";
        String info = DIM + sessionTitle + RESET + " │ " + messages.size() + "条";
        int infoLen = info.length();
        sb.append(" ".repeat(Math.max(0, width - 90 - infoLen)));
        sb.append(info);
        
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
     * 获取消息
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