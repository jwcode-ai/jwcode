package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

public class TaskCreateTool implements Tool<TaskCreateTool.Input, TaskCreateTool.Output, TaskCreateTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "TaskCreate"; }
    @Override public String getDescription() { return "创建新任务。用于跟踪和管理待办事项。"; }
    
    @Override
    public String getPrompt() {
        return """
               使用 TaskCreate 工具创建新任务。
               
               参数:
               - title: 任务标题（必需）
               - description: 任务描述（可选）
               
               示例:
               - {"title": "修复登录bug"} - 创建简单任务
               - {"title": "实现用户管理", "description": "包括增删改查功能"} - 创建带描述的任务
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"title\": {\"type\": \"string\", \"description\": \"任务标题\"}, \"description\": {\"type\": \"string\", \"description\": \"任务描述\"}}, \"required\": [\"title\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String title; public String description; }
    public static class Output { public boolean success; public String id; }
    public static class Progress {}
}
