package com.jwcode.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置管理器 - 管理用户和项目级配置
 * 
 * 配置文件位置:
 * - 用户级: ~/.jwcode/config.json
 * - 项目级: .jwcode/config.json (当前工作目录)
 */
public class ConfigManager {
    
    private static final String CONFIG_DIR = ".jwcode";
    private static final String CONFIG_FILE = "config.json";
    
    private static ConfigManager instance;
    
    private final Map<String, String> userConfig = new ConcurrentHashMap<>();
    private final Map<String, String> projectConfig = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    
    private Path userConfigPath;
    private Path projectConfigPath;
    
    private ConfigManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        initPaths();
        loadConfigs();
    }
    
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    private void initPaths() {
        // 用户级配置路径
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            userConfigPath = Paths.get(userHome, CONFIG_DIR, CONFIG_FILE);
        }
        
        // 项目级配置路径
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            projectConfigPath = Paths.get(userDir, CONFIG_DIR, CONFIG_FILE);
        }
    }
    
    private void loadConfigs() {
        // 加载用户配置
        if (userConfigPath != null) {
            loadConfig(userConfigPath, userConfig);
        }
        
        // 加载项目配置
        if (projectConfigPath != null) {
            loadConfig(projectConfigPath, projectConfig);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadConfig(Path path, Map<String, String> target) {
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                Map<String, Object> map = objectMapper.readValue(content, Map.class);
                map.forEach((k, v) -> target.put(k, v.toString()));
            } catch (IOException e) {
                System.err.println("加载配置失败: " + path + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * 获取配置值（项目级优先）
     */
    public String get(String key) {
        // 项目级优先
        if (projectConfig.containsKey(key)) {
            return projectConfig.get(key);
        }
        return userConfig.get(key);
    }
    
    /**
     * 设置配置值（默认保存到用户级）
     */
    public void set(String key, String value) {
        userConfig.put(key, value);
        saveUserConfig();
    }
    
    /**
     * 设置项目级配置
     */
    public void setProject(String key, String value) {
        projectConfig.put(key, value);
        saveProjectConfig();
    }
    
    /**
     * 删除配置值
     */
    public void delete(String key) {
        userConfig.remove(key);
        projectConfig.remove(key);
        saveUserConfig();
        saveProjectConfig();
    }
    
    /**
     * 获取所有配置（合并）
     */
    public Map<String, String> getAll() {
        Map<String, String> all = new HashMap<>(userConfig);
        all.putAll(projectConfig);
        return Collections.unmodifiableMap(all);
    }
    
    /**
     * 获取用户级配置
     */
    public Map<String, String> getUserConfig() {
        return Collections.unmodifiableMap(userConfig);
    }
    
    /**
     * 获取项目级配置
     */
    public Map<String, String> getProjectConfig() {
        return Collections.unmodifiableMap(projectConfig);
    }
    
    private void saveUserConfig() {
        if (userConfigPath != null) {
            saveConfig(userConfigPath, userConfig);
        }
    }
    
    private void saveProjectConfig() {
        if (projectConfigPath != null) {
            saveConfig(projectConfigPath, projectConfig);
        }
    }
    
    private void saveConfig(Path path, Map<String, String> config) {
        try {
            Files.createDirectories(path.getParent());
            String content = objectMapper.writeValueAsString(config);
            Files.writeString(path, content);
        } catch (IOException e) {
            System.err.println("保存配置失败: " + path + " - " + e.getMessage());
        }
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        userConfig.clear();
        projectConfig.clear();
        loadConfigs();
    }
}
