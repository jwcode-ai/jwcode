package com.jwcode.core.agent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * AgentConfig - Agent 配置类
 * 
 * 功能说明：
 * 支持从 JSON/YAML 文件加载 Agent 配置，实现 Agent 的可配置化。
 * 
 * 配置文件示例 (agent.yaml):
 * ```yaml
 * name: "Coding Agent"
 * description: "专业的代码编写 Agent"
 * type: "coder"
 * systemPrompt: |
 *   你是一个专业的编码助手...
 * model:
 *   name: "sonnet"
 *   temperature: 0.7
 *   maxTokens: 4096
 * tools:
 *   - FileRead
 *   - FileWrite
 *   - Bash
 *   - Grep
 * permissions:
 *   allowFileWrite: true
 *   allowShell: true
 *   maxFileSize: 1048576
 * ```
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentConfig {
    
    private String id;
    private String name;
    private String description;
    private String type = "general";
    private String version = "1.0.0";
    
    @JsonProperty("systemPrompt")
    private String systemPrompt;
    
    private ModelConfig model = new ModelConfig();
    private List<String> tools = new ArrayList<>();
    private List<String> skills = new ArrayList<>();
    private PermissionConfig permissions = new PermissionConfig();
    private Map<String, Object> metadata = new HashMap<>();
    
    // 继承关系
    private String extendsAgent;
    private List<String> overrides = new ArrayList<>();
    
    public AgentConfig() {
        this.id = UUID.randomUUID().toString();
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    
    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }
    
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
    
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    
    public PermissionConfig getPermissions() { return permissions; }
    public void setPermissions(PermissionConfig permissions) { this.permissions = permissions; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public String getExtendsAgent() { return extendsAgent; }
    public void setExtendsAgent(String extendsAgent) { this.extendsAgent = extendsAgent; }
    
    public List<String> getOverrides() { return overrides; }
    public void setOverrides(List<String> overrides) { this.overrides = overrides; }
    
    /**
     * 模型配置
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelConfig {
        private String name = "sonnet";
        private Double temperature = 0.7;
        private Integer maxTokens = 4096;
        private Double topP = 1.0;
        private Double presencePenalty = 0.0;
        private Double frequencyPenalty = 0.0;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
        
        public Integer getMaxTokens() { return maxTokens; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
        
        public Double getTopP() { return topP; }
        public void setTopP(Double topP) { this.topP = topP; }
        
        public Double getPresencePenalty() { return presencePenalty; }
        public void setPresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }
        
        public Double getFrequencyPenalty() { return frequencyPenalty; }
        public void setFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }
    }
    
    /**
     * 权限配置
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PermissionConfig {
        private boolean allowFileRead = true;
        private boolean allowFileWrite = true;
        private boolean allowFileEdit = true;
        private boolean allowShell = true;
        private boolean allowWebSearch = true;
        private boolean allowWebFetch = true;
        private long maxFileSize = 1048576; // 1MB
        private int maxShellTimeout = 300; // 5 minutes
        
        // Getters and Setters
        public boolean isAllowFileRead() { return allowFileRead; }
        public void setAllowFileRead(boolean allowFileRead) { this.allowFileRead = allowFileRead; }
        
        public boolean isAllowFileWrite() { return allowFileWrite; }
        public void setAllowFileWrite(boolean allowFileWrite) { this.allowFileWrite = allowFileWrite; }
        
        public boolean isAllowFileEdit() { return allowFileEdit; }
        public void setAllowFileEdit(boolean allowFileEdit) { this.allowFileEdit = allowFileEdit; }
        
        public boolean isAllowShell() { return allowShell; }
        public void setAllowShell(boolean allowShell) { this.allowShell = allowShell; }
        
        public boolean isAllowWebSearch() { return allowWebSearch; }
        public void setAllowWebSearch(boolean allowWebSearch) { this.allowWebSearch = allowWebSearch; }
        
        public boolean isAllowWebFetch() { return allowWebFetch; }
        public void setAllowWebFetch(boolean allowWebFetch) { this.allowWebFetch = allowWebFetch; }
        
        public long getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }
        
        public int getMaxShellTimeout() { return maxShellTimeout; }
        public void setMaxShellTimeout(int maxShellTimeout) { this.maxShellTimeout = maxShellTimeout; }
    }
    
    /**
     * 从 YAML 文件加载配置
     */
    public static AgentConfig fromYaml(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(path), AgentConfig.class);
    }
    
    /**
     * 从 YAML 文件加载配置
     */
    public static AgentConfig fromYaml(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(file, AgentConfig.class);
    }
    
    /**
     * 从 JSON 文件加载配置
     */
    public static AgentConfig fromJson(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(path), AgentConfig.class);
    }
    
    /**
     * 从 JSON 文件加载配置
     */
    public static AgentConfig fromJson(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(file, AgentConfig.class);
    }
    
    /**
     * 保存为 YAML
     */
    public void saveAsYaml(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writeValue(new File(path), this);
    }
    
    /**
     * 保存为 JSON
     */
    public void saveAsJson(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), this);
    }
    
    /**
     * 合并父配置
     */
    public AgentConfig mergeWithParent(AgentConfig parent) {
        if (this.systemPrompt == null) {
            this.systemPrompt = parent.systemPrompt;
        }
        if (this.model == null) {
            this.model = parent.model;
        }
        if (this.tools.isEmpty()) {
            this.tools = new ArrayList<>(parent.tools);
        }
        if (this.skills.isEmpty()) {
            this.skills = new ArrayList<>(parent.skills);
        }
        if (this.permissions == null) {
            this.permissions = parent.permissions;
        }
        return this;
    }
    
    /**
     * 获取默认配置目录
     */
    public static Path getDefaultConfigDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".jwcode", "agents");
    }
    
    /**
     * 创建默认配置
     */
    public static AgentConfig createDefault(String name, String type) {
        AgentConfig config = new AgentConfig();
        config.setName(name);
        config.setType(type);
        config.setDescription("Auto-generated agent configuration");
        
        // 根据类型设置默认工具
        switch (type.toLowerCase()) {
            case "coder":
            case "coding":
                config.setSystemPrompt("你是一个专业的编码助手，擅长编写、重构和优化代码。");
                config.setTools(Arrays.asList(
                    "FileRead", "FileWrite", "FileEdit", 
                    "Glob", "Grep", "Bash", "WebSearch"
                ));
                break;
            case "review":
            case "reviewer":
                config.setSystemPrompt("你是一个代码审查专家，擅长发现代码中的问题和改进建议。");
                config.setTools(Arrays.asList(
                    "FileRead", "Grep", "Glob", "WebSearch"
                ));
                break;
            case "debug":
            case "debugger":
                config.setSystemPrompt("你是一个调试专家，擅长定位和修复代码中的 bug。");
                config.setTools(Arrays.asList(
                    "FileRead", "Grep", "Bash", "WebSearch", "WebFetch"
                ));
                break;
            default:
                config.setSystemPrompt("你是一个通用的 AI 助手。");
                config.setTools(Arrays.asList(
                    "FileRead", "WebSearch", "Bash"
                ));
        }
        
        return config;
    }
    
    @Override
    public String toString() {
        return String.format("AgentConfig{id='%s', name='%s', type='%s', version='%s'}", 
            id, name, type, version);
    }
}
