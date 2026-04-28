package com.jwcode.core.tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
               - path 参数必须是完整的文件路径（包含文件名和扩展名），例如："D:/test.txt"
               - 不要只传入目录路径，必须包含文件名
               - content 参数是要写入的文件内容
               - 如果父目录不存在，会自动创建
               
               示例：
               - 正确：{"path": "D:/test.txt", "content": "Hello World"}
               - 错误：{"path": "D:/"} （缺少文件名）
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            String schema = """
               {
                 "type": "object",
                 "properties": {
                   "path": {"type": "string", "description": "完整的文件路径（包含文件名和扩展名，例如：D:/test.txt）"},
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
     * 覆盖 parseInput 方法以支持 path 和 file_path 两种字段名
     */
    @Override
    public Input parseInput(JsonNode json) {
        try {
            // 如果 JSON 中有 file_path 但没有 path，则将 file_path 复制到 path
            if (json.has("file_path") && !json.has("path")) {
                ObjectNode objNode = (ObjectNode) json;
                JsonNode filePathNode = objNode.get("file_path");
                // 确保 file_path 是字符串类型
                if (filePathNode != null && filePathNode.isTextual()) {
                    objNode.put("path", filePathNode.asText());
                } else {
                    throw new IllegalArgumentException("file_path 必须是一个字符串值");
                }
            }
            return ToolSchemaGenerator.parseJson(json, getInputType());
        } catch (IllegalArgumentException e) {
            throw e; // 直接重新抛出已知异常
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析路径中的环境变量（如 %USERNAME%）
     */
    private String resolveEnvironmentVariables(String path) {
        if (path == null) return null;
        
        // 解析 Windows 环境变量 %VAR%
        String result = path;
        int start = result.indexOf('%');
        while (start != -1) {
            int end = result.indexOf('%', start + 1);
            if (end == -1) break;
            
            String varName = result.substring(start + 1, end);
            String varValue = System.getenv(varName);
            if (varValue != null) {
                result = result.substring(0, start) + varValue + result.substring(end + 1);
            }
            start = result.indexOf('%', start + 1);
        }
        
        // 解析 Unix 环境变量 $VAR 和 ${VAR}
        result = result.replace("$HOME", System.getenv("HOME") != null ? System.getenv("HOME") : System.getenv("USERPROFILE"));
        result = result.replace("~", System.getenv("HOME") != null ? System.getenv("HOME") : System.getenv("USERPROFILE"));
        
        return result;
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
                // 解析环境变量 - 支持 path 和 file_path 两种字段
                String resolvedPath = resolveEnvironmentVariables(input.getResolvedPath());
                Path filePath = Paths.get(resolvedPath);
                
                if (!filePath.isAbsolute() && context != null && context.getWorkingDirectory() != null) {
                    filePath = context.getWorkingDirectory().resolve(resolvedPath);
                }
                
                Path parentDir = filePath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                
                Files.writeString(filePath, input.content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                long size = Files.size(filePath);
                
                Output output = new Output();
                output.path = filePath.toString();
                output.size = size;
                output.success = true;
                
                return ToolResult.success(output);
            } catch (Exception e) {
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
        String resolvedPath = input.getResolvedPath();
        if (resolvedPath == null || resolvedPath.isEmpty()) {
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
        String resolvedPath = input.getResolvedPath();
        if (resolvedPath != null) {
            Path filePath = Paths.get(resolvedPath);
            return Files.exists(filePath);
        }
        return false;
    }
    
    public static class Input {
        // 支持两种字段名：path 和 file_path（兼容不同工具的命名约定）
        public String path;
        public String file_path;
        public String content;
        
        public Input() {}
        
        public Input(String path, String content) {
            this.path = path;
            this.content = content;
        }
        
        /**
         * 获取解析后的路径（优先使用 path，fallback 到 file_path）
         */
        public String getResolvedPath() {
            return path != null && !path.isEmpty() ? path : file_path;
        }
    }
    
    public static class Output {
        public String path;
        public long size;
        public boolean success;
    }
    
    public static class Progress {}
}
