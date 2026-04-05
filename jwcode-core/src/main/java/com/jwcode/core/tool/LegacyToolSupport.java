package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LegacyToolSupport - 旧版工具支持类
 * 
 * 提供静态方法将旧版 Tool 适配到新版 Tool 接口
 * 用于兼容旧版 Tool 实现（返回 String schema 和不同 call 签名）
 */
public class LegacyToolSupport {
    
    /**
     * 将旧版 schema 字符串转换为 JsonNode
     */
    public static JsonNode parseSchema(String schemaStr) {
        if (schemaStr == null || schemaStr.isEmpty()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(schemaStr);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查给定的 Tool 实现是否为旧版实现（getInputSchema 返回 String）
     */
    public static boolean isLegacyTool(Object tool) {
        if (tool == null) return false;
        try {
            java.lang.reflect.Method method = tool.getClass().getMethod("getInputSchema");
            return method.getReturnType() == String.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
