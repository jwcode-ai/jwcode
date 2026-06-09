package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.agent.EnhancedOrchestratorAgent;
import com.jwcode.core.config.SystemPromptLoader;
import com.jwcode.core.hook.HookApprovalManager;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.plan.PlanModeManager;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMQueryEngine.EngineConfig;
import com.jwcode.core.llm.LLMQueryEngine.QueryResult;
import com.jwcode.core.model.Message;
import com.jwcode.core.model.PlanTask;
import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;import com.jwcode.core.task.Task;
import com.jwcode.core.task.TaskStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * WebSocketMessageHandler — WebSocket 消息处理器。
 *
 * <p>负责解析前端 WebSocket 消息，根据消息类型分发给对应的处理器：
 * <ul>
 *   <li>{@code type: "chat"} — 普通对话，通过 LLMQueryEngine 处理</li>
 *   <li>{@code type: "plan"} — Plan 模式，通过 EnhancedOrchestratorAgent 进行意图分析、任务拆解、分配子Agent</li>
 *   <li>{@code type: "ping"} — 心跳回复</li>
 * </ul>
 *
 * <p>这是修复"用户发消息后主Agent没有创建任务列表"问题的核心类。
 * 原来 TaskWebSocketServer.onMessage() 只回复了 ack，没有做任何业务处理。
 */
public class WebSocketMessageHandler {

    private static final Logger logger = Logger.getLogger(WebSocketMessageHandler.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final SessionManager sessionManager;
    private final EnhancedOrchestratorAgent orchestrator;
    private final LLMService llmService;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final TaskWebSocketServer wsServer;

    // 缓存每个 session 的 LLMQueryEngine（按 sessionId）
    private final ConcurrentHashMap<String, LLMQueryEngine> queryEngines;

    public WebSocketMessageHandler(SessionManager sessionManager,
                                   EnhancedOrchestratorAgent orchestrator,
                                   LLMService llmService,
                                   ToolExecutor toolExecutor,
                                   ToolRegistry toolRegistry,
                                   TaskWebSocketServer wsServer) {
        this.sessionManager = sessionManager;
        this.orchestrator = orchestrator;
        this.llmService = llmService;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.wsServer = wsServer;
        this.queryEngines = new ConcurrentHashMap<>();

        // 设置 Hook 审批 WebSocket 广播
        setupHookApprovalBroadcaster();
    }

    /**
     * 处理 WebSocket 消息
     *
     * @param sessionId 会话 ID（从前端传入）
     * @param type      消息类型（chat / plan / ping）
     * @param message   消息内容
     * @return 处理结果（异步）
     */
    public CompletableFuture<String> handleMessage(String sessionId, String type, String message) {
        if (sessionId == null || sessionId.isEmpty()) {
            return CompletableFuture.completedFuture("{\"type\":\"error\",\"data\":\"Missing sessionId\"}");
        }

        // 获取或创建 Session
        Session session = sessionManager.getOrCreateSession(sessionId);

        logger.info("[WSHandler] Processing message: type=" + type + ", sessionId=" + sessionId
                + ", message=" + (message != null ? message.substring(0, Math.min(50, message.length())) : ""));

        switch (type) {
            case "plan":
                return handlePlanMessage(session, message);
            case "plan_confirm":
                return handlePlanConfirm(session, message);
            case "plan_refine":
                return handlePlanRefine(session, message);
            case "plan_mode_change":
                return handlePlanModeChange(session, message);
            case "chat":
                // Act 模式下走 Orchestrator 自动创建任务并执行
                if (PlanModeManager.getInstance().isActMode()) {
                    return handleActMessage(session, message);
                }
                return handleChatMessage(session, message);
            case "workspace":
                return handleWorkspaceMessage(session, message);
            case "ping":
                return CompletableFuture.completedFuture("{\"type\":\"pong\"}");
            case "hook_allow":
                return handleHookApproval(message, true);
            case "hook_deny":
                return handleHookApproval(message, false);
            case "update_docs":
                return handleUpdateDocs();
            case "doctor":
                return handleDoctor();
            case "rewind":
                return handleRewind(session);
            default:
                logger.warning("[WSHandler] Unknown message type: " + type);
                return CompletableFuture.completedFuture(
                        "{\"type\":\"error\",\"data\":\"Unknown message type: " + type + "\"}");
        }
    }

    /**
     * 处理 Plan 模式消息 — 两阶段流程的 Phase 1：仅规划，不执行。
     *
     * <p>此方法只做意图分析和任务拆解，生成 Plan 文本和任务树后，
     * 通过 WebSocket 广播 plan_tasks + plan_complete(waiting_confirm)，
     * 等待用户在前端确认后，再由 handlePlanConfirm 触发执行。</p>
     *
     * <p>关键改进：将原来的"规划+执行"一步完成拆分为"规划→确认→执行"两阶段，
     * 符合 AGENTS.md 中 Plan/Act 模式的规范。</p>
     */
    private CompletableFuture<String> handlePlanMessage(Session session, String message) {
        Supplier<String> supplier = () -> {
            try {
                // 0. 设置 Orchestrator 的 sessionId 和 PlanTaskBroadcaster
                orchestrator.setSessionId(session.getId());
                orchestrator.setPlanTaskBroadcaster(PlanTaskBroadcaster.getInstance());
                orchestrator.setAgentFlowBroadcaster(AgentFlowBroadcaster.getInstance());

                // 1. 通知前端：开始规划
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_start",
                        "sessionId", session.getId(),
                        "data", "开始分析用户请求..."
                ));

                // 2. 仅执行规划阶段（意图分析 → 任务拆解 → 生成计划文本 + 任务树）
                //    不执行任何子任务，只生成计划
                String planResult = orchestrator.processPlanOnly(message);

                // 3. 广播 plan_tasks — 发送完整的任务树到前端
                //    EnhancedOrchestratorAgent.processPlanOnly 内部已通过
                //    PlanTaskBroadcaster 广播 plan_thinking 和 plan_tasks
                //    但需要确保 plan_tasks 已发送
                List<PlanTask> taskTree = orchestrator.buildPlanTaskTree();
                if (!taskTree.isEmpty()) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                    String tasksJson = mapper.writeValueAsString(taskTree);
                    PlanTaskBroadcaster.getInstance().broadcastPlanTasks(session.getId(), tasksJson);
                }

                // 4. 通知前端：规划完成，等待用户确认
                //    关键：带上 status: "waiting_confirm" 让前端显示确认按钮
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_complete",
                        "sessionId", session.getId(),
                        "status", "waiting_confirm",
                        "data", planResult
                ));

                // 5. 将计划文本作为 assistant 消息添加到会话（供用户预览）
                session.addMessage(Message.createAssistantMessage(planResult));

                return planResult;

            } catch (Exception e) {
                logger.severe("[WSHandler] Plan processing failed: " + e.getMessage());
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_error",
                        "sessionId", session.getId(),
                        "data", "规划失败: " + e.getMessage()
                ));
                return "Error: " + e.getMessage();
            }
        };
        return CompletableFuture.supplyAsync(supplier);
    }

    /**
     * 处理 plan_refine 消息 — 用户完善计划后重新规划。
     *
     * <p>当用户在前端 PlanPanel 的确认区域输入补充说明并点击"完善计划"按钮后，
     * 前端发送 plan_refine 消息到此方法。此方法复用 handlePlanMessage 的规划逻辑，
     * 重新进行意图分析和任务拆解，生成新的 Plan 文本和任务树，
     * 然后通过 WebSocket 广播 plan_tasks + plan_complete(waiting_confirm) 更新前端。</p>
     */
    private CompletableFuture<String> handlePlanRefine(Session session, String message) {
        Supplier<String> supplier = () -> {
            try {
                // 提取用户补充的完善内容
                String refineMessage = message;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msgMap = MAPPER.readValue(message, Map.class);
                    if (msgMap.containsKey("message")) {
                        refineMessage = (String) msgMap.get("message");
                    }
                } catch (Exception ignored) { logger.fine("Message parse skipped: " + ignored.getMessage()); }

                // 通知前端：开始重新规划（完善中）
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_start",
                        "sessionId", session.getId(),
                        "data", "根据补充说明重新规划..."
                ));

                // 复用 handlePlanMessage 的核心逻辑：重新规划
                // 将补充说明与原有 goal 合并作为新输入
                List<Message> history = session.getMessages();
                String originalGoal = "";
                for (int i = history.size() - 1; i >= 0; i--) {
                    Message msg = history.get(i);
                    if ("user".equals(msg.getRole())) {
                        originalGoal = msg.getTextContent();
                        break;
                    }
                }
                String combinedInput = originalGoal.isEmpty()
                        ? refineMessage
                        : originalGoal + "\n\n【补充说明】" + refineMessage;

                // 设置 Orchestrator
                orchestrator.setSessionId(session.getId());
                orchestrator.setPlanTaskBroadcaster(PlanTaskBroadcaster.getInstance());
                orchestrator.setAgentFlowBroadcaster(AgentFlowBroadcaster.getInstance());

                // 执行规划
                String planResult = orchestrator.processPlanOnly(combinedInput);

                // 广播 plan_tasks — 发送更新后的任务树
                List<PlanTask> taskTree = orchestrator.buildPlanTaskTree();
                if (!taskTree.isEmpty()) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                    String tasksJson = mapper.writeValueAsString(taskTree);
                    PlanTaskBroadcaster.getInstance().broadcastPlanTasks(session.getId(), tasksJson);
                }

                // 广播 plan_complete — 通知前端规划完成，等待确认
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_complete",
                        "sessionId", session.getId(),
                        "status", "waiting_confirm",
                        "data", planResult
                ));

                // 将更新后的计划文本添加到会话
                session.addMessage(Message.createAssistantMessage(
                        "【完善后的计划】\n" + planResult));

                return planResult;

            } catch (Exception e) {
                logger.severe("[WSHandler] Plan refine failed: " + e.getMessage());
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_error",
                        "sessionId", session.getId(),
                        "data", "完善计划失败: " + e.getMessage()
                ));
                return "Error: " + e.getMessage();
            }
        };
        return CompletableFuture.supplyAsync(supplier);
    }

    /**
     * 处理 plan_confirm 消息 — 两阶段流程的 Phase 2：执行已确认的计划。
     *
     * <p>当用户在前端点击"确认执行"按钮后，前端发送 plan_confirm 消息到此方法。
     * 此方法调用 EnhancedOrchestratorAgent.processConfirmedPlan() 执行已确认的计划。
     * 执行前自动创建 Task（PENDING），执行中更新为 RUNNING，完成后更新为 COMPLETED。</p>
     */
    private CompletableFuture<String> handlePlanConfirm(Session session, String message) {
        Supplier<String> supplier = () -> {
            try {
                // 0. 设置 Orchestrator 的 sessionId 和 PlanTaskBroadcaster
                orchestrator.setSessionId(session.getId());
                orchestrator.setPlanTaskBroadcaster(PlanTaskBroadcaster.getInstance());
                orchestrator.setAgentFlowBroadcaster(AgentFlowBroadcaster.getInstance());

                // 0.5 自动创建 Task（PENDING 状态）
                String goal = message;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msgMap = MAPPER.readValue(message, Map.class);
                    if (msgMap.containsKey("goal")) {
                        goal = (String) msgMap.get("goal");
                    }
                } catch (Exception ignored) { logger.fine("Message parse skipped: " + ignored.getMessage()); }

                Task planTask = new Task();
                planTask.setTitle("Plan: " + (goal.length() > 50 ? goal.substring(0, 50) + "..." : goal));
                planTask.setDescription(goal);
                planTask.setStatus(TaskStatus.PLANNED);
                planTask.setPriority(5);
                // 使用 TaskStore 持久化（如果有）
                com.jwcode.core.task.TaskStore taskStore =
                    com.jwcode.core.task.TaskStore.getInstance();
                com.jwcode.core.task.Task created = taskStore.create(planTask);

                // 广播 Task 创建
                if (wsServer != null) {
                    wsServer.broadcastTaskUpdate(created.getId(), "created", created);
                }

                // 1. 通知前端：开始执行
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_start",
                        "sessionId", session.getId(),
                        "data", "开始执行已确认的计划..."
                ));

                // 1.5 更新 Task 为 RUNNING
                created.setStatus(TaskStatus.EXECUTING);
                created.setStartedAt(java.time.LocalDateTime.now());
                taskStore.update(created);
                if (wsServer != null) {
                    wsServer.broadcastTaskUpdate(created.getId(), "updated", created);
                }

                // 2. 获取当前 session 的 AI 回复（plan 文本）作为执行依据
                List<Message> history = session.getMessages();
                String aiPlanResponse = "";
                for (int i = history.size() - 1; i >= 0; i--) {
                    Message msg = history.get(i);
                    if ("assistant".equals(msg.getRole())) {
                        aiPlanResponse = msg.getTextContent();
                        break;
                    }
                }

                // 3. 执行已确认的计划
                String result = orchestrator.processConfirmedPlan(aiPlanResponse, goal);

                // 3.5 更新 Task 为 COMPLETED
                created.setStatus(TaskStatus.COMPLETED);
                created.setProgress(100);
                created.setCompletedAt(java.time.LocalDateTime.now());
                created.setOutput(new StringBuilder(result));
                taskStore.update(created);
                if (wsServer != null) {
                    wsServer.broadcastTaskUpdate(created.getId(), "updated", created);
                }

                // 5. 通知前端：执行完成
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_complete",
                        "sessionId", session.getId(),
                        "status", "completed",
                        "data", result
                ));

                // 6. 将执行结果作为 assistant 消息添加到会话
                session.addMessage(Message.createAssistantMessage(result));

                return result;

            } catch (Exception e) {
                logger.severe("[WSHandler] Plan confirm execution failed: " + e.getMessage());
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_error",
                        "sessionId", session.getId(),
                        "data", "执行失败: " + e.getMessage()
                ));
                return "Error: " + e.getMessage();
            }
        };
        return CompletableFuture.supplyAsync(supplier);
    }

    /**
     * 处理 plan_mode_change 消息 — 前端 Plan/Act 模式切换同步到后端。
     *
     * <p>当前端用户点击 Plan/Act 切换按钮时，前端通过 WebSocket 发送
     * plan_mode_change 消息到后端。此方法调用 PlanModeManager 进行模式切换，
     * 并将结果广播给所有客户端。</p>
     */
    private CompletableFuture<String> handlePlanModeChange(Session session, String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = MAPPER.readValue(message, Map.class);
            String newMode = (String) data.get("newMode");
            String previousMode = (String) data.get("previousMode");

            if (newMode == null) {
                return CompletableFuture.completedFuture(
                    "{\"type\":\"error\",\"data\":\"Missing newMode\"}");
            }

            PlanModeManager modeManager = PlanModeManager.getInstance();
            boolean success;

            switch (newMode) {
                case "plan":
                    success = modeManager.enterPlanMode("前端用户手动切换");
                    break;
                case "act":
                    success = modeManager.enterActMode();
                    break;
                default:
                    success = modeManager.exitPlanMode("前端用户手动切换");
                    break;
            }

            logger.info("[WSHandler] Plan mode change: " + previousMode + " → " + newMode
                + " (success=" + success + ")");

            // 广播模式变更到所有客户端
            if (wsServer != null) {
                wsServer.broadcast(Map.of(
                    "type", "plan_mode_change",
                    "sessionId", session.getId(),
                    "data", MAPPER.writeValueAsString(Map.of(
                        "previousMode", previousMode,
                        "newMode", newMode,
                        "success", success,
                        "timestamp", System.currentTimeMillis()
                    ))
                ));
            }

            return CompletableFuture.completedFuture(
                "{\"type\":\"plan_mode_change_ack\",\"data\":\"ok\"}");

        } catch (Exception e) {
            logger.severe("[WSHandler] Plan mode change failed: " + e.getMessage());
            return CompletableFuture.completedFuture(
                "{\"type\":\"error\",\"data\":\"Mode change failed: " + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 Act 模式消息 — 通过 Orchestrator 自动创建任务并执行。
     *
     * <p>Act 模式下，用户发送的消息不再走普通对话路径，而是交给
     * EnhancedOrchestratorAgent.processInput() 进行意图分析、任务拆解、
     * 分配子Agent执行，并广播 plan_tasks + plan_complete 到前端。</p>
     */
    private CompletableFuture<String> handleActMessage(Session session, String message) {
        Supplier<String> supplier = () -> {
            try {
                // 1. 设置 Orchestrator 的 sessionId 和 PlanTaskBroadcaster
                orchestrator.setSessionId(session.getId());
                orchestrator.setPlanTaskBroadcaster(PlanTaskBroadcaster.getInstance());
                orchestrator.setAgentFlowBroadcaster(AgentFlowBroadcaster.getInstance());

                // 2. 调用 Orchestrator 的 processInput — 规划+执行一步完成
                //    内部会自动广播 plan_start、plan_thinking、plan_tasks、plan_complete
                String result = orchestrator.processInput(message);

                // 4. 将执行结果作为 assistant 消息添加到会话
                session.addMessage(Message.createAssistantMessage(result));

                return result;

            } catch (Exception e) {
                logger.severe("[WSHandler] Act processing failed: " + e.getMessage());
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_error",
                        "sessionId", session.getId(),
                        "data", "Act 执行失败: " + e.getMessage()
                ));
                return "Error: " + e.getMessage();
            }
        };
        return CompletableFuture.supplyAsync(supplier);
    }

    /**
     * 处理 Chat 模式消息 — 通过 LLMQueryEngine 进行对话
     */
    private CompletableFuture<String> handleChatMessage(Session session, String message) {
        Supplier<String> supplier = () -> {
            try {
                // 1. 通知前端：开始处理
                broadcastToSession(session.getId(), Map.of(
                        "type", "start",
                        "sessionId", session.getId()
                ));

                // 2. 获取或创建 LLMQueryEngine
                LLMQueryEngine engine = getOrCreateQueryEngine(session);

                // 3. 执行查询
                QueryResult result = engine.query(message).join();

                // 4. 通知前端：处理完成
                broadcastToSession(session.getId(), Map.of(
                        "type", "complete",
                        "sessionId", session.getId()
                ));

                // 从 QueryResult 中获取消息内容
                if (result != null && result.getMessage() != null) {
                    return result.getMessage().getTextContent();
                }
                return "处理完成";

            } catch (Exception e) {
                logger.severe("[WSHandler] Chat processing failed: " + e.getMessage());
                broadcastToSession(session.getId(), Map.of(
                        "type", "error",
                        "sessionId", session.getId(),
                        "data", "处理失败: " + e.getMessage()
                ));
                return "Error: " + e.getMessage();
            }
        };
        return CompletableFuture.supplyAsync(supplier);
    }

    /**
     * 处理工作目录切换消息
     *
     * <p>当用户在 Web 前端切换工作目录时，前端通过 WebSocket 发送
     * {@code {type: "workspace", message: "新目录路径"}} 通知后端。
     * 后端需要同步更新 Session 的工作目录，并清除缓存的 LLMQueryEngine
     * （因为 LLMQueryEngine 中缓存了旧的环境信息）。</p>
     */
    private CompletableFuture<String> handleWorkspaceMessage(Session session, String newDir) {
        if (newDir == null || newDir.trim().isEmpty()) {
            logger.warning("[WSHandler] Workspace change with empty directory ignored");
            return CompletableFuture.completedFuture(
                    "{\"type\":\"error\",\"data\":\"Workspace directory cannot be empty\"}");
        }

        String trimmed = newDir.trim();
        String oldDir = session.getWorkingDirectory();

        // 目录没变，无需处理
        if (trimmed.equals(oldDir)) {
            logger.info("[WSHandler] Workspace unchanged: " + trimmed);
            return CompletableFuture.completedFuture("{\"type\":\"workspace_unchanged\",\"data\":\"" + escapeJson(trimmed) + "\"}");
        }

        // 1. 更新 Session 的工作目录
        session.setWorkingDirectory(trimmed);
        logger.info("[WSHandler] Workspace changed: " + oldDir + " -> " + trimmed);

        // 2. 同步更新 System property，确保 SystemPromptLoader 和 LLMQueryEngine 获取正确目录
        System.setProperty("user.dir", trimmed);

        // 3. 清除旧的 [ENV_INFO] 环境信息消息，确保下次注入获取最新工作目录
        int removed = session.removeSystemMessagesContaining("[ENV_INFO]");
        if (removed > 0) {
            logger.fine("[WSHandler] 已清除会话中 " + removed + " 条旧环境信息消息");
        }

        // 4. 立即注入新的环境信息（含正确的工作目录）
        String freshEnvInfo = SystemPromptLoader.getEnvironmentInfo(trimmed);
        session.addMessage(Message.createSystemMessage(freshEnvInfo));

        // 5. 添加工作目录变更的系统通知消息
        session.addMessage(Message.createSystemMessage(
                "[系统通知] 工作目录已切换为：" + trimmed + "。所有文件操作请基于此目录进行。"
        ));

        // 6. 清除旧的 LLMQueryEngine 缓存（下次查询会重新创建，获取新的环境信息）
        LLMQueryEngine oldEngine = queryEngines.remove(session.getId());
        if (oldEngine != null) {
            logger.info("[WSHandler] Cleared cached LLMQueryEngine for session: " + session.getId());
        }

        // 3. 通知前端：工作目录已切换
        broadcastToSession(session.getId(), Map.of(
                "type", "workspace_changed",
                "sessionId", session.getId(),
                "data", "{\"oldDir\":\"" + escapeJson(oldDir) + "\",\"newDir\":\"" + escapeJson(trimmed) + "\"}"
        ));

        return CompletableFuture.completedFuture(
                "{\"type\":\"workspace_changed\",\"data\":\"" + escapeJson(trimmed) + "\"}");
    }

    /**
     * 获取或创建 Session 对应的 LLMQueryEngine
     */
    private LLMQueryEngine getOrCreateQueryEngine(Session session) {
        return queryEngines.computeIfAbsent(session.getId(), k -> {
            EngineConfig config = EngineConfig.defaultConfig();
            LLMQueryEngine engine = new LLMQueryEngine(session, llmService, toolExecutor, config, null);

            // 设置步骤回调，通过 WebSocket 推送进度
            engine.setStepCallback(new LLMQueryEngine.StepCallback() {
                @Override
                public void onStepStart(String stepName, String description) {
                    broadcastToSession(session.getId(), Map.of(
                            "type", "step_start",
                            "sessionId", session.getId(),
                            "data", "{\"step\":\"" + escapeJson(stepName) + "\",\"description\":\"" + escapeJson(description) + "\"}"
                    ));
                }

                @Override
                public void onStepThinking(String stepName, String thought) {
                    broadcastToSession(session.getId(), Map.of(
                            "type", "step_thinking",
                            "sessionId", session.getId(),
                            "data", "{\"step\":\"" + escapeJson(stepName) + "\",\"thought\":\"" + escapeJson(thought) + "\"}"
                    ));
                }

                @Override
                public void onStepAction(String stepName, String action) {
                    broadcastToSession(session.getId(), Map.of(
                            "type", "step_action",
                            "sessionId", session.getId(),
                            "data", "{\"step\":\"" + escapeJson(stepName) + "\",\"action\":\"" + escapeJson(action) + "\"}"
                    ));
                }

                @Override
                public void onStepComplete(String stepName, String result) {
                    broadcastToSession(session.getId(), Map.of(
                            "type", "step_complete",
                            "sessionId", session.getId(),
                            "data", "{\"step\":\"" + escapeJson(stepName) + "\",\"result\":\"" + escapeJson(result) + "\"}"
                    ));
                }

                @Override
                public void onToolResult(String toolName, String result, String toolCallId) {
                    broadcastToSession(session.getId(), Map.of(
                            "type", "tool_result",
                            "sessionId", session.getId(),
                            "data", "{\"id\":\"" + escapeJson(toolCallId) + "\",\"toolName\":\"" + escapeJson(toolName) + "\",\"result\":\"" + escapeJson(result) + "\"}"
                    ));
                }

                @Override
                public void onContentChunk(String chunk) {
                    broadcastToSession(session.getId(), Map.of(
                            "type", "content",
                            "sessionId", session.getId(),
                            "data", chunk
                    ));
                }

                @Override
                public void onThinkingChunk(String chunk) {
                    broadcastToSession(session.getId(), Map.of(
                            "type", "thinking",
                            "sessionId", session.getId(),
                            "data", chunk
                    ));
                }
            });

            return engine;
        });
    }

    /**
     * 设置 Hook 审批的 WebSocket 广播器。
     * 当 Hook 返回 ASK 时，通过 WebSocket 通知前端。
     */
    private void setupHookApprovalBroadcaster() {
        HookApprovalManager.getInstance().setWebSocketBroadcaster(request -> {
            try {
                Map<String, Object> dataMap = new LinkedHashMap<>();
                dataMap.put("approvalId", request.id);
                dataMap.put("toolName", request.toolName);
                dataMap.put("askPayload", request.askPayload != null ? request.askPayload : "");
                Map<String, Object> msg = Map.of(
                    "type", "hook_ask",
                    "data", MAPPER.writeValueAsString(dataMap)
                );
                broadcastToSession(null, msg);
            } catch (Exception e) {
                logger.warning("[WSHandler] Hook broadcast error: " + e.getMessage());
            }
        });
    }

    /**
     * 处理 Hook 审批响应（allow/deny）。
     */
    private CompletableFuture<String> handleHookApproval(String message, boolean approved) {
        try {
            Map<String, Object> msgMap = MAPPER.readValue(message, Map.class);
            String approvalId = (String) msgMap.get("approvalId");
            if (approvalId == null || approvalId.isEmpty()) {
                return CompletableFuture.completedFuture("{\"type\":\"error\",\"data\":\"Missing approvalId\"}");
            }
            if (approved) {
                HookApprovalManager.getInstance().approve(approvalId);
            } else {
                HookApprovalManager.getInstance().deny(approvalId);
            }
            return CompletableFuture.completedFuture("{\"type\":\"hook_response_ack\"}");
        } catch (Exception e) {
            logger.warning("[WSHandler] Hook approval error: " + e.getMessage());
            return CompletableFuture.completedFuture("{\"type\":\"error\",\"data\":\"" + e.getMessage() + "\"}");
        }
    }

    private CompletableFuture<String> handleDoctor() {
        return CompletableFuture.supplyAsync(() -> {
            var results = new com.jwcode.core.service.DoctorService().runAll();
            StringBuilder sb = new StringBuilder();
            for (var r : results) sb.append(r.toString()).append("\n");
            String data = escapeJson(sb.toString());
            return "{\"type\":\"doctor_result\",\"data\":\"" + data + "\"}";
        });
    }

    private CompletableFuture<String> handleRewind(Session session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var cpm = new com.jwcode.core.planner.checkpoint.CheckpointManager(
                    java.nio.file.Path.of(System.getProperty("user.dir", ".")));
                var checkpoints = cpm.listCheckpoints();
                if (checkpoints.isEmpty())
                    return "{\"type\":\"error\",\"data\":\"No checkpoints found\"}";
                var cp = cpm.loadCheckpoint(checkpoints.get(0));
                if (cp != null) {
                    session.setMessages(new java.util.ArrayList<>());
                    session.addMessage(com.jwcode.core.model.Message.createSystemMessage(
                        "[Rewind] Restored from checkpoint: " + cp.getTaskId()));
                    return "{\"type\":\"rewind_result\",\"data\":\"Restored checkpoint\"}";
                }
                return "{\"type\":\"error\",\"data\":\"Failed to load checkpoint\"}";
            } catch (Exception e) {
                return "{\"type\":\"error\",\"data\":\"" + e.getMessage() + "\"}";
            }
        });
    }

    private CompletableFuture<String> handleUpdateDocs() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var gen = new com.jwcode.core.service.ProjectDocGenerator(
                    java.nio.file.Path.of(System.getProperty("user.dir", ".")));
                var result = gen.generateAll();
                String json = String.format(
                    "{\"type\":\"docs_updated\",\"data\":\"%s\"}",
                    escapeJson(result.summary));
                logger.info("[WSHandler] Docs refreshed: " + result.summary);
                return json;
            } catch (Exception e) {
                return "{\"type\":\"error\",\"data\":\"Doc update failed: " + e.getMessage() + "\"}";
            }
        });
    }

    /**
     * 向指定会话广播消息。
     *
     * <p>如果 sessionId 不为空，使用定向发送（sendToSession），
     * 避免将会话专属消息广播到所有连接，减少网络压力和消息丢失风险。
     * 如果 sessionId 为空或定向发送失败，则回退到全局广播。</p>
     */
    private void broadcastToSession(String sessionId, Map<String, Object> data) {
        if (wsServer == null) return;

        // 确保 data 中始终包含 sessionId 字段
        if (sessionId != null && !data.containsKey("sessionId")) {
            // 使用 copy 避免修改传入的 Map
            Map<String, Object> enriched = new java.util.HashMap<>(data);
            enriched.put("sessionId", sessionId);
            data = enriched;
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            // 优先定向发送到该 session 对应的连接
            boolean sent = wsServer.sendToSession(sessionId, data);
            if (!sent) {
                // 定向发送失败（如连接已断开），回退到全局广播
                logger.fine("[WSHandler] sendToSession failed for " + sessionId + ", falling back to broadcast");
                wsServer.broadcast(data);
            }
        } else {
            // 没有 sessionId 时使用全局广播
            wsServer.broadcast(data);
        }
    }

    /**
     * 构建子任务树形结构
     * 从 Orchestrator 获取当前任务信息，生成树形子任务列表
     */
    private List<PlanTask> buildSubTaskTree() {
        List<PlanTask> subTasks = new ArrayList<>();

        // 从 Orchestrator 获取任务目标，拆解为子任务
        String taskGoal = orchestrator.getCurrentTaskGoal();
        if (taskGoal == null || taskGoal.isEmpty()) {
            return subTasks;
        }

        // 根据任务类型构建树形子任务
        // Phase 1: 调研/分析
        PlanTask exploreTask = PlanTask.builder()
                .id("explore-" + System.currentTimeMillis())
                .title("调研分析")
                .description("分析现有代码结构和需求")
                .status("pending")
                .agentType("explorer")
                .build();

        // Phase 2: 设计
        PlanTask designTask = PlanTask.builder()
                .id("design-" + System.currentTimeMillis())
                .title("方案设计")
                .description("设计实现方案和接口")
                .status("pending")
                .agentType("architect")
                .build();

        // Phase 3: 实现（包含子步骤）
        PlanTask implementTask = PlanTask.builder()
                .id("implement-" + System.currentTimeMillis())
                .title("编码实现")
                .description("实现核心功能代码")
                .status("pending")
                .agentType("coder")
                .build();

        // 实现阶段的子步骤
        PlanTask step1 = PlanTask.builder()
                .id("impl-step1-" + System.currentTimeMillis())
                .title("Step 1: 基础框架")
                .description("搭建基础代码框架")
                .status("pending")
                .agentType("coder")
                .build();

        PlanTask step2 = PlanTask.builder()
                .id("impl-step2-" + System.currentTimeMillis())
                .title("Step 2: 核心逻辑")
                .description("实现核心业务逻辑")
                .status("pending")
                .agentType("coder")
                .build();

        PlanTask step3 = PlanTask.builder()
                .id("impl-step3-" + System.currentTimeMillis())
                .title("Step 3: 集成测试")
                .description("编写集成测试用例")
                .status("pending")
                .agentType("tester")
                .build();

        implementTask.setChildren(List.of(step1, step2, step3));

        // Phase 4: 验证
        PlanTask reviewTask = PlanTask.builder()
                .id("review-" + System.currentTimeMillis())
                .title("代码审查")
                .description("审查代码质量和安全性")
                .status("pending")
                .agentType("reviewer")
                .build();

        subTasks.add(exploreTask);
        subTasks.add(designTask);
        subTasks.add(implementTask);
        subTasks.add(reviewTask);

        return subTasks;
    }

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
