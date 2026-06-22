package com.jwcode.web.stream;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.jwcode.web.stream.StreamingWebSocketHandler.LogEntry;

/**
 * Logback Appender — 将 SLF4J 日志转发到 WebSocketLogBroadcaster，
 * 使后端 logger.info/warn/error 在前端日志面板实时可见。
 *
 * <p>只处理 {@code com.jwcode} 包级别的日志（INFO 及以上），
 * 避免第三方库的调试日志涌入 WebSocket。</p>
 *
 * <p>配置方式（已在 logback.xml 中添加）：</p>
 * <pre>{@code
 * <appender name="WEBSOCKET" class="com.jwcode.web.stream.WebSocketLogAppender"/>
 * <logger name="com.jwcode" level="INFO" additivity="false">
 *   <appender-ref ref="CONSOLE" />
 *   <appender-ref ref="FILE" />
 *   <appender-ref ref="WEBSOCKET" />
 * </logger>
 * }</pre>
 */
public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    // 只转发 com.jwcode 包和指定级别的日志
    private static final String PREFIX = "com.jwcode";

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || !isStarted()) {
            return;
        }

        // 只处理 com.jwcode 包下的日志
        String loggerName = event.getLoggerName();
        if (loggerName == null || !loggerName.startsWith(PREFIX)) {
            return;
        }

        // 映射 Logback 级别 → 前端 LogLevel
        String level = mapLevel(event.getLevel().toString());

        // 提取简短源名称
        String source = shortenLogger(loggerName);

        // 构建 LogEntry 并广播
        LogEntry entry = new LogEntry(level, source, event.getFormattedMessage());
        StreamingWebSocketHandler.WebSocketLogBroadcaster.getInstance().broadcast(entry);
    }

    /**
     * 将 Logback 级别映射到前端日志级别
     */
    private static String mapLevel(String logbackLevel) {
        return switch (logbackLevel) {
            case "TRACE", "DEBUG" -> "info";
            case "WARN" -> "warn";
            case "ERROR" -> "error";
            default -> "info";
        };
    }

    /**
     * 将完整类名截断为简短易读的名称。
     * 如 {@code com.jwcode.core.task.TaskLifecycleManager} → {@code TaskLifecycleManager}
     */
    private static String shortenLogger(String loggerName) {
        int lastDot = loggerName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < loggerName.length() - 1) {
            return loggerName.substring(lastDot + 1);
        }
        return loggerName;
    }
}
