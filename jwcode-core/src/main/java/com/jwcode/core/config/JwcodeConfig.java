package com.jwcode.core.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * JWCode 主配置类
 * 
 * 支持 YAML 配置文件格式，例如：
 * <pre>
 * moonshot:
 *   base-url: https://api.moonshot.cn
 *   api-type: openai-completions
 *   api-keys:
 *     - sk-xxx
 *     - sk-yyy
 *   key-rotation:
 *     strategy: round_robin
 *     failover-enabled: true
 *   models:
 *     - id: kimi-k2.5
 *       name: kimi-k2.5
 *       temperature: 1
 *       max-tokens: 32768
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JwcodeConfig {
    
    /**
     * 提供商配置（如 moonshot, openai 等）
     */
    @JsonProperty("providers")
    private Map<String, ProviderConfig> providers = new HashMap<>();
    
    /**
     * 默认使用的提供商
     */
    @JsonProperty("default-provider")
    private String defaultProvider;
    
    /**
     * 备用提供商（当默认提供商触发硬性配额限制时自动切换）
     */
    @JsonProperty("fallback-provider")
    private String fallbackProvider;
    
    /**
     * 全局设置
     */
    @JsonProperty("settings")
    private GlobalSettings settings = new GlobalSettings();
    
    /**
     * 获取指定提供商的配置
     */
    public ProviderConfig getProvider(String name) {
        return providers.get(name);
    }

    /**
     * 获取所有提供商配置（不可修改）。
     */
    @JsonIgnore
    public Map<String, ProviderConfig> getAllProviders() {
        return java.util.Collections.unmodifiableMap(providers);
    }
    
    /**
     * 获取默认提供商名称
     */
    @JsonIgnore
    public String getDefaultProviderName() {
        if (defaultProvider != null && providers.containsKey(defaultProvider)) {
            return defaultProvider;
        }
        if (!providers.isEmpty()) {
            return providers.entrySet().iterator().next().getKey();
        }
        return null;
    }

    /**
     * 获取默认提供商配置
     * 如果未设置 defaultProvider，则取 providers 中第一个；都没有则返回空配置
     */
    @JsonIgnore
    public ProviderConfig getDefaultProvider() {
        if (defaultProvider != null && providers.containsKey(defaultProvider)) {
            return providers.get(defaultProvider);
        }
        // 未设置默认提供商时，取第一个可用的
        if (!providers.isEmpty()) {
            Map.Entry<String, ProviderConfig> first = providers.entrySet().iterator().next();
            return first.getValue();
        }
        return createDefaultProvider();
    }
    
    /**
     * 获取默认模型配置
     */
    @JsonIgnore
    public ModelDefinition getDefaultModel() {
        ProviderConfig provider = getDefaultProvider();
        if (provider != null && !provider.getModels().isEmpty()) {
            return provider.getModels().get(0);
        }
        return null;
    }
    
    /**
     * 创建默认提供商配置
     */
    private ProviderConfig createDefaultProvider() {
        ProviderConfig config = new ProviderConfig();
        config.setBaseUrl("https://api.moonshot.cn");
        config.setApiType("openai-completions");
        return config;
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 提供商配置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProviderConfig {
        
        @JsonProperty("base-url")
        private String baseUrl;
        
        @JsonProperty("anthropic-version")
        private String anthropicVersion = "2023-06-01";
        
        @JsonProperty("api-type")
        private String apiType = "openai-completions";
        
        @JsonProperty("api-keys")
        private List<String> apiKeys = new ArrayList<>();
        
        @JsonProperty("key-rotation")
        private KeyRotationConfig keyRotation = new KeyRotationConfig();
        
        @JsonProperty("models")
        private List<ModelDefinition> models = new ArrayList<>();
        
        /** 轮询计数器（线程安全） */
        @JsonIgnore
        private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

        /** API Key 健康状态（key -> 不可用到 nextRetryTime） */
        @JsonIgnore
        private final Map<String, Long> keyHealthMap = new ConcurrentHashMap<>();

        /**
         * 获取当前应该使用的 API Key（支持轮询 + 故障转移）
         */
        @JsonIgnore
        public String getCurrentApiKey() {
            if (apiKeys.isEmpty()) {
                return null;
            }
            if (apiKeys.size() == 1) {
                String onlyKey = apiKeys.get(0);
                return isKeyHealthy(onlyKey) ? onlyKey : null;
            }

            String strategy = keyRotation != null ? keyRotation.getStrategy() : "round_robin";

            switch (strategy) {
                case "random":
                    return getRandomKey();
                case "priority":
                    return getPriorityKey();
                case "round_robin":
                default:
                    return getRoundRobinKey();
            }
        }

        /**
         * 轮询策略：使用 AtomicInteger 保证线程安全，纳秒级精度
         */
        private String getRoundRobinKey() {
            int size = apiKeys.size();
            for (int attempt = 0; attempt < size; attempt++) {
                int index = roundRobinCounter.getAndIncrement() % size;
                if (index < 0) index = -index;
                String key = apiKeys.get(index);
                if (isKeyHealthy(key)) {
                    return key;
                }
            }
            // 所有 key 都不可用，重置健康状态并重试
            keyHealthMap.clear();
            int fallbackIndex = roundRobinCounter.getAndIncrement() % size;
            if (fallbackIndex < 0) fallbackIndex = -fallbackIndex;
            return apiKeys.get(fallbackIndex);
        }

        /**
         * 随机策略
         */
        private String getRandomKey() {
            List<String> healthy = apiKeys.stream()
                .filter(this::isKeyHealthy).collect(Collectors.toList());
            if (healthy.isEmpty()) {
                keyHealthMap.clear();
                healthy = new ArrayList<>(apiKeys);
            }
            return healthy.get(new Random().nextInt(healthy.size()));
        }

        /**
         * 优先级策略：按配置顺序优先使用前面的 key
         */
        private String getPriorityKey() {
            for (String key : apiKeys) {
                if (isKeyHealthy(key)) {
                    return key;
                }
            }
            keyHealthMap.clear();
            return apiKeys.get(0);
        }

        /**
         * 检查 key 是否健康（未进入冷却期）
         */
        private boolean isKeyHealthy(String key) {
            Long nextRetry = keyHealthMap.get(key);
            return nextRetry == null || System.currentTimeMillis() >= nextRetry;
        }

        /**
         * 标记 key 为不可用，进入冷却期
         */
        public void markKeyFailed(String key) {
            long cooldown = keyRotation != null ? keyRotation.getCooldownMs() : 60000;
            keyHealthMap.put(key, System.currentTimeMillis() + cooldown);
        }

        /**
         * 重置所有 key 的健康状态
         */
        public void resetKeyHealth() {
            keyHealthMap.clear();
        }
        
        /**
         * 根据模型 ID 查找模型配置
         */
        public Optional<ModelDefinition> findModel(String modelId) {
            return models.stream()
                .filter(m -> m.getId().equals(modelId) || m.getName().equals(modelId))
                .findFirst();
        }
    }
    
    /**
     * 密钥轮询配置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KeyRotationConfig {
        
        @JsonProperty("strategy")
        private String strategy = "round_robin";  // round_robin, random, priority
        
        @JsonProperty("failover-enabled")
        private boolean failoverEnabled = true;
        
        @JsonProperty("max-retries")
        private int maxRetries = 3;
        
        @JsonProperty("cooldown-ms")
        private long cooldownMs = 60000;
    }
    
    /**
     * 模型定义
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelDefinition {
        
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("enabled")
        private boolean enabled = true;
        
        @JsonProperty("priority")
        private int priority = 10;
        
        // 推理设置
        @JsonProperty("reasoning")
        private boolean reasoning = false;
        
        // 参数限制
        @JsonProperty("context-window")
        private int contextWindow = 128000;
        
        @JsonProperty("max-tokens")
        private Integer maxTokens = null;  // null 表示不限制，让模型输出自己的最大值
        
        @JsonProperty("temperature")
        private Double temperature;  // null 表示使用模型默认值
        
        // 成本设置
        @JsonProperty("cost")
        private CostConfig cost = new CostConfig();
        
        // 支持的输入类型
        @JsonProperty("input")
        private List<String> input = Arrays.asList("text");
        
        // 能力设置
        @JsonProperty("supports-vision")
        private boolean supportsVision = false;
        
        @JsonProperty("supports-image-generation")
        private boolean supportsImageGeneration = false;
        
        @JsonProperty("supported-modalities")
        private List<String> supportedModalities = Arrays.asList("text");
        
        @JsonProperty("max-image-size")
        private Long maxImageSize;
        
        /**
         * 获取有效的温度值
         */
        public Double getEffectiveTemperature() {
            return temperature;
        }
    }
    
    /**
     * 成本配置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CostConfig {
        
        @JsonProperty("input")
        private double input = 0.0;
        
        @JsonProperty("output")
        private double output = 0.0;
        
        @JsonProperty("cache-read")
        private double cacheRead = 0.0;
        
        @JsonProperty("cache-write")
        private double cacheWrite = 0.0;
    }
    
    /**
     * 全局设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GlobalSettings {
        
        @JsonProperty("timeout-seconds")
        private int timeoutSeconds = 60;
        
        @JsonProperty("max-retries")
        private int maxRetries = 3;
        
        @JsonProperty("debug")
        private boolean debug = false;
        
        @JsonProperty("log-level")
        private String logLevel = "INFO";
        
        // ========== 引擎配置 ==========
        @JsonProperty("engine")
        private EngineSettings engine = new EngineSettings();
        
        // ========== 权限配置 ==========
        @JsonProperty("permissions")
        private PermissionSettings permissions = new PermissionSettings();
        
        // ========== 消息配置 ==========
        @JsonProperty("messaging")
        private MessagingSettings messaging = new MessagingSettings();
        
        // ========== 搜索配置 ==========
        @JsonProperty("search")
        private SearchSettings search = new SearchSettings();
        
        // ========== 思考模式配置 ==========
        @JsonProperty("thinking")
        private ThinkingSettings thinking = new ThinkingSettings();
        
        // ========== 上下文压缩配置 ==========
        @JsonProperty("compression")
        private CompressionSettings compression = new CompressionSettings();
        
        // ========== 代理配置 ==========
        @JsonProperty("agent")
        private AgentSettings agent = new AgentSettings();
        
        // ========== 规划配置 ==========
        @JsonProperty("planning")
        private PlanningSettings planning = new PlanningSettings();
        
        // ========== 其他高级配置 ==========
        @JsonProperty("advanced")
        private AdvancedSettings advanced = new AdvancedSettings();
    }
    
    /**
     * 引擎设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EngineSettings {
        @JsonProperty("max-iterations")
        private int maxIterations = 50;  // 最大迭代次数，默认 50 轮，设为 0 表示无限制，设为 -1 表示使用引擎默认值
        
        @JsonProperty("timeout-minutes")
        private int timeoutMinutes = 5;   // 超时时间（分钟）
        
        @JsonProperty("token-budget")
        private long tokenBudget = 1_000_000;  // Token 预算，默认 1M
        
        @JsonProperty("max-message-history")
        private int maxMessageHistory = 0;  // Session 消息 FIFO 硬上限，默认 0 表示不限制

        @JsonProperty("max-consecutive-tool-only-rounds")
        private int maxConsecutiveToolOnlyRounds = 100;  // 连续仅工具调用无文本回复的轮数上限
    }
    
    /**
     * 权限设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PermissionSettings {
        @JsonProperty("auto-approve-read")
        private boolean autoApproveRead = true;
        
        @JsonProperty("auto-approve-write")
        private boolean autoApproveWrite = false;
        
        @JsonProperty("auto-approve-delete")
        private boolean autoApproveDelete = false;
        
        @JsonProperty("auto-approve-destructive")
        private boolean autoApproveDestructive = false;
    }
    
    /**
     * 消息设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessagingSettings {
        @JsonProperty("history-enabled")
        private boolean historyEnabled = true;
        
        @JsonProperty("max-history-size")
        private int maxHistorySize = 1000;
        
        @JsonProperty("show-timestamp")
        private boolean showTimestamp = true;
        
        @JsonProperty("use-color")
        private boolean useColor = true;
    }
    
    /**
     * 搜索设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SearchSettings {
        @JsonProperty("default-engine")
        private String defaultEngine = "bing";
        
        @JsonProperty("default-summary-length")
        private int defaultSummaryLength = 300;
        
        @JsonProperty("similarity-threshold")
        private double similarityThreshold = 0.8;
        
        @JsonProperty("timeout-ms")
        private int timeoutMs = 10000;
        
        @JsonProperty("connect-timeout")
        private int connectTimeout = 10000;
        
        @JsonProperty("read-timeout")
        private int readTimeout = 10000;
        
        @JsonProperty("embedding-dimension")
        private int embeddingDimension = 256;
    }
    
    /**
     * 思考模式设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ThinkingSettings {
        @JsonProperty("thinking-delay-ms")
        private long thinkingDelayMs = 2000;  // 思考延迟（毫秒）
        
        @JsonProperty("show-thinking-trace")
        private boolean showThinkingTrace = true;  // 是否显示思考轨迹
        
        @JsonProperty("max-thinking-depth")
        private int maxThinkingDepth = 3;  // 最大思考深度
        
        @JsonProperty("max-actions-per-minute")
        private int maxActionsPerMinute = 30;
        
        @JsonProperty("dangerous-commands")
        @JsonDeserialize(using = StringToListDeserializer.class)
        private List<String> dangerousCommands = Arrays.asList("rm -rf", "format", "del /s /q");
    }
    
    /**
     * 上下文压缩设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompressionSettings {
        @JsonProperty("max-messages-before-compression")
        private int maxMessagesBeforeCompression = 50;
        
        @JsonProperty("token-threshold")
        private int tokenThreshold = 8000;
        
        // ========== 结构化上下文管理配置 ==========
        @JsonProperty("structured-context-enabled")
        private boolean structuredContextEnabled = true;
        
        @JsonProperty("max-active-size")
        private int maxActiveSize = 50;
        
        @JsonProperty("min-retain-count")
        private int minRetainCount = 5;
        
        @JsonProperty("enable-archive")
        private boolean enableArchive = true;
        
        @JsonProperty("periodic-eval-interval")
        private int periodicEvalInterval = 10;
        
        // ========== 安全守卫配置 ==========
        @JsonProperty("enable-intent-protection")
        private boolean enableIntentProtection = true;
        
        @JsonProperty("enable-refcount-protection")
        private boolean enableRefCountProtection = true;
        
        @JsonProperty("enable-recent-protection")
        private boolean enableRecentProtection = true;
        
        @JsonProperty("recent-protection-count")
        private int recentProtectionCount = 10;
        
        // ========== AI 评估配置 ==========
        @JsonProperty("enable-ai-evaluation")
        private boolean enableAiEvaluation = true;
    }
    
    /**
     * 代理设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentSettings {
        @JsonProperty("default-timeout")
        private long defaultTimeout = 60000;  // 默认超时（毫秒）
        
        @JsonProperty("default-priority")
        private int defaultPriority = 5;
        
        @JsonProperty("max-agents-per-type")
        private int maxAgentsPerType = 5;
        
        @JsonProperty("max-total-agents")
        private int maxTotalAgents = 50;
        
        @JsonProperty("default-queue-size")
        private int defaultQueueSize = 1000;
        
        @JsonProperty("thread-pool-size")
        private int threadPoolSize = Runtime.getRuntime().availableProcessors();
        
        @JsonProperty("message-cleanup-interval")
        private long messageCleanupInterval = 300000;  // 5分钟
        
        @JsonProperty("max-history-size")
        private int maxHistorySize = 10000;
        
        @JsonProperty("default-message-ttl")
        private long defaultMessageTTL = 60000;  // 60秒
    }
    
    /**
     * 规划设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanningSettings {
        @JsonProperty("token-budget")
        private int tokenBudget = 10000;
        
        @JsonProperty("time-budget-ms")
        private long timeBudgetMs = 300000;  // 5分钟
        
        @JsonProperty("allow-parallel")
        private boolean allowParallel = true;
        
        @JsonProperty("max-parallelism")
        private int maxParallelism = 4;
        
        @JsonProperty("ai-mode")
        private boolean aiMode = true;  // 默认使用 AI 模式

        @JsonProperty("max-plan-rounds")
        private int maxPlanRounds = 8;  // Plan 模式最大分析轮次，0 表示无限制
    }
    
    /**
     * 高级设置
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdvancedSettings {
        @JsonProperty("auto-swarm-enabled")
        private boolean autoSwarmEnabled = false;
        
        @JsonProperty("yolo-mode")
        private boolean yoloMode = false;
        
        @JsonProperty("auto-compact-enabled")
        private boolean autoCompactEnabled = true;
        
        @JsonProperty("analytics-enabled")
        private boolean analyticsEnabled = true;
        
        @JsonProperty("anonymous-mode")
        private boolean anonymousMode = true;
    }
    
    // ==================== 静态方法 ====================
    
    /**
     * 从 YAML 文件加载配置
     */
    public static JwcodeConfig load(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(path), JwcodeConfig.class);
    }
    
    /**
     * 获取当前提供商配置（别名）
     */
    @JsonIgnore
    public ProviderConfig getCurrentProvider() {
        return getDefaultProvider();
    }

    /**
     * 获取当前模型配置（别名）
     */
    @JsonIgnore
    public ModelDefinition getCurrentModel() {
        return getDefaultModel();
    }
    
    // ==================== 兼容性别名类 ====================
    
    /**
     * ModelConfig 是 ModelDefinition 的别名（兼容性）
     */
    public static class ModelConfig extends ModelDefinition {
    }

    // ==================== 自定义 Jackson 反序列化器 ====================

    /**
     * 兼容字符串格式的 List<String> 反序列化器。
     * <p>
     * 当配置文件中 dangerous-commands 被错误写为字符串（如 "[rm -rf, format, del /s /q]"）
     * 而非 YAML 列表时，此反序列化器会尝试解析字符串并转换为 List。
     * 同时也兼容标准的 YAML 列表格式。
     */
    public static class StringToListDeserializer extends JsonDeserializer<List<String>> {
        
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) 
                throws IOException {
            
            // 情况 1：标准 YAML 列表格式
            if (p.currentToken() == JsonToken.START_ARRAY) {
                List<String> result = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    result.add(p.getValueAsString());
                }
                return result;
            }
            
            // 情况 2：字符串格式（容错处理）
            String raw = p.getValueAsString();
            if (raw == null || raw.isBlank()) {
                return new ArrayList<>();
            }
            
            // 去除首尾方括号和引号
            String cleaned = raw.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            
            // 按逗号分割，并清理每个元素的空白和引号
            return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .map(s -> {
                    if ((s.startsWith("\"") && s.endsWith("\"")) || 
                        (s.startsWith("'") && s.endsWith("'"))) {
                        return s.substring(1, s.length() - 1).trim();
                    }
                    return s;
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }
    }
}
