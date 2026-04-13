package com.jwcode.core.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 配置项类
 * 表示一个具体的配置项，包含键、值、作用域、描述和元数据
 * 支持类型安全转换
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigItem<T> {
    
    /**
     * 配置键（唯一标识）
     * 支持点分路径，如: server.port, database.connection.timeout
     */
    @JsonProperty("key")
    private final String key;
    
    /**
     * 配置值
     * 可以是任意类型，实际存储为字符串，使用时进行类型转换
     */
    @JsonProperty("value")
    private T value;
    
    /**
     * 作用域
     * 决定配置项的优先级和存储位置
     */
    @JsonProperty("scope")
    private final ConfigScope scope;
    
    /**
     * 描述信息
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 创建时间
     */
    @JsonProperty("createdAt")
    private final Instant createdAt;
    
    /**
     * 更新时间
     */
    @JsonProperty("updatedAt")
    private Instant updatedAt;
    
    /**
     * 值类型（用于类型转换）
     */
    @JsonProperty("valueType")
    private final Class<T> valueType;
    
    /**
     * 是否已加密
     */
    @JsonProperty("encrypted")
    private boolean encrypted;
    
    /**
     * 默认值（用于重置）
     */
    @JsonProperty("defaultValue")
    private T defaultValue;
    
    /**
     * 创建新的配置项
     * @param key 配置键
     * @param value 配置值
     * @param scope 作用域
     * @param valueType 值类型
     */
    public ConfigItem(String key, T value, ConfigScope scope, Class<T> valueType) {
        this.key = Objects.requireNonNull(key, "Key cannot be null");
        this.scope = scope != null ? scope : ConfigScope.USER;
        this.valueType = valueType;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.encrypted = false;
        setValue(value);
    }
    
    /**
     * 创建字符串类型的配置项
     */
    public static ConfigItem<String> of(String key, String value, ConfigScope scope) {
        return new ConfigItem<>(key, value, scope, String.class);
    }
    
    /**
     * 创建字符串类型的配置项（默认 USER 作用域）
     */
    public static ConfigItem<String> of(String key, String value) {
        return of(key, value, ConfigScope.USER);
    }
    
    /**
     * 创建整数类型的配置项
     */
    public static ConfigItem<Integer> ofInt(String key, Integer value, ConfigScope scope) {
        return new ConfigItem<>(key, value, scope, Integer.class);
    }
    
    /**
     * 创建长整数类型的配置项
     */
    public static ConfigItem<Long> ofLong(String key, Long value, ConfigScope scope) {
        return new ConfigItem<>(key, value, scope, Long.class);
    }
    
    /**
     * 创建双精度浮点数类型的配置项
     */
    public static ConfigItem<Double> ofDouble(String key, Double value, ConfigScope scope) {
        return new ConfigItem<>(key, value, scope, Double.class);
    }
    
    /**
     * 创建布尔类型的配置项
     */
    public static ConfigItem<Boolean> ofBoolean(String key, Boolean value, ConfigScope scope) {
        return new ConfigItem<>(key, value, scope, Boolean.class);
    }
    
    // ==================== Getter 和 Setter ====================
    
    public String getKey() {
        return key;
    }
    
    public T getValue() {
        return value;
    }
    
    /**
     * 设置值并更新更新时间
     */
    public void setValue(T value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }
    
    public ConfigScope getScope() {
        return scope;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public Class<T> getValueType() {
        return valueType;
    }
    
    public boolean isEncrypted() {
        return encrypted;
    }
    
    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }
    
    public T getDefaultValue() {
        return defaultValue;
    }
    
    public void setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    // ==================== 类型转换方法 ====================
    
    /**
     * 将值转换为字符串
     */
    public String getValueAsString() {
        return value != null ? value.toString() : null;
    }
    
    /**
     * 将值转换为整数
     * @throws NumberFormatException 如果转换失败
     */
    public int getValueAsInt() {
        if (value == null) {
            throw new NumberFormatException("Value is null");
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }
    
    /**
     * 将值转换为长整数
     * @throws NumberFormatException 如果转换失败
     */
    public long getValueAsLong() {
        if (value == null) {
            throw new NumberFormatException("Value is null");
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }
    
    /**
     * 将值转换为双精度浮点数
     * @throws NumberFormatException 如果转换失败
     */
    public double getValueAsDouble() {
        if (value == null) {
            throw new NumberFormatException("Value is null");
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
    
    /**
     * 将值转换为布尔值
     */
    public boolean getValueAsBoolean() {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String str = value.toString().toLowerCase();
        return str.equals("true") || str.equals("yes") || str.equals("1") || str.equals("on");
    }
    
    /**
     * 将字符串值转换为指定类型
     * @param value 字符串值
     * @param type 目标类型
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertValue(String value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type == String.class) {
            return (T) value;
        }
        if (type == Integer.class || type == int.class) {
            return (T) Integer.valueOf(value);
        }
        if (type == Long.class || type == long.class) {
            return (T) Long.valueOf(value);
        }
        if (type == Double.class || type == double.class) {
            return (T) Double.valueOf(value);
        }
        if (type == Boolean.class || type == boolean.class) {
            String lower = value.toLowerCase();
            return (T) Boolean.valueOf(
                lower.equals("true") || lower.equals("yes") || lower.equals("1") || lower.equals("on")
            );
        }
        if (type == Float.class || type == float.class) {
            return (T) Float.valueOf(value);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
    
    /**
     * 重置为默认值
     */
    public void resetToDefault() {
        if (defaultValue != null) {
            setValue(defaultValue);
        }
    }
    
    /**
     * 检查值是否已修改（与默认值不同）
     */
    public boolean isModified() {
        if (defaultValue == null) {
            return value != null;
        }
        return !defaultValue.equals(value);
    }
    
    /**
     * 获取格式化的创建时间
     */
    public String getFormattedCreatedAt() {
        return DateTimeFormatter.ISO_INSTANT.format(createdAt);
    }
    
    /**
     * 获取格式化的更新时间
     */
    public String getFormattedUpdatedAt() {
        return DateTimeFormatter.ISO_INSTANT.format(updatedAt);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigItem<?> that = (ConfigItem<?>) o;
        return Objects.equals(key, that.key) && scope == that.scope;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(key, scope);
    }
    
    @Override
    public String toString() {
        return String.format("ConfigItem{key='%s', scope=%s, value=%s}", 
            key, scope, encrypted ? "***encrypted***" : value);
    }
}
