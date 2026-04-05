package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

public class ToolSearchTool implements Tool<ToolSearchTool.Input, ToolSearchTool.Output, ToolSearchTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "ToolSearch"; }
    @Override public String getDescription() { return "搜索可用工具。当你不确定使用哪个工具时使用。"; }
    
    @Override
    public String getPrompt() {
        return """
               使用 ToolSearch 工具搜索可用的工具。
               
               参数:
               - query: 搜索关键词（必需）
               
               示例:
               - {"query": "文件操作"} - 搜索文件相关工具
               - {"query": "git"} - 搜索 Git 相关工具
               - {"query": "搜索"} - 搜索搜索类工具
               
               注意:
               - 当你不确定某个功能应该使用哪个工具时，可以使用此工具搜索
               - 返回匹配的工具列表及其描述
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"query\": {\"type\": \"string\", \"description\": \"搜索关键词\"}}, \"required\": [\"query\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String query; }
    public static class Output { public boolean success; public java.util.List<String> results; }
    public static class Progress {}
}
