package com.jwcode.cli;

import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.YamlConfigLoader;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * ConfigManager - 配置管理器
 * 
 * 优先读取 YAML 配置 (~/.jwcode/config.yaml)
 * 如果没有 YAML 配置，则读取旧版 properties 配置
 */
public class ConfigManager {
    
    private static final String CONFIG_DIR = ".jwcode";
    private static final String CONFIG_FILE = "config.properties";
    
    private final Properties properties;
    private final Path configPath;
    private final JwcodeConfig yamlConfig;
    private boolean useYamlConfig;
    
    public ConfigManager() {
        this.properties = new Properties();
        String userHome = System.getProperty("user.home");
        this.configPath = Paths.get(userHome, CONFIG_DIR, CONFIG_FILE);
        
        // 首先尝试加载 YAML 配置
        this.yamlConfig = YamlConfigLoader.getInstance().getConfig();
        this.useYamlConfig = yamlConfig != null && !yamlConfig.getProviders().isEmpty();
        
        // 如果没有 YAML 配置，加载旧版 properties
        if (!useYamlConfig) {
            loadConfig();
        }
    }
    
    private void loadConfig() {
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                properties.load(is);
            } catch (IOException e) {
                System.err.println("加载配置失败: " + e.getMessage());
            }
        }
    }
    
    public void saveConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream os = Files.newOutputStream(configPath)) {
                properties.store(os, "JWCode Configuration");
            }
        } catch (IOException e) {
            System.err.println("保存配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取 API Key
     * 优先从 YAML 配置读取
     */
    public String getApiKey() {
        if (useYamlConfig) {
            JwcodeConfig.ProviderConfig provider = yamlConfig.getDefaultProvider();
            if (provider != null) {
                String key = provider.getCurrentApiKey();
                if (key != null && !key.isEmpty()) {
                    return key;
                }
            }
        }
        // 回退到 properties
        return properties.getProperty("api.key", "");
    }
    
    public void setApiKey(String apiKey) {
        properties.setProperty("api.key", apiKey);
        useYamlConfig = false;  // 修改后使用 properties
    }
    
    /**
     * 获取 API 端点
     * 优先从 YAML 配置读取
     */
    public String getApiEndpoint() {
        if (useYamlConfig) {
            JwcodeConfig.ProviderConfig provider = yamlConfig.getDefaultProvider();
            if (provider != null && provider.getBaseUrl() != null) {
                return provider.getBaseUrl();
            }
        }
        // 回退到 properties，但不再硬编码默认值
        String endpoint = properties.getProperty("api.endpoint", "");
        if (endpoint.isEmpty()) {
            // 如果都没有配置，尝试从环境变量读取
            endpoint = System.getenv("OPENAI_API_ENDPOINT");
            if (endpoint == null) {
                endpoint = System.getenv("MOONSHOT_API_ENDPOINT");
            }
        }
        return endpoint != null ? endpoint : "";
    }
    
    public void setApiEndpoint(String endpoint) {
        properties.setProperty("api.endpoint", endpoint);
        useYamlConfig = false;
    }
    
    /**
     * 获取模型名称
     * 优先从 YAML 配置读取
     */
    public String getModel() {
        if (useYamlConfig) {
            JwcodeConfig.ModelDefinition model = yamlConfig.getDefaultModel();
            if (model != null && model.getName() != null) {
                return model.getName();
            }
        }
        // 回退到 properties，但不再硬编码默认值
        String model = properties.getProperty("api.model", "");
        if (model.isEmpty()) {
            // 如果都没有配置，返回空字符串，让调用者处理
            return "";
        }
        return model;
    }
    
    public void setModel(String model) {
        properties.setProperty("api.model", model);
        useYamlConfig = false;
    }
    
    /**
     * 检查是否已配置
     */
    public boolean isConfigured() {
        return !getApiKey().isEmpty() && !getApiEndpoint().isEmpty();
    }
    
    /**
     * 获取配置文件路径
     */
    public String getConfigPath() {
        if (useYamlConfig) {
            return YamlConfigLoader.getInstance().getUserConfigPath().toString();
        }
        return configPath.toString();
    }
    
    /**
     * 是否使用 YAML 配置
     */
    public boolean isUsingYamlConfig() {
        return useYamlConfig;
    }
    
    /**
     * 获取 YAML 配置对象
     */
    public JwcodeConfig getYamlConfig() {
        return yamlConfig;
    }
    
    /**
     * 获取当前模型配置（包含 temperature 等参数）
     */
    public JwcodeConfig.ModelDefinition getCurrentModelConfig() {
        if (useYamlConfig) {
            return yamlConfig.getDefaultModel();
        }
        return null;
    }
    
    /**
     * 获取温度参数
     */
    public Double getTemperature() {
        JwcodeConfig.ModelDefinition model = getCurrentModelConfig();
        if (model != null) {
            return model.getEffectiveTemperature();
        }
        return null;  // 未配置，使用模型默认值
    }
}
