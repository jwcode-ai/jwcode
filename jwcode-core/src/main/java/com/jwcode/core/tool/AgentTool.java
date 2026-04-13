package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.agent.parallel.*;
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
               - assign: 分配任务给 Agent
               - execute: 并行执行多个任务
               - status: 查看 Agent 状态
               - list: 列出所有 Agent
               - stop: 停止 Agent
               - merge: 合并 Agent 结果
               - cancel: 取消正在执行的任务
               
               create 参数:
               - name: Agent 名称（可选）
               - role: Agent 角色/专业领域（可选）
               - color: Agent 颜色标识（可选）
               
               create_parallel 参数:
               - agents: 要创建的 Agent 配置列表 [{name, role}, ...]
               
               assign 参数:
               - agent_id: Agent ID（必需）
               - task: 任务描述（必需）
               - context: 上下文信息（可选）
               - timeout: 超时时间毫秒（可选，默认60000）
               
               execute 参数:
               - tasks: 任务列表 [{name, role, task, depends_on}, ...]
               - parallel: 是否并行执行（可选，默认true）
               - timeout: 全局超时时间（可选，默认300000）
               
               示例:
               - {"action": "create", "name": "测试专家", "role": "负责编写和运行测试"}
               - {"action": "create_parallel", "agents": [{"name": "前端专家"}, {"name": "后端专家"}]}
               - {"action": "execute", "tasks": [{"name": "task1", "role": "coder", "task": "编写登录功能"}]}
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "action": {"type": "string", "enum": ["create", "create_parallel", "assign", "execute", "status", "list", "stop", "merge", "cancel"]},
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
                String action = (String) input.get("action");
                
                if (action == null || action.isEmpty()) {
                    return ToolResult.error("action 参数是必需的");
                }
                
                // 初始化执行器
                ensureExecutorInitialized(context);
                
                switch (action) {
                    case "create":
                        return createAgent(input, context.getSession());
                    case "create_parallel":
                        return createAgentsParallel(input, context.getSession());
                    case "assign":
                        return assignTask(input, context.getSession());
                    case "execute":
                        return executeTasks(input, context.getSession());
                    case "status":
                        return getAgentStatus(input, context.getSession());
                    case "list":
                        return listAgents(context.getSession());
                    case "stop":
                        return stopAgent(input, context.getSession());
                    case "merge":
                        return mergeResults(input, context.getSession());
                    case "cancel":
                        return cancelTask(input, context.getSession());
                    default:
                        return ToolResult.error("未知的操作类型：" + action);
                }
            } catch (Exception e) {
                logger.severe("AgentTool 执行失败: " + e.getMessage());
                return ToolResult.error("AgentTool 执行失败: " + e.getMessage());
            }
        });
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
     * 分配任务给 Agent
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
        
        session.getLogger().info("AgentTool: 分配任务 - agentId=" + agentId + ", task=" + task);
        
        StringBuilder content = new StringBuilder();
        content.append("任务分配成功！\n\n");
        content.append("Agent: ").append(agent.name).append(" (").append(agentId).append(")\n");
        content.append("任务：").append(task).append("\n");
        content.append("超时：").append(timeout).append("ms\n");
        
        if (context != null && !context.isEmpty()) {
            content.append("上下文：").append(context).append("\n");
        }
        
        content.append("\nAgent 状态已更新为：working\n");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agent_id", agentId);
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
     * 取消任务
     */
    private ToolResult<Map<String, Object>> cancelTask(Map<String, Object> args, Session session) {
        String taskId = (String) args.get("task_id");
        
        if (taskId == null || taskId.isEmpty()) {
            return ToolResult.error("task_id 参数是必需的");
        }
        
        boolean cancelled = parallelExecutor != null && parallelExecutor.cancel(taskId);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("task_id", taskId);
        metadata.put("cancelled", cancelled);
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(cancelled);
        result.setContent(cancelled ? "任务已取消: " + taskId : "取消任务失败或任务已完成: " + taskId);
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
                if (agent.lastResult != null) {
                    content.append(agent.lastResult).append("\n");
                    results.add(agent.lastResult);
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
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (input == null || !input.containsKey("action")) {
            builder.addError("action 是必需的");
            return builder.build();
        }
        
        String action = (String) input.get("action");
        Set<String> validActions = Set.of("create", "create_parallel", "assign", "execute", 
                                          "status", "list", "stop", "merge", "cancel");
        
        if (!validActions.contains(action)) {
            builder.addError("无效的 action: " + action);
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(Map<String, Object> input) {
        String action = (String) input.get("action");
        return "status".equals(action) || "list".equals(action);
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
