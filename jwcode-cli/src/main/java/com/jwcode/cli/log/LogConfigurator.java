package com.jwcode.cli.log;

import java.util.logging.*;

/**
 * 日志配置器 - 配置 Java 标准日志库
 * 
 * 减少无关日志输出
 */
public class LogConfigurator {
    
    /**
     * 配置简洁日志模式
     */
    public static void configureQuietMode() {
        // 获取根日志器
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.WARNING);
        
        // 禁用控制台处理器
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(Level.WARNING);
            }
        }
        
        // 降低特定包日志级别
        setPackageLevel("com.jwcode.core", Level.WARNING);
        setPackageLevel("org.jline", Level.WARNING);
    }
    
    /**
     * 配置调试模式
     */
    public static void configureDebugMode() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.FINE);
        
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
    }
    
    /**
     * 配置正常模式（推荐）
     */
    public static void configureNormalMode() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        
        // 自定义格式化器
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(new SimpleFormatter() {
                    @Override
                    public String format(LogRecord record) {
                        // 只显示 WARNING 及以上级别
                        if (record.getLevel().intValue() < Level.WARNING.intValue()) {
                            return "";
                        }
                        return String.format("[%s] %s: %s%n",
                            record.getLevel(),
                            record.getSourceClassName().substring(record.getSourceClassName().lastIndexOf('.') + 1),
                            record.getMessage());
                    }
                });
            }
        }
        
        // 降低噪音包的日志级别
        setPackageLevel("java.net.http", Level.WARNING);
        setPackageLevel("sun.net.www.protocol", Level.WARNING);
    }
    
    /**
     * 设置包级别
     */
    private static void setPackageLevel(String packageName, Level level) {
        Logger logger = Logger.getLogger(packageName);
        if (logger != null) {
            logger.setLevel(level);
        }
    }
    
    /**
     * 完全禁用日志
     */
    public static void disableLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.OFF);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(Level.OFF);
        }
    }
}
