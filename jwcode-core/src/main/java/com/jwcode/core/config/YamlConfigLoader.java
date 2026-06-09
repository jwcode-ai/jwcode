package com.jwcode.core.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/**
 * 增强的 YAML 配置文件加载器
 * 
 * 支持功能：
 * 1. 完整的 YAML 解析
 * 2. 嵌套配置支持 (a.b.c = value)
 * 3. 类型安全转换
 * 4. 与 ConfigManager 集成
 * 
 * 配置加载优先级（从高到低）:
 * 1. Runtime (内存)
 * 2. Project: ./.jwcode/config.yaml
 * 3. User: ~/.jwcode/config.yaml
 * 4. System: /etc/jwcode/config.yaml
 */
@Slf4j
public class YamlConfigLoader {
    
    private static final String CONFIG_DIR = ".jwcode";
    private static final String CONFIG_FILE = "config.yaml";
    private static final String FALLBACK_CONFIG_FILE = "config.yml";
    
    private static volatile YamlConfigLoader instance;
    
    private volatile JwcodeConfig jwcodeConfig;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    
    // 配置路径
    private final Path systemConfigPath;
    private final Path userConfigPath;
    private final Path projectConfigPath;
    
    // 类型转换器注册表
    private final Map<Class<?>, Function<String, ?>> typeConverters;
    
    private YamlConfigLoader() {
        // 配置 YAML 解析器
        YAMLFactory factory = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();
        this.yamlMapper = new ObjectMapper(factory);
        this.jsonMapper = new ObjectMapper();
        
        // 初始化类型转换器
        this.typeConverters = new HashMap<>();
        registerDefaultConverters();
        
        // 初始化配置路径
        String userHome = System.getProperty("user.home");
        String userDir = System.getProperty("user.dir");
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            String programData = System.getenv("ProgramData");
            this.systemConfigPath = programData != null ? 
                Paths.get(programData, "jwcode", CONFIG_FILE) : null;
        } else {
            this.systemConfigPath = Paths.get("/etc", "jwcode", CONFIG_FILE);
        }
        
        this.userConfigPath = userHome != null ? 
            Paths.get(userHome, CONFIG_DIR, CONFIG_FILE) : null;
        this.projectConfigPath = userDir != null ? 
            Paths.get(userDir, CONFIG_DIR, CONFIG_FILE) : null;
        
        // 加载配置
        loadConfig();
    }
    
    public static YamlConfigLoader getInstance() {
        if (instance == null) {
            synchronized (YamlConfigLoader.class) {
                if (instance == null) {
                    instance = new YamlConfigLoader();
                }
            }
        }
        return instance;
    }

    /**
     * Reset the singleton instance so config is reloaded from disk on next access.
     */
    public static synchronized void resetInstance() {
        instance = null;
    }
    
    /**
     * 创建新实例（用于测试）
     */
    public static YamlConfigLoader createNew() {
        return new YamlConfigLoader();
    }
    
    // ==================== 类型转换器 ====================
    
    private void registerDefaultConverters() {
        typeConverters.put(String.class, s -> s);
        typeConverters.put(Integer.class, Integer::parseInt);
        typeConverters.put(int.class, Integer::parseInt);
        typeConverters.put(Long.class, Long::parseLong);
        typeConverters.put(long.class, Long::parseLong);
        typeConverters.put(Double.class, Double::parseDouble);
        typeConverters.put(double.class, Double::parseDouble);
        typeConverters.put(Float.class, Float::parseFloat);
        typeConverters.put(float.class, Float::parseFloat);
        typeConverters.put(Boolean.class, this::parseBoolean);
        typeConverters.put(boolean.class, this::parseBoolean);
    }
    
    private boolean parseBoolean(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        return lower.equals("true") || lower.equals("yes") || 
               lower.equals("1") || lower.equals("on") || lower.equals("enabled");
    }
    
    /**
     * 注册自定义类型转换器
     */
    public <T> void registerConverter(Class<T> type, Function<String, T> converter) {
        typeConverters.put(type, converter);
    }
    
    /**
     * 获取类型转换器
     */
    @SuppressWarnings("unchecked")
    public <T> Function<String, T> getConverter(Class<T> type) {
        return (Function<String, T>) typeConverters.get(type);
    }
    
    // ==================== 配置加载 ====================
    
    /**
     * 获取配置（自动加载）
     */
    public JwcodeConfig getConfig() {
        if (jwcodeConfig == null) {
            synchronized (this) {
                if (jwcodeConfig == null) {
                    loadConfig();
                }
            }
        }
        return jwcodeConfig;
    }
    
    /**
     * 重新加载配置
     */
    public synchronized void reload() {
        loadConfig();
    }
    
    /**
     * 加载配置（按优先级合并）
     * 优先级: Runtime → Project → User → System
     */
    private void loadConfig() {
        // 从默认配置开始
        this.jwcodeConfig = createDefaultConfig();
        
        // 按优先级从低到高加载并合并
        mergeConfig(loadFromPath(systemConfigPath));
        mergeConfig(loadFromPath(getAlternativePath(systemConfigPath)));
        mergeConfig(loadFromPath(userConfigPath));
        mergeConfig(loadFromPath(getAlternativePath(userConfigPath)));
        mergeConfig(loadFromPath(projectConfigPath));
        mergeConfig(loadFromPath(getAlternativePath(projectConfigPath)));
    }
    
    private Path getAlternativePath(Path path) {
        if (path == null) return null;
        String filename = path.getFileName().toString();
        if (filename.equals(CONFIG_FILE)) {
            return path.getParent().resolve(FALLBACK_CONFIG_FILE);
        }
        return null;
    }
    
    private Optional<JwcodeConfig> loadFromPath(Path path) {
        if (path == null || !Files.exists(path)) {
            return Optional.empty();
        }
        
        try {
            String content = Files.readString(path);
            JwcodeConfig loaded = yamlMapper.readValue(content, JwcodeConfig.class);
            String providerName = loaded != null ? loaded.getDefaultProviderName() : "null";
            log.info("Loaded config from: {} (defaultProvider={})", path, providerName);
            return Optional.of(loaded);
        } catch (IOException e) {
            log.warn("Failed to load config from {}: {}", path, e.getMessage());
            // 输出文件内容前 200 字符帮助排查 YAML 解析问题
            try {
                String content = Files.readString(path);
                log.warn("Config file content preview (first 200 chars): {}",
                    content != null ? content.substring(0, Math.min(200, content.length())) : "null");
            } catch (IOException ignored) {}
            return Optional.empty();
        }
    }
    
    /**
     * 合并配置
     */
    private void mergeConfig(Optional<JwcodeConfig> other) {
        if (other.isEmpty()) return;
        
        JwcodeConfig source = other.get();
        
        // 合并提供商配置
        if (source.getProviders() != null) {
            source.getProviders().forEach((name, provider) -> {
                jwcodeConfig.getProviders().merge(name, provider, this::mergeProviderConfig);
            });
        }
        
        // 合并默认提供商
        if (source.getDefaultProviderName() != null) {
            jwcodeConfig.setDefaultProvider(source.getDefaultProviderName());
        }
        
        // 合并设置
        if (source.getSettings() != null) {
            mergeSettings(jwcodeConfig.getSettings(), source.getSettings());
        }
    }
    
    private JwcodeConfig.ProviderConfig mergeProviderConfig(
            JwcodeConfig.ProviderConfig existing,
            JwcodeConfig.ProviderConfig incoming) {
        
        if (incoming.getBaseUrl() != null) {
            existing.setBaseUrl(incoming.getBaseUrl());
        }
        if (incoming.getApiType() != null) {
            existing.setApiType(incoming.getApiType());
        }
        if (!incoming.getApiKeys().isEmpty()) {
            existing.getApiKeys().addAll(incoming.getApiKeys());
        }
        if (incoming.getKeyRotation() != null) {
            existing.setKeyRotation(incoming.getKeyRotation());
        }
        if (!incoming.getModels().isEmpty()) {
            // 合并模型列表，避免重复
            for (JwcodeConfig.ModelDefinition newModel : incoming.getModels()) {
                boolean exists = existing.getModels().stream()
                    .anyMatch(m -> m.getId().equals(newModel.getId()));
                if (!exists) {
                    existing.getModels().add(newModel);
                }
            }
        }
        return existing;
    }
    
    private void mergeSettings(JwcodeConfig.GlobalSettings target, JwcodeConfig.GlobalSettings source) {
        // 合并基础设置
        if (source.getTimeoutSeconds() != 60) { // 非默认值
            target.setTimeoutSeconds(source.getTimeoutSeconds());
        }
        if (source.getMaxRetries() != 3) { // 非默认值
            target.setMaxRetries(source.getMaxRetries());
        }
        if (source.getLogLevel() != null) {
            target.setLogLevel(source.getLogLevel());
        }
        target.setDebug(source.isDebug());
        
        // 合并引擎配置
        if (source.getEngine() != null) {
            mergeEngineSettings(target.getEngine(), source.getEngine());
        }
        
        // 合并权限配置
        if (source.getPermissions() != null) {
            mergePermissionSettings(target.getPermissions(), source.getPermissions());
        }
        
        // 合并消息配置
        if (source.getMessaging() != null) {
            mergeMessagingSettings(target.getMessaging(), source.getMessaging());
        }
        
        // 合并搜索配置
        if (source.getSearch() != null) {
            mergeSearchSettings(target.getSearch(), source.getSearch());
        }
        
        // 合并思考模式配置
        if (source.getThinking() != null) {
            mergeThinkingSettings(target.getThinking(), source.getThinking());
        }
        
        // 合并压缩配置
        if (source.getCompression() != null) {
            mergeCompressionSettings(target.getCompression(), source.getCompression());
        }
        
        // 合并代理配置
        if (source.getAgent() != null) {
            mergeAgentSettings(target.getAgent(), source.getAgent());
        }
        
        // 合并规划配置
        if (source.getPlanning() != null) {
            mergePlanningSettings(target.getPlanning(), source.getPlanning());
        }
        
        // 合并高级配置
        if (source.getAdvanced() != null) {
            mergeAdvancedSettings(target.getAdvanced(), source.getAdvanced());
        }
    }
    
    private void mergeEngineSettings(JwcodeConfig.EngineSettings target, JwcodeConfig.EngineSettings source) {
        // maxIterations is kept at 0 (unlimited), controlled by TokenBudget instead
        if (source.getTimeoutMinutes() != 5) {
            target.setTimeoutMinutes(source.getTimeoutMinutes());
        }
    }
    
    private void mergePermissionSettings(JwcodeConfig.PermissionSettings target, JwcodeConfig.PermissionSettings source) {
        target.setAutoApproveRead(source.isAutoApproveRead());
        target.setAutoApproveWrite(source.isAutoApproveWrite());
        target.setAutoApproveDelete(source.isAutoApproveDelete());
        target.setAutoApproveDestructive(source.isAutoApproveDestructive());
    }
    
    private void mergeMessagingSettings(JwcodeConfig.MessagingSettings target, JwcodeConfig.MessagingSettings source) {
        target.setHistoryEnabled(source.isHistoryEnabled());
        if (source.getMaxHistorySize() != 1000) {
            target.setMaxHistorySize(source.getMaxHistorySize());
        }
        target.setShowTimestamp(source.isShowTimestamp());
        target.setUseColor(source.isUseColor());
    }
    
    private void mergeSearchSettings(JwcodeConfig.SearchSettings target, JwcodeConfig.SearchSettings source) {
        if (source.getDefaultEngine() != null) {
            target.setDefaultEngine(source.getDefaultEngine());
        }
        if (source.getDefaultSummaryLength() != 300) {
            target.setDefaultSummaryLength(source.getDefaultSummaryLength());
        }
        if (source.getSimilarityThreshold() != 0.8) {
            target.setSimilarityThreshold(source.getSimilarityThreshold());
        }
        if (source.getTimeoutMs() != 10000) {
            target.setTimeoutMs(source.getTimeoutMs());
        }
        if (source.getConnectTimeout() != 10000) {
            target.setConnectTimeout(source.getConnectTimeout());
        }
        if (source.getReadTimeout() != 10000) {
            target.setReadTimeout(source.getReadTimeout());
        }
    }
    
    private void mergeThinkingSettings(JwcodeConfig.ThinkingSettings target, JwcodeConfig.ThinkingSettings source) {
        if (source.getThinkingDelayMs() != 2000) {
            target.setThinkingDelayMs(source.getThinkingDelayMs());
        }
        target.setShowThinkingTrace(source.isShowThinkingTrace());
        if (source.getMaxThinkingDepth() != 3) {
            target.setMaxThinkingDepth(source.getMaxThinkingDepth());
        }
        if (source.getMaxActionsPerMinute() != 30) {
            target.setMaxActionsPerMinute(source.getMaxActionsPerMinute());
        }
        if (source.getDangerousCommands() != null && !source.getDangerousCommands().isEmpty()) {
            target.setDangerousCommands(source.getDangerousCommands());
        }
    }
    
    private void mergeCompressionSettings(JwcodeConfig.CompressionSettings target, JwcodeConfig.CompressionSettings source) {
        if (source.getMaxMessagesBeforeCompression() != 50) {
            target.setMaxMessagesBeforeCompression(source.getMaxMessagesBeforeCompression());
        }
        if (source.getTokenThreshold() != 8000) {
            target.setTokenThreshold(source.getTokenThreshold());
        }
    }
    
    private void mergeAgentSettings(JwcodeConfig.AgentSettings target, JwcodeConfig.AgentSettings source) {
        if (source.getDefaultTimeout() != 60000) {
            target.setDefaultTimeout(source.getDefaultTimeout());
        }
        if (source.getDefaultPriority() != 5) {
            target.setDefaultPriority(source.getDefaultPriority());
        }
        if (source.getMaxAgentsPerType() != 5) {
            target.setMaxAgentsPerType(source.getMaxAgentsPerType());
        }
        if (source.getMaxTotalAgents() != 50) {
            target.setMaxTotalAgents(source.getMaxTotalAgents());
        }
        if (source.getDefaultQueueSize() != 1000) {
            target.setDefaultQueueSize(source.getDefaultQueueSize());
        }
        if (source.getThreadPoolSize() != Runtime.getRuntime().availableProcessors()) {
            target.setThreadPoolSize(source.getThreadPoolSize());
        }
        if (source.getMessageCleanupInterval() != 300000) {
            target.setMessageCleanupInterval(source.getMessageCleanupInterval());
        }
        if (source.getMaxHistorySize() != 10000) {
            target.setMaxHistorySize(source.getMaxHistorySize());
        }
        if (source.getDefaultMessageTTL() != 60000) {
            target.setDefaultMessageTTL(source.getDefaultMessageTTL());
        }
    }
    
    private void mergePlanningSettings(JwcodeConfig.PlanningSettings target, JwcodeConfig.PlanningSettings source) {
        if (source.getTokenBudget() != 10000) {
            target.setTokenBudget(source.getTokenBudget());
        }
        if (source.getTimeBudgetMs() != 300000) {
            target.setTimeBudgetMs(source.getTimeBudgetMs());
        }
        target.setAllowParallel(source.isAllowParallel());
        if (source.getMaxParallelism() != 4) {
            target.setMaxParallelism(source.getMaxParallelism());
        }
        target.setAiMode(source.isAiMode());
    }
    
    private void mergeAdvancedSettings(JwcodeConfig.AdvancedSettings target, JwcodeConfig.AdvancedSettings source) {
        target.setAutoSwarmEnabled(source.isAutoSwarmEnabled());
        target.setYoloMode(source.isYoloMode());
        target.setAutoCompactEnabled(source.isAutoCompactEnabled());
        target.setAnalyticsEnabled(source.isAnalyticsEnabled());
        target.setAnonymousMode(source.isAnonymousMode());
    }
    
    // ==================== 嵌套配置支持 ====================
    
    /**
     * 获取嵌套配置值
     * @param key 点分键，如 "server.port"
     * @return 配置值，如果不存在返回 null
     */
    public String getNestedValue(String key) {
        return getNestedValue(key, String.class);
    }
    
    /**
     * 获取嵌套配置值（带类型转换）
     * @param key 点分键
     * @param type 目标类型
     * @return 配置值
     */
    public <T> T getNestedValue(String key, Class<T> type) {
        String[] parts = key.split("\\.");
        Object current = jwcodeConfig;
        
        try {
            for (String part : parts) {
                if (current instanceof JwcodeConfig) {
                    current = getFieldValue((JwcodeConfig) current, part);
                } else if (current instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;
                    current = map.get(part);
                } else if (current instanceof JwcodeConfig.ProviderConfig) {
                    current = getProviderFieldValue((JwcodeConfig.ProviderConfig) current, part);
                } else if (current instanceof JwcodeConfig.GlobalSettings) {
                    current = getSettingsFieldValue((JwcodeConfig.GlobalSettings) current, part);
                } else {
                    return null;
                }
                
                if (current == null) {
                    return null;
                }
            }
            
            return convertValue(current, type);
        } catch (Exception e) {
            log.debug("Failed to get nested value for key '{}': {}", key, e.getMessage());
            return null;
        }
    }
    
    private Object getFieldValue(JwcodeConfig config, String field) {
        return switch (field) {
            case "providers" -> config.getProviders();
            case "defaultProvider", "default-provider" -> config.getDefaultProvider();
            case "settings" -> config.getSettings();
            default -> null;
        };
    }
    
    private Object getProviderFieldValue(JwcodeConfig.ProviderConfig provider, String field) {
        return switch (field) {
            case "baseUrl", "base-url" -> provider.getBaseUrl();
            case "apiType", "api-type" -> provider.getApiType();
            case "apiKeys", "api-keys" -> provider.getApiKeys();
            case "models" -> provider.getModels();
            case "keyRotation", "key-rotation" -> provider.getKeyRotation();
            default -> null;
        };
    }
    
    private Object getSettingsFieldValue(JwcodeConfig.GlobalSettings settings, String field) {
        return switch (field) {
            case "timeoutSeconds", "timeout-seconds" -> settings.getTimeoutSeconds();
            case "maxRetries", "max-retries" -> settings.getMaxRetries();
            case "logLevel", "log-level" -> settings.getLogLevel();
            case "debug" -> settings.isDebug();
            default -> null;
        };
    }
    
    @SuppressWarnings("unchecked")
    private <T> T convertValue(Object value, Class<T> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        
        Function<String, T> converter = (Function<String, T>) typeConverters.get(type);
        if (converter != null) {
            return converter.apply(value.toString());
        }
        
        return jsonMapper.convertValue(value, type);
    }
    
    /**
     * 设置嵌套配置值
     */
    public void setNestedValue(String key, Object value) {
        try {
            // 转换为 JSON 树
            JsonNode root = yamlMapper.valueToTree(jwcodeConfig);
            String[] parts = key.split("\\.");
            
            JsonNode current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                if (current instanceof ObjectNode obj) {
                    current = obj.with(parts[i]);
                }
            }
            
            if (current instanceof ObjectNode obj) {
                obj.set(parts[parts.length - 1], yamlMapper.valueToTree(value));
            }
            
            // 转换回对象
            this.jwcodeConfig = yamlMapper.treeToValue(root, JwcodeConfig.class);
            
        } catch (Exception e) {
            log.error("Failed to set nested value for key '{}': {}", key, e.getMessage());
        }
    }
    
    // ==================== 配置保存 ====================
    
    /**
     * 保存配置到用户目录
     */
    public synchronized void saveConfig(JwcodeConfig config) {
        saveConfig(config, userConfigPath);
    }
    
    /**
     * 保存配置到指定路径
     */
    public synchronized void saveConfig(JwcodeConfig config, Path path) {
        try {
            Files.createDirectories(path.getParent());
            String yaml = yamlMapper.writeValueAsString(config);
            String header = "# JWCode Configuration\n"
                + "# 可通过 Web UI (设置 > 配置文件) 或直接编辑此文件\n"
                + "# 修改 provider/model 配置后需重启服务生效\n"
                + "# 功能开关 (yolo, autoSwarm 等) 保存在 settings.json，此文件仅存 provider/model 配置\n"
                + "\n";
            Files.writeString(path, header + yaml);
            this.jwcodeConfig = config;
            log.info("Saved config to: {}", path);
        } catch (IOException e) {
            log.error("Failed to save config: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save config", e);
        }
    }
    
    /**
     * 保存当前配置
     */
    public synchronized void saveCurrentConfig() {
        saveConfig(jwcodeConfig);
    }
    
    // ==================== 与 ConfigManager 集成 ====================
    
    /**
     * 同步到 ConfigManager
     * 将当前 YAML 配置同步到 ConfigManager 的扁平键值存储
     */
    public void syncToConfigManager(ConfigManager manager) {
        Map<String, String> flatConfig = flattenConfig(jwcodeConfig);
        flatConfig.forEach((k, v) -> manager.set(k, v, ConfigScope.USER));
    }
    
    /**
     * 从 ConfigManager 同步
     * 从 ConfigManager 的扁平键值存储同步到当前 YAML 配置
     */
    public void syncFromConfigManager(ConfigManager manager) {
        // 将扁平配置转换回嵌套结构
        Map<String, Object> nested = unflattenToMap(manager.getAll());
        try {
            String yaml = yamlMapper.writeValueAsString(nested);
            this.jwcodeConfig = yamlMapper.readValue(yaml, JwcodeConfig.class);
        } catch (IOException e) {
            log.error("Failed to sync from ConfigManager: {}", e.getMessage());
        }
    }
    
    private Map<String, String> flattenConfig(JwcodeConfig config) {
        Map<String, String> result = new HashMap<>();
        try {
            JsonNode node = yamlMapper.valueToTree(config);
            flattenJsonNode(node, "", result);
        } catch (Exception e) {
            log.error("Failed to flatten config: {}", e.getMessage());
        }
        return result;
    }
    
    private void flattenJsonNode(JsonNode node, String prefix, Map<String, String> result) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenJsonNode(entry.getValue(), newPrefix, result);
            });
        } else if (node.isArray()) {
            result.put(prefix, node.toString());
        } else if (!node.isNull()) {
            result.put(prefix, node.asText());
        }
    }
    
    private Map<String, Object> unflattenToMap(Map<String, String> flat) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, String> entry : flat.entrySet()) {
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
    
    // ==================== 默认配置 ====================
    
    private JwcodeConfig createDefaultConfig() {
        // 返回空配置，所有值由配置文件决定
        return new JwcodeConfig();
    }
    
    // ==================== 路径获取 ====================
    
    public Path getUserConfigPath() {
        return userConfigPath;
    }
    
    public Path getProjectConfigPath() {
        return projectConfigPath;
    }
    
    public Path getSystemConfigPath() {
        return systemConfigPath;
    }
    
    public boolean configExists() {
        return Files.exists(userConfigPath) || Files.exists(projectConfigPath) || Files.exists(systemConfigPath);
    }
    
    public boolean hasUserConfig() {
        return userConfigPath != null && Files.exists(userConfigPath);
    }
    
    public boolean hasProjectConfig() {
        return projectConfigPath != null && Files.exists(projectConfigPath);
    }
    
    public boolean hasSystemConfig() {
        return systemConfigPath != null && Files.exists(systemConfigPath);
    }
    
    // ==================== 配置示例 ====================
    
    public static String getDefaultConfigExample() {
        return """
# JWCode Configuration
# Run 'jwcode' and follow the interactive setup, or configure manually below.
# Config file location: ~/.jwcode/config.yaml

# Default provider name (must match a key in providers below)
default-provider: ""

# Fallback provider when primary hits hard rate limits (optional)
# fallback-provider: ""

# Provider configurations
# Examples:
#   OpenAI:
#     base-url: https://api.openai.com/v1
#     api-type: openai-completions
#     api-keys: [sk-...]
#   Anthropic:
#     base-url: https://api.anthropic.com/v1
#     api-type: anthropic-messages
#     api-keys: [sk-ant-...]
#   Moonshot (Kimi):
#     base-url: https://api.moonshot.cn
#     api-type: openai-completions
#     api-keys: [sk-...]
#   DeepSeek:
#     base-url: https://api.deepseek.com/v1
#     api-type: openai-completions
#     api-keys: [sk-...]
providers: {}

# Global settings
settings:
  timeout-seconds: 120
  max-retries: 3
  debug: false
  log-level: INFO
""";
    }
    
    /**
     * Check whether at least one provider is properly configured with a valid API key.
     * Returns false if no config file exists, or if all providers have placeholder keys.
     */
    public boolean isProviderConfigured() {
        JwcodeConfig config = getConfig();
        if (config.getProviders().isEmpty()) return false;
        for (JwcodeConfig.ProviderConfig p : config.getProviders().values()) {
            if (!p.getApiKeys().isEmpty()) {
                for (String key : p.getApiKeys()) {
                    if (key != null && !key.isBlank()
                        && !key.contains("your-api-key")
                        && !key.equals("sk-your-api-key-here")
                        && key.length() >= 20) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Get a summary of configured providers with API keys masked.
     * Safe to expose via API.
     */
    public Map<String, Object> getProviderSummary() {
        Map<String, Object> result = new HashMap<>();
        JwcodeConfig config = getConfig();
        result.put("configured", isProviderConfigured());
        result.put("defaultProvider", config.getDefaultProviderName());
        Map<String, Object> providers = new HashMap<>();
        for (var entry : config.getProviders().entrySet()) {
            Map<String, Object> info = new HashMap<>();
            JwcodeConfig.ProviderConfig p = entry.getValue();
            info.put("baseUrl", p.getBaseUrl());
            info.put("hasApiKey", !p.getApiKeys().isEmpty()
                && p.getApiKeys().stream().anyMatch(k -> k != null && !k.isBlank() && k.length() >= 20));
            info.put("modelCount", p.getModels().size());
            info.put("apiType", p.getApiType());
            providers.put(entry.getKey(), info);
        }
        result.put("providers", providers);
        return result;
    }

    /**
     * 创建新配置文件
     */
    public void createDefaultConfigFile() throws IOException {
        if (userConfigPath != null && !Files.exists(userConfigPath)) {
            Files.createDirectories(userConfigPath.getParent());
            Files.writeString(userConfigPath, getDefaultConfigExample());
            log.info("Created default config file at: {}", userConfigPath);
        }
    }
}
