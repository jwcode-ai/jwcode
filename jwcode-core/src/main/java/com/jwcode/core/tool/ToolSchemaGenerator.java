package com.jwcode.core.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具 Schema 生成器
 * 
 * 自动生成记录类的 JSON Schema
 * 支持 @JsonProperty 和验证注解（@NotBlank, @NotNull）
 */
public class ToolSchemaGenerator {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    /**
     * 生成记录类的 JSON Schema
     */
    public static <T> JsonNode generateSchema(TypeReference<T> typeRef) {
        Class<?> clazz = typeRef.getType() instanceof Class<?> 
            ? (Class<?>) typeRef.getType() 
            : Object.class;
        
        return generateSchema(clazz);
    }
    
    /**
     * 生成记录类的 JSON Schema
     * - 读取 @JsonProperty 获取 JSON 属性名
     * - 读取 @NotBlank/@NotNull 判断必需字段
     * - 支持 Java 记录和普通 class
     */
    public static JsonNode generateSchema(Class<?> clazz) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode required = objectMapper.createArrayNode();
        
        if (clazz.isRecord()) {
            // 处理 Java 记录
            RecordComponent[] components = clazz.getRecordComponents();
            for (RecordComponent component : components) {
                String fieldName = component.getName();
                Class<?> type = component.getType();
                
                // 获取 @JsonProperty 注解
                String jsonName = fieldName;
                boolean isRequired = false;
                
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    JsonProperty jsonProp = field.getAnnotation(JsonProperty.class);
                    if (jsonProp != null && jsonProp.value() != null && !jsonProp.value().isEmpty()) {
                        jsonName = jsonProp.value();
                    }
                    
                    // 检查验证注解
                    isRequired = field.isAnnotationPresent(NotBlank.class) 
                              || field.isAnnotationPresent(NotNull.class);
                } catch (NoSuchFieldException e) {
                    // 忽略
                }
                
                ObjectNode property = getTypeSchema(type);
                properties.set(jsonName, property);
                
                if (isRequired) {
                    required.add(jsonName);
                }
            }
        } else {
            // 处理普通 class - 通过反射获取公共字段
            Field[] fields = clazz.getFields();
            for (Field field : fields) {
                // 跳过静态字段和合成字段
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                
                String fieldName = field.getName();
                Class<?> type = field.getType();
                
                // 获取 @JsonProperty 注解
                String jsonName = fieldName;
                boolean isRequired = false;
                
                JsonProperty jsonProp = field.getAnnotation(JsonProperty.class);
                if (jsonProp != null && jsonProp.value() != null && !jsonProp.value().isEmpty()) {
                    jsonName = jsonProp.value();
                }
                
                // 检查验证注解
                isRequired = field.isAnnotationPresent(NotBlank.class) 
                          || field.isAnnotationPresent(NotNull.class);
                
                ObjectNode property = getTypeSchema(type);
                properties.set(jsonName, property);
                
                if (isRequired) {
                    required.add(jsonName);
                }
            }
        }
        
        schema.set("properties", properties);
        if (required.size() > 0) {
            schema.set("required", required);
        }
        return schema;
    }
    
    /**
     * 获取类型的 Schema
     */
    private static ObjectNode getTypeSchema(Class<?> type) {
        ObjectNode node = objectMapper.createObjectNode();
        
        if (type == String.class) {
            node.put("type", "string");
        } else if (type == int.class || type == Integer.class ||
                   type == long.class || type == Long.class) {
            node.put("type", "integer");
        } else if (type == double.class || type == Double.class ||
                   type == float.class || type == Float.class) {
            node.put("type", "number");
        } else if (type == boolean.class || type == Boolean.class) {
            node.put("type", "boolean");
        } else if (type.isArray() || Iterable.class.isAssignableFrom(type)) {
            node.put("type", "array");
        } else if (Map.class.isAssignableFrom(type)) {
            node.put("type", "object");
        } else {
            node.put("type", "object");
        }
        
        return node;
    }
    
    /**
     * 从 JSON 解析输入
     */
    public static <T> T parseJson(JsonNode json, TypeReference<T> typeRef) {
        try {
            return objectMapper.convertValue(json, typeRef);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将对象序列化为 JSON
     */
    public static JsonNode toJson(Object obj) {
        return objectMapper.valueToTree(obj);
    }
    
    /**
     * 从 Map 解析输入
     */
    public static <T> T parseMap(Map<String, Object> map, TypeReference<T> typeRef) {
        try {
            return objectMapper.convertValue(map, typeRef);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 Map: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将对象转换为 Map
     */
    public static Map<String, Object> toMap(Object obj) {
        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("无法转换为 Map: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取 ObjectMapper 实例
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
