package com.jwcode.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jwcode.core.agent.EnhancedOrchestratorAgent;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.llm.LLMQueryEngine;
import com.jwcode.core.llm.LLMQueryEngine.EngineConfig;
import com.jwcode.core.llm.LLMQueryEngine.QueryResult;
import com.jwcode.core.model.Message;
import com.jwcode.core.model.PlanTask;
import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;
import com.jwcode.core.tool.ToolExecutor;
import com.jwcode.core.tool.ToolRegistry;

import java.util.ArrayList;
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
            case "chat":
                return handleChatMessage(session, message);
            case "workspace":
                return handleWorkspaceMessage(session, message);
            case "ping":
                return CompletableFuture.completedFuture("{\"type\":\"pong\"}");
            default:
                logger.warning("[WSHandler] Unknown message type: " + type);
                return CompletableFuture.completedFuture(
                        "{\"type\":\"error\",\"data\":\"Unknown message type: " + type + "\"}");
        }
    }

    /**
     * 处理 Plan 模式消息 — 通过 EnhancedOrchestratorAgent 进行意图分析、任务拆解、分配子Agent
     *
     * <p>关键改进：在调用 Orchestrator 之前，先设置 sessionId 和 PlanTaskBroadcaster，
     * 这样 Orchestrator 在执行子任务时能通过 WebSocket 实时广播任务状态到前端。</p>
     */
    private CompletableFuture<String> handlePlanMessage(Session session, String message) {
        Supplier<String> supplier = () -> {
            try {
                // 0. 设置 Orchestrator 的 sessionId 和 PlanTaskBroadcaster
                // 这样 Orchestrator 在执行子任务时能广播 plan_task_start/update/result 消息
                orchestrator.setSessionId(session.getId());
                orchestrator.setPlanTaskBroadcaster(PlanTaskBroadcaster.getInstance());

                // 1. 通知前端：开始规划
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_start",
                        "sessionId", session.getId(),
                        "data", "开始分析用户请求..."
                ));

                // 2. 通过 Orchestrator 处理（意图分析 → 任务拆解 → 分配子Agent）
                // Orchestrator 内部会通过 PlanTaskBroadcaster 广播 plan_task_start/update/result
                String result = orchestrator.processInput(message);

                // 3. 通知前端：规划完成
                broadcastToSession(session.getId(), Map.of(
                        "type", "plan_complete",
                        "sessionId", session.getId(),
                        "data", result
                ));

                // 4. 将结果作为 assistant 消息添加到会话
                session.addMessage(Message.createAssistantMessage(result));

                return result;

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

        // 3. 添加工作目录变更的系统通知消息
        session.addMessage(Message.createSystemMessage(
                "[系统通知] 工作目录已切换为：" + trimmed + "。所有文件操作请基于此目录进行。"
        ));

        // 4. 清除旧的 LLMQueryEngine 缓存（下次查询会重新创建，获取新的环境信息）
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
                public void onToolResult(String toolName, String result) {
                    broadcastToSession(session.getId(), Map.of(
                            "type", "tool_result",
                            "sessionId", session.getId(),
                            "data", "{\"toolName\":\"" + escapeJson(toolName) + "\",\"result\":\"" + escapeJson(result) + "\"}"
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
     * 向指定会话广播消息
     */
    private void broadcastToSession(String sessionId, Map<String, Object> data) {
        if (wsServer != null) {
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
