package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;

/**
 * ExitPlanModeTool - 退出计划模式工具
 */
public class ExitPlanModeTool implements Tool<ExitPlanModeTool.Input, ExitPlanModeTool.Output, ExitPlanModeTool.Progress> {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override
    public String getName() {
        return "ExitPlanMode";
    }
    
    @Override
    public String getDescription() {
        return "退出计划模式";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            String schema = """
               {
                 "type": "object",
                 "properties": {}
               }
               """;
            return MAPPER.readTree(schema);
        } catch (Exception e) {
            return null;
        }
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
    public CompletableFuture<ToolResult<Output>> call(
            Input args,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input {}
    
    public static class Output {
        public boolean success;
    }
    
    public static class Progress {}
}
