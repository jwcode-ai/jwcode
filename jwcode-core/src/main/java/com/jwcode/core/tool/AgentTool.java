package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Agent 工具 - 多代理协作管理
 * 
 * 用于创建和管理多个 Agent，支持：
 * - 创建专用 Agent
 * - 分配子任务给 Agent
 * - 管理 Agent 状态和输出
 * - Agent 间通信
 * - Agent 颜色管理
 * - Agent 内存管理
 */
public class AgentTool implements Tool<Map<String, Object>, Map<String, Object>, Void> {
    
    // Agent 管理器单例
    private static final AgentManager agentManager = new AgentManager();
    
    @Override
    public String getName() {
        return "AgentTool";
    }
    
    @Override
    public String getDescription() {
        return "创建和管理多个 Agent 进行协作。可以分配子任务给专用 Agent，并跟踪它们的执行状态。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 AgentTool 创建和管理多个 Agent 进行协作。
               
               参数:
               - action: 操作类型（必需）
                 - create: 创建新 Agent
                 - assign: 分配任务给 Agent
                 - status: 查看 Agent 状态
                 - list: 列出所有 Agent
                 - stop: 停止 Agent
                 - merge: 合并 Agent 结果
               - agent_id: Agent ID（create 以外操作必需）
               - name: Agent 名称（可选）
               - role: Agent 角色/专业领域（可选）
               - task: 任务描述（assign 必需）
               - context: 上下文信息（可选）
               - color: Agent 颜色标识（可选）
               
               示例:
               - {"action": "create", "name": "测试专家", "role": "负责编写和运行测试"}
               - {"action": "assign", "agent_id": "agent-1", "task": "为这个函数编写单元测试"}
               - {"action": "status", "agent_id": "agent-1"}
               - {"action": "list"}
               
               内置 Agent 类型:
               - 代码审查员：负责代码审查
               - 测试专家：负责测试编写
               - 文档专家：负责文档编写
               - 安全专家：负责安全检查
               - 性能专家：负责性能优化
               
               Agent 状态:
               - idle: 空闲
               - working: 工作中
               - completed: 已完成
               - failed: 失败
               - stopped: 已停止
               """;
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
                
                switch (action) {
                    case "create":
                        return createAgent(input, context.getSession());
                    case "assign":
                        return assignTask(input, context.getSession());
                    case "status":
                        return getAgentStatus(input, context.getSession());
                    case "list":
                        return listAgents(context.getSession());
                    case "stop":
                        return stopAgent(input, context.getSession());
                    case "merge":
                        return mergeResults(input, context.getSession());
                    default:
                        return ToolResult.error("未知的操作类型：" + action);
                }
            } catch (Exception e) {
                return ToolResult.error("AgentTool 执行失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 创建 Agent
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
        
        Agent agent = new Agent(name, role, color);
        String agentId = agentManager.registerAgent(agent);
        
        session.getLogger().info("AgentTool: 创建 Agent - id=" + agentId + ", name=" + name + ", role=" + role);
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 创建成功！\n\n");
        content.append("Agent ID: ").append(agentId).append("\n");
        content.append("名称：").append(name).append("\n");
        content.append("角色：").append(role != null ? role : "未指定").append("\n");
        content.append("颜色：").append(color).append("\n");
        content.append("状态：idle\n");
        
        Map<String, Object> metadata = Map.of(
            "agent_id", agentId,
            "name", name,
            "role", role,
            "color", color,
            "status", "idle"
        );
        
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
        
        if (agentId == null || agentId.isEmpty()) {
            return ToolResult.error("agent_id 参数是必需的");
        }
        
        if (task == null || task.isEmpty()) {
            return ToolResult.error("task 参数是必需的");
        }
        
        Agent agent = agentManager.getAgent(agentId);
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
        
        if (context != null && !context.isEmpty()) {
            content.append("上下文：").append(context).append("\n");
        }
        
        content.append("\nAgent 状态已更新为：working\n");
        
        Map<String, Object> metadata = Map.of(
            "agent_id", agentId,
            "task", task,
            "status", "working"
        );
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 获取 Agent 状态
     */
    private ToolResult<Map<String, Object>> getAgentStatus(Map<String, Object> args, Session session) {
        String agentId = (String) args.get("agent_id");
        
        if (agentId == null || agentId.isEmpty()) {
            return ToolResult.error("agent_id 参数是必需的");
        }
        
        Agent agent = agentManager.getAgent(agentId);
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
        
        Map<String, Object> metadata = Map.of(
            "agent_id", agentId,
            "name", agent.name,
            "status", agent.status.toString(),
            "current_task", agent.currentTask
        );
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 列出所有 Agent
     */
    private ToolResult<Map<String, Object>> listAgents(Session session) {
        List<Agent> agents = agentManager.getAllAgents();
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 列表\n\n");
        content.append("总数：").append(agents.size()).append("\n\n");
        
        if (agents.isEmpty()) {
            content.append("暂无 Agent\n");
        } else {
            content.append(String.format("%-12s %-15s %-15s %-10s %-10s\n", 
                       "ID", "名称", "角色", "状态", "颜色"));
            content.append("-".repeat(70)).append("\n");
            
            for (Agent agent : agents) {
                String id = agent.id.length() > 10 ? agent.id.substring(0, 9) + "..." : agent.id;
                String name = agent.name.length() > 13 ? agent.name.substring(0, 12) + "..." : agent.name;
                String role = agent.role != null ? 
                    (agent.role.length() > 13 ? agent.role.substring(0, 12) + "..." : agent.role) : "-";
                
                content.append(String.format("%-12s %-15s %-15s %-10s %-10s\n",
                           id, name, role, agent.status, agent.color));
            }
        }
        
        Map<String, Object> metadata = Map.of(
            "count", agents.size(),
            "agents", agents.stream().map(a -> Map.of(
                "id", a.id,
                "name", a.name,
                "role", a.role,
                "status", a.status.toString()
            )).toList()
        );
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 停止 Agent
     */
    private ToolResult<Map<String, Object>> stopAgent(Map<String, Object> args, Session session) {
        String agentId = (String) args.get("agent_id");
        
        if (agentId == null || agentId.isEmpty()) {
            return ToolResult.error("agent_id 参数是必需的");
        }
        
        Agent agent = agentManager.getAgent(agentId);
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
        
        Map<String, Object> metadata = Map.of(
            "agent_id", agentId,
            "status", "stopped"
        );
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 合并 Agent 结果
     */
    private ToolResult<Map<String, Object>> mergeResults(Map<String, Object> args, Session session) {
        @SuppressWarnings("unchecked")
        List<String> agentIds = (List<String>) args.get("agent_ids");
        
        if (agentIds == null || agentIds.isEmpty()) {
            return ToolResult.error("agent_ids 参数是必需的");
        }
        
        StringBuilder content = new StringBuilder();
        content.append("Agent 结果合并\n\n");
        
        List<String> results = new ArrayList<>();
        for (String agentId : agentIds) {
            Agent agent = agentManager.getAgent(agentId);
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
        
        Map<String, Object> metadata = Map.of(
            "merged_count", results.size(),
            "agent_ids", agentIds
        );
        
        ToolResult<Map<String, Object>> result = new ToolResult<>(metadata);
        result.setSuccess(true);
        result.setContent(content.toString());
        result.setMetadata(metadata);
        
        return result;
    }
    
    /**
     * 获取 Agent 管理器
     */
    public static AgentManager getAgentManager() {
        return agentManager;
    }
    
    /**
     * Agent 状态枚举
     */
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
    
    /**
     * Agent 类
     */
    public static class Agent {
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
        
        public Agent(String name, String role, String color) {
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
    
    /**
     * Agent 管理器类
     */
    public static class AgentManager {
        private final Map<String, Agent> agents = new ConcurrentHashMap<>();
        private final List<String> colors = Arrays.asList("🔵", "🟢", "🟡", "🟣", "🔶", "🔷", "💜", "💚");
        private int colorIndex = 0;
        
        /**
         * 注册 Agent
         */
        public String registerAgent(Agent agent) {
            agents.put(agent.id, agent);
            return agent.id;
        }
        
        /**
         * 获取 Agent
         */
        public Agent getAgent(String agentId) {
            return agents.get(agentId);
        }
        
        /**
         * 获取所有 Agent
         */
        public List<Agent> getAllAgents() {
            return new ArrayList<>(agents.values());
        }
        
        /**
         * 获取 Agent 数量
         */
        public int getAgentCount() {
            return agents.size();
        }
        
        /**
         * 停止 Agent
         */
        public void stopAgent(String agentId) {
            Agent agent = agents.get(agentId);
            if (agent != null) {
                agent.status = AgentStatus.STOPPED;
                agent.updatedAt = new Date();
            }
        }
        
        /**
         * 获取下一个颜色
         */
        public String getNextColor() {
            String color = colors.get(colorIndex);
            colorIndex = (colorIndex + 1) % colors.size();
            return color;
        }
        
        /**
         * 创建内置 Agent
         */
        public Agent createBuiltInAgent(String type) {
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
            
            Agent agent = new Agent(name, role, getNextColor());
            registerAgent(agent);
            return agent;
        }
    }
}