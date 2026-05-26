package com.jwcode.core.hook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * HookEventBroadcaster — Hook 事件推送器。
 *
 * <p>将 Hook 拦截事件推送至日志和前端（通过 WebSocket）。
 * 当前版本通过日志输出，WebSocket 推送预留扩展点。</p>
 *
 * <h3>日志格式</h3>
 * <pre>
 * [HookEvent] PRE_TOOL_USE | BashSafetyHook → DENY | Dangerous command pattern...
 * </pre>
 *
 * @author JWCode Team
 * @since 2.1.1
 */
public class HookEventBroadcaster {

    private static final Logger logger = Logger.getLogger(HookEventBroadcaster.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** WebSocket 广播回调（可选，由 TaskApiServer 注入） */
    private static volatile WebSocketHookSender wsSender;

    /** WebSocket 发送器接口 */
    @FunctionalInterface
    public interface WebSocketHookSender {
        void send(String jsonMessage);
    }

    /**
     * 设置 WebSocket 发送器。
     */
    public static void setWebSocketSender(WebSocketHookSender sender) {
        wsSender = sender;
    }

    /**
     * 广播 Hook 事件。
     *
     * @param context Hook 上下文
     * @param result  Hook 执行结果
     */
    public static void broadcast(HookContext context, HookResult result) {
        // 1. 日志输出
        String toolInfo = context.getToolName() != null
            ? " tool=" + context.getToolName() : "";
        logger.info("[HookEvent] " + context.getEventType() + toolInfo
            + " | " + result.getHookName() + " → " + result.getDecision()
            + (result.getReason() != null ? " | " + result.getReason() : ""));

        // 2. WebSocket 推送（如果已配置）
        if (wsSender != null) {
            try {
                ObjectNode msg = MAPPER.createObjectNode();
                msg.put("type", "hook-event");
                msg.put("event", context.getEventType().name());
                msg.put("hookName", result.getHookName());
                msg.put("decision", result.getDecision().name());
                if (result.getReason() != null && !result.getReason().isEmpty()) {
                    msg.put("reason", result.getReason());
                }
                if (context.getToolName() != null) {
                    msg.put("toolName", context.getToolName());
                }
                msg.put("timestamp", Instant.now().toString());
                wsSender.send(MAPPER.writeValueAsString(msg));
            } catch (JsonProcessingException e) {
                logger.fine("[HookEventBroadcaster] WS send failed: " + e.getMessage());
            }
        }
    }

    /**
     * 广播 Hook 系统初始化完成事件。
     */
    public static void broadcastInit(int hookCount, int resolvedCount) {
        logger.info("[HookEvent] System initialized: " + hookCount + " hooks, "
            + resolvedCount + " resolved");

        if (wsSender != null) {
            try {
                ObjectNode msg = MAPPER.createObjectNode();
                msg.put("type", "hook-init");
                msg.put("hookCount", hookCount);
                msg.put("resolvedCount", resolvedCount);
                msg.put("timestamp", Instant.now().toString());
                wsSender.send(MAPPER.writeValueAsString(msg));
            } catch (JsonProcessingException e) {
                logger.fine("[HookEventBroadcaster] Init WS send failed: " + e.getMessage());
            }
        }
    }
}
