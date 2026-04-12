package com.jwcode.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

/**
 * YAML 配置文件加载器
 * 
 * 支持从以下位置加载配置：
 * 1. 用户级配置: ~/.jwcode/config.yaml
 * 2. 项目级配置: ./.jwcode/config.yaml
 * 3. 环境变量: JWCODE_CONFIG_PATH 指定的路径
 */
@Slf4j
public class YamlConfigLoader {
    
    private static final String CONFIG_DIR = ".jwcode";
    private static final String CONFIG_FILE = "config.yaml";
    private static final String FALLBACK_CONFIG_FILE = "config.yml";
    
    private static volatile YamlConfigLoader instance;
    private volatile JwcodeConfig config;
    private final ObjectMapper yamlMapper;
    private final Path userConfigPath;
    private final Path projectConfigPath;
    
    private YamlConfigLoader() {
        // 配置 YAML 解析器
        YAMLFactory factory = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();
        this.yamlMapper = new ObjectMapper(factory);
        
        // 初始化配置路径
        String userHome = System.getProperty("user.home");
        String userDir = System.getProperty("user.dir");
        
        this.userConfigPath = Paths.get(userHome, CONFIG_DIR, CONFIG_FILE);
        Path userConfigPathFallback = Paths.get(userHome, CONFIG_DIR, FALLBACK_CONFIG_FILE);
        this.projectConfigPath = Paths.get(userDir, CONFIG_DIR, CONFIG_FILE);
        Path projectConfigPathFallback = Paths.get(userDir, CONFIG_DIR, FALLBACK_CONFIG_FILE);
        
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
     * 获取配置
     */
    public JwcodeConfig getConfig() {
        if (config == null) {
            synchronized (this) {
                if (config == null) {
                    loadConfig();
                }
            }
        }
        return config;
    }
    
    /**
     * 重新加载配置
     */
    public synchronized void reload() {
        loadConfig();
    }
    
    /**
     * 加载配置（按优先级）
     */
    private void loadConfig() {
        // 1. 首先尝试加载用户级配置
        Optional<JwcodeConfig> userConfig = loadFromPath(userConfigPath);
        if (userConfig.isPresent()) {
            this.config = userConfig.get();
            log.info("Loaded user config from: {}", userConfigPath);
            return;
        }
        
        // 2. 尝试加载用户级 .yml 配置
        Optional<JwcodeConfig> userConfigYml = loadFromPath(
            Paths.get(System.getProperty("user.home"), CONFIG_DIR, FALLBACK_CONFIG_FILE));
        if (userConfigYml.isPresent()) {
            this.config = userConfigYml.get();
            return;
        }
        
        // 3. 尝试加载项目级配置
        Optional<JwcodeConfig> projectConfig = loadFromPath(projectConfigPath);
        if (projectConfig.isPresent()) {
            this.config = projectConfig.get();
            log.info("Loaded project config from: {}", projectConfigPath);
            return;
        }
        
        // 4. 尝试加载项目级 .yml 配置
        Optional<JwcodeConfig> projectConfigYml = loadFromPath(
            Paths.get(System.getProperty("user.dir"), CONFIG_DIR, FALLBACK_CONFIG_FILE));
        if (projectConfigYml.isPresent()) {
            this.config = projectConfigYml.get();
            return;
        }
        
        // 5. 使用默认配置
        log.info("No config file found, using default configuration");
        this.config = createDefaultConfig();
    }
    
    /**
     * 从指定路径加载配置
     */
    private Optional<JwcodeConfig> loadFromPath(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        
        try {
            String content = Files.readString(path);
            JwcodeConfig loaded = yamlMapper.readValue(content, JwcodeConfig.class);
            return Optional.of(loaded);
        } catch (IOException e) {
            log.warn("Failed to load config from {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 保存配置到用户目录
     */
    public synchronized void saveConfig(JwcodeConfig config) {
        try {
            Files.createDirectories(userConfigPath.getParent());
            String yaml = yamlMapper.writeValueAsString(config);
            Files.writeString(userConfigPath, yaml);
            this.config = config;
            log.info("Saved config to: {}", userConfigPath);
        } catch (IOException e) {
            log.error("Failed to save config: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save config", e);
        }
    }
    
    /**
     * 创建默认配置
     */
    private JwcodeConfig createDefaultConfig() {
        JwcodeConfig config = new JwcodeConfig();
        
        // 创建默认 moonshot 配置
        JwcodeConfig.ProviderConfig moonshot = new JwcodeConfig.ProviderConfig();
        moonshot.setBaseUrl("https://api.moonshot.cn");
        moonshot.setApiType("openai-completions");
        
        // 添加默认模型
        JwcodeConfig.ModelDefinition model = new JwcodeConfig.ModelDefinition();
        model.setId("kimi-k2.5");
        model.setName("kimi-k2.5");
        model.setTemperature(1.0);  // kimi-k2.5 需要 temperature=1
        model.setMaxTokens(32768);
        model.setContextWindow(2048000);
        
        moonshot.getModels().add(model);
        config.getProviders().put("moonshot", moonshot);
        config.setDefaultProvider("moonshot");
        
        return config;
    }
    
    /**
     * 获取配置文件路径
     */
    public Path getUserConfigPath() {
        return userConfigPath;
    }
    
    public Path getProjectConfigPath() {
        return projectConfigPath;
    }
    
    /**
     * 检查配置文件是否存在
     */
    public boolean configExists() {
        return Files.exists(userConfigPath) || Files.exists(projectConfigPath);
    }
    
    /**
     * 获取默认配置示例（用于初始化）
     */
    public static String getDefaultConfigExample() {
        return """
# JWCode 配置文件
# 配置文件位置: ~/.jwcode/config.yaml

# 默认使用的提供商
default-provider: moonshot

# 提供商配置
providers:
  moonshot:
    base-url: https://api.moonshot.cn
    api-type: openai-completions
    api-keys:
      - sk-your-api-key-here
    key-rotation:
      strategy: round_robin
      failover-enabled: true
      max-retries: 3
      cooldown-ms: 60000
    models:
      - id: kimi-k2.5
        name: kimi-k2.5
        enabled: true
        priority: 10
        reasoning: false
        context-window: 2048000
        max-tokens: 32768
        temperature: 1  # kimi-k2.5 必须设置为 1
        cost:
          input: 0.0
          output: 0.0
        input:
          - text
          - image
        supports-vision: true
        supports-image-generation: false

# 全局设置
settings:
  timeout-seconds: 60
  max-retries: 3
  debug: false
  log-level: INFO
""";
    }
}
