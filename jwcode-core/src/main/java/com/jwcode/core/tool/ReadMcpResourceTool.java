package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ReadMcpResourceTool implements Tool<ReadMcpResourceTool.Input, ReadMcpResourceTool.Output, ReadMcpResourceTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "ReadMcpResource"; }
    @Override public String getDescription() { return "读取 MCP 资源"; }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"uri\": {\"type\": \"string\"}}, \"required\": [\"uri\"]}"); } 
        catch (Exception e) { return null; }
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
    
    public static class Input { public String uri; }
    public static class Output { public boolean success; public String content; }
    public static class Progress {}
}
