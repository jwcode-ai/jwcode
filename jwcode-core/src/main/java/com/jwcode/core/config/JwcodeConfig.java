package com.jwcode.core.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
    private String defaultProvider = "moonshot";
    
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
     * 获取默认提供商名称
     */
    public String getDefaultProviderName() {
        return defaultProvider;
    }
    
    /**
     * 获取默认提供商配置
     */
    public ProviderConfig getDefaultProvider() {
        return providers.getOrDefault(defaultProvider, createDefaultProvider());
    }
    
    /**
     * 获取默认模型配置
     */
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
        
        @JsonProperty("api-type")
        private String apiType = "openai-completions";
        
        @JsonProperty("api-keys")
        private List<String> apiKeys = new ArrayList<>();
        
        @JsonProperty("key-rotation")
        private KeyRotationConfig keyRotation = new KeyRotationConfig();
        
        @JsonProperty("models")
        private List<ModelDefinition> models = new ArrayList<>();
        
        /**
         * 获取当前应该使用的 API Key（支持轮询）
         */
        public String getCurrentApiKey() {
            if (apiKeys.isEmpty()) {
                return null;
            }
            // 简单的轮询策略
            int index = (int) (System.currentTimeMillis() / 1000) % apiKeys.size();
            return apiKeys.get(index);
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
        private int maxTokens = 4096;
        
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
    public ProviderConfig getCurrentProvider() {
        return getDefaultProvider();
    }
    
    /**
     * 获取当前模型配置（别名）
     */
    public ModelDefinition getCurrentModel() {
        return getDefaultModel();
    }
    
    // ==================== 兼容性别名类 ====================
    
    /**
     * ModelConfig 是 ModelDefinition 的别名（兼容性）
     */
    public static class ModelConfig extends ModelDefinition {
    }
}
