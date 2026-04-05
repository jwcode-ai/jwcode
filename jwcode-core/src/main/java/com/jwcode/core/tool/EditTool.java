package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * EditTool - 代码编辑工具
 */
public class EditTool implements Tool<EditTool.Input, EditTool.Output, EditTool.Progress> {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override
    public String getName() {
        return "EditTool";
    }
    
    @Override
    public String getDescription() {
        return "对文件内容进行精确编辑";
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            String schema = """
               {
                 "type": "object",
                 "properties": {
                   "file_path": {"type": "string"},
                   "old_string": {"type": "string"},
                   "new_string": {"type": "string"}
                 },
                 "required": ["file_path", "old_string"]
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
            Consumer<ToolProgress<Progress>> onProgress) {
        return CompletableFuture.completedFuture(ToolResult.success(new Output()));
    }
    
    public static class Input {
        public String file_path;
        public String old_string;
        public String new_string;
    }
    
    public static class Output {
        public String file_path;
        public boolean success;
    }
    
    public static class Progress {}
}
