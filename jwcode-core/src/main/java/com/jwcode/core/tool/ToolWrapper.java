package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * ToolWrapper - 工具包装器
 * 
 * 用于将旧版 Tool 实现包装为新版 Tool 接口
 * 旧版 Tool 实现需要实现 legacyCall 方法而不是 call 方法
 * 
 * 这是一个基础类，不直接实现 Tool 接口，子类可以自行实现
 */
public abstract class ToolWrapper<I, O, P> {
    
    /**
     * 获取输入 schema
     */
    public JsonNode getInputSchema() {
        // 优先使用旧版 getInputSchema 返回的字符串
        String schemaStr = getInputSchemaString();
        if (schemaStr != null && !schemaStr.isEmpty()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(schemaStr);
            } catch (Exception e) {
                // 忽略，使用默认
                return null;
            }
        }
        return null;
    }
    
    /**
     * 获取输出 schema
     */
    public JsonNode getOutputSchema() {
        String schemaStr = getOutputSchemaString();
        if (schemaStr != null && !schemaStr.isEmpty()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(schemaStr);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * 执行工具调用
     */
    public CompletableFuture<ToolResult<O>> call(
            I input,
            ToolExecutionContext context,
            Consumer<ToolProgress<P>> onProgress) {
        return legacyCall(input, context, onProgress);
    }

    /**
     * 获取输入类型
     */
    public TypeReference<I> getInputType() {
        return getInputTypeRef();
    }

    /**
     * 获取输出类型
     */
    public TypeReference<O> getOutputType() {
        return getOutputTypeRef();
    }

    // 子类需要实现的方法
    protected abstract String getInputSchemaString();
    protected abstract String getOutputSchemaString();
    protected abstract TypeReference<I> getInputTypeRef();
    protected abstract TypeReference<O> getOutputTypeRef();
    protected abstract CompletableFuture<ToolResult<O>> legacyCall(
            I input,
            ToolExecutionContext context,
            Consumer<ToolProgress<P>> onProgress);
}
