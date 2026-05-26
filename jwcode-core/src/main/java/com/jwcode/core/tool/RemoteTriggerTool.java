package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RemoteTriggerTool implements Tool<RemoteTriggerTool.Input, RemoteTriggerTool.Output, RemoteTriggerTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "RemoteTrigger"; }
    @Override public String getDescription() { return "远程触发"; }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"action\": {\"type\": \"string\"}}, \"required\": [\"action\"]}"); } 
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
        return call(input, (ToolContext) null, null, null, onProgress);
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String action; public String target; }
    public static class Output { public boolean success; }
    public static class Progress {}
}
