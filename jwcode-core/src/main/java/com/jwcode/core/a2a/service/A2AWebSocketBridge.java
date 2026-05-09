package com.jwcode.core.a2a.service;

import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.registry.A2ARegistry;
import com.jwcode.core.a2a.registry.AgentSession;
import com.jwcode.core.api.TaskWebSocketServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * A2AWebSocketBridge — 连接 TaskService 与 TaskWebSocketServer 的桥梁。
 *
 * <p>核心职责：
 * <ul>
 *   <li>将 TaskService 的 TaskDispatcher 实现为通过 WebSocket 推送任务到 Agent</li>
 *   <li>监听 TaskService 事件，通过 WebSocket 广播状态变更</li>
 *   <li>处理 Agent 通过 WebSocket 上报的任务结果</li>
 *   <li>提供 REST API 风格的查询接口（供前端展示）</li>
 * </ul>
 * </p>
 */
public class A2AWebSocketBridge {

    private static final Logger logger = Logger.getLogger(A2AWebSocketBridge.class.getName());

    private final TaskWebSocketServer wsServer;
    private final TaskService taskService;
    private final A2ARegistry registry;

    /** Agent 连接映射：agentName -> connectionId */
    private final ConcurrentHashMap<String, String> agentConnections = new ConcurrentHashMap<>();

    public A2AWebSocketBridge(TaskWebSocketServer wsServer, TaskService taskService) {
        this.wsServer = Objects.requireNonNull(wsServer, "wsServer must not be null");
        this.taskService = Objects.requireNonNull(taskService, "taskService must not be null");
        this.registry = wsServer.getRegistry();

        // 注册 TaskDispatcher
        this.taskService.setTaskDispatcher(this::dispatchTaskViaWebSocket);

        // 监听任务事件，广播状态变更
        this.taskService.addListener(this::onTaskEvent);
    }

    // ==================== TaskDispatcher 实现 ====================

    /**
     * 通过 WebSocket 推送任务到 Agent
     */
    private void dispatchTaskViaWebSocket(AgentSession agent, A2ATask task) throws Exception {
        String agentName = agent.getAgentName();

        Map<String, Object> taskMessage = Map.of(
            "type", "a2a_task_submit",
            "taskId", task.getTaskId(),
            "skillId", task.getSkillId(),
            "description", task.getDescription(),
            "input", task.getInput() != null ? task.getInput() : Map.of(),
            "priority", task.getPriority(),
            "timestamp", System.currentTimeMillis()
        );

        boolean sent = wsServer.sendToAgent(agentName, taskMessage);
        if (!sent) {
            // 尝试通过 connectionId 发送
            String connId = agent.getConnectionId();
            if (connId != null) {
                sent = wsServer.sendToConnection(connId, taskMessage);
            }
        }

        if (!sent) {
            throw new RuntimeException("Failed to send task " + task.getTaskId()
                + " to agent " + agentName + ": WebSocket connection not found");
        }

        logger.info("[A2ABridge] Task " + task.getTaskId() + " sent to agent " + agentName);
    }

    // ==================== 任务事件监听 ====================

    /**
     * 监听任务事件，广播到所有 WebSocket 客户端
     */
    private void onTaskEvent(TaskService.TaskEvent event) {
        A2ATask task = event.task();

        Map<String, Object> eventMessage = Map.of(
            "type", "a2a_task_event",
            "eventType", event.type().name(),
            "taskId", task.getTaskId(),
            "skillId", task.getSkillId(),
            "status", task.getStatus().name(),
            "description", task.getDescription(),
            "errorMessage", task.getErrorMessage(),
            "timestamp", event.timestamp().toEpochMilli()
        );

        wsServer.broadcast(eventMessage);
    }

    // ==================== 处理 Agent 上报结果 ====================

    /**
     * 处理 Agent 通过 WebSocket 上报的任务结果
     *
     * @param message WebSocket 消息 Map
     */
    @SuppressWarnings("unchecked")
    public void handleAgentResult(Map<String, Object> message) {
        try {
            String type = (String) message.get("type");
            if (!"a2a_task_result".equals(type)) {
                return;
            }

            String taskId = (String) message.get("taskId");
            String agentId = (String) message.get("agentId");
            String statusStr = (String) message.get("status");
            String result = (String) message.get("result");
            String error = (String) message.get("error");

            if (taskId == null || statusStr == null) {
                logger.warning("[A2ABridge] Invalid task result message: " + message);
                return;
            }

            A2ATask.TaskStatus status = A2ATask.TaskStatus.valueOf(statusStr);
            taskService.handleTaskResult(taskId, agentId, status, result, error);

            logger.info("[A2ABridge] Task result processed: " + taskId
                + " status=" + statusStr + " from agent=" + agentId);

        } catch (Exception e) {
            logger.warning("[A2ABridge] Error handling agent result: " + e.getMessage());
        }
    }

    // ==================== 查询接口（供前端展示） ====================

    /**
     * 获取所有在线 Agent 列表
     */
    public List<Map<String, Object>> getOnlineAgents() {
        List<Map<String, Object>> agents = new ArrayList<>();
        for (AgentSession session : registry.getOnlineSessions()) {
            AgentCard card = session.getAgentCard();
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("name", session.getAgentName());
            agentInfo.put("type", session.getAgentType());
            agentInfo.put("status", session.getStatus().name());
            agentInfo.put("currentLoad", session.getCurrentLoad());
            agentInfo.put("maxLoad", session.getMaxLoad());
            agentInfo.put("loadRatio", session.getLoadRatio());
            agentInfo.put("skills", card != null && card.getSkills() != null
                ? card.getSkills().stream().map(s -> Map.of("id", s.getId(), "name", s.getName())).toList()
                : List.of());
            agentInfo.put("lastHeartbeat", session.getLastHeartbeat().toEpochMilli());
            agentInfo.put("createdAt", session.getCreatedAt().toEpochMilli());
            agents.add(agentInfo);
        }
        return agents;
    }

    /**
     * 获取所有任务列表
     */
    public List<Map<String, Object>> getAllTasks() {
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (A2ATask task : taskService.getAllTasks()) {
            Map<String, Object> taskInfo = new LinkedHashMap<>();
            taskInfo.put("taskId", task.getTaskId());
            taskInfo.put("skillId", task.getSkillId());
            taskInfo.put("description", task.getDescription());
            taskInfo.put("status", task.getStatus().name());
            taskInfo.put("priority", task.getPriority());
            taskInfo.put("errorMessage", task.getErrorMessage());
            taskInfo.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
            taskInfo.put("updatedAt", task.getUpdatedAt() != null ? task.getUpdatedAt().toString() : null);
            taskList.add(taskInfo);
        }
        return taskList;
    }

    /**
     * 获取任务统计
     */
    public Map<String, Object> getStats() {
        TaskService.TaskStats stats = taskService.getStats();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", stats.total());
        result.put("pending", stats.pending());
        result.put("running", stats.running());
        result.put("completed", stats.completed());
        result.put("failed", stats.failed());
        result.put("timeout", stats.timeout());
        result.put("onlineAgents", registry.getOnlineSessions().size());
        result.put("availableAgents", registry.getAvailableSessions().size());
        return result;
    }

    /**
     * 提交一个测试任务
     */
    public A2ATask submitTestTask(String skillId, String description) {
        return taskService.submitSimpleTask("Test Task", skillId, description, 5);
    }
}
