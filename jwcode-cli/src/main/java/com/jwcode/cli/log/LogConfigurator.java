package com.jwcode.cli.log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.*;

/**
 * 日志配置器 - 配置日志输出到文件和控制台
 */
public class LogConfigurator {
    
    private static final String LOG_DIR = System.getProperty("user.home") + File.separator + ".jwcode" + File.separator + "logs";
    private static final String LOG_FILE = LOG_DIR + File.separator + "jwcode.log";
    private static final String DEBUG_LOG_FILE = LOG_DIR + File.separator + "jwcode-debug.log";
    private static final long MAX_LOG_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_LOG_FILES = 5;
    
    /**
     * 配置安静模式（最小输出）
     */
    public static void configureQuietMode() {
        try {
            // 创建日志目录
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
            }
            
            // 获取根日志器
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.WARNING);
            
            // 清除现有处理器
            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }
            
            // 添加控制台处理器（仅警告及以上）
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.WARNING);
            consoleHandler.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(consoleHandler);
            
        } catch (IOException e) {
            System.err.println("Failed to configure quiet mode: " + e.getMessage());
        }
    }
    
    /**
     * 配置详细日志模式（用于调试）
     */
    public static void configureVerboseMode() {
        try {
            // 创建日志目录
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
            }
            
            // 获取根日志器
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.ALL);
            
            // 清除现有处理器
            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }
            
            // 添加控制台处理器（全部级别）
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(createDetailedFormatter());
            rootLogger.addHandler(consoleHandler);
            
            // 添加文件处理器（所有级别）
            FileHandler fileHandler = new FileHandler(LOG_FILE, MAX_LOG_SIZE, MAX_LOG_FILES, true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(createDetailedFormatter());
            rootLogger.addHandler(fileHandler);
            
            // 添加调试文件处理器（仅 finest 级别）
            FileHandler debugHandler = new FileHandler(DEBUG_LOG_FILE, MAX_LOG_SIZE, MAX_LOG_FILES, true);
            debugHandler.setLevel(Level.FINEST);
            debugHandler.setFormatter(createDetailedFormatter());
            rootLogger.addHandler(debugHandler);
            
            System.out.println("[LogConfigurator] Verbose logging enabled");
            System.out.println("[LogConfigurator] Log file: " + LOG_FILE);
            System.out.println("[LogConfigurator] Debug file: " + DEBUG_LOG_FILE);
            
        } catch (IOException e) {
            System.err.println("Failed to configure verbose mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 配置调试模式
     */
    public static void configureDebugMode() {
        configureVerboseMode();
        
        // 设置特定包的日志级别
        Logger.getLogger("com.jwcode.core.llm").setLevel(Level.FINEST);
        Logger.getLogger("com.jwcode.cli").setLevel(Level.FINEST);
        Logger.getLogger("com.jwcode.core.tool").setLevel(Level.FINEST);
    }
    
    /**
     * 获取日志文件路径
     */
    public static String getLogFilePath() {
        return LOG_FILE;
    }
    
    /**
     * 获取调试日志文件路径
     */
    public static String getDebugLogFilePath() {
        return DEBUG_LOG_FILE;
    }
    
    /**
     * 创建详细格式化器
     */
    private static Formatter createDetailedFormatter() {
        return new Formatter() {
            @Override
            public String format(LogRecord record) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("[%1$s %2$s %3$s] %4$s - %5$s%n",
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(record.getMillis()),
                    record.getLevel(),
                    Thread.currentThread().getName(),
                    record.getLoggerName(),
                    record.getMessage()
                ));
                
                if (record.getThrown() != null) {
                    sb.append("  Exception: ");
                    sb.append(record.getThrown().toString()).append("\n");
                    for (StackTraceElement element : record.getThrown().getStackTrace()) {
                        if (element.getClassName().startsWith("com.jwcode")) {
                            sb.append("    at ").append(element).append("\n");
                        }
                    }
                }
                
                return sb.toString();
            }
        };
    }
    
    /**
     * 刷新所有处理器
     */
    public static void flush() {
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            handler.flush();
        }
    }
    
    /**
     * 关闭所有处理器
     */
    public static void close() {
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            handler.close();
        }
    }
}
