package com.jwcode.core.config;

import java.util.*;

/**
 * 配置模板
 * 预定义常用配置模板，用于快速初始化配置
 */
public class ConfigTemplate {
    
    private final String name;
    private final String description;
    private final List<TemplateItem<?>> items;
    private final Map<String, String> metadata;
    
    public ConfigTemplate(String name, String description) {
        this.name = name;
        this.description = description;
        this.items = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    /**
     * 模板配置项
     */
    public static class TemplateItem<T> {
        private final String key;
        private final T defaultValue;
        private final ConfigScope scope;
        private final String description;
        private final Class<T> type;
        private final boolean required;
        
        public TemplateItem(String key, T defaultValue, ConfigScope scope, 
                           String description, Class<T> type, boolean required) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.scope = scope != null ? scope : ConfigScope.USER;
            this.description = description;
            this.type = type;
            this.required = required;
        }
        
        public String getKey() { return key; }
        public T getDefaultValue() { return defaultValue; }
        public ConfigScope getScope() { return scope; }
        public String getDescription() { return description; }
        public Class<T> getType() { return type; }
        public boolean isRequired() { return required; }
    }
    
    // ==================== 构建器方法 ====================
    
    /**
     * 添加字符串配置项
     */
    public ConfigTemplate addString(String key, String defaultValue, ConfigScope scope, 
                                     String description, boolean required) {
        items.add(new TemplateItem<>(key, defaultValue, scope, description, String.class, required));
        return this;
    }
    
    /**
     * 添加字符串配置项（简化版）
     */
    public ConfigTemplate addString(String key, String defaultValue, String description) {
        return addString(key, defaultValue, ConfigScope.USER, description, false);
    }
    
    /**
     * 添加整数配置项
     */
    public ConfigTemplate addInt(String key, Integer defaultValue, ConfigScope scope, 
                                  String description, boolean required) {
        items.add(new TemplateItem<>(key, defaultValue, scope, description, Integer.class, required));
        return this;
    }
    
    /**
     * 添加整数配置项（简化版）
     */
    public ConfigTemplate addInt(String key, Integer defaultValue, String description) {
        return addInt(key, defaultValue, ConfigScope.USER, description, false);
    }
    
    /**
     * 添加布尔配置项
     */
    public ConfigTemplate addBoolean(String key, Boolean defaultValue, ConfigScope scope, 
                                      String description, boolean required) {
        items.add(new TemplateItem<>(key, defaultValue, scope, description, Boolean.class, required));
        return this;
    }
    
    /**
     * 添加布尔配置项（简化版）
     */
    public ConfigTemplate addBoolean(String key, Boolean defaultValue, String description) {
        return addBoolean(key, defaultValue, ConfigScope.USER, description, false);
    }
    
    /**
     * 添加双精度浮点数配置项
     */
    public ConfigTemplate addDouble(String key, Double defaultValue, ConfigScope scope, 
                                     String description, boolean required) {
        items.add(new TemplateItem<>(key, defaultValue, scope, description, Double.class, required));
        return this;
    }
    
    /**
     * 添加元数据
     */
    public ConfigTemplate withMetadata(String key, String value) {
        metadata.put(key, value);
        return this;
    }
    
    // ==================== 应用模板 ====================
    
    /**
     * 将模板应用到配置管理器
     * @param manager 配置管理器
     * @param overwrite 是否覆盖现有配置
     */
    public void apply(ConfigManager manager, boolean overwrite) {
        for (TemplateItem<?> item : items) {
            String existingValue = manager.get(item.getKey(), item.getScope());
            if (!overwrite && existingValue != null) {
                continue; // 跳过现有配置
            }
            
            // 使用默认值或必需提示
            if (item.getDefaultValue() != null) {
                manager.set(item.getKey(), item.getDefaultValue().toString(), item.getScope());
            } else if (item.isRequired()) {
                // 对于必需项但没有默认值的情况，设置空值作为占位
                manager.set(item.getKey(), "", item.getScope());
            }
        }
    }
    
    /**
     * 应用模板（默认不覆盖）
     */
    public void apply(ConfigManager manager) {
        apply(manager, false);
    }
    
    /**
     * 获取缺失的必需配置
     */
    public List<String> getMissingRequired(ConfigManager manager) {
        List<String> missing = new ArrayList<>();
        for (TemplateItem<?> item : items) {
            if (item.isRequired() && manager.get(item.getKey()) == null) {
                missing.add(item.getKey());
            }
        }
        return missing;
    }
    
    // ==================== 预定义模板 ====================
    
    /**
     * 创建默认模板
     */
    public static ConfigTemplate defaultTemplate() {
        return new ConfigTemplate("default", "JWCode 默认配置模板")
            .addString("default-provider", "moonshot", 
                "默认使用的 AI 提供商 (moonshot, openai, anthropic 等)")
            .addString("log-level", "INFO", 
                "日志级别 (DEBUG, INFO, WARN, ERROR)")
            .addInt("timeout-seconds", 60, 
                "请求超时时间（秒）")
            .addInt("max-retries", 3, 
                "最大重试次数")
            .addBoolean("debug", false, 
                "是否启用调试模式")
            .addBoolean("auto-approve", false, ConfigScope.USER,
                "是否自动批准低风险操作", false)
            .addString("theme", "dark", ConfigScope.USER,
                "界面主题 (dark, light, system)", false);
    }
    
    /**
     * 创建 AI 提供商配置模板
     */
    public static ConfigTemplate aiProviderTemplate() {
        return new ConfigTemplate("ai-provider", "AI 提供商配置模板")
            .addString("moonshot.api-key", "", ConfigScope.USER,
                "Moonshot API Key", true)
            .addString("moonshot.base-url", "https://api.moonshot.cn", ConfigScope.USER,
                "Moonshot API 基础 URL", false)
            .addString("moonshot.default-model", "kimi-k2.5", ConfigScope.USER,
                "默认使用的 Moonshot 模型", false)
            
            .addString("openai.api-key", "", ConfigScope.USER,
                "OpenAI API Key", false)
            .addString("openai.base-url", "https://api.openai.com/v1", ConfigScope.USER,
                "OpenAI API 基础 URL", false)
            
            .addString("anthropic.api-key", "", ConfigScope.USER,
                "Anthropic API Key", false)
            .addString("anthropic.base-url", "https://api.anthropic.com", ConfigScope.USER,
                "Anthropic API 基础 URL", false)
            
            .addBoolean("key-rotation.enabled", true, ConfigScope.USER,
                "是否启用 API Key 轮换", false)
            .addInt("key-rotation.max-retries", 3, ConfigScope.USER,
                "Key 轮换最大重试次数", false);
    }
    
    /**
     * 创建项目配置模板
     */
    public static ConfigTemplate projectTemplate() {
        return new ConfigTemplate("project", "项目级配置模板")
            .addString("project.name", "", ConfigScope.PROJECT,
                "项目名称", false)
            .addString("project.description", "", ConfigScope.PROJECT,
                "项目描述", false)
            .addString("project.language", "java", ConfigScope.PROJECT,
                "项目主要编程语言", false)
            .addBoolean("project.auto-save", true, ConfigScope.PROJECT,
                "是否启用自动保存", false)
            .addString("project.codestyle", "google", ConfigScope.PROJECT,
                "代码风格 (google, standard, custom)", false)
            .addBoolean("project.use-lsp", true, ConfigScope.PROJECT,
                "是否启用 LSP 支持", false);
    }
    
    /**
     * 创建 MCP 配置模板
     */
    public static ConfigTemplate mcpTemplate() {
        return new ConfigTemplate("mcp", "MCP (Model Context Protocol) 配置模板")
            .addBoolean("mcp.enabled", true, ConfigScope.USER,
                "是否启用 MCP", false)
            .addString("mcp.transport", "stdio", ConfigScope.USER,
                "MCP 传输类型 (stdio, http, websocket)", false)
            .addInt("mcp.timeout-seconds", 30, ConfigScope.USER,
                "MCP 请求超时时间", false)
            .addBoolean("mcp.auto-connect", true, ConfigScope.USER,
                "是否自动连接 MCP 服务器", false)
            .addString("mcp.servers.config-path", "", ConfigScope.USER,
                "MCP 服务器配置文件路径", false);
    }
    
    /**
     * 创建团队配置模板
     */
    public static ConfigTemplate teamTemplate() {
        return new ConfigTemplate("team", "团队协作配置模板")
            .addString("team.name", "", ConfigScope.PROJECT,
                "团队名称", false)
            .addString("team.lead", "", ConfigScope.PROJECT,
                "团队负责人", false)
            .addBoolean("team.code-review.required", true, ConfigScope.PROJECT,
                "是否需要代码审查", false)
            .addInt("team.code-review.min-reviewers", 1, ConfigScope.PROJECT,
                "最少审查人数", false)
            .addBoolean("team.auto-format", true, ConfigScope.PROJECT,
                "是否自动格式化代码", false);
    }
    
    /**
     * 获取所有预定义模板
     */
    public static List<ConfigTemplate> getAllTemplates() {
        return Arrays.asList(
            defaultTemplate(),
            aiProviderTemplate(),
            projectTemplate(),
            mcpTemplate(),
            teamTemplate()
        );
    }
    
    /**
     * 通过名称查找模板
     */
    public static ConfigTemplate findByName(String name) {
        return getAllTemplates().stream()
            .filter(t -> t.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
    
    // ==================== Getter ====================
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<TemplateItem<?>> getItems() {
        return new ArrayList<>(items);
    }
    
    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * 生成配置示例
     */
    public String generateExample() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(name).append("\n");
        sb.append("# ").append(description).append("\n\n");
        
        for (TemplateItem<?> item : items) {
            sb.append("# ").append(item.getDescription()).append("\n");
            sb.append("# Scope: ").append(item.getScope()).append(", Type: ").append(item.getType().getSimpleName());
            if (item.isRequired()) {
                sb.append(" [REQUIRED]");
            }
            sb.append("\n");
            
            if (item.getDefaultValue() != null) {
                sb.append(item.getKey()).append(": ").append(item.getDefaultValue()).append("\n\n");
            } else {
                sb.append("# ").append(item.getKey()).append(": <value>\n\n");
            }
        }
        
        return sb.toString();
    }
}
