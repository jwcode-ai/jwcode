package com.jwcode.core.mcp.model;

import java.util.List;

/**
 * MCP 服务器信息模型
 */
public class McpServerInfo {
    private String name;
    private String type;
    private String command;
    private boolean enabled;
    private List<String> resources;
    private List<String> tools;

    public McpServerInfo() {}

    public McpServerInfo(String name, String type, String command, boolean enabled,
                         List<String> resources, List<String> tools) {
        this.name = name;
        this.type = type;
        this.command = command;
        this.enabled = enabled;
        this.resources = resources;
        this.tools = tools;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getResources() { return resources; }
    public void setResources(List<String> resources) { this.resources = resources; }

    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
}
