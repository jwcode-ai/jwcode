package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

public class TeamCreateTool implements Tool<TeamCreateTool.Input, TeamCreateTool.Output, TeamCreateTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "TeamCreate"; }
    @Override public String getDescription() { return "创建新的工作团队。用于多用户协作场景。"; }
    
    @Override
    public String getPrompt() {
        return """
               使用 TeamCreate 工具创建新团队。
               
               参数:
               - name: 团队名称（必需）
               - description: 团队描述（可选）
               
               示例:
               - {"name": "后端开发组"} - 创建简单团队
               - {"name": "前端团队", "description": "负责用户界面开发"} - 创建带描述的团队
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"团队名称\"}, \"description\": {\"type\": \"string\", \"description\": \"团队描述\"}}, \"required\": [\"name\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String name; public String description; }
    public static class Output { public boolean success; public String id; }
    public static class Progress {}
}
