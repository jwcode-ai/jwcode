package com.jwcode.core.command;

import com.jwcode.core.mcp.McpConnectionManager;
import com.jwcode.core.session.Session;

/** /mcp - show MCP server connection status. */
public class McpCommand implements Command {
    @Override public String getName() { return "mcp"; }
    @Override public String getDescription() { return "Show MCP server status"; }
    @Override public String getUsage() { return "mcp"; }
    @Override public String getCategory() { return "config"; }
    @Override public CommandSource getSource() { return CommandSource.CONFIG; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        try {
            McpConnectionManager mcpManager = new McpConnectionManager();
            var statuses = mcpManager.getAllConnectionStatuses();
            StringBuilder sb = new StringBuilder();
            sb.append("MCP Server Status:\n");
            for (var entry : statuses.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            if (statuses.isEmpty()) {
                sb.append("  (no MCP servers configured)\n");
            }
            return CommandResult.success(sb.toString());
        } catch (Exception e) {
            return CommandResult.error("MCP operation failed: " + e.getMessage());
        }
    }
}
