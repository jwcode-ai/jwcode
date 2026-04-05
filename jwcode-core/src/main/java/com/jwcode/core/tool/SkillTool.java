package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

public class SkillTool implements Tool<SkillTool.Input, SkillTool.Output, SkillTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "Skill"; }
    @Override public String getDescription() { return "执行预定义的技能（Skill）。技能是可重用的自动化工作流。"; }
    
    @Override
    public String getPrompt() {
        return """
               使用 Skill 工具执行预定义的技能（Skill）。
               
               参数:
               - name: 技能名称（必需）
               - params: 技能参数（可选，JSON 字符串格式）
               
               示例:
               - {"name": "git-commit", "params": "{\\"message\\": \\"修复 bug\\"}"}
               - {"name": "code-review"}
               
               注意:
               - 技能名称必须是已注册的技能
               - params 是 JSON 格式的字符串，不是对象
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"技能名称\"}, \"params\": {\"type\": \"string\", \"description\": \"技能参数（JSON 字符串格式）\"}}, \"required\": [\"name\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String name; public String params; }
    public static class Output { public boolean success; public String result; }
    public static class Progress {}
}
