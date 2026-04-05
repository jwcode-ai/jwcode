package com.jwcode.common.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * ConfigLoader - 配置加载器
 * 
 * 功能说明：
 * 加载和管理应用配置，支持本地和全局配置。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class ConfigLoader {
    
    private final Map<String, Object> config;
    private final Path configPath;
    
    public ConfigLoader() {
        this.config = new HashMap<>();
        this.configPath = resolveConfigPath();
        loadConfig(false);
    }
    
    /**
     * 解析配置文件路径
     */
    private Path resolveConfigPath() {
        // 优先使用用户目录下的配置
        Path userConfigPath = Paths.get(System.getProperty("user.home"), ".jwcode", "config.properties");
        if (Files.exists(userConfigPath)) {
            return userConfigPath;
        }
        // 使用当前目录配置
        return Paths.get("jwcode.properties");
    }
    
    /**
     * 加载配置
     */
    public Map<String, Object> loadConfig(boolean global) {
        Path path = global ? resolveGlobalConfigPath() : configPath;
        Properties props = new Properties();
        
        if (Files.exists(path)) {
            try {
                props.load(Files.newInputStream(path));
                for (String key : props.stringPropertyNames()) {
                    config.put(key, props.getProperty(key));
                }
            } catch (IOException e) {
                // 忽略加载错误，使用默认配置
            }
        }
        
        return Collections.unmodifiableMap(config);
    }
    
    /**
     * 设置配置项
     */
    public void setConfig(String key, Object value, boolean global) {
        config.put(key, value);
        saveConfig(global);
    }
    
    /**
     * 移除配置项
     */
    public void removeConfig(String key, boolean global) {
        config.remove(key);
        saveConfig(global);
    }
    
    /**
     * 获取配置项
     */
    public Object getConfig(String key, boolean global) {
        return config.get(key);
    }
    
    /**
     * 保存配置
     */
    private void saveConfig(boolean global) {
        Path path = global ? resolveGlobalConfigPath() : configPath;
        Properties props = new Properties();
        
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            props.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        
        try {
            // 确保目录存在
            Files.createDirectories(path.getParent());
            props.store(Files.newOutputStream(path), "JWCode Configuration");
        } catch (IOException e) {
            // 忽略保存错误
        }
    }
    
    /**
     * 解析全局配置文件路径
     */
    private Path resolveGlobalConfigPath() {
        return Paths.get(System.getProperty("user.home"), ".jwcode", "global.properties");
    }
    
    /**
     * 清空配置
     */
    public void clearConfig(boolean global) {
        config.clear();
        saveConfig(global);
    }
}