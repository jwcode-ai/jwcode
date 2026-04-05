package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

public class TaskUpdateTool implements Tool<TaskUpdateTool.Input, TaskUpdateTool.Output, TaskUpdateTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "TaskUpdate"; }
    @Override public String getDescription() { return "更新任务"; }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"string\"}}, \"required\": [\"id\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String id; public String title; public String status; }
    public static class Output { public boolean success; }
    public static class Progress {}
}
