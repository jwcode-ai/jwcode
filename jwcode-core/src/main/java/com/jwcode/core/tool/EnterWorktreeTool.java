package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;

/**
 * EnterWorktreeTool
 */
public class EnterWorktreeTool implements Tool<EnterWorktreeTool.Input, EnterWorktreeTool.Output, EnterWorktreeTool.Progress> {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override
    public String getName() { return "EnterWorktree"; }
    
    @Override
    public String getDescription() { return "进入 worktree"; }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
               {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}
               """);
        } catch (Exception e) { return null; }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<Input>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<Output>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolExecutionContext context, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String name; }
    public static class Output { public boolean success; }
    public static class Progress {}
}
