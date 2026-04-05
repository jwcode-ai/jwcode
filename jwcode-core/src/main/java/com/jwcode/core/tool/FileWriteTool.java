package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * FileWriteTool - 文件写入工具
 */
public class FileWriteTool implements Tool<FileWriteTool.Input, FileWriteTool.Output, FileWriteTool.Progress> {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override
    public String getName() {
        return "FileWriteTool";
    }
    
    @Override
    public String getDescription() {
        return "写入内容到文件。支持自动创建父目录。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 FileWriteTool 写入文件内容。
               
               重要提示：
               - path 参数必须是完整的文件路径（包含文件名和扩展名），例如："C:/Users/username/Desktop/myfile.txt"
               - 不要只传入目录路径，必须包含文件名
               - content 参数是要写入的文件内容
               - 如果父目录不存在，会自动创建
               
               示例：
               - 正确：{"path": "C:/Users/admin/Desktop/tetris.html", "content": "<!DOCTYPE html>..."}
               - 错误：{"path": "C:/Users/admin/Desktop"} （缺少文件名）
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            String schema = """
               {
                 "type": "object",
                 "properties": {
                   "path": {"type": "string", "description": "完整的文件路径（包含文件名和扩展名，例如：C:/Users/username/Desktop/file.txt）"},
                   "content": {"type": "string", "description": "文件内容"}
                 },
                 "required": ["path", "content"]
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
    
    /**
     * 新版 3 参数 call 方法（Tool 接口标准）
     */
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = Paths.get(input.path);
                if (!filePath.isAbsolute() && context != null && context.getWorkingDirectory() != null) {
                    filePath = context.getWorkingDirectory().resolve(input.path);
                }
                
                Path parentDir = filePath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                
                Files.writeString(filePath, input.content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                long size = Files.size(filePath);
                
                Output output = new Output();
                output.path = filePath.toString();
                output.size = size;
                output.success = true;
                
                return ToolResult.success(output);
            } catch (IOException e) {
                return ToolResult.<Output>error("写入文件失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 旧版 5 参数 call 方法（兼容旧代码）
     */
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input args,
            ToolContext context,
            CanUseToolFn canUseTool,
            Object parentMessage,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        // 将 ToolContext 转换为 ToolExecutionContext
        ToolExecutionContext execContext = null;
        if (context != null && context.getWorkingDirectory() != null) {
            execContext = new ToolExecutionContext(null, Paths.get(context.getWorkingDirectory()), null);
        }
        return call(args, execContext, onProgress);
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        if (input.path == null || input.path.isEmpty()) {
            return ToolValidationResult.invalid("文件路径不能为空");
        }
        if (input.content == null) {
            return ToolValidationResult.invalid("文件内容不能为空");
        }
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return false;
    }
    
    @Override
    public boolean isDestructive(Input input) {
        // 如果文件已存在，写入是破坏性的
        if (input.path != null) {
            Path filePath = Paths.get(input.path);
            return Files.exists(filePath);
        }
        return false;
    }
    
    public static class Input {
        public String path;
        public String content;
        
        public Input() {}
        public Input(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }
    
    public static class Output {
        public String path;
        public long size;
        public boolean success;
    }
    
    public static class Progress {}
}
