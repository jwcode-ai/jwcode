package com.jwcode.core.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 特定作用域的配置存储
 * 用于存储某一作用域的所有配置项
 */
public class ConfigScopeConfig {
    
    private final ConfigScope scope;
    private final Map<String, ConfigItem<?>> items;
    private final String sourcePath;
    
    public ConfigScopeConfig(ConfigScope scope, String sourcePath) {
        this.scope = scope;
        this.sourcePath = sourcePath;
        this.items = new HashMap<>();
    }
    
    /**
     * 添加配置项
     */
    public <T> void put(ConfigItem<T> item) {
        items.put(item.getKey(), item);
    }
    
    /**
     * 获取配置项
     */
    @SuppressWarnings("unchecked")
    public <T> ConfigItem<T> get(String key) {
        return (ConfigItem<T>) items.get(key);
    }
    
    /**
     * 获取字符串值
     */
    public String getString(String key) {
        ConfigItem<?> item = items.get(key);
        return item != null ? item.getValueAsString() : null;
    }
    
    /**
     * 获取整数值
     */
    public Integer getInt(String key) {
        ConfigItem<?> item = items.get(key);
        if (item == null || item.getValue() == null) {
            return null;
        }
        try {
            return item.getValueAsInt();
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 获取布尔值
     */
    public Boolean getBoolean(String key) {
        ConfigItem<?> item = items.get(key);
        if (item == null || item.getValue() == null) {
            return null;
        }
        return item.getValueAsBoolean();
    }
    
    /**
     * 获取双精度浮点数值
     */
    public Double getDouble(String key) {
        ConfigItem<?> item = items.get(key);
        if (item == null || item.getValue() == null) {
            return null;
        }
        try {
            return item.getValueAsDouble();
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 获取长整数值
     */
    public Long getLong(String key) {
        ConfigItem<?> item = items.get(key);
        if (item == null || item.getValue() == null) {
            return null;
        }
        try {
            return item.getValueAsLong();
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 移除配置项
     */
    public ConfigItem<?> remove(String key) {
        return items.remove(key);
    }
    
    /**
     * 检查是否包含指定键
     */
    public boolean containsKey(String key) {
        return items.containsKey(key);
    }
    
    /**
     * 获取所有配置项
     */
    public Map<String, ConfigItem<?>> getAllItems() {
        return new HashMap<>(items);
    }
    
    /**
     * 获取所有键值对（简单形式）
     */
    public Map<String, String> getAllAsMap() {
        Map<String, String> result = new HashMap<>();
        items.forEach((k, v) -> result.put(k, v.getValueAsString()));
        return result;
    }
    
    /**
     * 清空配置
     */
    public void clear() {
        items.clear();
    }
    
    /**
     * 获取配置项数量
     */
    public int size() {
        return items.size();
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    public ConfigScope getScope() {
        return scope;
    }
    
    public String getSourcePath() {
        return sourcePath;
    }
    
    /**
     * 批量添加配置
     */
    public void putAll(Map<String, String> configs) {
        configs.forEach((k, v) -> put(ConfigItem.of(k, v, scope)));
    }
}
