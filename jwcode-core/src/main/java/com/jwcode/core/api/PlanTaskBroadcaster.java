package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PlanTaskBroadcaster — Plan 模式任务状态广播器。
 *
 * <p>专门用于将 Plan 模式下的任务状态变更通过 WebSocket 广播到前端。
 * 支持两种传输通道：
 * <ul>
 *   <li>主通道：TaskWebSocketServer（独立 WS 服务器）</li>
 *   <li>回退通道：MessageSender（直接注入 sendMessage 回调，通过主 WebSocket 发送）</li>
 * </ul>
 * </p>
 */
public class PlanTaskBroadcaster {

    private static final Logger logger = Logger.getLogger(PlanTaskBroadcaster.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // WebSocket 服务器实例（独立通道）
    private static volatile TaskWebSocketServer wsServer;

    // 回退发送器：当 wsServer 为 null 时使用（直接注入 sendMessage 回调）
    private static volatile MessageSender messageSender;

    // 广播器单例
    private static volatile PlanTaskBroadcaster instance;

    // 是否启用广播
    private boolean enabled = true;

    private PlanTaskBroadcaster() {
    }

    /**
     * 获取单例实例
     */
    public static PlanTaskBroadcaster getInstance() {
        if (instance == null) {
            synchronized (PlanTaskBroadcaster.class) {
                if (instance == null) {
                    instance = new PlanTaskBroadcaster();
                }
            }
        }
        return instance;
    }

    /**
     * 设置 WebSocket 服务器
     */
    public static void setWebSocketServer(TaskWebSocketServer server) {
        wsServer = server;
        logger.info("PlanTaskBroadcaster: WebSocket server configured");
    }

    /**
     * 设置回退消息发送器（当 wsServer 为 null 时使用）
     */
    public static void setMessageSender(MessageSender sender) {
        messageSender = sender;
        logger.info("PlanTaskBroadcaster: messageSender configured (fallback transport)");
    }

    /**
     * 启用/禁用广播
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("PlanTaskBroadcaster: enabled=" + enabled);
    }

    /**
     * 检查是否已配置（任一通道可用即为已配置）
     */
    public static boolean isConfigured() {
        return wsServer != null || messageSender != null;
    }

    // ==================== 统一分发 ====================

    /**
     * 通过可用通道发送广播消息
     */
    private void dispatch(String type, String sessionId, String data) {
        if (!enabled || sessionId == null) return;
        if (wsServer != null) {
            try {
                wsServer.broadcast(Map.of("type", type, "sessionId", sessionId, "data", data));
                return;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to broadcast via wsServer, trying fallback", e);
            }
        }
        if (messageSender != null) {
            try {
                messageSender.send(type, sessionId, data);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send via messageSender", e);
            }
        }
    }

    // ==================== 广播方法 ====================

    /**
     * 广播 plan_start — 开始规划
     */
    public void broadcastPlanStart(String sessionId, String data) {
        dispatch("plan_start", sessionId, data != null ? data : "");
        logger.fine("[PlanBroadcaster] plan_start: session=" + sessionId);
    }

    /**
     * 广播 plan_thinking — 规划思考中
     */
    public void broadcastPlanThinking(String sessionId, String thought) {
        dispatch("plan_thinking", sessionId, thought != null ? thought : "");
    }

    /**
     * 广播 plan_tasks — 发送完整的任务树列表
     */
    public void broadcastPlanTasks(String sessionId, Object tasksJson) {
        try {
            String dataStr = tasksJson instanceof String
                    ? (String) tasksJson
                    : MAPPER.writeValueAsString(tasksJson);
            dispatch("plan_tasks", sessionId, "{\"tasks\":" + dataStr + "}");
            logger.info("[PlanBroadcaster] plan_tasks sent: session=" + sessionId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_tasks", e);
        }
    }

    /**
     * 广播 plan_tasks — 发送原始结构化数据（不额外包装）
     */
    public void broadcastPlanData(String sessionId, Object payload) {
        try {
            String dataStr = payload instanceof String
                    ? (String) payload
                    : MAPPER.writeValueAsString(payload);
            dispatch("plan_tasks", sessionId, dataStr);
            logger.info("[PlanBroadcaster] plan_tasks raw data sent: session=" + sessionId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_tasks raw data", e);
        }
    }

    /**
     * 广播 plan_task_start — 单个任务开始执行
     */
    public void broadcastPlanTaskStart(String sessionId, String taskId, String agentType) {
        dispatch("plan_task_start", sessionId,
                "{\"id\":\"" + escapeJson(taskId) + "\",\"agentType\":\"" + escapeJson(agentType) + "\"}");
        logger.fine("[PlanBroadcaster] plan_task_start: " + taskId);
    }

    /**
     * 广播 plan_task_update — 单个任务进度更新
     */
    public void broadcastPlanTaskUpdate(String sessionId, String taskId, int progress, String logs) {
        StringBuilder json = new StringBuilder();
        json.append("{\"id\":\"").append(escapeJson(taskId)).append("\"");
        json.append(",\"progress\":").append(progress);
        if (logs != null && !logs.isEmpty()) {
            json.append(",\"logs\":\"").append(escapeJson(logs)).append("\"");
        }
        json.append("}");
        dispatch("plan_task_update", sessionId, json.toString());
    }

    /**
     * 广播 plan_task_result — 单个任务完成/失败
     */
    public void broadcastPlanTaskResult(String sessionId, String taskId,
                                         String status, String result, String error) {
        StringBuilder json = new StringBuilder();
        json.append("{\"id\":\"").append(escapeJson(taskId)).append("\"");
        json.append(",\"status\":\"").append(escapeJson(status)).append("\"");
        if (result != null && !result.isEmpty()) {
            json.append(",\"result\":\"").append(escapeJson(result)).append("\"");
        }
        if (error != null && !error.isEmpty()) {
            json.append(",\"error\":\"").append(escapeJson(error)).append("\"");
        }
        json.append("}");
        dispatch("plan_task_result", sessionId, json.toString());
        logger.fine("[PlanBroadcaster] plan_task_result: " + taskId + " = " + status);
    }

    /**
     * 广播 plan_complete — 全部任务完成
     */
    public void broadcastPlanComplete(String sessionId, String result) {
        dispatch("plan_complete", sessionId, result != null ? result : "");
        logger.info("[PlanBroadcaster] plan_complete: session=" + sessionId);
    }

    /**
     * 广播 plan_error — 规划出错
     */
    public void broadcastPlanError(String sessionId, String error) {
        dispatch("plan_error", sessionId, error != null ? error : "未知错误");
        logger.warning("[PlanBroadcaster] plan_error: session=" + sessionId + " error=" + error);
    }

    /**
     * 广播 step_prompt — 进入步骤时向AI注入的上下文提示
     */
    public void broadcastStepPrompt(String sessionId, String taskId, int stepIndex,
                                     String description, String action, String stepPrompt, String agentType) {
        StringBuilder json = new StringBuilder();
        json.append("{\"taskId\":\"").append(escapeJson(taskId)).append("\"");
        json.append(",\"stepIndex\":").append(stepIndex);
        json.append(",\"stepNumber\":").append(stepIndex + 1);
        json.append(",\"description\":\"").append(escapeJson(description)).append("\"");
        json.append(",\"action\":\"").append(escapeJson(action != null ? action : "")).append("\"");
        json.append(",\"stepPrompt\":\"").append(escapeJson(stepPrompt != null ? stepPrompt : "")).append("\"");
        json.append(",\"agentType\":\"").append(escapeJson(agentType != null ? agentType : "")).append("\"");
        json.append("}");
        dispatch("step_prompt", sessionId, json.toString());
        logger.info("[PlanBroadcaster] step_prompt: session=" + sessionId + " step=" + (stepIndex + 1));
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
