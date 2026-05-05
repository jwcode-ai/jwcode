package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.agent.parallel.*;
import com.jwcode.core.api.StepMessageBroadcaster;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Agent 工具 - Phase 2 增强版
 * 
 * 多代理协作管理工具，集成 ParallelAgentExecutor，支持：
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
    
    // Agent 管理器单例
    private static final AgentManager agentManager = new AgentManager();
    
    // 并行执行器
    private volatile ParallelAgentExecutor parallelExecutor;
    
    // LLM 服务（用于真正的 Agent 执行）
    private volatile LLMService llmService;
    private volatile AgentRegistry agentRegistry;
    
    // 【修复】用于同步注册到全局 AgentRegistry
    private static volatile AgentRegistry sharedAgentRegistry;
    
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
                - timeout: 超时时间毫秒（可选，默认60000）
                
                execute 参数:
                - tasks: 任务列表（复数，用于多个任务）[{name, role, task, depends_on}, ...]
                - parallel: 是否并行执行（可选，默认true）
                - timeout: 全局超时时间（可选，默认300000）
                
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
                        "action": {"type": "string", "enum": ["create", "create_parallel", "assign", "execute", "status", "list", "stop", "merge", "cancel", "query"]},
                        "agent_id": {"type": "string"},
                        "name": {"type": "string"},
                        "role": {"type": "string"},
                        "color": {"type": "string"},
                        "task": {"type": "string"},
                        "context": {"type": "string"},
                        "timeout": {"type": "integer", "default": 60000},
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
                        return assignTask(normalizedInput, context.getSession());
                    case "execute":
                        return executeTasks(normalizedInput, context.getSession());
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
        
        return normalized;
    }
    
    // ==================== 初始化 ====================
    
    private void ensureExecutorInitialized(ToolExecutionContext context) {
        if (parallelExecutor == null) {
            synchronized (this) {
                if (parallelExecutor == null) {
                    agentRegistry = context.getAgentRegistry();
                    llmService = context.getLLMService();
                    parallelExecutor = new ParallelAgentExecutor(agentRegistry, llmService);
                    logger.info("ParallelAgentExecutor initialized");
                }
            }
        }
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
     * 分配任务给 Agent（自动提交到 ParallelAgentExecutor 执行）
     */
    private ToolResult<Map<String, Object>> assignTask(Map<String, Object> args, Session session) {
        String agentId = (String) args.get("agent_id");
        String task = (String) args.get("task");
        String context = (String) args.get("context");
        Long timeout = args.get("timeout") instanceof Number ? 
            ((Number) args.get("timeout")).longValue() : 60000L;
        
        if (agentId == null || agentId.isEmpty()) {
            return ToolResult.error("agent_id 参数是必需的");
        }
        
        if (task == null || task.isEmpty()) {
            return ToolResult.error("task 参数是必需的");
        }
        
        AgentInfo agent = agentManager.getAgent(agentId);
        if (agent == null) {
            return ToolResult.error("未找到 Agent：" + agentId);
        }
        
        if (agent.status != AgentStatus.IDLE) {
            return ToolResult.error("Agent 当前状态为 " + agent.status + "，无法分配新任务");
        }
        
        agent.assignTask(task, context);
        
        // 构建 SubAgentTask 并提交到 ParallelAgentExecutor
        Map<String, Object> taskContext = new HashMap<>();
        if (context != null && !context.isEmpty()) {
            taskContext.put("user_context", context);
        }
        
        SubAgentTask subTask = SubAgentTask.builder()
            .name(agent.name)
            .role(agent.role)
            .taskDescription(task)
            .agentType(inferAgentType(agent.role))
            .timeout(timeout)
            .context(taskContext)
            .build();
        
        CompletableFuture<ParallelExecutionResult> parallelFuture = 
            parallelExecutor.executeAsync(List.of(subTask), session, timeout);
        
        CompletableFuture<SubAgentResult> subAgentFuture = parallelFuture.thenApply(result -> {
            List<SubAgentResult> results = result.getResults();
            if (results != null && !results.isEmpty()) {
                return results.get(0);
            }
            return SubAgentResult.failure(subTask.getTaskId(), "No result returned");
        }).exceptionally(ex -> {
            return SubAgentResult.failure(subTask.getTaskId(), ex.getMessage());
        });
        
        agent.executionFuture = subAgentFuture;
        agent.taskId = subTask.getTaskId();
        agent.taskTimeout = timeout;
        
        subAgentFuture.whenComplete((result, error) -> {
            if (error != null) {
                agent.executionResult = SubAgentResult.failure(agent.taskId, error.getMessage());
                agent.failTask(error.getMessage());
            } else if (result != null) {
                agent.executionResult = result;
                if (result.isSuccess()) {
                    agent.completeTask(result.getOutput());
                } else {
                    agent.failTask(result.getError());
                }
            }
        });
        
        session.getLogger().info("AgentTool: 分配任务并提交执行 - agentId=" + agentId + ", taskId=" + subTask.getTaskId() + ", task=" + task);
        
        StringBuilder content = new StringBuilder();
        content.append("任务分配成功！\n\n");
        content.append("Agent: ").append(agent.name).append(" (").append(agentId).append(")\n");
        content.append("任务ID: ").append(subTask.getTaskId()).append("\n");
        content.append("任务：").append(task).append("\n");
        content.append("超时：").append(timeout).append("ms\n");
        
        if (context != null && !context.isEmpty()) {
            content.append("上下文：").append(context).append("\n");
        }
        
        content.append("\nAgent 状态已更新为：working\n");
        content.append("任务已自动提交到执行器，可通过 status 或 query 查询进度。\n");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_id", agentId);
        metadata.put("task_id", subTask.getTaskId());
        metadata.put("task", task);
        metadata.put("timeout", timeout);
        metadata.put("status", "working");
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 并行执行多个任务
     */
    @SuppressWarnings("unchecked")
    private ToolResult<Map<String, Object>> executeTasks(Map<String, Object> args, Session session) {
        List<Map<String, Object>> taskConfigs = (List<Map<String, Object>>) args.get("tasks");
        Boolean parallel = args.get("parallel") instanceof Boolean ? 
            (Boolean) args.get("parallel") : true;
        Long globalTimeout = args.get("timeout") instanceof Number ? 
            ((Number) args.get("timeout")).longValue() : 300000L;
        
        if (taskConfigs == null || taskConfigs.isEmpty()) {
            return ToolResult.error("tasks 参数是必需的");
        }
        
        session.getLogger().info("AgentTool: 执行 " + taskConfigs.size() + " 个任务, parallel=" + parallel);
        
        // 构建 SubAgentTask 列表
        List<SubAgentTask> tasks = new ArrayList<>();
        for (Map<String, Object> config : taskConfigs) {
            SubAgentTask task = buildTaskFromConfig(config);
            tasks.add(task);
            // 广播 step_start 消息到前端
            StepMessageBroadcaster.getInstance().broadcastStepStart(
                task.getTaskId(),
                task.getName(),
                task.getTaskDescription()
            );
        }
        
        try {
            ParallelExecutionResult executionResult;
            
            if (parallel) {
                // 异步执行
                CompletableFuture<ParallelExecutionResult> future = 
                    parallelExecutor.executeAsync(tasks, session, globalTimeout);
                executionResult = future.get();
            } else {
                // 同步执行
                executionResult = parallelExecutor.execute(tasks, session, globalTimeout);
            }
            
            // 构建结果
            return buildExecutionResult(executionResult);
            
        } catch (Exception e) {
            logger.severe("Task execution failed: " + e.getMessage());
            return ToolResult.error("任务执行失败: " + e.getMessage());
        }
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
            ((Number) config.get("timeout")).longValue() : 60000L;
        List<String> dependsOn = (List<String>) config.get("depends_on");
        Map<String, Object> context = (Map<String, Object>) config.get("context");
        
        SubAgentTask task = SubAgentTask.builder()
            .name(name)
            .role(role)
            .taskDescription(taskDesc)
            .agentType(agentType)
            .priority(priority)
            .timeout(timeout)
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
        
        // 1. 尝试取消 ParallelAgentExecutor 中的任务
        if (parallelExecutor != null) {
            cancelled = parallelExecutor.cancel(taskId);
        }
        
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
        if (agent.taskId != null && parallelExecutor != null) {
            parallelExecutor.cancel(agent.taskId);
        }
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
    
    public void setAgentRegistry(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
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
        Set<String> validActions = Set.of("create", "create_parallel", "assign", "execute", 
                                          "status", "list", "stop", "merge", "cancel", "query");
        
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
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(Map<String, Object> input) {
        String action = (String) input.get("action");
        return "status".equals(action) || "list".equals(action) || "query".equals(action);
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
        
        // 新增字段：与 ParallelAgentExecutor 打通
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
