package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.jwcode.core.tool.context.ToolExecutionContext;

/**
 * Tool - 工具接口（重构后）
 * 
 * 对标 JavaScript 项目的 Tool 架构
 * 使用类型安全的泛型参数，支持异步执行和进度回调
 * 
 * @param <I> 工具输入类型（必须是记录类）
 * @param <O> 工具输出类型（必须是记录类）
 * @param <P> 工具进度类型
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface Tool<I, O, P> {
    
    /**
     * 获取工具名称（唯一标识）
     */
    default String getName() {
        throw new UnsupportedOperationException("子类必须实现 getName()");
    }
    
    /**
     * 获取工具描述（用于 AI 理解工具用途）
     */
    default String getDescription() {
        return "";
    }
    
    /**
     * 获取工具提示（详细的工具使用说明）
     */
    default String getPrompt() {
        return getDescription();
    }
    
    /**
     * 获取输入参数的 JSON Schema
     * 默认实现返回 null，子类应覆盖此方法
     */
    default JsonNode getInputSchema() {
        return null;
    }
    
    /**
     * 获取输出参数的 JSON Schema
     * 默认实现返回 null，子类应覆盖此方法
     */
    default JsonNode getOutputSchema() {
        return null;
    }
    
    /**
     * 执行工具调用（3参数版本 - 新版）
     * 子类必须实现此方法
     */
    default CompletableFuture<ToolResult<O>> call(
        I input,
        com.jwcode.core.tool.context.ToolExecutionContext context,
        Consumer<ToolProgress<P>> onProgress
    ) {
        throw new UnsupportedOperationException("子类必须实现 call(I, ToolExecutionContext, Consumer) 方法");
    }
    
    /**
     * 执行工具调用（5参数版本 - 旧版）
     * 默认实现：调用3参数版本
     */
    default CompletableFuture<ToolResult<O>> call(
        I input,
        com.jwcode.core.tool.ToolContext context,
        CanUseToolFn canUseTool,
        Object parentMessage,
        Consumer<ToolProgress<P>> onProgress
    ) {
        throw new UnsupportedOperationException("子类必须实现 call(I, ToolContext, CanUseToolFn, Object, Consumer) 方法");
    }
    
    /**
     * 同步执行工具调用（便捷方法）
     */
    default ToolResult<O> callSync(I input, ToolExecutionContext context) {
        try {
            return call(input, context, null).get();
        } catch (Exception e) {
            return ToolResult.error("工具执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证输入参数
     */
    default ToolValidationResult validate(I input) {
        return ToolValidationResult.valid();
    }
    
    /**
     * 检查工具是否并发安全
     */
    default boolean isConcurrencySafe(I input) {
        return false;
    }
    
    /**
     * 检查工具是否只读操作
     */
    default boolean isReadOnly(I input) {
        return false;
    }
    
    /**
     * 检查工具是否具有破坏性
     */
    default boolean isDestructive(I input) {
        return false;
    }
    
    /**
     * 检查工具是否需要用户确认
     */
    default boolean requiresApproval(I input) {
        return isDestructive(input);
    }
    
    /**
     * 检查工具是否启用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 获取工具的并发安全级别
     *
     * <p>AI 编排引擎利用此信息决定工具是否可与其他工具并行执行。</p>
     */
    default ConcurrencyLevel getConcurrencyLevel() {
        return ConcurrencyLevel.SEQUENTIAL;
    }

    /**
     * 获取工具的依赖工具名称列表
     *
     * <p>声明此工具在执行前必须先调用的其他工具。
     * 例如：FileEditTool 依赖 FileReadTool。</p>
     */
    default List<String> getDependencies() {
        return List.of();
    }

    /**
     * 获取工具的副作用类型集合
     *
     * <p>显式声明工具可能对系统产生的副作用，用于智能编排和安全检查。</p>
     */
    default Set<SideEffect> getSideEffects() {
        return Set.of();
    }

    /**
     * 获取工具所属类别
     *
     * <p>用于按功能域组织工具，替代平铺列表。</p>
     */
    default ToolCategory getCategory() {
        return ToolCategory.SYSTEM;
    }

    /**
     * 获取工具用户友好名称
     */
    default String getUserFacingName(I input) {
        return getName();
    }
    
    /**
     * 获取输入类型
     * 默认实现抛出异常，子类应该覆盖此方法
     */
    default TypeReference<I> getInputType() {
        throw new UnsupportedOperationException("子类必须实现 getInputType()");
    }
    
    /**
     * 获取输出类型
     * 默认实现抛出异常，子类应该覆盖此方法
     */
    default TypeReference<O> getOutputType() {
        throw new UnsupportedOperationException("子类必须实现 getOutputType()");
    }
    
    /**
     * 将输入从 JSON 解析
     */
    default I parseInput(JsonNode json) {
        return ToolSchemaGenerator.parseJson(json, getInputType());
    }
    
    /**
     * 将输出序列化为 JSON
     */
    default JsonNode serializeOutput(O output) {
        return ToolSchemaGenerator.toJson(output);
    }
}
