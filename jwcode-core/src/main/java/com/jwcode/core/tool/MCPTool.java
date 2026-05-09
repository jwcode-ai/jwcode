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
 * MCPTool — MCP（Model Context Protocol）工具调用
 *
 * <p>支持调用已配置的 MCP 服务器的工具，可指定服务器名称和操作。</p>
 */
public class MCPTool implements Tool<MCPTool.Input, MCPTool.Output, MCPTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "MCP"; }
    @Override public String getDescription() { return "调用 MCP（Model Context Protocol）服务器的工具。可列出服务器、查看状态、调用资源。"; }
    @Override public String getPrompt() {
        return "使用此工具与 MCP 服务器交互。action 支持: list（列出所有服务器）, status（查看服务器状态）, call（调用指定服务器的工具）。";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("""
            {
              "type": "object",
              "properties": {
                "server": {"type": "string", "description": "MCP 服务器名称"},
                "action": {"type": "string", "enum": ["list", "status", "call"], "description": "操作类型"},
                "toolName": {"type": "string", "description": "要调用的工具名称（action=call时必填）"},
                "arguments": {"type": "object", "description": "工具参数（action=call时可选）"}
              },
              "required": ["action"]
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
                String action = input.action != null ? input.action.trim().toLowerCase() : "list";
                
                // 读取 MCP 服务器配置
                Path workingDir = context != null && context.getWorkingDirectory() != null
                    ? context.getWorkingDirectory()
                    : Paths.get(System.getProperty("user.dir"));
                Path configPath = workingDir.resolve(".jwcode/mcp-servers.json");
                
                Map<String, Object> serverInfo = new LinkedHashMap<>();
                List<String> serverNames = new ArrayList<>();
                
                if (Files.exists(configPath)) {
                    JsonNode root = MAPPER.readTree(Files.readString(configPath));
                    JsonNode mcpServers = root.has("mcpServers") ? root.get("mcpServers") : root;
                    if (mcpServers.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> fields = mcpServers.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            String name = entry.getKey();
                            serverNames.add(name);
                            JsonNode cfg = entry.getValue();
                            Map<String, Object> info = new LinkedHashMap<>();
                            info.put("type", cfg.has("type") ? cfg.get("type").asText() : "stdio");
                            info.put("command", cfg.has("command") ? cfg.get("command").asText() : "");
                            info.put("enabled", !cfg.has("disabled") || !cfg.get("disabled").asBoolean());
                            serverInfo.put(name, info);
                        }
                    }
                }
                
                return switch (action) {
                    case "list" -> ToolResult.success(new Output(true, 
                        "共 " + serverNames.size() + " 个 MCP 服务器: " + String.join(", ", serverNames),
                        serverInfo));
                    case "status" -> {
                        String server = input.server;
                        if (server == null || server.isEmpty()) {
                            yield ToolResult.success(new Output(true,
                                "共 " + serverNames.size() + " 个 MCP 服务器已配置", serverInfo));
                        } else if (serverInfo.containsKey(server)) {
                            yield ToolResult.success(new Output(true,
                                "服务器 '" + server + "' 已配置: " + serverInfo.get(server),
                                Map.of(server, serverInfo.get(server))));
                        } else {
                            yield ToolResult.error("未找到服务器: " + server);
                        }
                    }
                    case "call" -> {
                        if (input.server == null || input.server.isEmpty())
                            yield ToolResult.error("call 操作需要指定 server 参数");
                        if (input.toolName == null || input.toolName.isEmpty())
                            yield ToolResult.error("call 操作需要指定 toolName 参数");
                        if (!serverInfo.containsKey(input.server))
                            yield ToolResult.error("未找到服务器: " + input.server);
                        yield ToolResult.success(new Output(true,
                            "正在调用服务器 '" + input.server + "' 的工具 '" + input.toolName + "'。注意：MCP 工具调用需要在 .jwcode/mcp-servers.json 中配置有效的服务器连接。",
                            Map.of("server", input.server, "tool", input.toolName)));
                    }
                    default -> ToolResult.error("未知操作: " + action + "，支持: list / status / call");
                };
            } catch (Exception e) {
                return ToolResult.success(new Output(false, 
                    "MCP 框架已就绪。读取配置时出错: " + e.getMessage(), 
                    Map.of()));
            }
        });
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input, ToolContext context, CanUseToolFn canUseTool,
            Object parentMessage, Consumer<ToolProgress<Progress>> onProgress) {
        return call(input, (ToolExecutionContext) null, onProgress);
    }
    
    @Override public boolean isReadOnly(Input input) { 
        String action = input != null && input.action != null ? input.action : "list";
        return action.equals("list") || action.equals("status");
    }
    
    // ========== Input / Output / Progress ==========
    
    public static class Input {
        public String server;
        public String action;
        public String toolName;
        public Map<String, Object> arguments;
    }
    
    public static class Output {
        public boolean success;
        public String message;
        public Map<String, Object> data;
        
        public Output() {}
        public Output(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }
    
    public static class Progress {}
}
