package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.config.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 增强的 ConfigTool - 配置管理工具
 * 
 * 功能说明：
 * - 管理应用程序配置，支持获取、设置、删除配置项
 * - 支持 USER、PROJECT、SYSTEM、RUNTIME 四个作用域
 * - 显示配置继承链
 * - 配置验证
 * - 配置导出功能
 * 
 * 上下文关系：
 * - 被 QueryEngine 调用
 * - 使用增强的 ConfigManager 管理配置
 * - 支持配置继承和作用域优先级
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ConfigTool implements Tool<ConfigTool.Input, ConfigTool.Output, ConfigTool.Progress> {
    
    public static final String NAME = "Config";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final ConfigManager configManager;
    private final ConfigValidator validator;
    
    public ConfigTool() {
        this.configManager = ConfigManager.getInstance();
        this.validator = new ConfigValidator();
        
        // 设置验证器
        this.configManager.setValidator(validator);
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getDescription() {
        return "管理应用程序配置。支持获取、设置、删除、列举配置项，支持多级作用域和配置继承。";
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
                 - "chain": 显示配置继承链
                 - "validate": 验证配置
                 - "export": 导出配置
                 - "template": 应用配置模板
               - key: 配置键（get/set/delete/chain 时必需）
                 支持点分路径，如: server.port, database.connection.timeout
               - value: 配置值（set 时必需）
               - scope: 配置作用域（可选，默认 "user"）
                 - "system": 系统级配置（/etc/jwcode/ 或 %ProgramData%/jwcode/）
                 - "user": 用户级配置（~/.jwcode/）
                 - "project": 项目级配置（./.jwcode/）
                 - "runtime": 运行时配置（内存中，不持久化）
               - format: 导出格式（export 时使用，默认 "yaml"）
                 - "json": JSON 格式
                 - "yaml": YAML 格式
                 - "properties": Java Properties 格式
                 - "env": Shell 环境变量格式
               - template: 模板名称（template 时使用）
                 - "default": 默认配置
                 - "ai-provider": AI 提供商配置
                 - "project": 项目配置
                 - "mcp": MCP 配置
                 - "team": 团队配置
               
               配置继承顺序（优先级从高到低）:
               RUNTIME → PROJECT → USER → SYSTEM
               
               示例:
               - {"action": "get", "key": "api.endpoint"} - 获取 API 端点配置
               - {"action": "get", "key": "api.endpoint", "scope": "user"} - 获取用户级配置
               - {"action": "set", "key": "theme", "value": "dark", "scope": "user"} - 设置用户主题
               - {"action": "set", "key": "project.name", "value": "myapp", "scope": "project"} - 设置项目名称
               - {"action": "delete", "key": "temp.value", "scope": "runtime"} - 删除运行时配置
               - {"action": "list", "scope": "project"} - 列举项目配置
               - {"action": "chain", "key": "api.endpoint"} - 显示配置继承链
               - {"action": "validate"} - 验证所有配置
               - {"action": "export", "scope": "user", "format": "yaml", "path": "/tmp/config.yaml"} - 导出配置
               - {"action": "template", "template": "ai-provider"} - 应用 AI 提供商模板
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
                     "enum": ["get", "set", "delete", "list", "chain", "validate", "export", "template"],
                     "description": "操作类型"
                   },
                   "key": {
                     "type": "string",
                     "description": "配置键，支持点分路径如 server.port"
                   },
                   "value": {
                     "type": ["string", "number", "boolean", "object"],
                     "description": "配置值，用于 set 操作"
                   },
                   "scope": {
                     "type": "string",
                     "enum": ["system", "user", "project", "runtime"],
                     "description": "配置作用域，默认 user"
                   },
                   "format": {
                     "type": "string",
                     "enum": ["json", "yaml", "properties", "env"],
                     "description": "导出格式，用于 export 操作"
                   },
                   "path": {
                     "type": "string",
                     "description": "导出文件路径，用于 export 操作"
                   },
                   "template": {
                     "type": "string",
                     "enum": ["default", "ai-provider", "project", "mcp", "team"],
                     "description": "模板名称，用于 template 操作"
                   },
                   "overwrite": {
                     "type": "boolean",
                     "description": "是否覆盖现有配置，用于 template 操作"
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
                ConfigScope scope = parseScope(args.scope);
                
                switch (args.action) {
                    case "get" -> handleGet(args, scope, output);
                    case "set" -> handleSet(args, scope, output);
                    case "delete" -> handleDelete(args, scope, output);
                    case "list" -> handleList(args, scope, output);
                    case "chain" -> handleChain(args, output);
                    case "validate" -> handleValidate(output);
                    case "export" -> handleExport(args, scope, output);
                    case "template" -> handleTemplate(args, output);
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
    
    private ConfigScope parseScope(String scopeStr) {
        if (scopeStr == null || scopeStr.isEmpty()) {
            return ConfigScope.USER;
        }
        ConfigScope scope = ConfigScope.fromName(scopeStr);
        return scope != null ? scope : ConfigScope.USER;
    }
    
    // ==================== 操作处理 ====================
    
    private void handleGet(Input args, ConfigScope scope, Output output) {
        if (args.key == null || args.key.isEmpty()) {
            output.success = false;
            output.message = "get 操作需要指定 key 参数";
            return;
        }
        
        String value;
        ConfigScope effectiveScope;
        
        if (args.scope != null && !args.scope.isEmpty()) {
            // 获取指定作用域的值
            value = configManager.get(args.key, scope);
            effectiveScope = scope;
        } else {
            // 获取合并后的值
            value = configManager.get(args.key);
            ConfigChain chain = configManager.getConfigChain(args.key);
            effectiveScope = chain.getEffectiveScope();
        }
        
        output.key = args.key;
        output.value = value;
        output.scope = effectiveScope != null ? effectiveScope.getName() : null;
        
        if (value != null) {
            output.message = String.format("获取配置: %s = %s (scope: %s)", 
                args.key, value, output.scope != null ? output.scope : "not set");
        } else {
            output.message = String.format("配置项不存在: %s", args.key);
        }
    }
    
    private void handleSet(Input args, ConfigScope scope, Output output) {
        if (args.key == null || args.key.isEmpty()) {
            output.success = false;
            output.message = "set 操作需要指定 key 参数";
            return;
        }
        
        String value = args.value != null ? args.value.toString() : "";
        
        // 验证键名格式
        if (!validator.isValidKey(args.key)) {
            output.success = false;
            output.message = "无效的键名格式: " + args.key;
            return;
        }
        
        // 敏感配置检查
        if (validator.isSensitiveKey(args.key) && scope == ConfigScope.PROJECT) {
            output.warning = "警告: 敏感配置不建议存储在项目级配置中";
        }
        
        configManager.set(args.key, value, scope);
        
        output.key = args.key;
        output.value = value;
        output.scope = scope.getName();
        output.message = String.format("设置配置: %s = %s (scope: %s)", args.key, value, scope.getName());
        
        if (output.warning != null) {
            output.message += "\n" + output.warning;
        }
    }
    
    private void handleDelete(Input args, ConfigScope scope, Output output) {
        if (args.key == null || args.key.isEmpty()) {
            output.success = false;
            output.message = "delete 操作需要指定 key 参数";
            return;
        }
        
        if (args.scope != null && !args.scope.isEmpty()) {
            // 删除指定作用域的配置
            configManager.delete(args.key, scope);
            output.message = String.format("删除配置: %s (scope: %s)", args.key, scope.getName());
        } else {
            // 删除所有作用域的配置
            configManager.delete(args.key);
            output.message = String.format("删除配置: %s (所有作用域)", args.key);
        }
        
        output.key = args.key;
    }
    
    private void handleList(Input args, ConfigScope scope, Output output) {
        Map<String, String> configs;
        
        if (args.scope != null && !args.scope.isEmpty()) {
            // 列出指定作用域的配置
            configs = configManager.getAll(scope);
            output.scope = scope.getName();
        } else {
            // 列出所有合并后的配置
            configs = configManager.getAll();
            output.scope = "all";
        }
        
        output.config = new HashMap<>(configs);
        output.message = String.format("共 %d 条配置项", configs.size());
        
        // 添加统计信息
        output.stats = configManager.getStats();
    }
    
    private void handleChain(Input args, Output output) {
        if (args.key == null || args.key.isEmpty()) {
            output.success = false;
            output.message = "chain 操作需要指定 key 参数";
            return;
        }
        
        ConfigChain chain = configManager.getConfigChain(args.key);
        
        output.key = args.key;
        output.value = chain.getEffectiveValue();
        output.scope = chain.getEffectiveScope() != null ? chain.getEffectiveScope().getName() : null;
        output.chain = chain.getEntries().stream()
            .map(e -> Map.of(
                "scope", e.getScope().getName(),
                "value", e.getValue() != null ? e.getValue() : "(not set)",
                "source", e.getSource(),
                "present", String.valueOf(e.isPresent())
            ))
            .collect(Collectors.toList());
        output.message = chain.formatChain();
    }
    
    private void handleValidate(Output output) {
        ConfigValidator.ValidationReport report = configManager.validate();
        
        output.success = report.isValid();
        output.message = report.toString();
        
        if (!report.isValid()) {
            output.errors = report.getErrors().stream()
                .map(e -> Map.of(
                    "key", e.getKey() != null ? e.getKey() : "",
                    "message", e.getMessage()
                ))
                .collect(Collectors.toList());
        }
    }
    
    private void handleExport(Input args, ConfigScope scope, Output output) throws Exception {
        ConfigExportFormat format = ConfigExportFormat.valueOf(
            args.format != null ? args.format.toUpperCase() : "YAML"
        );
        
        String path = args.path;
        if (path == null || path.isEmpty()) {
            path = "./jwcode-config-export." + format.getExtension();
        }
        
        Path exportPath = Paths.get(path);
        configManager.exportConfig(
            args.scope != null ? scope : null,
            format,
            exportPath
        );
        
        output.message = String.format("配置已导出到: %s", exportPath.toAbsolutePath());
        output.value = exportPath.toAbsolutePath().toString();
    }
    
    private void handleTemplate(Input args, Output output) {
        String templateName = args.template != null ? args.template : "default";
        ConfigTemplate template = ConfigTemplate.findByName(templateName);
        
        if (template == null) {
            output.success = false;
            output.message = "未知的模板: " + templateName;
            return;
        }
        
        boolean overwrite = args.overwrite != null && args.overwrite;
        configManager.applyTemplate(template, overwrite);
        
        output.message = String.format("已应用模板: %s (%s)", template.getName(), template.getDescription());
        output.value = template.generateExample();
    }
    
    // ==================== 工具接口实现 ====================
    
    @Override
    public boolean isConcurrencySafe(Input input) {
        return true;
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        if (input == null || input.action == null) {
            return true;
        }
        return input.action.equals("get") || input.action.equals("list") || 
               input.action.equals("chain") || input.action.equals("validate");
    }
    
    @Override
    public boolean isDestructive(Input input) {
        return input != null && "delete".equals(input.action);
    }
    
    @Override
    public boolean requiresApproval(Input input) {
        return isDestructive(input);
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        if (input.action == null) {
            return ToolValidationResult.invalid("action 参数不能为空");
        }
        
        // 验证操作类型
        List<String> validActions = Arrays.asList("get", "set", "delete", "list", "chain", "validate", "export", "template");
        if (!validActions.contains(input.action)) {
            return ToolValidationResult.invalid("无效的操作类型: " + input.action);
        }
        
        // 验证必需参数
        if ((input.action.equals("get") || input.action.equals("delete") || input.action.equals("chain")) 
                && (input.key == null || input.key.isEmpty())) {
            return ToolValidationResult.invalid("'" + input.action + "' 操作需要指定 key 参数");
        }
        
        if (input.action.equals("set") && (input.key == null || input.key.isEmpty())) {
            return ToolValidationResult.invalid("'set' 操作需要指定 key 参数");
        }
        
        // 验证作用域
        if (input.scope != null && !input.scope.isEmpty()) {
            if (ConfigScope.fromName(input.scope) == null) {
                return ToolValidationResult.invalid("无效的作用域: " + input.scope);
            }
        }
        
        return ToolValidationResult.valid();
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 输入类
     */
    public static class Input {
        public String action;
        public String key;
        public Object value;
        public String scope;
        public String format;
        public String path;
        public String template;
        public Boolean overwrite;
    }
    
    /**
     * 输出类
     */
    public static class Output {
        public String action;
        public boolean success;
        public String message;
        public String key;
        public Object value;
        public String scope;
        public String warning;
        public Map<String, String> config;
        public List<Map<String, String>> chain;
        public List<Map<String, String>> errors;
        public Map<String, Integer> stats;
    }
    
    /**
     * 进度类
     */
    public static class Progress {
        public String phase;
        public int progress;
    }
}
