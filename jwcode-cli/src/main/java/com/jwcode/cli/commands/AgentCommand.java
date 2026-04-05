package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;

import java.util.Collection;

/**
 * AgentCommand - Agent 管理命令
 * 
 * 用法：
 * - agent list        列出所有 Agent
 * - agent switch <id> 切换到指定 Agent
 * - agent current     显示当前 Agent
 * - agent info <id>   显示 Agent 详情
 */
public class AgentCommand implements Command {
    
    private final AgentRegistry registry;
    
    public AgentCommand() {
        this.registry = AgentRegistry.createDefault();
    }
    
    @Override
    public String getName() {
        return "agent";
    }
    
    @Override
    public String getDescription() {
        return "管理 AI Agent，切换不同角色";
    }
    
    @Override
    public String getUsage() {
        return "agent <list|switch|current|info> [args]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            return showCurrentAgent();
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String action = parts[0];
        String arg = parts.length > 1 ? parts[1] : null;
        
        switch (action) {
            case "list":
                return listAgents();
            case "switch":
                return switchAgent(arg);
            case "current":
                return showCurrentAgent();
            case "info":
                return showAgentInfo(arg);
            default:
                return CommandResult.error("未知操作: " + action + "\n可用操作: list, switch, current, info");
        }
    }
    
    private CommandResult listAgents() {
        Collection<Agent> agents = registry.getAll();
        Agent current = registry.getCurrent();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== 可用 Agent ===\n\n");
        
        for (Agent agent : agents) {
            String marker = agent.getId().equals(current.getId()) ? " → " : "   ";
            sb.append(marker).append(agent.getId()).append("\n");
            sb.append("     ").append(agent.getName()).append("\n");
            sb.append("     ").append(agent.getDescription()).append("\n");
            sb.append("     工具数: ").append(agent.getTools().size()).append("\n\n");
        }
        
        return CommandResult.success(sb.toString());
    }
    
    private CommandResult switchAgent(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return CommandResult.error("请指定 Agent ID，使用 'agent list' 查看可用 Agent");
        }
        
        if (registry.switchTo(agentId)) {
            Agent agent = registry.getCurrent();
            return CommandResult.success(
                String.format("已切换到 Agent: %s (%s)\n%s", 
                    agent.getName(), agent.getId(), agent.getDescription())
            );
        } else {
            return CommandResult.error("Agent 不存在: " + agentId);
        }
    }
    
    private CommandResult showCurrentAgent() {
        Agent agent = registry.getCurrent();
        return CommandResult.success(formatAgentInfo(agent, true));
    }
    
    private CommandResult showAgentInfo(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return CommandResult.error("请指定 Agent ID");
        }
        
        Agent agent = registry.get(agentId);
        if (agent == null) {
            return CommandResult.error("Agent 不存在: " + agentId);
        }
        
        return CommandResult.success(formatAgentInfo(agent, false));
    }
    
    private String formatAgentInfo(Agent agent, boolean isCurrent) {
        StringBuilder sb = new StringBuilder();
        
        if (isCurrent) {
            sb.append("=== 当前 Agent ===\n\n");
        } else {
            sb.append("=== Agent 详情 ===\n\n");
        }
        
        sb.append("ID: ").append(agent.getId()).append("\n");
        sb.append("名称: ").append(agent.getName()).append("\n");
        sb.append("描述: ").append(agent.getDescription()).append("\n");
        sb.append("工具数: ").append(agent.getTools().size()).append("\n");
        
        if (agent.getModelConfig() != null) {
            sb.append("模型: ").append(agent.getModelConfig().getModel()).append("\n");
        }
        
        if (agent.getConfig() != null && !agent.getConfig().isEmpty()) {
            sb.append("配置:\n");
            agent.getConfig().forEach((k, v) -> {
                sb.append("  ").append(k).append(" = ").append(v).append("\n");
            });
        }
        
        return sb.toString();
    }
}
