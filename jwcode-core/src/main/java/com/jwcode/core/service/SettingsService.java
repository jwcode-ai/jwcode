package com.jwcode.core.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SettingsService - 设置管理服务
 * 
 * 功能说明：
 * 管理应用程序设置，支持用户设置和项目设置两个层级。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class SettingsService {
    
    private final Map<String, Object> userSettings;
    private final Map<String, Object> projectSettings;
    
    public SettingsService() {
        this.userSettings = new ConcurrentHashMap<>();
        this.projectSettings = new ConcurrentHashMap<>();
        initializeDefaultSettings();
    }
    
    /**
     * 初始化默认设置
     */
    private void initializeDefaultSettings() {
        // 默认用户设置
        userSettings.put("theme", "dark");
        userSettings.put("model", "sonnet");
        userSettings.put("verbose", false);
        userSettings.put("autoUpdate", true);
        userSettings.put("permissionMode", "default");
        
        // 默认项目设置 (ConcurrentHashMap 不接受 null 值)
        projectSettings.put("model", "");
        projectSettings.put("mcpServers", java.util.Collections.emptyList());
    }
    
    /**
     * 获取设置值
     */
    public Object get(String key) {
        return get(key, null);
    }
    
    /**
     * 获取设置值，支持默认值
     */
    public Object get(String key, Object defaultValue) {
        // 先查项目设置，再查用户设置
        Object value = projectSettings.get(key);
        if (value == null) {
            value = userSettings.get(key);
        }
        return value != null ? value : defaultValue;
    }
    
    /**
     * 获取字符串设置
     */
    public String getString(String key, String defaultValue) {
        Object value = get(key, defaultValue);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * 获取布尔设置
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = get(key, defaultValue);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 获取整数设置
     */
    public int getInteger(String key, int defaultValue) {
        Object value = get(key, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 设置用户设置
     */
    public void set(String key, Object value) {
        set(key, value, false);
    }
    
    /**
     * 设置设置值
     */
    public void set(String key, Object value, boolean projectScope) {
        if (projectScope) {
            projectSettings.put(key, value);
        } else {
            userSettings.put(key, value);
        }
    }
    
    /**
     * 删除设置
     */
    public void remove(String key) {
        remove(key, false);
    }
    
    /**
     * 删除设置
     */
    public void remove(String key, boolean projectScope) {
        if (projectScope) {
            projectSettings.remove(key);
        } else {
            userSettings.remove(key);
        }
    }
    
    /**
     * 获取所有用户设置
     */
    public Map<String, Object> getAllUserSettings() {
        return new ConcurrentHashMap<>(userSettings);
    }
    
    /**
     * 获取所有项目设置
     */
    public Map<String, Object> getAllProjectSettings() {
        return new ConcurrentHashMap<>(projectSettings);
    }
    
    /**
     * 重置为默认设置
     */
    public void resetToDefaults() {
        userSettings.clear();
        projectSettings.clear();
        initializeDefaultSettings();
    }
    
    /**
     * 导出设置为 Map
     */
    public Map<String, Object> exportSettings() {
        Map<String, Object> exported = new ConcurrentHashMap<>();
        exported.putAll(userSettings);
        exported.put("project", new ConcurrentHashMap<>(projectSettings));
        return exported;
    }
    
    /**
     * 从 Map 导入设置
     */
    @SuppressWarnings("unchecked")
    public void importSettings(Map<String, Object> settings) {
        if (settings == null) {
            return;
        }
        
        Object projectObj = settings.remove("project");
        userSettings.putAll(settings);
        
        if (projectObj instanceof Map) {
            Map<String, Object> projectMap = new ConcurrentHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) projectObj).entrySet()) {
                if (entry.getKey() instanceof String) {
                    projectMap.put((String) entry.getKey(), entry.getValue());
                }
            }
            projectSettings.putAll(projectMap);
        }
    }

    // ── 持久化 ──

    private static final java.nio.file.Path SETTINGS_FILE =
        java.nio.file.Path.of(System.getProperty("user.home"), ".jwcode", "settings.json");
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    public void save() {
        try {
            java.nio.file.Files.createDirectories(SETTINGS_FILE.getParent());
            MAPPER.writeValue(SETTINGS_FILE.toFile(), exportSettings());
        } catch (java.io.IOException e) {
            System.err.println("[SettingsService] Save failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void load() {
        try {
            if (java.nio.file.Files.exists(SETTINGS_FILE)) {
                Map<String, Object> data = MAPPER.readValue(SETTINGS_FILE.toFile(), Map.class);
                importSettings(data);
            }
        } catch (java.io.IOException e) {
            System.err.println("[SettingsService] Load failed: " + e.getMessage());
        }
    }
}