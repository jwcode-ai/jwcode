package com.jwcode.core.command;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;

/** /agents - list available agents. */
public class AgentsCommand implements Command {
    private final ToolRegistry toolRegistry;

    public AgentsCommand(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override public String getName() { return "agents"; }
    @Override public String getDescription() { return "List available agents"; }
    @Override public String getUsage() { return "agents"; }
    @Override public String getCategory() { return "config"; }
    @Override public CommandSource getSource() { return CommandSource.CONFIG; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        try {
            AgentRegistry registry = new AgentRegistry(toolRegistry);
            var agents = registry.getAll();
            StringBuilder sb = new StringBuilder();
            sb.append("Available Agents (").append(agents.size()).append("):\n");
            for (var agent : agents) {
                sb.append("  ").append(agent.getName()).append(" - ").append(agent.getDescription()).append("\n");
            }
            if (agents.isEmpty()) {
                sb.append("  (no agents configured)\n");
            }
            return CommandResult.success(sb.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to list agents: " + e.getMessage());
        }
    }
}
