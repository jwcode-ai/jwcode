package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 抽象工具基类，提供默认实现
 * 
 * @param <I> 输入类型
 * @param <O> 输出类型
 * @param <P> 进度类型
 */
public abstract class AbstractTool<I, O, P> implements Tool<I, O, P> {
    
    @Override
    public String getPrompt() {
        return getDescription();
    }
    
    @Override
    public JsonNode getInputSchema() {
        // 返回 null，子类应覆盖此方法
        return null;
    }
    
    @Override
    public JsonNode getOutputSchema() {
        // 返回 null，子类应覆盖此方法
        return null;
    }
    
    @Override
    public ToolValidationResult validate(I input) {
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isConcurrencySafe(I input) {
        return false;
    }
    
    @Override
    public boolean isReadOnly(I input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(I input) {
        return false;
    }
    
    @Override
    public boolean requiresApproval(I input) {
        return isDestructive(input);
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getUserFacingName(I input) {
        return getName();
    }
    
    @Override
    public I parseInput(JsonNode json) {
        return Tool.super.parseInput(json);
    }
    
    @Override
    public JsonNode serializeOutput(O output) {
        return Tool.super.serializeOutput(output);
    }
    
    @Override
    public ToolResult<O> callSync(I input, ToolExecutionContext context) {
        return Tool.super.callSync(input, context);
    }
}