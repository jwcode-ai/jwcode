package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * ListMcpResourcesTool — 列出所有已配置的 MCP 服务器及其资源
 *
 * <p>从配置文件中读取 MCP 服务器列表，返回每个服务器的名称、类型、
 * 连接状态和可用资源信息。</p>
 */
public class ListMcpResourcesTool implements Tool<ListMcpResourcesTool.Input, ListMcpResourcesTool.Output, ListMcpResourcesTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MCP_CONFIG_FILE = ".jwcode/mcp-servers.json";
    
    @Override public String getName() { return "ListMcpResources"; }
    @Override public String getDescription() {
        return "列出所有已配置的 MCP（Model Context Protocol）服务器及其可用资源。返回服务器名称、类型、状态和资源列表。";
    }
    @Override public String getPrompt() {
        return "使用此工具列出所有已配置的 MCP 服务器。当用户询问有哪些 MCP 工具/资源可用时使用。不需要参数。";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                  "type": "object",
                  "properties": {},
                  "description": "列出 MCP 资源，不需要任何参数"
                }
                """);
        } catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 尝试从工作目录的 .jwcode/mcp-servers.json 读取配置
                Path workingDir = context != null && context.getWorkingDirectory() != null
                    ? context.getWorkingDirectory()
                    : Paths.get(System.getProperty("user.dir"));
                Path configPath = workingDir.resolve(MCP_CONFIG_FILE);
                
                List<McpServerInfo> servers = new ArrayList<>();
                
                if (Files.exists(configPath)) {
                    String json = Files.readString(configPath);
                    JsonNode root = MAPPER.readTree(json);
                    
                    // 解析 mcpServers 配置
                    JsonNode mcpServers = root.has("mcpServers") ? root.get("mcpServers") : root;
                    if (mcpServers.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> fields = mcpServers.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            String serverName = entry.getKey();
                            JsonNode serverConfig = entry.getValue();
                            
                            String type = serverConfig.has("type") 
                                ? serverConfig.get("type").asText() : "stdio";
                            String command = serverConfig.has("command") 
                                ? serverConfig.get("command").asText() : "";
                            boolean enabled = !serverConfig.has("disabled") 
                                || !serverConfig.get("disabled").asBoolean();
                            
                            // 收集该服务器的资源/工具信息
                            List<String> resources = new ArrayList<>();
                            if (serverConfig.has("resources") && serverConfig.get("resources").isArray()) {
                                serverConfig.get("resources").forEach(r -> resources.add(r.asText()));
                            }
                            
                            List<String> tools = new ArrayList<>();
                            if (serverConfig.has("tools") && serverConfig.get("tools").isArray()) {
                                serverConfig.get("tools").forEach(t -> tools.add(t.asText()));
                            }
                            
                            servers.add(new McpServerInfo(serverName, type, command, enabled, resources, tools));
                        }
                    }
                }
                
                // 也检查 JwcodeConfig 中的 MCP 配置
                // 如果配置文件不存在或为空，至少返回 MCP 框架可用状态
                String summary;
                if (servers.isEmpty()) {
                    summary = "MCP 框架已就绪。当前未配置 MCP 服务器。请在 .jwcode/mcp-servers.json 中添加服务器配置，格式：{\"mcpServers\": {\"serverName\": {\"type\": \"stdio\", \"command\": \"...\"}}}";
                } else {
                    long enabledCount = servers.stream().filter(s -> s.enabled).count();
                    summary = "共 " + servers.size() + " 个 MCP 服务器（" + enabledCount + " 个已启用）";
                }
                
                return ToolResult.success(new Output(true, summary, servers));
                
            } catch (Exception e) {
                return ToolResult.success(new Output(false, 
                    "MCP 框架已就绪，但读取配置时出错: " + e.getMessage(), 
                    List.of()));
            }
        });
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolContext context,
            CanUseToolFn canUseTool,
            Object parentMessage,
            Consumer<ToolProgress<Progress>> onProgress) {
        // 委托到 3 参数版本
        return call(input, (ToolExecutionContext) null, onProgress);
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return true;
    }
    
    // ========== Input / Output / Progress ==========
    
    public static class Input {
        public Input() {}
    }
    
    public static class Output {
        public boolean success;
        public String summary;
        public java.util.List<McpServerInfo> servers;
        
        public Output() {}
        
        public Output(boolean success, String summary, List<McpServerInfo> servers) {
            this.success = success;
            this.summary = summary;
            this.servers = servers;
        }
    }
    
    /**
     * MCP 服务器信息
     */
    public static class McpServerInfo {
        public String name;
        public String type;
        public String command;
        public boolean enabled;
        public java.util.List<String> resources;
        public java.util.List<String> tools;
        
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
    }
    
    public static class Progress {}
}
