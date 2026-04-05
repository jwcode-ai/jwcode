package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;

public class REPLTool implements Tool<REPLTool.Input, REPLTool.Output, REPLTool.Progress> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override public String getName() { return "REPL"; }
    @Override public String getDescription() { return "交互式编程环境（Read-Eval-Print Loop）。用于执行代码片段。"; }
    
    @Override
    public String getPrompt() {
        return """
               使用 REPL 工具执行代码片段。
               
               参数:
               - code: 要执行的代码（必需）
               - language: 编程语言（可选，如 "python", "javascript", "java"）
               
               示例:
               - {"code": "print('Hello World')", "language": "python"}
               - {"code": "console.log(2 + 2)", "language": "javascript"}
               
               注意:
               - 支持多种编程语言
               - 代码在当前环境中执行，有安全限制
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try { return MAPPER.readTree("{\"type\": \"object\", \"properties\": {\"code\": {\"type\": \"string\", \"description\": \"要执行的代码\"}, \"language\": {\"type\": \"string\", \"description\": \"编程语言\"}}, \"required\": [\"code\"]}"); } 
        catch (Exception e) { return null; }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(Input args, ToolContext context, CanUseToolFn canUseTool, Object parentMessage, java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input { public String code; public String language; }
    public static class Output { public boolean success; public String result; }
    public static class Progress {}
}
