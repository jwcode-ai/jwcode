package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

public class ExitWorktreeTool implements Tool<ExitWorktreeTool.Input, ExitWorktreeTool.Output, ExitWorktreeTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "ExitWorktree"; }
    @Override public String getDescription() { return "退出 worktree"; }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {}}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input {}
    public static class Output { public boolean success; }
    public static class Progress {}
}
