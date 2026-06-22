package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.parallel.ParallelExecutionResult;
import com.jwcode.core.agent.parallel.SubAgentResult;
import com.jwcode.core.agent.parallel.SubAgentTask;
import com.jwcode.core.api.StepMessageBroadcaster;
import com.jwcode.core.hands.AgentRequest;
import com.jwcode.core.hands.AgentResult;
import com.jwcode.core.hands.LocalAgentHand;
import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.workflow.EffectVM;
import com.jwcode.core.workflow.WorkflowArtifactStore;
import com.jwcode.core.workflow.WorkflowBudget;
import com.jwcode.core.workflow.WorkflowIR;
import com.jwcode.core.workflow.WorkflowInput;
import com.jwcode.core.workflow.WorkflowLedger;
import com.jwcode.core.workflow.WorkflowResult;
import com.jwcode.core.workflow.ir.AgentNode;
import com.jwcode.core.workflow.ir.ErrorMode;
import com.jwcode.core.workflow.ir.ParallelNode;
import com.jwcode.core.workflow.ir.PhaseNode;
import com.jwcode.core.workflow.ir.PipelineNode;
import com.jwcode.core.workflow.ir.WorkflowNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Agent 工具 - Phase 2 增强版
 * 
 * 多代理协作管理工具，集成 Workflow Runtime，支持：
 * - 创建和管理多个 Agent
 * - 并行执行子任务
 * - 分配任务给 Agent
 * - 管理 Agent 状态和输出
 * - Agent 间通信
 * - 自动结果收集和汇总
 * - 子 Agent 真正的执行（非模拟）
 */
public class AgentTool implements Tool<Map<String, Object>, Map<String, Object>, Void> {
    
    private static final Logger logger = Logger.getLogger(AgentTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 默认超时: 全局 5min，单子任务 2min
    private static final long DEFAULT_GLOBAL_TIMEOUT_MS = 300_000L;
    private static final long DEFAULT_TASK_TIMEOUT_MS = 120_000L;
    // 单子任务硬上限 10min
    private static final long HARD_TASK_TIMEOUT_MS = 600_000L;
    private static final int MAX_AUTO_AGENTS = 3;
    private static final int LONG_TASK_THRESHOLD = 120;

    // 重试/熔断/降级配置
    private static final int MAX_RETRIES_PER_TASK = 1;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final int STRATEGY_SWITCH_HINT_THRESHOLD = 3;
    
    // Agent 管理器单例
    private static final AgentManager agentManager = new AgentManager();
    
    // 并行执行器
    private final WorkflowBlackboard sharedBus = new WorkflowBlackboard();
    
    // LLM 服务（用于真正的 Agent 执行）
    private volatile LLMService llmService;
    
    // 【修复】用于同步注册到全局 AgentRegistry
    
    // 执行统计
    private final AtomicInteger executionCounter = new AtomicInteger(0);
    
    @Override
    public String getName() {
        return "AgentTool";
    }
    
    @Override
    public String getDescription() {
        return "创建和管理多个 Agent 进行协作。支持并行执行、任务分配、结果收集和汇总。";
    }

    @Override
    public String getNegativeGuidance() {
        return """
            **When NOT to use AgentTool:**
            - **单个简单查询**（读文件、搜索、列表）→ 直接用 GlobTool / GrepTool / FileReadTool，不需要创建 Agent
            - **单步操作**（写一个文件、运行一个命令）→ 直接用 EditTool / FileWriteTool / BashTool
            - **需要实时交互**（Agent 创建后异步执行，不适用于需要立即响应的场景）
            - **Plan Mode 下**→ AgentTool 在 Plan Mode 被禁用，使用 SmartAnalyzeTool / GlobTool / FileReadTool 替代
            - **子 Agent 还能递归创建 Agent** → 子 Agent 不能再创建 Agent，设计你的任务分解时将并行度控制在第一层
            """;
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 AgentTool 创建和管理多个 Agent 进行协作。
               
               操作类型:
               - create: 创建新 Agent
               - create_parallel: 并行创建多个 Agent
               - assign: 分配任务给 Agent（自动提交执行）
               - execute: 并行执行多个任务
               - status: 查看 Agent 状态
               - list: 列出所有 Agent
               - stop: 停止 Agent
               - merge: 合并 Agent 结果
               - cancel: 取消正在执行的任务
               - query: 查询已分配任务的结果
               
                create 参数:
                - name: Agent 名称（可选）
                - role: Agent 角色/专业领域（可选）
                - color: Agent 颜色标识（可选）
                
                create_parallel 参数:
                - agents: 要创建的 Agent 配置列表 [{name, role}, ...]
                
                assign 参数:
                - agent_id: Agent ID（必需）
                - task: 任务描述（单数，用于单个任务）
                - context: 上下文信息（可选）
                - timeout: 超时时间毫秒（可选，默认600000）
                
                execute 参数:
                - tasks: 任务列表（复数，用于多个任务）[{name, role, task, depends_on}, ...]
                - parallel: 是否并行执行（可选，默认true）
                - timeout: 全局超时时间（可选，默认1800000）

                auto_execute 参数:
                - task: 长任务描述
                - context: 上下文信息（可选）
                - timeout: 全局超时时间（可选，默认1800000）
                - task_timeout: 单个子任务超时时间（可选，默认600000）
                - max_agents: 最大子 Agent 数（可选，默认5）

                blackboard 参数:
                - blackboard_put: key, value
                - blackboard_get: key
                - blackboard_list: 无
                - blackboard_clear: key（可选，不传则清空全部）
                
                merge 参数:
                - agent_ids: Agent ID 列表（必需）
                
                query 参数:
                - agent_id: Agent ID（必需）
               
                示例:
                - {"action": "create", "name": "测试专家", "role": "负责编写和运行测试"}
                - {"action": "create_parallel", "agents": [{"name": "前端专家"}, {"name": "后端专家"}]}
                - {"action": "assign", "agent_id": "abc123", "task": "分析代码结构"}
                - {"action": "execute", "tasks": [{"name": "task1", "role": "coder", "task": "编写登录功能"}]}
                - {"action": "merge", "agent_ids": ["abc123", "def456"]}
                - {"action": "query", "agent_id": "abc123"}
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "action": {"type": "string", "enum": ["create", "create_parallel", "assign", "execute", "auto_execute", "status", "list", "stop", "merge", "cancel", "query", "blackboard_put", "blackboard_get", "blackboard_list", "blackboard_clear"]},
                        "agent_id": {"type": "string"},
                        "name": {"type": "string"},
                        "role": {"type": "string"},
                        "color": {"type": "string"},
                        "task": {"type": "string"},
                        "context": {"type": "string"},
                        "timeout": {"type": "integer", "default": 300000},
                        "task_timeout": {"type": "integer", "default": 120000},
                        "max_agents": {"type": "integer", "default": 3},
                        "key": {"type": "string"},
                        "value": {},
                        "agents": {"type": "array"},
                        "tasks": {"type": "array"},
                        "parallel": {"type": "boolean", "default": true}
                    },
                    "required": ["action"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<Map<String, Object>> getInputType() {
        return new TypeReference<Map<String, Object>>() {};
    }
    
    @Override
    public TypeReference<Map<String, Object>> getOutputType() {
        return new TypeReference<Map<String, Object>>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<Map<String, Object>>> call(
            Map<String, Object> input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 【核心修复】先归化（容错处理）- 在验证和执行之前
                Map<String, Object> normalizedInput = normalizeInput(input);
                
                String action = (String) normalizedInput.get("action");
                
                if (action == null || action.isEmpty()) {
                    return ToolResult.error("action 参数是必需的");
                }
                
                // 初始化执行器
                ensureExecutorInitialized(context);
                
                switch (action) {
                    case "create":
                        return createAgent(normalizedInput, context.getSession());
                    case "create_parallel":
                        return createAgentsParallel(normalizedInput, context.getSession());
                    case "assign":
                        return assignTask(normalizedInput, context);
                    case "execute":
                        return executeTasks(normalizedInput, context);
                    case "auto_execute":
                        return autoExecuteTasks(normalizedInput, context);
                    case "status":
                        return getAgentStatus(normalizedInput, context.getSession());
                    case "list":
                        return listAgents(context.getSession());
                    case "stop":
                        return stopAgent(normalizedInput, context.getSession());
                    case "merge":
                        return mergeResults(normalizedInput, context.getSession());
                    case "cancel":
                        return cancelTask(normalizedInput, context.getSession());
                    case "query":
                        return queryTask(normalizedInput, context.getSession());
                    case "blackboard_put":
                        return blackboardPut(normalizedInput);
                    case "blackboard_get":
                        return blackboardGet(normalizedInput);
                    case "blackboard_list":
                        return blackboardList();
                    case "blackboard_clear":
                        return blackboardClear(normalizedInput);
                    default:
                        return ToolResult.error("未知的操作类型：" + action);
                }
            } catch (Exception e) {
                logger.severe("AgentTool 执行失败: " + e.getMessage());
                return ToolResult.error("AgentTool 执行失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 智能参数归一化层
     * 修复 LLM 生成的 JSON 与 Schema 不匹配的问题：
     * - tasks/task 单复数混用
     * - agent_id/agentId/id 字段名混用
     * - tasks 数组元素为字符串而非对象
     * - 字段类型错误
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeInput(Map<String, Object> input) {
        if (input == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> normalized = new HashMap<>(input);
        String action = (String) normalized.get("action");
        
        // 1. 字段名归一化：agent_id / agentId / id → agent_id
        if (!normalized.containsKey("agent_id") || normalized.get("agent_id") == null) {
            if (normalized.containsKey("agentId") && normalized.get("agentId") != null) {
                normalized.put("agent_id", normalized.get("agentId"));
                logger.fine("[AgentTool] 自动映射 agentId → agent_id");
            } else if (normalized.containsKey("id") && normalized.get("id") != null && !"action".equals(normalized.get("id"))) {
                normalized.put("agent_id", normalized.get("id"));
                logger.fine("[AgentTool] 自动映射 id → agent_id");
            }
        }
        
        // 2. task_ids / agentIds → agent_ids
        if (!normalized.containsKey("agent_ids") || normalized.get("agent_ids") == null) {
            if (normalized.containsKey("agentIds") && normalized.get("agentIds") != null) {
                normalized.put("agent_ids", normalized.get("agentIds"));
                logger.fine("[AgentTool] 自动映射 agentIds → agent_ids");
            }
        }
        
        // 3. execute action 的 tasks/task 归一化
        if ("execute".equals(action)) {
            Object tasks = normalized.get("tasks");
            Object task = normalized.get("task");
            
            // 如果 tasks 为空但 task 不为空，将 task 包装为 tasks 数组
            if ((tasks == null) && task != null) {
                if (task instanceof String) {
                    String taskText = (String) task;
                    if (shouldAutoDecompose(taskText)) {
                        normalized.put("action", "auto_execute");
                        return normalized;
                    }
                    // 单个字符串任务 → 包装为数组
                    List<Map<String, Object>> taskList = new ArrayList<>();
                    Map<String, Object> singleTask = new HashMap<>();
                    singleTask.put("task", task);
                    singleTask.put("name", "unnamed-task");
                    taskList.add(singleTask);
                    normalized.put("tasks", taskList);
                    logger.fine("[AgentTool] 自动将单数 task 包装为 tasks 数组");
                } else if (task instanceof Map) {
                    // 单个对象任务 → 包装为数组
                    List<Map<String, Object>> taskList = new ArrayList<>();
                    taskList.add((Map<String, Object>) task);
                    normalized.put("tasks", taskList);
                    logger.fine("[AgentTool] 自动将单个 task 对象包装为 tasks 数组");
                }
            }
            
            // 修复 tasks 数组中的元素类型
            if (normalized.get("tasks") instanceof List) {
                List<?> taskList = (List<?>) normalized.get("tasks");
                List<Map<String, Object>> fixedList = new ArrayList<>();
                for (int i = 0; i < taskList.size(); i++) {
                    Object item = taskList.get(i);
                    if (item instanceof String) {
                        // 字符串 → 包装为对象
                        Map<String, Object> taskObj = new HashMap<>();
                        taskObj.put("task", item);
                        taskObj.put("name", "task-" + (i + 1));
                        fixedList.add(taskObj);
                        logger.fine("[AgentTool] 自动将 tasks[" + i + "] 从字符串转换为对象");
                    } else if (item instanceof Map) {
                        Map<String, Object> taskObj = new HashMap<>((Map<String, Object>) item);
                        // 确保 name 字段存在
                        if (!taskObj.containsKey("name") || taskObj.get("name") == null) {
                            taskObj.put("name", "task-" + (i + 1));
                        }
                        // 确保 task 字段存在（兼容 instruction 字段）
                        if ((!taskObj.containsKey("task") || taskObj.get("task") == null) 
                            && taskObj.containsKey("instruction") && taskObj.get("instruction") != null) {
                            taskObj.put("task", taskObj.get("instruction"));
                            logger.fine("[AgentTool] 自动将 tasks[" + i + "].instruction 映射为 task");
                        }
                        fixedList.add(taskObj);
                    }
                }
                normalized.put("tasks", fixedList);
            }
        }
        
        // 4. assign action 的 task/tasks 归一化
        if ("assign".equals(action)) {
            // 如果 task 为空但 tasks 数组不为空，取第一个
            if ((!normalized.containsKey("task") || normalized.get("task") == null)
                && normalized.containsKey("tasks") && normalized.get("tasks") instanceof List) {
                List<?> tasksList = (List<?>) normalized.get("tasks");
                if (!tasksList.isEmpty() && tasksList.get(0) instanceof Map) {
                    Map<String, Object> firstTask = (Map<String, Object>) tasksList.get(0);
                    if (firstTask.containsKey("task")) {
                        normalized.put("task", firstTask.get("task"));
                        logger.fine("[AgentTool] 自动从 tasks[0].task 提取 assign 的 task");
                    }
                }
            }
        }
        
        // 5. timeout 类型归一化（兼容字符串数字）
        if (normalized.containsKey("timeout") && normalized.get("timeout") instanceof String) {
            try {
                normalized.put("timeout", Long.parseLong((String) normalized.get("timeout")));
                logger.fine("[AgentTool] 自动将 timeout 从字符串转换为数字");
            } catch (NumberFormatException e) {
                // 忽略，保留原值
            }
        }
        
        if (normalized.containsKey("task_timeout") && normalized.get("task_timeout") instanceof String) {
            try {
                normalized.put("task_timeout", Long.parseLong((String) normalized.get("task_timeout")));
            } catch (NumberFormatException e) {
                // Keep original value for validation.
            }
        }
        if (normalized.containsKey("max_agents") && normalized.get("max_agents") instanceof String) {
            try {
                normalized.put("max_agents", Integer.parseInt((String) normalized.get("max_agents")));
            } catch (NumberFormatException e) {
                // Keep original value for validation.
            }
        }

        return normalized;
    }
    
    // ==================== 初始化 ====================
    
    private void ensureExecutorInitialized(ToolExecutionContext context) {
        llmService = context.getLLMService();
    }
    
    // ==================== 核心操作 ====================
    
    /**
     * 创建单个 Agent
     */
    private ToolResult<Map<String, Object>> createAgent(Map<String, Object> args, Session session) {
        String name = (String) args.get("name");
        String role = (String) args.get("role");
        String color = (String) args.get("color");
        
        if (name == null || name.isEmpty()) {
            name = "Agent-" + (agentManager.getAgentCount() + 1);
        }
        
        if (color == null || color.isEmpty()) {
            color = agentManager.getNextColor();
        }
        
        AgentInfo agent = new AgentInfo(name, role, color);
        String agentId = agentManager.registerAgent(agent);
        
        session.getLogger().info("AgentTool: 创建 Agent - id=" + agentId + ", name=" + name + ", role=" + role);
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 创建成功！\n\n");
        content.append("Agent ID: ").append(agentId).append("\n");
        content.append("名称：").append(name).append("\n");
        content.append("角色：").append(role != null ? role : "未指定").append("\n");
        content.append("颜色：").append(color).append("\n");
        content.append("状态：idle\n");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_id", agentId);
        metadata.put("name", name);
        metadata.put("role", role);
        metadata.put("color", color);
        metadata.put("status", "idle");
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 并行创建多个 Agent
     */
    @SuppressWarnings("unchecked")
    private ToolResult<Map<String, Object>> createAgentsParallel(Map<String, Object> args, Session session) {
        List<Map<String, Object>> agentConfigs = (List<Map<String, Object>>) args.get("agents");
        
        if (agentConfigs == null || agentConfigs.isEmpty()) {
            return ToolResult.error("agents 参数是必需的");
        }
        
        session.getLogger().info("AgentTool: 并行创建 " + agentConfigs.size() + " 个 Agent");
        
        List<Map<String, Object>> createdAgents = new ArrayList<>();
        
        // 使用并行流创建 Agent
        agentConfigs.parallelStream().forEach(config -> {
            String name = (String) config.get("name");
            String role = (String) config.get("role");
            String color = (String) config.get("color");
            
            if (name == null || name.isEmpty()) {
                name = "Agent-" + (agentManager.getAgentCount() + 1);
            }
            if (color == null || color.isEmpty()) {
                color = agentManager.getNextColor();
            }
            
            AgentInfo agent = new AgentInfo(name, role, color);
            String agentId = agentManager.registerAgent(agent);
            
            Map<String, Object> info = new HashMap<>();
            info.put("agent_id", agentId);
            info.put("name", name);
            info.put("role", role);
            info.put("color", color);
            info.put("status", "idle");
            
            synchronized (createdAgents) {
                createdAgents.add(info);
            }
        });
        
        StringBuilder content = new StringBuilder();
        content.append("并行创建 Agent 成功！\n\n");
        content.append("创建数量：").append(createdAgents.size()).append("\n\n");
        
        for (Map<String, Object> info : createdAgents) {
            content.append("- ").append(info.get("name"))
                   .append(" (").append(info.get("agent_id")).append(")\n");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("count", createdAgents.size());
        metadata.put("agents", createdAgents);
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 分配任务给 Agent（自动提交到 Workflow Runtime 执行）
     */
    private ToolResult<Map<String, Object>> assignTask(Map<String, Object> args, ToolExecutionContext toolContext) {
        Session session = toolContext.getSession();
        String agentId = (String) args.get("agent_id");
        String task = (String) args.get("task");
        String context = (String) args.get("context");
        Long timeout = args.get("timeout") instanceof Number
            ? ((Number) args.get("timeout")).longValue()
            : DEFAULT_TASK_TIMEOUT_MS;

        if (agentId == null || agentId.isEmpty()) {
            return ToolResult.error("agent_id parameter is required");
        }
        if (task == null || task.isEmpty()) {
            return ToolResult.error("task parameter is required");
        }

        AgentInfo agent = agentManager.getAgent(agentId);
        if (agent == null) {
            return ToolResult.error("Agent not found: " + agentId);
        }
        if (agent.status != AgentStatus.IDLE) {
            return ToolResult.error("Agent is not idle: " + agent.status);
        }

        agent.assignTask(task, context);
        Map<String, Object> taskContext = new HashMap<>();
        if (context != null && !context.isEmpty()) {
            taskContext.put("user_context", context);
        }

        Map<String, Object> singleTask = new HashMap<>();
        singleTask.put("name", agent.name);
        singleTask.put("role", agent.role);
        singleTask.put("task", task);
        singleTask.put("agent_type", inferAgentType(agent.role));
        singleTask.put("timeout", timeout);
        singleTask.put("context", taskContext);

        Map<String, Object> executeArgs = new HashMap<>();
        executeArgs.put("action", "execute");
        executeArgs.put("tasks", List.of(singleTask));
        executeArgs.put("parallel", false);
        executeArgs.put("timeout", timeout);

        ToolResult<Map<String, Object>> workflowResult =
            executeTasksViaWorkflowRuntime(executeArgs, List.of(singleTask), false, timeout, toolContext);

        String taskId = "agent-task-" + executionCounter.incrementAndGet();
        SubAgentResult subAgentResult = workflowResult.isSuccess()
            ? SubAgentResult.success(taskId, workflowResult.getContent())
            : SubAgentResult.failure(taskId, workflowResult.getContent());
        agent.executionFuture = CompletableFuture.completedFuture(subAgentResult);
        agent.taskId = taskId;
        agent.taskTimeout = timeout;
        agent.executionResult = subAgentResult;
        if (subAgentResult.isSuccess()) {
            agent.completeTask(subAgentResult.getOutput());
        } else {
            agent.failTask(subAgentResult.getError());
        }

        session.getLogger().info("AgentTool: assigned task via workflow runtime - agentId="
            + agentId + ", taskId=" + taskId + ", task=" + task);

        Map<String, Object> metadata = workflowResult.getMetadata() != null
            ? new HashMap<>(workflowResult.getMetadata())
            : new HashMap<>();
        metadata.put("agent_id", agentId);
        metadata.put("task_id", taskId);
        metadata.put("task", task);
        metadata.put("timeout", timeout);
        metadata.put("status", agent.status.toString());
        workflowResult.setMetadata(metadata);
        return workflowResult;
    }

    /**
     * Execute agent tasks through Workflow IR + EffectVM.
     */
    @SuppressWarnings("unchecked")
    private ToolResult<Map<String, Object>> executeTasks(Map<String, Object> args, ToolExecutionContext toolContext) {
        List<Map<String, Object>> taskConfigs = (List<Map<String, Object>>) args.get("tasks");
        Boolean parallel = args.get("parallel") instanceof Boolean
            ? (Boolean) args.get("parallel")
            : true;
        Long globalTimeout = args.get("timeout") instanceof Number
            ? ((Number) args.get("timeout")).longValue()
            : DEFAULT_GLOBAL_TIMEOUT_MS;

        if (taskConfigs == null || taskConfigs.isEmpty()) {
            return ToolResult.error("tasks parameter is required");
        }
        return executeTasksViaWorkflowRuntime(args, taskConfigs, parallel, globalTimeout, toolContext);
    }

    private ToolResult<Map<String, Object>> autoExecuteTasks(Map<String, Object> args, ToolExecutionContext context) {
        Session session = context.getSession();
        String task = stringValue(args.get("task"));
        if (task == null || task.isBlank()) {
            return ToolResult.error("task parameter is required for auto_execute");
        }

        int maxAgents = clampMaxAgents(args.get("max_agents"));
        long taskTimeout = longValue(args.get("task_timeout"), DEFAULT_TASK_TIMEOUT_MS);
        long globalTimeout = longValue(args.get("timeout"), DEFAULT_GLOBAL_TIMEOUT_MS);

        List<Map<String, Object>> plannedTasks = decomposeTaskWithFallback(task, args.get("context"), maxAgents, taskTimeout);
        String executionId = "auto-" + executionCounter.incrementAndGet();
        sharedBus.put(executionId + ":plan", plannedTasks);
        sharedBus.put(executionId + ":task", task);

        Map<String, Object> executeArgs = new HashMap<>(args);
        executeArgs.put("action", "execute");
        executeArgs.put("tasks", plannedTasks);
        executeArgs.put("parallel", true);
        executeArgs.put("timeout", globalTimeout);

        ToolResult<Map<String, Object>> result = executeTasks(executeArgs, context);
        Map<String, Object> metadata = new HashMap<>();
        if (result.getMetadata() != null) {
            metadata.putAll(result.getMetadata());
        }
        metadata.put("execution_id", executionId);
        metadata.put("auto_decomposed", true);
        metadata.put("planned_count", plannedTasks.size());
        metadata.put("planned_tasks", plannedTasks);
        result.setMetadata(metadata);
        return result;
    }

    private ToolResult<Map<String, Object>> executeTasksViaWorkflowRuntime(
            Map<String, Object> args,
            List<Map<String, Object>> taskConfigs,
            boolean parallel,
            long globalTimeout,
            ToolExecutionContext toolContext) {
        long start = System.currentTimeMillis();
        Session session = toolContext.getSession();
        String runId = "agent-tool-" + sanitizeForPath(session.getId()) + "-" + executionCounter.incrementAndGet();
        Path workflowRoot = Path.of(System.getProperty(
            "jwcode.workflow.root",
            Path.of(System.getProperty("user.home"), ".jwcode", "workflows").toString()));
        Path runDir = workflowRoot.resolve(runId);

        try {
            List<SubAgentTask> tasks = new ArrayList<>();
            List<WorkflowNode> nodes = new ArrayList<>();
            StepMessageBroadcaster broadcaster = StepMessageBroadcaster.getInstance();

            for (Map<String, Object> config : taskConfigs) {
                SubAgentTask task = buildTaskFromConfig(config);
                tasks.add(task);
                broadcaster.broadcastStepStart(task.getTaskId(), task.getName(), task.getTaskDescription());
                nodes.add(toAgentNode(task));
            }

            WorkflowNode body = parallel
                ? new ParallelNode("agent-tool-parallel", nodes, Math.max(1, Math.min(nodes.size(), MAX_AUTO_AGENTS)), ErrorMode.NULL)
                : new PipelineNode("agent-tool-pipeline", nodes, ErrorMode.FAIL_FAST);
            WorkflowIR ir = new WorkflowIR(
                "agent-tool-workflow",
                new PhaseNode("agent-tool-execute", "agent-tool-execute", List.of(body)),
                new WorkflowBudget(
                    1_000_000L,
                    Math.max(1, taskConfigs.size() + 1),
                    0,
                    Duration.ofMillis(Math.max(1, globalTimeout)),
                    Math.max(1, Math.min(taskConfigs.size(), MAX_AUTO_AGENTS)),
                    MAX_AUTO_AGENTS),
                "workflow-ir.v1");
            Files.createDirectories(runDir);
            Files.writeString(runDir.resolve("ir.json"), new com.jwcode.core.workflow.WorkflowCompiler().toJson(ir), StandardCharsets.UTF_8);

            WorkflowLedger ledger = new WorkflowLedger(runId, runDir);
            WorkflowArtifactStore artifacts = new WorkflowArtifactStore(runDir);
            LocalAgentHand hand = llmService == null
                ? new LocalAgentHand()
                : new LocalAgentHand(llmService, null,
                    toolContext.getWorkingDirectory() != null ? toolContext.getWorkingDirectory() : Path.of(System.getProperty("user.dir")),
                    null);
            EffectVM vm = new EffectVM(ledger, artifacts, hand, null);
            WorkflowResult workflowResult = vm.execute(runId, ir,
                WorkflowInput.of(session.getId(), MAPPER.valueToTree(args)));

            List<SubAgentResult> subResults = toSubAgentResults(tasks, workflowResult.output(), runId);
            ParallelExecutionResult executionResult = new ParallelExecutionResult(subResults, System.currentTimeMillis() - start);
            ToolResult<Map<String, Object>> result = buildExecutionResult(executionResult);

            Map<String, Object> metadata = result.getMetadata() != null
                ? new HashMap<>(result.getMetadata())
                : new HashMap<>();
            metadata.put("workflow_runtime", true);
            metadata.put("workflow_run_id", runId);
            metadata.put("workflow_status", workflowResult.status().name());
            metadata.put("workflow_dir", runDir.toString());
            metadata.put("execution_mode", parallel ? "workflow_parallel" : "workflow_pipeline");
            result.setMetadata(metadata);
            result.setSuccess(workflowResult.status() == com.jwcode.core.workflow.WorkflowStatus.COMPLETED
                && executionResult.getFailureCount() == 0);
            return result;
        } catch (Exception e) {
            logger.warning("[AgentTool] Workflow runtime execution failed: " + e.getMessage());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("workflow_runtime", true);
            metadata.put("workflow_run_id", runId);
            metadata.put("workflow_status", "FAILED");
            metadata.put("workflow_dir", runDir.toString());
            metadata.put("execution_mode", parallel ? "workflow_parallel" : "workflow_pipeline");
            metadata.put("error", e.getMessage());
            ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
            result.setSuccess(false);
            result.setContent("Workflow runtime execution failed: " + e.getMessage());
            result.setMetadata(metadata);
            return result;
        }
    }

    private AgentNode toAgentNode(SubAgentTask task) {
        Map<String, Object> promptPayload = new LinkedHashMap<>();
        promptPayload.put("task_id", task.getTaskId());
        promptPayload.put("name", task.getName());
        promptPayload.put("role", task.getRole());
        promptPayload.put("task", task.getTaskDescription());
        promptPayload.put("context", task.getContext());
        String prompt = "Execute this AgentTool sub-task and return a concise result.\n\n"
            + MAPPER.valueToTree(promptPayload).toPrettyString();
        return new AgentNode(
            task.getTaskId(),
            normalizeWorkflowRole(task.getAgentType() != null ? task.getAgentType() : task.getRole()),
            prompt,
            List.of(),
            null,
            0,
            task.getTimeoutMs());
    }

    private String normalizeWorkflowRole(String role) {
        String lower = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (lower.contains("code") || lower.contains("debug") || lower.contains("implement") || lower.contains("开发")) {
            return "coder";
        }
        if (lower.contains("test") || lower.contains("review") || lower.contains("verify") || lower.contains("评审") || lower.contains("测试")) {
            return "verifier";
        }
        if (lower.contains("explore") || lower.contains("analy") || lower.contains("doc") || lower.contains("搜索") || lower.contains("分析")) {
            return "explorer";
        }
        return "main";
    }

    private List<SubAgentResult> toSubAgentResults(List<SubAgentTask> tasks, JsonNode output, String runId) {
        JsonNode resultsNode = unwrapAgentToolOutput(output);
        List<SubAgentResult> results = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            SubAgentTask task = tasks.get(i);
            JsonNode node = resultsNode != null && resultsNode.isArray() && i < resultsNode.size()
                ? resultsNode.get(i)
                : null;
            long now = System.currentTimeMillis();
            if (node == null || node.isNull()) {
                results.add(SubAgentResult.builder()
                    .taskId(task.getTaskId())
                    .success(false)
                    .error("Workflow sub-task returned null")
                    .agentId(normalizeWorkflowRole(task.getAgentType()))
                    .agentName(task.getName())
                    .startTime(now)
                    .endTime(now)
                    .metadata(Map.of("workflow_run_id", runId))
                    .build());
                continue;
            }

            boolean success = !node.has("success") || node.get("success").asBoolean(true);
            String content = node.hasNonNull("content") ? node.get("content").asText() : node.toString();
            long durationMs = node.has("durationMs") ? node.get("durationMs").asLong(0) : 0;
            Map<String, Object> data = MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {});
            results.add(SubAgentResult.builder()
                .taskId(task.getTaskId())
                .success(success)
                .output(success ? content : null)
                .error(success ? null : (node.hasNonNull("errorMessage") ? node.get("errorMessage").asText() : content))
                .executionTimeMs(durationMs)
                .agentId(normalizeWorkflowRole(task.getAgentType()))
                .agentName(task.getName())
                .data(data)
                .metadata(Map.of("workflow_run_id", runId))
                .build());
        }
        return results;
    }

    private JsonNode unwrapAgentToolOutput(JsonNode output) {
        if (output == null || output.isNull()) {
            return MAPPER.createArrayNode();
        }
        JsonNode node = output;
        if (node.isArray() && node.size() == 1 && node.get(0).isArray()) {
            node = node.get(0);
        }
        if (node.isArray() && node.size() == 1 && node.get(0).isArray()) {
            node = node.get(0);
        }
        return node;
    }

    private List<Map<String, Object>> decomposeTaskWithFallback(
            String task, Object context, int maxAgents, long taskTimeout) {
        List<Map<String, Object>> llmPlan = tryLlmDecompose(task, context, maxAgents, taskTimeout);
        if (!llmPlan.isEmpty()) {
            return llmPlan;
        }
        logger.info("[AgentTool] LLM decomposition timed out or empty, falling back to heuristic plan");
        StepMessageBroadcaster.getInstance().broadcastStepAction("decompose",
            "LLM decomposition fallback to heuristic plan (timeout=15s)");
        return heuristicDecompose(task, context, maxAgents, taskTimeout);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tryLlmDecompose(String task, Object context, int maxAgents, long taskTimeout) {
        if (llmService == null) {
            return List.of();
        }

        String prompt = """
            Split the user task into 1 to %d independent sub-agent tasks.
            Return only a JSON array. Each item must contain:
            name, role, task, agent_type, priority, depends_on.
            Use concise English identifiers for name and agent_type.
            Do not include markdown fences or commentary.

            Task:
            %s

            Context:
            %s
            """.formatted(maxAgents, task, context != null ? context : "");

        try {
            LLMResponse response = llmService.chat(List.of(
                LLMMessage.system("You are a task decomposition planner. Return strict JSON only."),
                LLMMessage.user(prompt)
            )).get(Math.min(DEFAULT_TASK_TIMEOUT_MS, 15_000L), TimeUnit.MILLISECONDS);

            if (response == null || response.hasError() || response.getContent() == null) {
                return List.of();
            }

            JsonNode root = MAPPER.readTree(extractJsonArray(response.getContent()));
            if (!root.isArray()) {
                return List.of();
            }

            List<Map<String, Object>> tasks = new ArrayList<>();
            for (JsonNode node : root) {
                if (tasks.size() >= maxAgents) break;
                Map<String, Object> map = MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {});
                normalizeAutoTask(map, tasks.size() + 1, task, context, taskTimeout);
                tasks.add(map);
            }
            return tasks.isEmpty() ? List.of() : tasks;
        } catch (Exception e) {
            logger.warning("[AgentTool] LLM task decomposition failed, using heuristic fallback: " + e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> heuristicDecompose(String task, Object context, int maxAgents, long taskTimeout) {
        int count = estimateAgentCount(task, maxAgents);
        List<Map<String, Object>> tasks = new ArrayList<>();
        String[][] templates = {
            {"structure-explorer", "Repository structure explorer", "explore", "Map the relevant files, modules, entrypoints, and current implementation shape for this task."},
            {"implementation-analyst", "Implementation analyst", "coder", "Identify the concrete code changes needed, affected interfaces, and likely integration points."},
            {"risk-reviewer", "Risk and regression reviewer", "review", "Find behavioral risks, compatibility issues, edge cases, and missing safeguards."},
            {"test-planner", "Test planner", "test", "Define focused validation steps, tests, and acceptance scenarios for this task."},
            {"documentation-summarizer", "Documentation and handoff specialist", "doc", "Summarize findings and produce a concise handoff-ready result."}
        };

        for (int i = 0; i < count; i++) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", templates[i][0]);
            map.put("role", templates[i][1]);
            map.put("agent_type", templates[i][2]);
            map.put("priority", 5);
            map.put("timeout", taskTimeout);
            map.put("depends_on", List.of());
            map.put("task", templates[i][3] + "\n\nOriginal task:\n" + task);
            Map<String, Object> taskContext = new HashMap<>();
            taskContext.put("blackboard", sharedBus.snapshot());
            if (context != null) {
                taskContext.put("user_context", context);
            }
            map.put("context", taskContext);
            tasks.add(map);
        }
        return tasks;
    }

    private void normalizeAutoTask(Map<String, Object> map, int index, String originalTask, Object context, long taskTimeout) {
        map.putIfAbsent("name", "auto-task-" + index);
        map.putIfAbsent("role", "Sub-agent " + index);
        map.putIfAbsent("agent_type", inferAgentType(stringValue(map.get("role"))));
        map.putIfAbsent("priority", 5);
        map.put("timeout", map.get("timeout") instanceof Number ? map.get("timeout") : taskTimeout);
        map.putIfAbsent("depends_on", List.of());
        if (!map.containsKey("task") || map.get("task") == null || stringValue(map.get("task")).isBlank()) {
            map.put("task", originalTask);
        }
        Map<String, Object> taskContext = new HashMap<>();
        Object existingContext = map.get("context");
        if (existingContext instanceof Map<?, ?> existingMap) {
            for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                taskContext.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        taskContext.put("blackboard", sharedBus.snapshot());
        if (context != null) {
            taskContext.put("user_context", context);
        }
        map.put("context", taskContext);
    }

    private boolean shouldAutoDecompose(String task) {
        if (task == null) return false;
        String trimmed = task.trim();
        return trimmed.length() >= LONG_TASK_THRESHOLD || trimmed.contains("\n");
    }

    private int clampMaxAgents(Object value) {
        int requested = intValue(value, MAX_AUTO_AGENTS);
        return Math.max(1, Math.min(MAX_AUTO_AGENTS, requested));
    }

    private long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int estimateAgentCount(String task, int maxAgents) {
        String lower = task != null ? task.toLowerCase(Locale.ROOT) : "";
        boolean complex = lower.contains("refactor") || lower.contains("architecture")
            || lower.contains("multi") || lower.contains("module") || lower.contains("debug")
            || lower.contains("全仓") || lower.contains("多模块") || lower.contains("重构") || lower.contains("排查");
        boolean simple = lower.length() < 80
            && !(lower.contains("analyze") || lower.contains("分析") || lower.contains("探索"));
        // 启发式: 简单=1, 中等=2, 复杂=max 3（只有用户显式传 max_agents 才允许到 5）
        int count = simple ? 1 : (complex ? 3 : 2);
        return Math.max(1, Math.min(maxAgents, count));
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 将字符串中的非法文件名字符替换为安全字符。
     * Windows 不允许 : &lt; &gt; " | ? * 出现在文件/路径名中。
     */
    private String sanitizeForPath(String id) {
        return id != null ? id.replaceAll("[:<>\"|?*]", "-") : "unknown";
    }

    /**
     * 在任务列表中根据 taskId 查找 SubAgentTask
     */
    private SubAgentTask findTaskInList(List<SubAgentTask> tasks, String taskId) {
        for (SubAgentTask t : tasks) {
            if (t.getTaskId() != null && t.getTaskId().equals(taskId)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 判断子任务结果是否可重试（timeout、网络错误、可恢复 LLM 错误）
     */
    private boolean isRetryableResult(SubAgentResult r) {
        if (r == null || r.isSuccess()) return false;
        String error = r.getError() != null ? r.getError().toLowerCase(Locale.ROOT) : "";
        return error.contains("timeout")
            || error.contains("timed out")
            || error.contains("connection")
            || error.contains("reset")
            || error.contains("unavailable")
            || error.contains("rate_limit")
            || error.contains("429")
            || error.contains("503")
            || error.contains("500")
            || error.contains("interrupted");
    }
    
    /**
     * 从配置构建任务
     */
    @SuppressWarnings("unchecked")
    private SubAgentTask buildTaskFromConfig(Map<String, Object> config) {
        String name = (String) config.get("name");
        String role = (String) config.get("role");
        String taskDesc = (String) config.get("task");
        String agentType = (String) config.get("agent_type");
        Integer priority = config.get("priority") instanceof Number ? 
            ((Number) config.get("priority")).intValue() : 5;
        Long timeout = config.get("timeout") instanceof Number ? 
            ((Number) config.get("timeout")).longValue() : DEFAULT_TASK_TIMEOUT_MS;
        List<String> dependsOn = (List<String>) config.get("depends_on");
        Map<String, Object> context = (Map<String, Object>) config.get("context");
        
        // 单子任务硬上限 10min
        long clampedTimeout = Math.min(timeout, HARD_TASK_TIMEOUT_MS);
        if (clampedTimeout < timeout) {
            logger.warning("[AgentTool] Task timeout clamped: " + timeout + "ms -> " + clampedTimeout + "ms for task " + name);
        }

        SubAgentTask task = SubAgentTask.builder()
            .name(name)
            .role(role)
            .taskDescription(taskDesc)
            .agentType(agentType)
            .priority(priority)
            .timeout(clampedTimeout)
            .dependencies(dependsOn)
            .context(context)
            .build();

        return task;
    }
    
    /**
     * 构建执行结果
     */
    private ToolResult<Map<String, Object>> buildExecutionResult(ParallelExecutionResult result) {
        StringBuilder content = new StringBuilder();
        content.append("任务执行完成！\n\n");
        content.append("总任务数：").append(result.getTotalCount()).append("\n");
        content.append("成功：").append(result.getSuccessCount()).append("\n");
        content.append("失败：").append(result.getFailureCount()).append("\n");
        content.append("成功率：").append(String.format("%.2f%%", result.getSuccessRate())).append("\n");
        content.append("总耗时：").append(result.getTotalExecutionTimeMs()).append("ms\n\n");
        
        // 成功结果
        if (result.getSuccessCount() > 0) {
            content.append("=== 成功结果 ===\n");
            for (SubAgentResult r : result.getSuccessfulResults()) {
                content.append("\n【").append(r.getTaskId()).append("】\n");
                if (r.getOutput() != null) {
                    content.append(r.getOutput()).append("\n");
                    sharedBus.put("task:" + r.getTaskId() + ":result", r.getOutput());
                }
            }
        }
        
        // 失败结果
        if (result.getFailureCount() > 0) {
            content.append("\n=== 失败详情 ===\n");
            for (SubAgentResult r : result.getFailedResults()) {
                content.append("\n【").append(r.getTaskId()).append("】\n");
                if (r.getError() != null) {
                    content.append("错误：").append(r.getError()).append("\n");
                }
            }
        }
        
        // 合并输出
        String combinedOutput = result.combineSuccessfulOutputs("\n\n---\n\n");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("total_count", result.getTotalCount());
        metadata.put("success_count", result.getSuccessCount());
        metadata.put("failure_count", result.getFailureCount());
        metadata.put("success_rate", result.getSuccessRate());
        metadata.put("execution_time_ms", result.getTotalExecutionTimeMs());
        metadata.put("combined_output", combinedOutput);
        metadata.put("results", result.getResults());
        
        ToolResult<Map<String, Object>> toolResult = new ToolResult<>(metadata);
        toolResult.setSuccess(result.getFailureCount() == 0);
        toolResult.setContent(content.toString());
        toolResult.setMetadata(metadata);
        
        return toolResult;
    }
    
    /**
     * 取消任务（支持 task_id 和 agent_id 两种取消方式）
     * 修复：同时支持 task_id 和 agent_id 参数
     */
    private ToolResult<Map<String, Object>> blackboardPut(Map<String, Object> args) {
        String key = stringValue(args.get("key"));
        if (key == null || key.isBlank()) {
            return ToolResult.error("key parameter is required");
        }
        Object value = args.get("value");
        sharedBus.put(key, value);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", key);
        metadata.put("value", value);

        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent("blackboard updated: " + key);
        result.setMetadata(metadata);
        return result;
    }

    private ToolResult<Map<String, Object>> blackboardGet(Map<String, Object> args) {
        String key = stringValue(args.get("key"));
        if (key == null || key.isBlank()) {
            return ToolResult.error("key parameter is required");
        }
        Object value = sharedBus.get(key);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", key);
        metadata.put("value", value);

        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(String.valueOf(value));
        result.setMetadata(metadata);
        return result;
    }

    private ToolResult<Map<String, Object>> blackboardList() {
        Map<String, Object> snapshot = sharedBus.snapshot();
        Map<String, String> intermediate = sharedBus.intermediateSnapshot();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("items", snapshot);
        metadata.put("intermediate", intermediate);
        metadata.put("count", snapshot.size());
        metadata.put("intermediate_count", intermediate.size());

        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent("blackboard=" + snapshot + "\nintermediate=" + intermediate);
        result.setMetadata(metadata);
        return result;
    }

    private ToolResult<Map<String, Object>> blackboardClear(Map<String, Object> args) {
        String key = stringValue(args.get("key"));
        Map<String, Object> metadata = new HashMap<>();
        if (key == null || key.isBlank()) {
            sharedBus.clearBlackboard();
            metadata.put("scope", "all");
        } else {
            sharedBus.remove(key);
            metadata.put("scope", "key");
            metadata.put("key", key);
        }

        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent("blackboard cleared");
        result.setMetadata(metadata);
        return result;
    }

    private ToolResult<Map<String, Object>> cancelTask(Map<String, Object> args, Session session) {
        String taskId = (String) args.get("task_id");
        String agentId = (String) args.get("agent_id");
        
        // 【修复】如果传的是 agent_id，则通过 agent 查找对应的 taskId
        if ((taskId == null || taskId.isEmpty()) && agentId != null && !agentId.isEmpty()) {
            AgentInfo agent = agentManager.getAgent(agentId);
            if (agent != null && agent.taskId != null) {
                taskId = agent.taskId;
            } else {
                return ToolResult.error("未找到 Agent 或该 Agent 没有正在执行的任务：" + agentId);
            }
        }
        
        if (taskId == null || taskId.isEmpty()) {
            return ToolResult.error("task_id 或 agent_id 参数是必需的");
        }
        
        boolean cancelled = false;
        
        // 1. 尝试取消 Workflow Runtime 中的任务
        // 2. 同时检查 AgentInfo 中的任务
        for (AgentInfo agent : agentManager.getAllAgents()) {
            if (taskId.equals(agent.taskId) && agent.executionFuture != null && !agent.executionFuture.isDone()) {
                agent.executionFuture.cancel(true);
                cancelled = true;
            }
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("task_id", taskId);
        metadata.put("cancelled", cancelled);
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(cancelled);
        result.setContent(cancelled ? "任务已取消: " + taskId : "取消任务失败或任务已完成: " + taskId);
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 查询已分配任务的结果
     */
    private ToolResult<Map<String, Object>> queryTask(Map<String, Object> args, Session session) {
        String agentId = (String) args.get("agent_id");
        
        if (agentId == null || agentId.isEmpty()) {
            return ToolResult.error("agent_id 参数是必需的");
        }
        
        AgentInfo agent = agentManager.getAgent(agentId);
        if (agent == null) {
            return ToolResult.error("未找到 Agent：" + agentId);
        }
        
        if (agent.executionFuture == null) {
            return ToolResult.error("该 Agent 尚未分配任务");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_id", agentId);
        metadata.put("task_id", agent.taskId);
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 任务查询\n\n");
        content.append("Agent: ").append(agent.name).append(" (").append(agentId).append(")\n");
        content.append("任务ID: ").append(agent.taskId).append("\n");
        
        if (!agent.executionFuture.isDone()) {
            content.append("状态：执行中\n");
            metadata.put("status", "running");
            metadata.put("done", false);
        } else {
            metadata.put("done", true);
            if (agent.executionFuture.isCancelled()) {
                content.append("状态：已取消\n");
                metadata.put("status", "cancelled");
            } else if (agent.executionFuture.isCompletedExceptionally()) {
                content.append("状态：执行异常\n");
                metadata.put("status", "exception");
            } else {
                content.append("状态：已完成\n");
                metadata.put("status", "completed");
            }
            
            if (agent.executionResult != null) {
                if (agent.executionResult.isSuccess()) {
                    content.append("结果：成功\n");
                    if (agent.executionResult.getOutput() != null) {
                        content.append("输出：\n").append(agent.executionResult.getOutput()).append("\n");
                    }
                    metadata.put("success", true);
                    metadata.put("output", agent.executionResult.getOutput());
                } else {
                    content.append("结果：失败\n");
                    if (agent.executionResult.getError() != null) {
                        content.append("错误：").append(agent.executionResult.getError()).append("\n");
                    }
                    metadata.put("success", false);
                    metadata.put("error", agent.executionResult.getError());
                }
            }
        }
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    // ==================== 其他操作 ====================
    
    private ToolResult<Map<String, Object>> getAgentStatus(Map<String, Object> args, Session session) {
        String agentId = (String) args.get("agent_id");
        
        if (agentId == null || agentId.isEmpty()) {
            return ToolResult.error("agent_id 参数是必需的");
        }
        
        AgentInfo agent = agentManager.getAgent(agentId);
        if (agent == null) {
            return ToolResult.error("未找到 Agent：" + agentId);
        }
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 状态\n\n");
        content.append("ID: ").append(agentId).append("\n");
        content.append("名称：").append(agent.name).append("\n");
        content.append("角色：").append(agent.role != null ? agent.role : "未指定").append("\n");
        content.append("颜色：").append(agent.color).append("\n");
        content.append("状态：").append(agent.status).append("\n");
        
        if (agent.currentTask != null) {
            content.append("\n当前任务：").append(agent.currentTask).append("\n");
        }
        
        if (agent.taskId != null) {
            content.append("任务ID：").append(agent.taskId).append("\n");
        }
        
        // 检查 executionFuture 动态状态
        if (agent.executionFuture != null) {
            if (!agent.executionFuture.isDone()) {
                content.append("执行状态：进行中\n");
            } else {
                if (agent.executionFuture.isCancelled()) {
                    content.append("执行状态：已取消\n");
                } else if (agent.executionFuture.isCompletedExceptionally()) {
                    content.append("执行状态：异常完成\n");
                } else {
                    content.append("执行状态：已完成\n");
                }
                if (agent.executionResult != null) {
                    content.append("执行结果：").append(agent.executionResult.isSuccess() ? "成功" : "失败").append("\n");
                }
            }
        }
        
        if (agent.completedTasks > 0) {
            content.append("完成任务数：").append(agent.completedTasks).append("\n");
        }
        
        if (agent.createdAt != null) {
            content.append("创建时间：").append(agent.createdAt).append("\n");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_id", agentId);
        metadata.put("name", agent.name);
        metadata.put("status", agent.status.toString());
        metadata.put("current_task", agent.currentTask);
        metadata.put("task_id", agent.taskId);
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    private ToolResult<Map<String, Object>> listAgents(Session session) {
        List<AgentInfo> agents = agentManager.getAllAgents();
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 列表\n\n");
        content.append("总数：").append(agents.size()).append("\n\n");
        
        if (agents.isEmpty()) {
            content.append("暂无 Agent\n");
        } else {
            content.append(String.format("%-12s %-15s %-15s %-10s %-10s\n", 
                       "ID", "名称", "角色", "状态", "颜色"));
            content.append("-".repeat(70)).append("\n");
            
            for (AgentInfo agent : agents) {
                String id = agent.id.length() > 10 ? agent.id.substring(0, 9) + "..." : agent.id;
                String name = agent.name.length() > 13 ? agent.name.substring(0, 12) + "..." : agent.name;
                String role = agent.role != null ? 
                    (agent.role.length() > 13 ? agent.role.substring(0, 12) + "..." : agent.role) : "-";
                
                content.append(String.format("%-12s %-15s %-15s %-10s %-10s\n",
                           id, name, role, agent.status, agent.color));
            }
        }
        
        List<Map<String, Object>> agentList = agents.stream()
            .map(a -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", a.id);
                map.put("name", a.name);
                map.put("role", a.role);
                map.put("status", a.status.toString());
                return map;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("count", agents.size());
        metadata.put("agents", agentList);
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    private ToolResult<Map<String, Object>> stopAgent(Map<String, Object> args, Session session) {
        String agentId = (String) args.get("agent_id");
        
        if (agentId == null || agentId.isEmpty()) {
            return ToolResult.error("agent_id 参数是必需的");
        }
        
        AgentInfo agent = agentManager.getAgent(agentId);
        if (agent == null) {
            return ToolResult.error("未找到 Agent：" + agentId);
        }
        
        // 如果有关联的执行任务，先取消
        if (agent.executionFuture != null && !agent.executionFuture.isDone()) {
            agent.executionFuture.cancel(true);
        }
        
        agentManager.stopAgent(agentId);
        
        session.getLogger().info("AgentTool: 停止 Agent - id=" + agentId);
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 已停止\n\n");
        content.append("ID: ").append(agentId).append("\n");
        content.append("名称：").append(agent.name).append("\n");
        content.append("最终状态：").append(agent.status).append("\n");
        
        if (agent.currentTask != null) {
            content.append("未完成的任务：").append(agent.currentTask).append("\n");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_id", agentId);
        metadata.put("status", "stopped");
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private ToolResult<Map<String, Object>> mergeResults(Map<String, Object> args, Session session) {
        List<String> agentIds = (List<String>) args.get("agent_ids");
        
        if (agentIds == null || agentIds.isEmpty()) {
            return ToolResult.error("agent_ids 参数是必需的");
        }
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 结果合并\n\n");
        
        List<String> results = new ArrayList<>();
        for (String agentId : agentIds) {
            AgentInfo agent = agentManager.getAgent(agentId);
            if (agent != null) {
                content.append("=== ").append(agent.name).append(" (").append(agentId).append(") ===\n");
                
                String result = null;
                // 优先检查 executionFuture 的结果
                if (agent.executionFuture != null) {
                    if (agent.executionFuture.isDone()) {
                        if (agent.executionResult != null) {
                            result = agent.executionResult.isSuccess() 
                                ? agent.executionResult.getOutput() 
                                : ("错误：" + agent.executionResult.getError());
                        }
                    } else {
                        result = "(任务仍在执行中)";
                    }
                }
                
                // 回退到 lastResult
                if (result == null && agent.lastResult != null) {
                    result = agent.lastResult;
                }
                
                if (result != null) {
                    content.append(result).append("\n");
                    results.add(result);
                } else {
                    content.append("(无结果)\n");
                }
                content.append("\n");
            } else {
                content.append("Agent ").append(agentId).append(" 未找到\n");
            }
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("merged_count", results.size());
        metadata.put("agent_ids", agentIds);
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 根据 role 推断 agentType
     */
    private String inferAgentType(String role) {
        if (role == null) return "default";
        String lower = role.toLowerCase();
        if (lower.contains("code") || lower.contains("coder") || lower.contains("编码") || lower.contains("开发")) {
            return "coder";
        }
        if (lower.contains("debug") || lower.contains("调试") || lower.contains("排查")) {
            return "debug";
        }
        if (lower.contains("review") || lower.contains("审查") || lower.contains("审核")) {
            return "review";
        }
        if (lower.contains("test") || lower.contains("测试")) {
            return "test";
        }
        if (lower.contains("doc") || lower.contains("文档")) {
            return "doc";
        }
        if (lower.contains("explore") || lower.contains("调研") || lower.contains("分析")) {
            return "explore";
        }
        if (lower.contains("architect") || lower.contains("架构") || lower.contains("设计")) {
            return "architect";
        }
        return "default";
    }
    
    // ==================== 公共方法 ====================
    
    public static AgentManager getAgentManager() {
        return agentManager;
    }
    
    public void setLLMService(LLMService llmService) {
        this.llmService = llmService;
    }
    
    public void setAgentRegistry(Object ignoredLegacyRegistry) {
        // AgentTool execution is workflow-only; registry injection is kept as a no-op for old callers.
    }
    
    @Override
    public ToolValidationResult validate(Map<String, Object> input) {
        // 【核心修复】先归一化再验证 - 让AI更容易适配参数
        Map<String, Object> normalized = normalizeInput(input);
        
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (normalized == null || !normalized.containsKey("action")) {
            builder.addError("action 是必需的");
            return builder.build();
        }
        
        String action = (String) normalized.get("action");
        Set<String> validActions = Set.of("create", "create_parallel", "assign", "execute", "auto_execute",
                                          "status", "list", "stop", "merge", "cancel", "query",
                                          "blackboard_put", "blackboard_get", "blackboard_list", "blackboard_clear");
        
        if (!validActions.contains(action)) {
            builder.addError("无效的 action: " + action);
        }
        
        // 【修复】同时接受 tasks（复数）或 task（单数）- 归一化后两者都有值
        if ("execute".equals(action)) {
            Object tasks = normalized.get("tasks");
            if (tasks == null) {
                // 【修复】归一化后 task 已被转换为 tasks，不再报错
                builder.addError("tasks 参数是必需的");
            } else if (!(tasks instanceof List)) {
                builder.addError("tasks 必须是数组类型，不能是字符串");
            } else {
                List<?> taskList = (List<?>) tasks;
                if (taskList.isEmpty()) {
                    builder.addError("tasks 数组不能为空");
                } else {
                    // 校验每个任务对象
                    for (int i = 0; i < taskList.size(); i++) {
                        Object item = taskList.get(i);
                        if (!(item instanceof Map)) {
                            builder.addError("tasks[" + i + "] 必须是对象 {name, task, role}，不能是字符串");
                        } else {
                            Map<String, Object> taskMap = (Map<String, Object>) item;
                            boolean hasName = taskMap.containsKey("name") && taskMap.get("name") != null;
                            boolean hasTask = taskMap.containsKey("task") && taskMap.get("task") != null;
                            boolean hasInstruction = taskMap.containsKey("instruction") && taskMap.get("instruction") != null;
                            if (!hasName && !hasTask && !hasInstruction) {
                                builder.addError("tasks[" + i + "] 必须包含 name 或 task 或 instruction 字段");
                            }
                        }
                    }
                }
            }
        }
        
        // 增强参数校验：create_parallel action 必须使用 agents 数组
        if ("create_parallel".equals(action)) {
            Object agents = input.get("agents");
            if (agents == null) {
                builder.addError("create_parallel action 必须提供 agents 参数");
            } else if (!(agents instanceof List)) {
                builder.addError("agents 必须是数组类型");
            } else if (((List<?>) agents).isEmpty()) {
                builder.addError("agents 数组不能为空");
            }
        }
        
        // 增强参数校验：assign action 必须使用 agent_id 和 task
        if ("assign".equals(action)) {
            if (!input.containsKey("agent_id") || input.get("agent_id") == null) {
                builder.addError("assign action 必须提供 agent_id 参数");
            }
            if (!input.containsKey("task") || input.get("task") == null) {
                builder.addError("assign action 必须提供 task 参数（注意是单数 task）");
            }
        }
        
        // 增强参数校验：query action 必须使用 agent_id
        if ("query".equals(action)) {
            if (!input.containsKey("agent_id") || input.get("agent_id") == null) {
                builder.addError("query action 必须提供 agent_id 参数");
            }
        }

        if ("auto_execute".equals(action)) {
            if (!input.containsKey("task") || input.get("task") == null) {
                builder.addError("auto_execute action 必须提供 task 参数");
            }
        }

        if ("blackboard_put".equals(action) || "blackboard_get".equals(action)) {
            if (!input.containsKey("key") || input.get("key") == null) {
                builder.addError(action + " action 必须提供 key 参数");
            }
        }

        if ("blackboard_clear".equals(action) && input.containsKey("key") && input.get("key") != null) {
            Object key = input.get("key");
            if (!(key instanceof String) || ((String) key).isBlank()) {
                builder.addError("blackboard_clear 的 key 参数必须是非空字符串，或不传以清空全部");
            }
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(Map<String, Object> input) {
        String action = (String) input.get("action");
        return "status".equals(action) || "list".equals(action) || "query".equals(action)
            || "blackboard_get".equals(action) || "blackboard_list".equals(action);
    }
    
    // ==================== 内部类 ====================
    
    public enum AgentStatus {
        IDLE("空闲"),
        WORKING("工作中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        STOPPED("已停止");
        
        private final String description;
        
        AgentStatus(String description) {
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }
    
    public static class AgentInfo {
        public final String id;
        public String name;
        public String role;
        public String color;
        public AgentStatus status;
        public String currentTask;
        public String context;
        public String lastResult;
        public int completedTasks;
        public Date createdAt;
        public Date updatedAt;
        
        // 新增字段：与 Workflow Runtime 打通
        public String taskId;
        public CompletableFuture<SubAgentResult> executionFuture;
        public SubAgentResult executionResult;
        public long taskTimeout;
        
        public AgentInfo(String name, String role, String color) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.name = name;
            this.role = role;
            this.color = color;
            this.status = AgentStatus.IDLE;
            this.completedTasks = 0;
            this.createdAt = new Date();
        }
        
        public void assignTask(String task, String context) {
            this.currentTask = task;
            this.context = context;
            this.status = AgentStatus.WORKING;
            this.updatedAt = new Date();
            // 重置执行状态
            this.taskId = null;
            this.executionFuture = null;
            this.executionResult = null;
            this.taskTimeout = 0;
        }
        
        public void completeTask(String result) {
            this.lastResult = result;
            this.currentTask = null;
            this.status = AgentStatus.IDLE;
            this.completedTasks++;
            this.updatedAt = new Date();
        }
        
        public void failTask(String error) {
            this.lastResult = "错误：" + error;
            this.status = AgentStatus.FAILED;
            this.updatedAt = new Date();
        }
    }
    
    private static class WorkflowBlackboard {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, String> intermediate = new ConcurrentHashMap<>();

        void put(String key, Object value) {
            values.put(key, value);
            if (value instanceof String stringValue) {
                intermediate.put(key, stringValue);
            }
        }

        Object get(String key) {
            return values.get(key);
        }

        void remove(String key) {
            values.remove(key);
            intermediate.remove(key);
        }

        void clearBlackboard() {
            values.clear();
            intermediate.clear();
        }

        Map<String, Object> snapshot() {
            return new LinkedHashMap<>(values);
        }

        Map<String, String> intermediateSnapshot() {
            return new LinkedHashMap<>(intermediate);
        }
    }

    public static class AgentManager {
        private final Map<String, AgentInfo> agents = new ConcurrentHashMap<>();
        private final List<String> colors = Arrays.asList("🔵", "🟢", "🟡", "🟣", "🔶", "🔷", "💜", "💚");
        private int colorIndex = 0;
        
        public String registerAgent(AgentInfo agent) {
            agents.put(agent.id, agent);
            return agent.id;
        }
        
        public AgentInfo getAgent(String agentId) {
            return agents.get(agentId);
        }
        
        public List<AgentInfo> getAllAgents() {
            return new ArrayList<>(agents.values());
        }
        
        public int getAgentCount() {
            return agents.size();
        }
        
        public void stopAgent(String agentId) {
            AgentInfo agent = agents.get(agentId);
            if (agent != null) {
                agent.status = AgentStatus.STOPPED;
                agent.updatedAt = new Date();
            }
        }
        
        public String getNextColor() {
            String color = colors.get(colorIndex);
            colorIndex = (colorIndex + 1) % colors.size();
            return color;
        }
        
        public AgentInfo createBuiltInAgent(String type) {
            String name;
            String role;
            
            switch (type) {
                case "code_reviewer":
                    name = "代码审查员";
                    role = "负责代码审查和质量检查";
                    break;
                case "test_expert":
                    name = "测试专家";
                    role = "负责编写和运行测试";
                    break;
                case "doc_expert":
                    name = "文档专家";
                    role = "负责文档编写和维护";
                    break;
                case "security_expert":
                    name = "安全专家";
                    role = "负责安全检查和漏洞扫描";
                    break;
                case "performance_expert":
                    name = "性能专家";
                    role = "负责性能分析和优化";
                    break;
                default:
                    name = "Agent-" + (agents.size() + 1);
                    role = "通用 Agent";
            }
            
            AgentInfo agent = new AgentInfo(name, role, getNextColor());
            registerAgent(agent);
            return agent;
        }
    }
}
