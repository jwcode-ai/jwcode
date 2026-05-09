package com.jwcode.core.log;

import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.logging.LogManager;

/**
 * 日志桥接器 — 将 java.util.logging 路由到 SLF4J。
 *
 * <p>在应用启动时调用 {@link #install()}，将所有 JUL 日志重定向到 SLF4J/Logback，
 * 统一日志输出格式和目的地。这样项目中混用的 {@code java.util.logging.Logger}、
 * {@code lombok.extern.java.Log} 都会被桥接到 SLF4J。</p>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * public static void main(String[] args) {
 *     LoggingBridge.install();
 *     // ... 启动应用
 * }
 * }</pre>
 */
public final class LoggingBridge {

    private static volatile boolean installed = false;

    private LoggingBridge() {
        // 工具类，禁止实例化
    }

    /**
     * 安装 JUL → SLF4J 桥接。
     *
     * <p>安全可重复调用，只有第一次调用会生效。</p>
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }

        // 关闭 JUL 的根日志器（防止双重输出）
        LogManager.getLogManager().reset();

        // 安装桥接器
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        installed = true;

        // 使用 SLF4J 输出安装成功的日志
        org.slf4j.LoggerFactory.getLogger(LoggingBridge.class)
                .info("JUL-to-SLF4J bridge installed. All java.util.logging output routed to SLF4J.");
    }

    /**
     * 检查桥接是否已安装
     */
    public static boolean isInstalled() {
        return installed;
    }
}
