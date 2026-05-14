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
 * 与 StepMessageBroadcaster 职责分离，StepMessageBroadcaster 负责 step_* 消息（Chat 面板步骤），
 * 而 PlanTaskBroadcaster 负责 plan_* 消息（Plan 面板任务树）。</p>
 *
 * <p>支持的消息类型：
 * <ul>
 *   <li>plan_start — 开始规划</li>
 *   <li>plan_thinking — 规划思考中</li>
 *   <li>plan_tasks — 任务列表生成</li>
 *   <li>plan_task_start — 单个任务开始执行</li>
 *   <li>plan_task_update — 单个任务进度更新</li>
 *   <li>plan_task_result — 单个任务完成/失败</li>
 *   <li>plan_complete — 全部任务完成</li>
 *   <li>plan_error — 规划出错</li>
 * </ul>
 * </p>
 */
public class PlanTaskBroadcaster {

    private static final Logger logger = Logger.getLogger(PlanTaskBroadcaster.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // WebSocket 服务器实例
    private static volatile TaskWebSocketServer wsServer;

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
     * 启用/禁用广播
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("PlanTaskBroadcaster: enabled=" + enabled);
    }

    /**
     * 检查是否已配置
     */
    public static boolean isConfigured() {
        return wsServer != null;
    }

    // ==================== 广播方法 ====================

    /**
     * 广播 plan_start — 开始规划
     */
    public void broadcastPlanStart(String sessionId, String data) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            wsServer.broadcast(Map.of(
                    "type", "plan_start",
                    "sessionId", sessionId,
                    "data", data != null ? data : ""
            ));
            logger.fine("[PlanBroadcaster] plan_start: session=" + sessionId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_start", e);
        }
    }

    /**
     * 广播 plan_thinking — 规划思考中
     */
    public void broadcastPlanThinking(String sessionId, String thought) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            wsServer.broadcast(Map.of(
                    "type", "plan_thinking",
                    "sessionId", sessionId,
                    "data", thought != null ? thought : ""
            ));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_thinking", e);
        }
    }

    /**
     * 广播 plan_tasks — 发送完整的任务树列表
     * <p>注意：此方法会自动将 data 用 {"tasks": ...} 包装。
     * 如果需要发送原始结构化数据（如 {"structuredTasks": [...]}），请使用 broadcastPlanData()。</p>
     */
    public void broadcastPlanTasks(String sessionId, Object tasksJson) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            String dataStr = tasksJson instanceof String
                    ? (String) tasksJson
                    : MAPPER.writeValueAsString(tasksJson);
            wsServer.broadcast(Map.of(
                    "type", "plan_tasks",
                    "sessionId", sessionId,
                    "data", "{\"tasks\":" + dataStr + "}"
            ));
            logger.info("[PlanBroadcaster] plan_tasks sent: session=" + sessionId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_tasks", e);
        }
    }

    /**
     * 广播 plan_tasks — 发送原始结构化数据（不额外包装 {"tasks": ...}）。
     *
     * <p>与 broadcastPlanTasks 不同，此方法直接将 payload 序列化为 data 字段，
     * 不会额外嵌套 tasks 层。适用于 {@code {"structuredTasks": [...]}} 等自定义格式。</p>
     *
     * @param sessionId 会话 ID
     * @param payload   原始数据对象（将被序列化为 JSON 作为 data 字段）
     */
    public void broadcastPlanData(String sessionId, Object payload) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            String dataStr = payload instanceof String
                    ? (String) payload
                    : MAPPER.writeValueAsString(payload);
            wsServer.broadcast(Map.of(
                    "type", "plan_tasks",
                    "sessionId", sessionId,
                    "data", dataStr
            ));
            logger.info("[PlanBroadcaster] plan_tasks raw data sent: session=" + sessionId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_tasks raw data", e);
        }
    }

    /**
     * 广播 plan_task_start — 单个任务开始执行
     */
    public void broadcastPlanTaskStart(String sessionId, String taskId, String agentType) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            wsServer.broadcast(Map.of(
                    "type", "plan_task_start",
                    "sessionId", sessionId,
                    "data", "{\"id\":\"" + escapeJson(taskId) + "\",\"agentType\":\"" + escapeJson(agentType) + "\"}"
            ));
            logger.fine("[PlanBroadcaster] plan_task_start: " + taskId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_task_start", e);
        }
    }

    /**
     * 广播 plan_task_update — 单个任务进度更新
     */
    public void broadcastPlanTaskUpdate(String sessionId, String taskId, int progress, String logs) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"id\":\"").append(escapeJson(taskId)).append("\"");
            json.append(",\"progress\":").append(progress);
            if (logs != null && !logs.isEmpty()) {
                json.append(",\"logs\":\"").append(escapeJson(logs)).append("\"");
            }
            json.append("}");
            wsServer.broadcast(Map.of(
                    "type", "plan_task_update",
                    "sessionId", sessionId,
                    "data", json.toString()
            ));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_task_update", e);
        }
    }

    /**
     * 广播 plan_task_result — 单个任务完成/失败
     */
    public void broadcastPlanTaskResult(String sessionId, String taskId,
                                         String status, String result, String error) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
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
            wsServer.broadcast(Map.of(
                    "type", "plan_task_result",
                    "sessionId", sessionId,
                    "data", json.toString()
            ));
            logger.fine("[PlanBroadcaster] plan_task_result: " + taskId + " = " + status);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_task_result", e);
        }
    }

    /**
     * 广播 plan_complete — 全部任务完成
     */
    public void broadcastPlanComplete(String sessionId, String result) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            wsServer.broadcast(Map.of(
                    "type", "plan_complete",
                    "sessionId", sessionId,
                    "data", result != null ? result : ""
            ));
            logger.info("[PlanBroadcaster] plan_complete: session=" + sessionId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_complete", e);
        }
    }

    /**
     * 广播 plan_error — 规划出错
     */
    public void broadcastPlanError(String sessionId, String error) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            wsServer.broadcast(Map.of(
                    "type", "plan_error",
                    "sessionId", sessionId,
                    "data", error != null ? error : "未知错误"
            ));
            logger.warning("[PlanBroadcaster] plan_error: session=" + sessionId + " error=" + error);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast plan_error", e);
        }
    }

    /**
     * 广播 step_prompt — 进入步骤时向AI注入的上下文提示
     */
    public void broadcastStepPrompt(String sessionId, String taskId, int stepIndex,
                                     String description, String action, String stepPrompt, String agentType) {
        if (!enabled || wsServer == null || sessionId == null) return;
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"taskId\":\"").append(escapeJson(taskId)).append("\"");
            json.append(",\"stepIndex\":").append(stepIndex);
            json.append(",\"stepNumber\":").append(stepIndex + 1);
            json.append(",\"description\":\"").append(escapeJson(description)).append("\"");
            json.append(",\"action\":\"").append(escapeJson(action != null ? action : "")).append("\"");
            json.append(",\"stepPrompt\":\"").append(escapeJson(stepPrompt != null ? stepPrompt : "")).append("\"");
            json.append(",\"agentType\":\"").append(escapeJson(agentType != null ? agentType : "")).append("\"");
            json.append("}");
            wsServer.broadcast(Map.of(
                    "type", "step_prompt",
                    "sessionId", sessionId,
                    "data", json.toString()
            ));
            logger.info("[PlanBroadcaster] step_prompt: session=" + sessionId + " step=" + (stepIndex + 1));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to broadcast step_prompt", e);
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
