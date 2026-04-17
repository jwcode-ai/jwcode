package com.jwcode.core.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 增强的配置管理器 - 管理多级配置继承和作用域支持
 * 
 * 配置文件位置（按优先级从低到高）:
 * - System: /etc/jwcode/config.yaml (Linux/Mac) 或 %ProgramData%/jwcode/config.yaml (Windows)
 * - User: ~/.jwcode/config.yaml
 * - Project: ./.jwcode/config.yaml (当前工作目录)
 * - Runtime: 内存中的临时配置
 * 
 * 查找顺序: Runtime → Project → User → System
 */
public class ConfigManager {
    
    private static final String CONFIG_DIR = ".jwcode";
    private static final String CONFIG_FILE_YAML = "config.yaml";
    private static final String CONFIG_FILE_JSON = "config.json";
    
    private static ConfigManager instance;
    
    // 各作用域的配置存储
    private final ConfigScopeConfig systemConfig;
    private final ConfigScopeConfig userConfig;
    private final ConfigScopeConfig projectConfig;
    private final ConfigScopeConfig runtimeConfig;
    
    // 配置路径
    private Path systemConfigPath;
    private Path userConfigPath;
    private Path projectConfigPath;
    
    // JSON/YAML 解析器
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    
    // 验证器
    private ConfigValidator validator;
    
    private ConfigManager() {
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        YAMLFactory yamlFactory = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();
        this.yamlMapper = new ObjectMapper(yamlFactory);
        
        // 初始化各作用域配置
        this.systemConfig = new ConfigScopeConfig(ConfigScope.SYSTEM, null);
        this.userConfig = new ConfigScopeConfig(ConfigScope.USER, null);
        this.projectConfig = new ConfigScopeConfig(ConfigScope.PROJECT, null);
        this.runtimeConfig = new ConfigScopeConfig(ConfigScope.RUNTIME, null);
        
        initPaths();
        loadAllConfigs();
    }
    
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    /**
     * 创建新实例（用于测试）
     */
    public static ConfigManager createNew() {
        return new ConfigManager();
    }
    
    // ==================== 路径初始化 ====================
    
    private void initPaths() {
        // 系统级配置路径
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String programData = System.getenv("ProgramData");
            if (programData != null) {
                systemConfigPath = Paths.get(programData, "jwcode", CONFIG_FILE_YAML);
            }
        } else {
            systemConfigPath = Paths.get("/etc", "jwcode", CONFIG_FILE_YAML);
        }
        
        // 用户级配置路径
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            userConfigPath = Paths.get(userHome, CONFIG_DIR, CONFIG_FILE_YAML);
        }
        
        // 项目级配置路径
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            projectConfigPath = Paths.get(userDir, CONFIG_DIR, CONFIG_FILE_YAML);
        }
        
        // 更新配置存储的源路径
        systemConfig.put(ConfigItem.of("_config.path", systemConfigPath != null ? systemConfigPath.toString() : "", ConfigScope.SYSTEM));
        userConfig.put(ConfigItem.of("_config.path", userConfigPath != null ? userConfigPath.toString() : "", ConfigScope.USER));
        projectConfig.put(ConfigItem.of("_config.path", projectConfigPath != null ? projectConfigPath.toString() : "", ConfigScope.PROJECT));
    }
    
    // ==================== 配置加载 ====================
    
    private void loadAllConfigs() {
        // 按优先级从低到高加载
        if (systemConfigPath != null) {
            loadFromFile(systemConfigPath, systemConfig);
        }
        if (userConfigPath != null) {
            loadFromFile(userConfigPath, userConfig);
        }
        if (projectConfigPath != null) {
            loadFromFile(projectConfigPath, projectConfig);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadFromFile(Path path, ConfigScopeConfig target) {
        if (path == null) {
            return;
        }
        
        if (!Files.exists(path)) {
            return;
        }
        
        try {
            String content = Files.readString(path);
            Map<String, Object> map;
            
            if (path.toString().endsWith(".yaml") || path.toString().endsWith(".yml")) {
                map = yamlMapper.readValue(content, new TypeReference<>() {});
            } else {
                map = jsonMapper.readValue(content, new TypeReference<>() {});
            }
            
            // 展平嵌套配置
            Map<String, String> flatMap = flattenMap(map, "");
            target.putAll(flatMap);
            
            System.out.println("配置已加载: " + path + " (" + flatMap.size() + " 项)");
            
        } catch (IOException e) {
            System.err.println("加载配置失败: " + path + " - " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("解析配置失败: " + path + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 将嵌套 Map 展平为点分键
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> flattenMap(Map<String, Object> map, String prefix) {
        Map<String, String> result = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                result.putAll(flattenMap((Map<String, Object>) value, key));
            } else if (value instanceof List) {
                result.put(key, value.toString());
            } else {
                result.put(key, value != null ? value.toString() : null);
            }
        }
        
        return result;
    }
    
    // ==================== 基本读写方法 ====================
    
    /**
     * 获取配置值（按优先级合并）
     * 查找顺序: Runtime → Project → User → System
     */
    public String get(String key) {
        // 按优先级从高到低查找
        String value = runtimeConfig.getString(key);
        if (value != null) return value;
        
        value = projectConfig.getString(key);
        if (value != null) return value;
        
        value = userConfig.getString(key);
        if (value != null) return value;
        
        return systemConfig.getString(key);
    }
    
    /**
     * 获取指定作用域的配置值
     */
    public String get(String key, ConfigScope scope) {
        return switch (scope) {
            case RUNTIME -> runtimeConfig.getString(key);
            case PROJECT -> projectConfig.getString(key);
            case USER -> userConfig.getString(key);
            case SYSTEM -> systemConfig.getString(key);
        };
    }
    
    /**
     * 获取配置项对象（包含元数据）
     */
    public ConfigItem<?> getConfigItem(String key) {
        // 按优先级查找第一个存在的配置项
        for (ConfigScope scope : ConfigScope.getSortedByPriorityDesc()) {
            ConfigItem<?> item = getScopeConfig(scope).get(key);
            if (item != null) {
                return item;
            }
        }
        return null;
    }
    
    /**
     * 获取指定作用域的配置项
     */
    public ConfigItem<?> getConfigItem(String key, ConfigScope scope) {
        return getScopeConfig(scope).get(key);
    }
    
    /**
     * 设置配置值（默认保存到 USER 作用域）
     */
    public void set(String key, String value) {
        set(key, value, ConfigScope.USER);
    }
    
    /**
     * 设置指定作用域的配置值
     */
    public void set(String key, String value, ConfigScope scope) {
        ConfigItem<String> item = ConfigItem.of(key, value, scope);
        
        // 验证
        if (validator != null) {
            ConfigValidator.ValidationResult result = validator.validate(item);
            if (!result.isValid()) {
                throw new IllegalArgumentException("Validation failed: " + result.getMessage());
            }
        }
        
        // 根据作用域存储
        switch (scope) {
            case RUNTIME -> runtimeConfig.put(item);
            case PROJECT -> {
                projectConfig.put(item);
                saveScopeConfig(ConfigScope.PROJECT);
            }
            case USER -> {
                userConfig.put(item);
                saveScopeConfig(ConfigScope.USER);
            }
            case SYSTEM -> {
                systemConfig.put(item);
                saveScopeConfig(ConfigScope.SYSTEM);
            }
        }
    }
    
    /**
     * 删除配置（所有作用域）
     */
    public void delete(String key) {
        runtimeConfig.remove(key);
        projectConfig.remove(key);
        userConfig.remove(key);
        systemConfig.remove(key);
        
        saveScopeConfig(ConfigScope.PROJECT);
        saveScopeConfig(ConfigScope.USER);
        saveScopeConfig(ConfigScope.SYSTEM);
    }
    
    /**
     * 删除指定作用域的配置
     */
    public void delete(String key, ConfigScope scope) {
        getScopeConfig(scope).remove(key);
        saveScopeConfig(scope);
    }
    
    // ==================== 配置继承链 ====================
    
    /**
     * 获取配置继承链
     * 显示该配置键在所有作用域中的值
     */
    public ConfigChain getConfigChain(String key) {
        ConfigChain chain = new ConfigChain(key);
        
        // 按查找顺序添加条目（从高优先级到低优先级）
        addChainEntry(chain, key, ConfigScope.RUNTIME, "memory");
        addChainEntry(chain, key, ConfigScope.PROJECT, projectConfigPath != null ? projectConfigPath.toString() : "project");
        addChainEntry(chain, key, ConfigScope.USER, userConfigPath != null ? userConfigPath.toString() : "user");
        addChainEntry(chain, key, ConfigScope.SYSTEM, systemConfigPath != null ? systemConfigPath.toString() : "system");
        
        return chain;
    }
    
    private void addChainEntry(ConfigChain chain, String key, ConfigScope scope, String source) {
        ConfigScopeConfig config = getScopeConfig(scope);
        boolean present = config.containsKey(key);
        String value = config.getString(key);
        chain.addEntry(scope, value, source, present);
    }
    
    // ==================== 批量操作 ====================
    
    /**
     * 获取所有配置（合并所有作用域）
     */
    public Map<String, String> getAll() {
        Map<String, String> all = new HashMap<>();
        // 按优先级从低到高合并，高优先级覆盖低优先级
        all.putAll(systemConfig.getAllAsMap());
        all.putAll(userConfig.getAllAsMap());
        all.putAll(projectConfig.getAllAsMap());
        all.putAll(runtimeConfig.getAllAsMap());
        return Collections.unmodifiableMap(all);
    }
    
    /**
     * 获取指定作用域的所有配置
     */
    public Map<String, String> getAll(ConfigScope scope) {
        return Collections.unmodifiableMap(getScopeConfig(scope).getAllAsMap());
    }
    
    /**
     * 获取所有配置项对象
     */
    public Map<String, ConfigItem<?>> getAllItems() {
        Map<String, ConfigItem<?>> all = new HashMap<>();
        // 按优先级合并
        for (ConfigScope scope : ConfigScope.getSortedByPriority()) {
            for (Map.Entry<String, ConfigItem<?>> entry : getScopeConfig(scope).getAllItems().entrySet()) {
                if (!entry.getKey().startsWith("_")) { // 排除内部配置
                    all.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(all);
    }
    
    /**
     * 获取指定作用域的配置存储
     */
    private ConfigScopeConfig getScopeConfig(ConfigScope scope) {
        return switch (scope) {
            case RUNTIME -> runtimeConfig;
            case PROJECT -> projectConfig;
            case USER -> userConfig;
            case SYSTEM -> systemConfig;
        };
    }
    
    // ==================== 配置保存 ====================
    
    private void saveScopeConfig(ConfigScope scope) {
        switch (scope) {
            case RUNTIME -> { /* 运行时配置不持久化 */ }
            case PROJECT -> {
                if (projectConfigPath != null) {
                    saveToYaml(projectConfigPath, projectConfig);
                }
            }
            case USER -> {
                if (userConfigPath != null) {
                    saveToYaml(userConfigPath, userConfig);
                }
            }
            case SYSTEM -> {
                // 系统配置通常不通过程序修改
                if (systemConfigPath != null) {
                    saveToYaml(systemConfigPath, systemConfig);
                }
            }
        }
    }
    
    private void saveToYaml(Path path, ConfigScopeConfig config) {
        try {
            Files.createDirectories(path.getParent());
            
            // 转换回嵌套结构
            Map<String, Object> nested = unflattenMap(config.getAllAsMap());
            String yaml = yamlMapper.writeValueAsString(nested);
            Files.writeString(path, yaml);
            
        } catch (IOException e) {
            System.err.println("保存配置失败: " + path + " - " + e.getMessage());
        }
    }
    
    /**
     * 将展平的 Map 转换为嵌套结构
     */
    private Map<String, Object> unflattenMap(Map<String, String> flatMap) {
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, String> entry : flatMap.entrySet()) {
            if (entry.getKey().startsWith("_")) {
                continue; // 跳过内部配置
            }
            
            String[] parts = entry.getKey().split("\\.");
            Map<String, Object> current = result;
            
            for (int i = 0; i < parts.length - 1; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> next = (Map<String, Object>) current.get(parts[i]);
                if (next == null) {
                    next = new HashMap<>();
                    current.put(parts[i], next);
                }
                current = next;
            }
            
            current.put(parts[parts.length - 1], entry.getValue());
        }
        
        return result;
    }
    
    // ==================== 配置导入/导出 ====================
    
    /**
     * 导出配置到文件
     * @param scope 要导出的作用域，null 表示所有
     * @param format 导出格式
     * @param path 目标路径
     */
    public void exportConfig(ConfigScope scope, ConfigExportFormat format, Path path) throws IOException {
        Map<String, String> config = scope != null ? getAll(scope) : getAll();
        
        Files.createDirectories(path.getParent());
        
        String content = switch (format) {
            case JSON -> jsonMapper.writeValueAsString(config);
            case YAML -> yamlMapper.writeValueAsString(unflattenMap(config));
            case PROPERTIES -> toPropertiesFormat(config);
            case ENV -> toEnvFormat(config);
        };
        
        Files.writeString(path, content);
    }
    
    /**
     * 导入配置从文件
     * @param scope 导入到的作用域
     * @param path 源文件路径
     */
    public void importConfig(ConfigScope scope, Path path) throws IOException {
        String content = Files.readString(path);
        Map<String, String> config;
        
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yamlMapper.readValue(content, new TypeReference<>() {});
            config = flattenMap(map, "");
        } else if (filename.endsWith(".json")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = jsonMapper.readValue(content, new TypeReference<>() {});
            config = flattenMap(map, "");
        } else if (filename.endsWith(".properties")) {
            config = fromPropertiesFormat(content);
        } else {
            throw new IllegalArgumentException("Unsupported file format: " + filename);
        }
        
        // 导入配置
        config.forEach((k, v) -> set(k, v, scope));
    }
    
    private String toPropertiesFormat(Map<String, String> config) {
        return config.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .map(e -> e.getKey() + "=" + escapeProperties(e.getValue()))
            .sorted()
            .collect(Collectors.joining("\n"));
    }
    
    private String escapeProperties(String value) {
        return value.replace("\\", "\\\\")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("=", "\\=")
                   .replace(":", "\\:");
    }
    
    private String toEnvFormat(Map<String, String> config) {
        return config.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .map(e -> {
                String envKey = e.getKey().toUpperCase().replace(".", "_").replace("-", "_");
                return "export " + envKey + "=\"" + e.getValue().replace("\"", "\\\"") + "\"";
            })
            .sorted()
            .collect(Collectors.joining("\n"));
    }
    
    private Map<String, String> fromPropertiesFormat(String content) {
        Map<String, String> result = new HashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                String key = line.substring(0, eq).trim();
                String value = unescapeProperties(line.substring(eq + 1).trim());
                result.put(key, value);
            }
        }
        return result;
    }
    
    private String unescapeProperties(String value) {
        return value.replace("\\=", "=")
                   .replace("\\:", ":")
                   .replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\\\", "\\");
    }
    
    // ==================== 配置验证 ====================
    
    /**
     * 设置配置验证器
     */
    public void setValidator(ConfigValidator validator) {
        this.validator = validator;
    }
    
    /**
     * 获取配置验证器
     */
    public ConfigValidator getValidator() {
        return validator;
    }
    
    /**
     * 验证当前配置
     */
    public ConfigValidator.ValidationReport validate() {
        if (validator == null) {
            return new ConfigValidator.ValidationReport(new ArrayList<>(), 0);
        }
        return validator.validate(this);
    }
    
    // ==================== 配置模板 ====================
    
    /**
     * 应用配置模板
     */
    public void applyTemplate(ConfigTemplate template, boolean overwrite) {
        template.apply(this, overwrite);
    }
    
    /**
     * 应用配置模板（不覆盖）
     */
    public void applyTemplate(ConfigTemplate template) {
        template.apply(this, false);
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 重新加载所有配置
     */
    public void reload() {
        systemConfig.clear();
        userConfig.clear();
        projectConfig.clear();
        runtimeConfig.clear();
        loadAllConfigs();
    }
    
    /**
     * 清除运行时配置
     */
    public void clearRuntime() {
        runtimeConfig.clear();
    }
    
    /**
     * 获取配置路径
     */
    public Path getConfigPath(ConfigScope scope) {
        return switch (scope) {
            case SYSTEM -> systemConfigPath;
            case USER -> userConfigPath;
            case PROJECT -> projectConfigPath;
            case RUNTIME -> null;
        };
    }
    
    /**
     * 获取配置统计信息
     */
    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("system", systemConfig.size());
        stats.put("user", userConfig.size());
        stats.put("project", projectConfig.size());
        stats.put("runtime", runtimeConfig.size());
        stats.put("total", getAll().size());
        return stats;
    }
    
    @Override
    public String toString() {
        Map<String, Integer> stats = getStats();
        return String.format("ConfigManager{system=%d, user=%d, project=%d, runtime=%d, unique=%d}",
            stats.get("system"), stats.get("user"), stats.get("project"),
            stats.get("runtime"), stats.get("total"));
    }
}
