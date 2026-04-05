package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LegacyToolAdapter - 旧版工具适配器
 * 
 * 用于兼容旧版 Tool 实现（返回 String schema 和不同 call 签名）
 * 新的工具实现应直接实现 Tool 接口
 * 
 * 这是一个基础类，不直接实现 Tool 接口，子类可以自行实现
 */
public abstract class LegacyToolAdapter<I, O, P> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取输入 schema（返回 JsonNode）
     */
    public JsonNode getInputSchema() {
        // 尝试从旧版 getInputSchema() 方法获取 schema 字符串
        String schemaStr = getInputSchemaLegacy();
        if (schemaStr != null && !schemaStr.isEmpty()) {
            // 将 JSON 字符串解析为 JsonNode
            try {
                return objectMapper.readTree(schemaStr);
            } catch (Exception e) {
                // 解析失败，返回 null 使用默认实现
                return null;
            }
        }
        return null;
    }
    
    /**
     * 获取输出 schema（返回 JsonNode）
     */
    public JsonNode getOutputSchema() {
        String schemaStr = getOutputSchemaLegacy();
        if (schemaStr != null && !schemaStr.isEmpty()) {
            try {
                return objectMapper.readTree(schemaStr);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 执行工具调用（新版 3 参数）
     */
    public CompletableFuture<ToolResult<O>> call(
            I input,
            ToolExecutionContext context,
            Consumer<ToolProgress<P>> onProgress) {
        return callLegacy(input, context, onProgress);
    }

    /**
     * 获取输入类型
     */
    public TypeReference<I> getInputType() {
        return getInputTypeLegacy();
    }

    /**
     * 获取输出类型
     */
    public TypeReference<O> getOutputType() {
        return getOutputTypeLegacy();
    }

    protected abstract TypeReference<I> getInputTypeLegacy();
    protected abstract TypeReference<O> getOutputTypeLegacy();
    
    // 旧版方法 - 子类需要实现
    protected String getInputSchemaLegacy() {
        return null;
    }
    
    protected String getOutputSchemaLegacy() {
        return null;
    }
    
    protected abstract CompletableFuture<ToolResult<O>> callLegacy(
            I input,
            ToolExecutionContext context,
            Consumer<ToolProgress<P>> onProgress);
}
