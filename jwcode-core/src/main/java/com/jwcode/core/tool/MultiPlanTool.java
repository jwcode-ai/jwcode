package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MultiPlanTool implements Tool<MultiPlanTool.Input, MultiPlanTool.Output, MultiPlanTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "MultiPlan"; }
    @Override public String getDescription() { return "多计划工具"; }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"query\": {\"type\": \"string\"}}, \"required\": [\"query\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<>() {};
    }
    
    /**
     * 新版 3 参数 call() — ToolExecutor 实际调用的入口
     */
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        // 委托给旧版 5 参数实现
        return call(input, (ToolContext) null, null, null, onProgress);
    }
    
    /**
     * 旧版 5 参数 call() — 保留兼容
     */
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input args, ToolContext context, CanUseToolFn canUseTool,
            Object parentMessage, Consumer<ToolProgress<Progress>> onProgress) {
        // 实际业务逻辑：生成多计划响应
        Output output = new Output();
        output.success = true;
        output.plans = java.util.Collections.singletonList("默认计划: " + (args != null ? args.query : "无查询"));
        return CompletableFuture.completedFuture(ToolResult.success(output));
    }
    
    public static class Input { public String query; }
    public static class Output { public boolean success; public java.util.List<String> plans; }
    public static class Progress {}
}
