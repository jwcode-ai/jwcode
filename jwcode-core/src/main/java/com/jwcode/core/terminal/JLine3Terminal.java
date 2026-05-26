package com.jwcode.core.terminal;

import org.jline.reader.*;
import org.jline.terminal.*;
import org.jline.utils.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * JLine3 终端封装类
 * 提供命令历史、自动补全和富文本渲染功能
 */
public class JLine3Terminal implements AutoCloseable {
    
    private static volatile JLine3Terminal instance;
    private volatile boolean interactive = false;
    
    private final Terminal terminal;
    private final LineReader lineReader;
    private final History history;
    private final Map<String, List<String>> commandCompletions;
    
    public JLine3Terminal() throws IOException {
        // 创建终端
        this.terminal = TerminalBuilder.builder()
                .name("JWCode")
                .build();
        
        // 创建 LineReader（使用默认历史记录）
        this.lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new JwCodeCompleter())
                .build();
        
        this.history = lineReader.getHistory();
        
        // 初始化命令补全
        this.commandCompletions = initializeCompletions();
        
        // 注册为全局实例
        instance = this;
    }
    
    /**
     * 获取当前 JLine3Terminal 实例（可能为 null）。
     */
    public static JLine3Terminal getInstance() {
        return instance;
    }
    
    /**
     * 标记终端进入/退出交互模式。
     * 交互模式下，后台日志将通过 printAbove 输出，避免干扰提示符。
     */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }
    
    /**
     * 检查终端是否处于交互模式。
     */
    public boolean isInteractive() {
        return interactive;
    }
    
    /**
     * 初始化补全列表
     */
    private Map<String, List<String>> initializeCompletions() {
        Map<String, List<String>> completions = new HashMap<>();
        
        // 基础命令补全
        completions.put("help", Arrays.asList(
                "help", "help -all", "help -v"
        ));
        completions.put("exit", Arrays.asList(
                "exit", "exit --force"
        ));
        completions.put("config", Arrays.asList(
                "config set", "config get", "config list", "config reset"
        ));
        completions.put("clear", Arrays.asList(
                "clear", "clear -history"
        ));
        
        // 常用命令前缀
        completions.put("", Arrays.asList(
                "help", "exit", "clear", "config", "plan", "todo", "theme",
                "diff", "files", "summary", "usage", "cost", "doctor",
                "export", "copy"
        ));
        
        return completions;
    }
    
    /**
     * 读取用户输入（单行）
     */
    public String readLine(String prompt) {
        try {
            return lineReader.readLine(prompt);
        } catch (UserInterruptException e) {
            // Ctrl+C 被按下
            return null;
        } catch (EndOfFileException e) {
            // EOF
            return null;
        }
    }
    
    /**
     * 读取多行输入（支持 Shift+Enter 和反斜杠续行）
     * Claude Code 风格的多行输入
     */
    public String readMultiline(String prompt) {
        StringBuilder input = new StringBuilder();
        String continuationPrompt = "      │ ";  // 多行继续提示
        
        // 读取第一行
        String line = readLine(prompt);
        if (line == null) return null;
        
        input.append(line);
        
        // 检测反斜杠续行或 Shift+Enter（检测行末有反斜杠）
        while (line.endsWith("\\")) {
            // 移除末尾的反斜杠
            if (input.length() > 0 && input.charAt(input.length() - 1) == '\\') {
                input.deleteCharAt(input.length() - 1);
            }
            
            // 读取下一行
            line = readLine(continuationPrompt);
            if (line == null) break;
            
            input.append("\n").append(line);
        }
        
        return input.toString();
    }
    
    /**
     * 读取密码输入
     */
    public String readPassword(String prompt) {
        try {
            return lineReader.readLine(prompt, '*');
        } catch (UserInterruptException e) {
            return null;
        } catch (EndOfFileException e) {
            return null;
        }
    }
    
    /**
     * 打印带有颜色的消息（简化版）
     */
    public void printColored(String message, AttributedStyle style) {
        // 简化输出：直接打印消息，不使用 ANSI 样式
        terminal.writer().print(message);
        terminal.flush();
    }
    
    /**
     * 打印普通消息
     */
    public void print(String message) {
        terminal.writer().print(message);
        terminal.flush();
    }
    
    /**
     * 打印行（带 String 参数）
     */
    public void println(String message) {
        terminal.writer().println(message);
        terminal.flush();
    }
    
    /**
     * 打印空行（无参数）
     */
    public void println() {
        terminal.writer().println();
        terminal.flush();
    }
    
    /**
     * 在输入行上方打印消息（不干扰当前输入）。
     * <p>使用 JLine3 的 printAbove 机制，在用户输入行上方输出日志，
     * 然后自动重绘提示符和当前输入内容。适用于后台任务日志输出。</p>
     *
     * @param message 要打印的消息
     */
    public void printAbove(String message) {
        try {
            lineReader.printAbove(message);
        } catch (Exception e) {
            // 降级：直接输出到终端
            terminal.writer().println(message);
            terminal.flush();
        }
    }
    
    /**
     * 在输入行上方打印格式化消息。
     *
     * @param format 消息格式
     * @param args   格式化参数
     */
    public void printAbove(String format, Object... args) {
        printAbove(String.format(format, args));
    }
    
    /**
     * 打印错误消息（红色）
     */
    public void printError(String message) {
        printColored(message, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
    }
    
    /**
     * 打印成功消息（绿色）
     */
    public void printSuccess(String message) {
        printColored(message, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
    }
    
    /**
     * 打印警告消息（黄色）
     */
    public void printWarning(String message) {
        printColored(message, AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
    }
    
    /**
     * 打印信息消息（蓝色）
     */
    public void printInfo(String message) {
        printColored(message, AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
    }
    
    /**
     * 清除屏幕
     */
    public void clear() {
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();
        } catch (Exception e) {
            // 降级方案：打印多行空行
            for (int i = 0; i < 50; i++) {
                terminal.writer().println();
            }
        }
    }
    
    /**
     * 获取终端宽度
     */
    public int getWidth() {
        return terminal.getSize().getColumns();
    }
    
    /**
     * 获取终端高度
     */
    public int getHeight() {
        return terminal.getSize().getRows();
    }
    
    /**
     * 添加到历史记录
     */
    public void addToHistory(String line) {
        if (line != null && !line.trim().isEmpty()) {
            history.add(line);
        }
    }
    
    /**
     * 获取历史记录（使用迭代器）
     */
    public List<String> getHistory() {
        List<String> result = new ArrayList<>();
        history.forEach(entry -> result.add(entry.line()));
        return result;
    }
    
    /**
     * 搜索历史记录
     */
    public List<String> searchHistory(String prefix) {
        List<String> result = new ArrayList<>();
        history.forEach(entry -> {
            if (entry.line().startsWith(prefix)) {
                result.add(entry.line());
            }
        });
        return result;
    }
    
    /**
     * 保存历史记录到文件（暂不实现）
     */
    public void saveHistory(Path file) throws IOException {
        // 历史记录由 JLine3 自动管理，保存到 ~/.jwcode/history
        // 暂不实现自定义保存
    }
    
    /**
     * 从文件加载历史记录（暂不实现）
     */
    public void loadHistory(Path file) throws IOException {
        // 历史记录由 JLine3 自动管理
        // 暂不实现自定义加载
    }
    
    @Override
    public void close() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException e) {
            // 忽略关闭错误
        }
    }
    
    /**
     * JWCode 命令补全器
     */
    private class JwCodeCompleter implements Completer {
        
        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word();
            String command = line.line().isEmpty() ? "" : line.line().split("\\s+")[0];
            
            // 获取可能的补全列表
            List<String> completions = getCompletions(command, word);
            
            for (String completion : completions) {
                candidates.add(new Candidate(
                        completion,
                        completion,
                        "Command",
                        null,
                        null,
                        null,
                        true
                ));
            }
        }
        
        private List<String> getCompletions(String command, String prefix) {
            List<String> result = new ArrayList<>();
            
            // 如果命令为空，提供所有命令
            if (command.isEmpty()) {
                for (String cmd : commandCompletions.getOrDefault("", Collections.emptyList())) {
                    if (cmd.startsWith(prefix)) {
                        result.add(cmd);
                    }
                }
            } else {
                // 提供特定命令的补全
                List<String> cmdCompletions = commandCompletions.getOrDefault(command, Collections.emptyList());
                for (String completion : cmdCompletions) {
                    if (completion.startsWith(prefix)) {
                        result.add(completion);
                    }
                }
            }
            
            return result;
        }
    }
}