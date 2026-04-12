package com.jwcode.cli.log;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * CLI 日志器 - 优化的终端日志输出
 * 
 * 参照 Kimi Code 的日志风格：
 * - 简洁格式
 * - 彩色输出
 * - 分级控制
 * - 进度指示
 */
public class CliLogger {
    
    // ANSI 颜色
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String GRAY = "\u001B[90m";
    public static final String RED = "\u001B[91m";
    public static final String GREEN = "\u001B[92m";
    public static final String YELLOW = "\u001B[93m";
    public static final String BLUE = "\u001B[94m";
    public static final String MAGENTA = "\u001B[95m";
    public static final String CYAN = "\u001B[96m";
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private LogLevel level = LogLevel.INFO;
    private boolean useColor = true;
    private boolean showTimestamp = true;
    private boolean compactMode = true;
    
    public enum LogLevel {
        DEBUG(0, "DEBUG", GRAY),
        INFO(1, "INFO", BLUE),
        SUCCESS(2, "✓", GREEN),
        WARN(3, "⚠", YELLOW),
        ERROR(4, "✗", RED);
        
        final int value;
        final String symbol;
        final String color;
        
        LogLevel(int value, String symbol, String color) {
            this.value = value;
            this.symbol = symbol;
            this.color = color;
        }
    }
    
    private static CliLogger instance;
    
    public static CliLogger getInstance() {
        if (instance == null) {
            instance = new CliLogger();
        }
        return instance;
    }
    
    /**
     * 设置日志级别
     */
    public void setLevel(LogLevel level) {
        this.level = level;
    }
    
    /**
     * 设置是否使用颜色
     */
    public void setUseColor(boolean useColor) {
        this.useColor = useColor;
    }
    
    /**
     * 设置紧凑模式
     */
    public void setCompactMode(boolean compact) {
        this.compactMode = compact;
    }
    
    // ============ 日志方法 ============
    
    public void debug(String message) {
        if (level.value <= LogLevel.DEBUG.value) {
            print(LogLevel.DEBUG, message);
        }
    }
    
    public void info(String message) {
        if (level.value <= LogLevel.INFO.value) {
            print(LogLevel.INFO, message);
        }
    }
    
    public void success(String message) {
        if (level.value <= LogLevel.SUCCESS.value) {
            print(LogLevel.SUCCESS, message);
        }
    }
    
    public void warn(String message) {
        if (level.value <= LogLevel.WARN.value) {
            print(LogLevel.WARN, message);
        }
    }
    
    public void warning(String message) {
        warn(message);
    }
    
    public void error(String message) {
        if (level.value <= LogLevel.ERROR.value) {
            print(LogLevel.ERROR, message);
        }
    }
    
    public void error(String message, Throwable e) {
        error(message);
        if (e != null && level == LogLevel.DEBUG) {
            e.printStackTrace();
        }
    }
    
    // ============ 特殊输出 ============
    
    /**
     * 打印工具调用
     */
    public void toolCall(String toolName, String params) {
        if (compactMode) {
            String line = String.format("┌─ %s %s", 
                colorize("Tool:", CYAN), 
                colorize(toolName, BOLD + CYAN));
            System.out.println(line);
            
            if (params != null && !params.isEmpty()) {
                String truncated = params.length() > 80 ? 
                    params.substring(0, 77) + "..." : params;
                System.out.println("│ " + colorize(truncated, GRAY));
            }
            System.out.println("└");
        } else {
            info("Calling tool: " + toolName);
        }
    }
    
    /**
     * 打印工具结果
     */
    public void toolResult(String toolName, boolean success, String result) {
        if (compactMode) {
            String symbol = success ? "✓" : "✗";
            String color = success ? GREEN : RED;
            String line = String.format("%s %s %s", 
                colorize(symbol, color),
                colorize(toolName, color),
                success ? "success" : "failed");
            System.out.println(line);
        }
    }
    
    /**
     * 打印流式内容
     */
    public void stream(String content) {
        System.out.print(content);
        System.out.flush();
    }
    
    /**
     * 打印思考过程
     */
    public void thinking(String content) {
        System.out.print(colorize(content, GRAY));
        System.out.flush();
    }
    
    /**
     * 打印进度
     */
    public void progress(String message, int current, int total) {
        int width = 20;
        int filled = (int) ((double) current / total * width);
        
        StringBuilder bar = new StringBuilder();
        bar.append("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("]");
        
        String line = String.format("\r%s %s %d/%d", 
            colorize(message, CYAN),
            bar.toString(),
            current, total);
        
        System.out.print(line);
        if (current == total) {
            System.out.println();
        }
        System.out.flush();
    }
    
    /**
     * 打印分隔线
     */
    public void divider() {
        System.out.println(colorize("─".repeat(50), GRAY));
    }
    
    /**
     * 打印标题
     */
    public void title(String text) {
        System.out.println();
        System.out.println(colorize("╔" + "═".repeat(48) + "╗", CYAN));
        System.out.println(colorize("║" + center(text, 48) + "║", CYAN));
        System.out.println(colorize("╚" + "═".repeat(48) + "╝", CYAN));
    }
    
    // ============ 私有方法 ============
    
    private void print(LogLevel level, String message) {
        StringBuilder sb = new StringBuilder();
        
        // 时间戳
        if (showTimestamp) {
            String time = LocalTime.now().format(TIME_FORMATTER);
            sb.append(colorize("[" + time + "]", GRAY)).append(" ");
        }
        
        // 级别符号
        if (compactMode && level.symbol.length() <= 2) {
            sb.append(colorize(level.symbol, level.color)).append(" ");
        } else {
            sb.append(colorize("[" + level.name() + "]", level.color)).append(" ");
        }
        
        // 消息
        sb.append(message);
        
        System.out.println(sb.toString());
    }
    
    private String colorize(String text, String color) {
        if (!useColor) return text;
        return color + text + RESET;
    }
    
    private String center(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text + " ".repeat(width - text.length() - padding);
    }
    
    // ============ 静态便捷方法 ============
    
    public static void logInfo(String message) {
        getInstance().info(message);
    }
    
    public static void logSuccess(String message) {
        getInstance().success(message);
    }
    
    public static void logError(String message) {
        getInstance().error(message);
    }
    
    public static void logWarn(String message) {
        getInstance().warn(message);
    }
    
    public static void logDebug(String message) {
        getInstance().debug(message);
    }
    
    // ============ AI 活动日志便捷方法 ============
    
    /**
     * 记录 AI 正在读取文件
     */
    public static String readingFile(String filePath) {
        return ActivityLogger.start(ActivityType.FILE_READ, "读取文件: " + filePath);
    }
    
    /**
     * 记录 AI 正在写入文件
     */
    public static String writingFile(String filePath) {
        return ActivityLogger.start(ActivityType.FILE_WRITE, "写入文件: " + filePath);
    }
    
    /**
     * 记录 AI 正在编辑文件
     */
    public static String editingFile(String filePath) {
        return ActivityLogger.start(ActivityType.FILE_EDIT, "编辑文件: " + filePath);
    }
    
    /**
     * 记录 AI 正在搜索代码
     */
    public static String searchingCode(String pattern, String path) {
        String desc = "搜索: " + pattern;
        if (path != null) desc += " 在 " + path;
        return ActivityLogger.start(ActivityType.CODE_SEARCH, desc);
    }
    
    /**
     * 记录 AI 正在搜索文件
     */
    public static String searchingFiles(String pattern) {
        return ActivityLogger.start(ActivityType.GLOB_SEARCH, "搜索文件: " + pattern);
    }
    
    /**
     * 记录 AI 正在执行命令
     */
    public static String executingCommand(String command) {
        return ActivityLogger.start(ActivityType.SHELL_EXEC, "执行: " + truncate(command, 60));
    }
    
    /**
     * 记录 AI 正在网络搜索
     */
    public static String webSearching(String query) {
        return ActivityLogger.start(ActivityType.WEB_SEARCH, "搜索: " + query);
    }
    
    /**
     * 记录 AI 正在思考
     */
    public static String aiThinking(String topic) {
        return ActivityLogger.start(ActivityType.AI_THINKING, "思考: " + topic);
    }
    
    /**
     * 记录 AI 正在规划
     */
    public static String aiPlanning(String task) {
        return ActivityLogger.start(ActivityType.AI_PLANNING, "规划: " + task);
    }
    
    /**
     * 完成活动
     */
    public static void done(String activityId) {
        ActivityLogger.complete(activityId);
    }
    
    /**
     * 完成活动（带结果）
     */
    public static void done(String activityId, String result) {
        ActivityLogger.complete(activityId, result);
    }
    
    /**
     * 活动失败
     */
    public static void failed(String activityId, String error) {
        ActivityLogger.fail(activityId, error);
    }
    
    /**
     * 更新进度
     */
    public static void progress(String activityId, int percent) {
        ActivityLogger.progress(activityId, percent);
    }
    
    // ============ 实时状态显示 ============
    
    /**
     * 显示 AI 正在思考动画
     */
    public static void showThinking(String message) {
        String id = ActivityLogger.start(ActivityType.AI_THINKING, message);
        // 模拟思考动画
        try {
            for (int i = 0; i <= 100; i += 10) {
                Thread.sleep(100);
                ActivityLogger.progress(id, i);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ActivityLogger.complete(id, "思考完成");
    }
    
    /**
     * 显示工具调用框（KimiCode 风格）
     */
    public static void toolCallBox(String toolName, String params) {
        System.out.println();
        System.out.println(CYAN + "┌─ Tool: " + BOLD + toolName + RESET);
        if (params != null && !params.isEmpty()) {
            String[] lines = params.split("\\r?\\n");
            for (String line : lines) {
                System.out.println(CYAN + "│ " + GRAY + truncate(line, 70) + RESET);
            }
        }
        System.out.println(CYAN + "└" + RESET);
    }
    
    /**
     * 显示工具结果框
     */
    public static void toolResultBox(String toolName, boolean success, String result) {
        String color = success ? GREEN : RED;
        String icon = success ? "✓" : "✗";
        System.out.println(color + icon + " " + toolName + (success ? " 成功" : " 失败") + RESET);
        if (result != null && !result.isEmpty()) {
            System.out.println(GRAY + "  → " + truncate(result, 60) + RESET);
        }
    }
    
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
