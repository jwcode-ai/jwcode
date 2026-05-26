package com.jwcode.core.terminal;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;

/**
 * JLine3 感知的 Logback Appender。
 *
 * <p>在 JLine3 交互模式下，将日志输出路由到 {@link JLine3Terminal#printAbove(String)}，
 * 在输入行上方打印日志消息并自动重绘提示符，避免后台日志覆盖 {@code jwcode>} 提示符。</p>
 *
 * <p>当 JLine3Terminal 不可用时（非交互模式或初始化失败），降级为 {@code System.err.println()}。</p>
 *
 * <p>使用方式：在 {@code logback.xml} 中配置：</p>
 * <pre>{@code
 * <appender name="JLINE3" class="com.jwcode.core.terminal.JLine3Appender">
 *     <encoder>
 *         <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
 *     </encoder>
 * </appender>
 * }</pre>
 */
public class JLine3Appender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        JLine3Terminal terminal = JLine3Terminal.getInstance();

        // 使用 encoder 格式化日志事件
        String message = formatEvent(event);
        if (message == null) {
            message = event.getFormattedMessage();
        }

        if (terminal != null && terminal.isInteractive()) {
            // JLine3 交互模式下，在输入行上方打印日志
            terminal.printAbove(message.trim());
        } else {
            // 非交互模式或终端不可用，降级到 stderr
            System.err.println(message);
        }
    }

    /**
     * 使用 Logback 配置的 encoder 格式化日志事件。
     */
    @SuppressWarnings("unchecked")
    private String formatEvent(ILoggingEvent event) {
        try {
            java.lang.reflect.Field encoderField = AppenderBase.class.getDeclaredField("encoder");
            encoderField.setAccessible(true);
            Encoder<ILoggingEvent> encoder = (Encoder<ILoggingEvent>) encoderField.get(this);
            if (encoder != null) {
                byte[] bytes = encoder.encode(event);
                if (bytes != null) {
                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            // 反射失败时返回 null，由调用方降级
        }
        return null;
    }
}
