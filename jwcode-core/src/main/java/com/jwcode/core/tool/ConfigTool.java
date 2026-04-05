package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConfigTool - 配置管理工具
 * 
 * 功能说明：
 * 管理应用程序配置，支持获取、设置、删除配置项。
 * 支持用户配置和项目配置两个层级。
 * 
 * 上下文关系：
 * - 被 QueryEngine 调用
 * - 使用 ConfigLoader 加载和保存配置
 * - 被/config 命令使用
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ConfigTool implements Tool<ConfigTool.Input, ConfigTool.Output, ConfigTool.Progress> {
    
    public static final String NAME = "Config";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final Map<String, Object> configStore;
    
    public ConfigTool() {
        this.configStore = new ConcurrentHashMap<>();
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getDescription() {
        return "管理应用程序配置。支持获取、设置、删除、列举配置项。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 Config 工具管理应用程序配置。
               
               参数:
               - action: 操作类型（必需）
                 - "get": 获取指定配置项的值
                 - "set": 设置配置项的值
                 - "delete": 删除配置项
                 - "list": 列举所有配置项
               - key: 配置键（get/set/delete 时必需）
               - value: 配置值（set 时必需，JSON 对象格式）
               - scope: 配置作用域（可选，默认 "user"）
                 - "user": 用户级配置（全局）
                 - "project": 项目级配置（当前项目）
               
               示例:
               - {"action": "get", "key": "api.endpoint"} - 获取 API 端点配置
               - {"action": "set", "key": "theme", "value": {"mode": "dark"}} - 设置主题
               - {"action": "list", "scope": "project"} - 列举项目配置
               - {"action": "delete", "key": "temp.value"} - 删除配置项
               """;
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<Input>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<Output>() {};
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            String schema = """
               {
                 "type": "object",
                 "properties": {
                   "action": {
                     "type": "string",
                     "enum": ["get", "set", "delete", "list"],
                     "description": "操作类型: get(获取), set(设置), delete(删除), list(列举)"
                   },
                   "key": {
                     "type": "string",
                     "description": "配置键，用于 get/set/delete 操作"
                   },
                   "value": {
                     "type": "object",
                     "description": "配置值，用于 set 操作（JSON 对象格式）"
                   },
                   "scope": {
                     "type": "string",
                     "enum": ["user", "project"],
                     "description": "配置作用域: user(用户级), project(项目级)，默认 user"
                   }
                 },
                 "required": ["action"]
               }
               """;
            return MAPPER.readTree(schema);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input args,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            Output output = new Output();
            output.action = args.action;
            output.success = true;
            
            try {
                switch (args.action) {
                    case "get" -> {
                        output.value = configStore.get(args.key);
                        output.message = "获取配置: " + args.key + " = " + output.value;
                    }
                    case "set" -> {
                        configStore.put(args.key, args.value);
                        output.message = "设置配置: " + args.key;
                    }
                    case "delete" -> {
                        configStore.remove(args.key);
                        output.message = "删除配置: " + args.key;
                    }
                    case "list" -> {
                        output.config = new java.util.HashMap<>(configStore);
                        output.message = "列出所有配置";
                    }
                    default -> {
                        output.success = false;
                        output.message = "未知操作: " + args.action;
                    }
                }
            } catch (Exception e) {
                output.success = false;
                output.message = "操作失败: " + e.getMessage();
            }
            
            return ToolResult.<Output>builder().data(output).build();
        });
    }
    
    @Override
    public boolean isConcurrencySafe(Input input) {
        return true;
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return input.action == null || input.action.equals("get") || input.action.equals("list");
    }
    
    /**
     * 输入类
     */
    public static class Input {
        public String action;
        public String key;
        public Object value;
        public String scope;
    }
    
    /**
     * 输出类
     */
    public static class Output {
        public String action;
        public boolean success;
        public String message;
        public Object value;
        public Map<String, Object> config;
    }
    
    /**
     * 进度类
     */
    public static class Progress {
    }
}
