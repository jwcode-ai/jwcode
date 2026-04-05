package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.tool.ToolRegistry;

import java.util.Collection;
import java.util.List;

/**
 * Agent 命令 - 多 Agent 系统管理
 * 
 * 用法：
 *   agent list          - 列出可用 agents
 *   agent show <name>   - 查看 agent 详情
 *   agent switch <name> - 切换到指定 agent
 */
public class AgentCmd implements Command {
    
    private final AgentRegistry registry;
    
    public AgentCmd() {
        this.registry = AgentRegistry.createDefault();
    }
    
    @Override
    public String getName() {
        return "agent";
    }
    
    @Override
    public String getDescription() {
        return "Agent 管理 - 多 Agent 系统";
    }
    
    @Override
    public String getUsage() {
        return "agent list | agent show <name> | agent switch <name>";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        if (args == null || args.trim().isEmpty()) {
            return CommandResult.error(getUsage());
        }
        
        String[] parts = args.trim().split("\\s+", 2);
        String subCommand = parts[0];
        String subArgs = parts.length > 1 ? parts[1] : "";
        
        switch (subCommand.toLowerCase()) {
            case "list":
                return handleList();
            case "show":
                return handleShow(subArgs);
            case "switch":
                return handleSwitch(subArgs);
            case "current":
                return handleCurrent();
            default:
                return CommandResult.error("未知子命令: " + subCommand);
        }
    }
    
    /**
     * 列出所有 agents
     */
    private CommandResult handleList() {
        Collection<Agent> agents = registry.getAll();
        Agent current = registry.getCurrent();
        
        if (agents.isEmpty()) {
            return CommandResult.success("暂无可用 Agents");
        }
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.CYAN + "╔════════════════════════════════════════╗" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "║" + CliLogger.BOLD + "           可用 Agents                  " + CliLogger.RESET + CliLogger.CYAN + "║" + CliLogger.RESET).append("\n");
        output.append(CliLogger.CYAN + "╚════════════════════════════════════════╝" + CliLogger.RESET).append("\n");
        output.append("\n");
        
        for (Agent agent : agents) {
            boolean isCurrent = current != null && current.getId().equals(agent.getId());
            String marker = isCurrent ? CliLogger.GREEN + "● " + CliLogger.RESET : "  ";
            
            output.append(marker + CliLogger.BOLD + agent.getId() + CliLogger.RESET).append("\n");
            output.append("    " + agent.getName()).append("\n");
            output.append("    " + agent.getDescription()).append("\n");
            
            Agent.ModelConfig modelConfig = agent.getModelConfig();
            if (modelConfig != null && modelConfig.getModel() != null) {
                output.append("    模型: " + modelConfig.getModel()).append("\n");
            }
            
            List<?> tools = agent.getTools();
            output.append("    工具: " + (tools.isEmpty() ? "无" : tools.size() + "个")).append("\n");
            output.append("\n");
        }
        
        output.append("使用 'agent show <name>' 查看详情").append("\n");
        output.append("使用 'agent switch <name>' 切换 Agent").append("\n");
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 查看 agent 详情
     */
    private CommandResult handleShow(String args) {
        if (args.isEmpty()) {
            return CommandResult.error("请指定 Agent 名称");
        }
        
        Agent agent = registry.get(args.trim());
        if (agent == null) {
            return CommandResult.error("Agent 不存在: " + args);
        }
        
        StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append(CliLogger.GREEN + "Agent: " + agent.getName() + CliLogger.RESET).append("\n");
        output.append("=" .repeat(40)).append("\n");
        output.append("\n");
        output.append("ID: " + agent.getId()).append("\n");
        output.append("描述: " + agent.getDescription()).append("\n");
        
        Agent.ModelConfig modelConfig = agent.getModelConfig();
        if (modelConfig != null) {
            output.append("模型: " + (modelConfig.getModel() != null ? modelConfig.getModel() : "默认")).append("\n");
            output.append("温度: " + (modelConfig.getTemperature() != null ? modelConfig.getTemperature() : "默认")).append("\n");
            output.append("Max Tokens: " + (modelConfig.getMaxTokens() != null ? modelConfig.getMaxTokens() : "默认")).append("\n");
        }
        
        output.append("\n");
        List<?> tools = agent.getTools();
        output.append("可用工具 (" + tools.size() + "): ");
        if (tools.isEmpty()) {
            output.append("无\n");
        } else {
            output.append("\n");
            for (Object tool : tools) {
                output.append("  - " + tool.getClass().getSimpleName()).append("\n");
            }
        }
        
        output.append("\n");
        output.append("系统提示词:").append("\n");
        output.append("-" .repeat(40)).append("\n");
        String systemPrompt = agent.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            // 截断过长的提示词
            if (systemPrompt.length() > 500) {
                output.append(systemPrompt.substring(0, 500)).append("...\n");
            } else {
                output.append(systemPrompt).append("\n");
            }
        } else {
            output.append("(空)\n");
        }
        output.append("-" .repeat(40)).append("\n");
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 切换到指定 agent
     */
    private CommandResult handleSwitch(String args) {
        if (args.isEmpty()) {
            return CommandResult.error("请指定 Agent 名称");
        }
        
        String agentId = args.trim();
        boolean success = registry.switchTo(agentId);
        
        if (success) {
            Agent agent = registry.get(agentId);
            return CommandResult.success(
                CliLogger.GREEN + "✓ 已切换到 Agent: " + agent.getName() + CliLogger.RESET
            );
        } else {
            return CommandResult.error("切换失败，Agent 不存在: " + agentId);
        }
    }
    
    /**
     * 显示当前 agent
     */
    private CommandResult handleCurrent() {
        Agent current = registry.getCurrent();
        if (current == null) {
            return CommandResult.success("当前没有活动的 Agent");
        }
        
        return CommandResult.success(
            "当前 Agent: " + CliLogger.BOLD + current.getName() + CliLogger.RESET + 
            " (" + current.getId() + ")"
        );
    }
}
