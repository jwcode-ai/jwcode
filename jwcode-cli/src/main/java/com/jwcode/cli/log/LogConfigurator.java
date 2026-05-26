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
     * 配置正常模式（仅文件 WARNING+）。
     * <p>注意：控制台日志由 Logback 统一管理（通过 LoggingBridge 桥接 JUL→SLF4J），
     * 此处不再重复配置 ConsoleHandler，避免与 JLine3 的 stdout 行编辑冲突。</p>
     */
    public static void configureQuietMode() {
        try {
            // 创建日志目录
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
            }
            
            // 文件处理器：WARNING 及以上（单文件模式，避免 .0 后缀）
            FileHandler fileHandler = new FileHandler(LOG_FILE, true);
            fileHandler.setLevel(Level.WARNING);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setFormatter(createDetailedFormatter());
            
            // 获取根日志器，仅添加文件处理器（不移除已有处理器，避免破坏 LoggingBridge）
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(fileHandler);
            
            // 诊断：强制写入一条日志确保文件创建
            Logger.getLogger(LogConfigurator.class.getName()).warning("Logging configured. Log file: " + LOG_FILE);
            fileHandler.flush();
            
        } catch (IOException e) {
            System.err.println("[LogConfigurator] Failed to configure logging: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 配置详细日志模式（用于调试）
     * <p>控制台日志由 Logback 统一管理，此处仅配置文件处理器。</p>
     */
    public static void configureVerboseMode() {
        try {
            // 创建日志目录
            Path logPath = Paths.get(LOG_DIR);
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
            }
            
            // 添加文件处理器（所有级别）
            Logger rootLogger = Logger.getLogger("");
            FileHandler fileHandler = new FileHandler(LOG_FILE, MAX_LOG_SIZE, MAX_LOG_FILES, true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setFormatter(createDetailedFormatter());
            rootLogger.addHandler(fileHandler);
            
            // 添加调试文件处理器（仅 finest 级别）
            FileHandler debugHandler = new FileHandler(DEBUG_LOG_FILE, MAX_LOG_SIZE, MAX_LOG_FILES, true);
            debugHandler.setLevel(Level.FINEST);
            debugHandler.setEncoding("UTF-8");
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
