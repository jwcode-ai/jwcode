package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AgentsCommand - /agents 命令
 * 
 * 功能说明：
 * 管理 Agent（代理），包括列表显示、状态查看、控制等。
 * 
 * Agent 类型：
 * - general: 通用助手
 * - coding: 编码专家
 * - review: 代码审查
 * - test: 测试专家
 * - debug: 调试专家
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/agents", description = "Agent 管理", 
         aliases = {"/agent", "/ag"})
public class AgentsCommand implements Runnable {
    
    @Parameters(index = "0", description = "子命令：list, status, start, stop, restart", 
                defaultValue = "list")
    private String subCommand;
    
    @Parameters(index = "1", description = "Agent 名称或 ID", arity = "0..1")
    private String agentName;
    
    @Option(names = {"-a", "--all"}, description = "显示所有 Agent（包括已停止的）")
    private boolean showAll;
    
    @Option(names = {"-v", "--verbose"}, description = "显示详细信息")
    private boolean verbose;
    
    @Option(names = {"-f", "--filter"}, description = "按类型过滤：general, coding, review, test, debug")
    private String filterByType;
    
    // 模拟的 Agent 状态存储
    private final Map<String, AgentInfo> agentStore;
    
    public AgentsCommand() {
        this.agentStore = new HashMap<>();
        initializeBuiltInAgents();
    }
    
    /**
     * 初始化内置 Agent
     */
    private void initializeBuiltInAgents() {
        agentStore.put("general", new AgentInfo("general", "通用助手", "general", true, "待命"));
        agentStore.put("coding", new AgentInfo("coding", "编码专家", "coding", true, "待命"));
        agentStore.put("review", new AgentInfo("review", "代码审查", "review", false, "已停止"));
        agentStore.put("test", new AgentInfo("test", "测试专家", "test", false, "已停止"));
        agentStore.put("debug", new AgentInfo("debug", "调试专家", "debug", false, "已停止"));
    }
    
    @Override
    public void run() {
        switch (subCommand.toLowerCase()) {
            case "list":
                handleList();
                break;
            case "status":
                handleStatus();
                break;
            case "start":
                handleStart();
                break;
            case "stop":
                handleStop();
                break;
            case "restart":
                handleRestart();
                break;
            default:
                System.out.println("未知命令：" + subCommand);
                System.out.println("可用命令：list, status, start, stop, restart");
        }
    }
    
    /**
     * 处理 list 命令 - 列出所有 Agent
     */
    private void handleList() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║           JWCode Agents                ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        List<AgentInfo> agents = new ArrayList<>(agentStore.values());
        
        // 过滤
        if (filterByType != null) {
            agents.removeIf(a -> !a.type.equals(filterByType));
        }
        if (!showAll) {
            agents.removeIf(a -> !a.enabled);
        }
        
        if (agents.isEmpty()) {
            System.out.println("没有找到符合条件的 Agent");
            return;
        }
        
        // 输出列表
        for (AgentInfo agent : agents) {
            String statusIcon = agent.enabled ? "✓" : "✗";
            String statusText = agent.enabled ? agent.status : "已禁用";
            
            System.out.printf("[%s] %-12s %-16s (%s)%n", 
                statusIcon, agent.id, agent.name, agent.type);
            
            if (verbose) {
                System.out.printf("    状态：%s%n", statusText);
                System.out.printf("    描述：%s%n", agent.getDescription());
            }
        }
        
        System.out.println();
        System.out.println("使用 /agents status <agent> 查看详细信息");
        System.out.println("使用 /agents start|stop <agent> 控制 Agent");
    }
    
    /**
     * 处理 status 命令 - 查看 Agent 状态
     */
    private void handleStatus() {
        if (agentName == null) {
            System.out.println("请指定 Agent 名称");
            System.out.println("用法：/agents status <agent>");
            return;
        }
        
        AgentInfo agent = agentStore.get(agentName);
        if (agent == null) {
            System.out.println("未找到 Agent: " + agentName);
            System.out.println("使用 /agents list 查看可用 Agent");
            return;
        }
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║  Agent 状态：" + agent.name);
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        System.out.println("ID:      " + agent.id);
        System.out.println("名称：    " + agent.name);
        System.out.println("类型：    " + agent.type);
        System.out.println("状态：    " + (agent.enabled ? agent.status : "已禁用"));
        System.out.println("启用：    " + (agent.enabled ? "是" : "否"));
        System.out.println();
        System.out.println("描述:");
        System.out.println("  " + agent.getDescription());
    }
    
    /**
     * 处理 start 命令 - 启动 Agent
     */
    private void handleStart() {
        if (agentName == null) {
            System.out.println("请指定 Agent 名称");
            System.out.println("用法：/agents start <agent>");
            return;
        }
        
        AgentInfo agent = agentStore.get(agentName);
        if (agent == null) {
            System.out.println("未找到 Agent: " + agentName);
            return;
        }
        
        if (agent.enabled) {
            System.out.println("Agent " + agent.name + " 已经在运行中");
            return;
        }
        
        agent.enabled = true;
        agent.status = "运行中";
        
        System.out.println("✓ 已启动 Agent: " + agent.name);
        System.out.println("  类型：" + agent.type);
        System.out.println("  状态：" + agent.status);
    }
    
    /**
     * 处理 stop 命令 - 停止 Agent
     */
    private void handleStop() {
        if (agentName == null) {
            System.out.println("请指定 Agent 名称");
            System.out.println("用法：/agents stop <agent>");
            return;
        }
        
        AgentInfo agent = agentStore.get(agentName);
        if (agent == null) {
            System.out.println("未找到 Agent: " + agentName);
            return;
        }
        
        if (!agent.enabled) {
            System.out.println("Agent " + agent.name + " 已经停止");
            return;
        }
        
        agent.enabled = false;
        agent.status = "已停止";
        
        System.out.println("✓ 已停止 Agent: " + agent.name);
    }
    
    /**
     * 处理 restart 命令 - 重启 Agent
     */
    private void handleRestart() {
        if (agentName == null) {
            System.out.println("请指定 Agent 名称");
            System.out.println("用法：/agents restart <agent>");
            return;
        }
        
        AgentInfo agent = agentStore.get(agentName);
        if (agent == null) {
            System.out.println("未找到 Agent: " + agentName);
            return;
        }
        
        agent.enabled = true;
        agent.status = "运行中";
        
        System.out.println("✓ 已重启 Agent: " + agent.name);
    }
    
    /**
     * Agent 信息类
     */
    private static class AgentInfo {
        String id;
        String name;
        String type;
        boolean enabled;
        String status;
        
        AgentInfo(String id, String name, String type, boolean enabled, String status) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.enabled = enabled;
            this.status = status;
        }
        
        String getDescription() {
            switch (type) {
                case "general":
                    return "通用助手，可以回答各种问题并提供一般性帮助";
                case "coding":
                    return "编码专家，擅长编写、重构和优化代码";
                case "review":
                    return "代码审查，帮助发现代码中的问题和改进建议";
                case "test":
                    return "测试专家，帮助编写测试用例和执行测试";
                case "debug":
                    return "调试专家，帮助定位和修复代码中的 bug";
                default:
                    return "未知类型的 Agent";
            }
        }
    }
}