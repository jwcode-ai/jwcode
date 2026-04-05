package com.jwcode.cli;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * ConfigManager - 配置管理器
 * 
 * 管理 JWCode 的配置，包括 API 密钥、端点等
 */
public class ConfigManager {
    
    private static final String CONFIG_DIR = ".jwcode";
    private static final String CONFIG_FILE = "config.properties";
    
    private final Properties properties;
    private final Path configPath;
    
    public ConfigManager() {
        this.properties = new Properties();
        String userHome = System.getProperty("user.home");
        this.configPath = Paths.get(userHome, CONFIG_DIR, CONFIG_FILE);
        loadConfig();
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
    
    public String getApiKey() {
        return properties.getProperty("api.key", "");
    }
    
    public void setApiKey(String apiKey) {
        properties.setProperty("api.key", apiKey);
    }
    
    public String getApiEndpoint() {
        return properties.getProperty("api.endpoint", "https://api.minimaxi.com/anthropic");
    }
    
    public void setApiEndpoint(String endpoint) {
        properties.setProperty("api.endpoint", endpoint);
    }
    
    public String getModel() {
        return properties.getProperty("api.model", "MiniMax-M2.7");
    }
    
    public void setModel(String model) {
        properties.setProperty("api.model", model);
    }
    
    public boolean isConfigured() {
        return !getApiKey().isEmpty();
    }
    
    public String getConfigPath() {
        return configPath.toString();
    }
}
