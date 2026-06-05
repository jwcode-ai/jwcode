package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AgentFlowBroadcaster — 实时 Agent 信息流广播器。
 *
 * <p>将 Agent 间的消息路由、任务分发、执行状态变更通过 WebSocket
 * 实时推送到前端，用于可视化展示 Agent 信息流图。
 *
 * <p>广播消息格式 (type=agent_flow_event)：
 * <pre>
 * {
 *   "type": "agent_flow_event",
 *   "data": {
 *     "eventType": "dispatch|complete|error|agent_status",
 *     "fromAgent": "Orchestrator",
 *     "toAgent": "Coder",
 *     "taskId": "uuid",
 *     "description": "Implement login feature",
 *     "status": "running|completed|failed",
 *     "sessionId": "session-uuid",
 *     "timestamp": 1234567890
 *   }
 * }
 * </pre>
 */
public class AgentFlowBroadcaster {

    private static final Logger logger = Logger.getLogger(AgentFlowBroadcaster.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static volatile TaskWebSocketServer wsServer;
    private static volatile MessageSender messageSender;
    private static volatile AgentFlowBroadcaster instance;

    private boolean enabled = true;

    private AgentFlowBroadcaster() {
    }

    public static AgentFlowBroadcaster getInstance() {
        if (instance == null) {
            synchronized (AgentFlowBroadcaster.class) {
                if (instance == null) {
                    instance = new AgentFlowBroadcaster();
                }
            }
        }
        return instance;
    }

    public static void setWebSocketServer(TaskWebSocketServer server) {
        wsServer = server;
        logger.info("AgentFlowBroadcaster: WebSocket server configured");
    }

    public static void setMessageSender(MessageSender sender) {
        messageSender = sender;
        logger.info("AgentFlowBroadcaster: messageSender configured (fallback transport)");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private void dispatch(Map<String, Object> payload) {
        if (!enabled) return;
        Map<String, Object> message = Map.of(
            "type", "agent_flow_event",
            "data", payload
        );
        if (wsServer != null) {
            try {
                wsServer.broadcast(message);
                return;
            } catch (Exception e) {
                logger.log(Level.WARNING, "AgentFlowBroadcaster: wsServer broadcast failed", e);
            }
        }
        if (messageSender != null) {
            try {
                String json = MAPPER.writeValueAsString(message);
                messageSender.send("agent_flow_event", null, json);
            } catch (Exception e) {
                logger.log(Level.WARNING, "AgentFlowBroadcaster: messageSender failed", e);
            }
        }
    }

    // ==================== 公开广播方法 ====================

    /**
     * 广播 Agent 任务分发事件。
     */
    public void broadcastDispatch(String fromAgent, String toAgent, String taskId,
                                   String description, String sessionId) {
        dispatch(Map.of(
            "eventType", "dispatch",
            "fromAgent", fromAgent,
            "toAgent", toAgent,
            "taskId", taskId,
            "description", description != null ? description : "",
            "status", "running",
            "sessionId", sessionId != null ? sessionId : "",
            "timestamp", System.currentTimeMillis()
        ));
        logger.fine("[AgentFlow] dispatch: " + fromAgent + " -> " + toAgent + " task=" + taskId);
    }

    /**
     * 广播 Agent 任务完成事件。
     */
    public void broadcastComplete(String fromAgent, String toAgent, String taskId,
                                   String status, String sessionId) {
        dispatch(Map.of(
            "eventType", "complete",
            "fromAgent", fromAgent,
            "toAgent", toAgent,
            "taskId", taskId,
            "description", "",
            "status", status != null ? status : "completed",
            "sessionId", sessionId != null ? sessionId : "",
            "timestamp", System.currentTimeMillis()
        ));
        logger.fine("[AgentFlow] complete: " + toAgent + " task=" + taskId + " status=" + status);
    }

    /**
     * 广播 Agent 状态变更事件（如 idle -> busy）。
     */
    public void broadcastAgentStatus(String agentName, String status, String taskId, String sessionId) {
        dispatch(Map.of(
            "eventType", "agent_status",
            "fromAgent", agentName,
            "toAgent", "",
            "taskId", taskId != null ? taskId : "",
            "description", "",
            "status", status,
            "sessionId", sessionId != null ? sessionId : "",
            "timestamp", System.currentTimeMillis()
        ));
    }
}

