package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TodoWriteBroadcaster — TodoWrite 状态广播器（1.3）。
 *
 * <p>将 TodoWrite 工具的状态变更通过 WebSocket 广播到前端，实现实时进度显示。
 * 与 PlanTaskBroadcaster 职责分离：PlanTaskBroadcaster 负责 plan_* 消息（任务树），
 * 而 TodoWriteBroadcaster 负责 todo_* 消息（待办列表）。</p>
 *
 * <p>支持的消息类型：
 * <ul>
 *   <li>todo_update — 待办事项列表更新（全量替换）</li>
 *   <li>todo_item_done — 单项完成通知</li>
 *   <li>todo_progress — 进度百分比更新</li>
 * </ul>
 * </p>
 */
public class TodoWriteBroadcaster {

    private static final Logger logger = Logger.getLogger(TodoWriteBroadcaster.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

// WebSocket 服务器实例（已废弃，优先使用 broadcastAdapter）
    private static volatile TaskWebSocketServer wsServer;

    /**
     * 广播适配器（替代 WebSocket 服务器，用于主 WebSocket 集成）
     */
    @FunctionalInterface
    public interface BroadcastAdapter {
        void broadcast(String sessionId, String type, String data);
    }
    
    private static volatile BroadcastAdapter broadcastAdapter;

    // 广播器单例
    private static volatile TodoWriteBroadcaster instance;

    // 是否启用广播
    private boolean enabled = true;

    private TodoWriteBroadcaster() {
    }

    /**
     * 获取单例实例
     */
    public static TodoWriteBroadcaster getInstance() {
        if (instance == null) {
            synchronized (TodoWriteBroadcaster.class) {
                if (instance == null) {
                    instance = new TodoWriteBroadcaster();
                }
            }
        }
        return instance;
    }

    /**
     * 设置广播适配器 — 用于主 WebSocket 集成
     * @param adapter 广播适配器
     */
    public static void setBroadcastAdapter(BroadcastAdapter adapter) {
        broadcastAdapter = adapter;
        logger.info("TodoWriteBroadcaster: BroadcastAdapter configured");
    }

    /**
     * 启用/禁用广播
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("TodoWriteBroadcaster: enabled=" + enabled);
    }

    /**
     * 检查是否已配置
     */
    public static boolean isConfigured() {
        return wsServer != null;
    }

    // ==================== 广播方法 ====================

    /**
     * 广播 todo_update — 待办事项列表更新（全量替换）
     *
     * <p>每次 TodoWrite 操作后调用，发送完整的待办列表给前端。</p>
     *
     * @param sessionId 会话ID
     * @param todosJson 待办事项 JSON 数组
     */
    public void broadcastTodoUpdate(String sessionId, Object todosJson) {
        if (!enabled || sessionId == null) return;
        try {
            String dataStr = todosJson instanceof String
                    ? (String) todosJson
                    : MAPPER.writeValueAsString(todosJson);
            // 优先使用 BroadcastAdapter
            if (broadcastAdapter != null) {
                broadcastAdapter.broadcast(sessionId, "todo_update", dataStr);
            } else if (wsServer != null) {
                wsServer.broadcast(Map.of(
                        "type", "todo_update",
                        "sessionId", sessionId,
                        "data", dataStr
                ));
            }
            logger.fine("[TodoBroadcaster] todo_update: session=" + sessionId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast todo_update", e);
        }
    }

    /**
     * 广播 todo_item_done — 单项完成通知
     *
     * @param sessionId 会话ID
     * @param taskId    完成的任务ID
     * @param summary   完成摘要
     */
    public void broadcastTodoItemDone(String sessionId, String taskId, String summary) {
        if (!enabled || sessionId == null) return;
        try {
            String dataStr = "{\"id\":\"" + escapeJson(taskId) + "\",\"summary\":\"" + escapeJson(summary) + "\"}";
            if (broadcastAdapter != null) {
                broadcastAdapter.broadcast(sessionId, "todo_item_done", dataStr);
            } else if (wsServer != null) {
                wsServer.broadcast(Map.of(
                        "type", "todo_item_done",
                        "sessionId", sessionId,
                        "data", dataStr
                ));
            }
            logger.fine("[TodoBroadcaster] todo_item_done: " + taskId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast todo_item_done", e);
        }
    }

    /**
     * 广播 todo_progress — 进度百分比更新
     *
     * @param sessionId   会话ID
     * @param completed   已完成数量
     * @param total       总数量
     * @param description 当前进行中的描述
     */
    public void broadcastTodoProgress(String sessionId, int completed, int total, String description) {
        if (!enabled || sessionId == null) return;
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"completed\":").append(completed);
            json.append(",\"total\":").append(total);
            if (description != null && !description.isEmpty()) {
                json.append(",\"description\":\"").append(escapeJson(description)).append("\"");
            }
            json.append("}");
            String dataStr = json.toString();
            if (broadcastAdapter != null) {
                broadcastAdapter.broadcast(sessionId, "todo_progress", dataStr);
            } else if (wsServer != null) {
                wsServer.broadcast(Map.of(
                        "type", "todo_progress",
                        "sessionId", sessionId,
                        "data", dataStr
                ));
            }
            logger.fine("[TodoBroadcaster] todo_progress: " + completed + "/" + total);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast todo_progress", e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
