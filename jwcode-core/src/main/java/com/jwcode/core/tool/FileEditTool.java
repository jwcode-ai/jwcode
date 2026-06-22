package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.FileEditInput;
import com.jwcode.core.tool.output.FileEditOutput;
import com.jwcode.core.service.FileHistoryService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 文件编辑工具
 * 
 * 基于 diff 的文件编辑，支持多种编程语言
 */
public class FileEditTool implements Tool<FileEditInput, FileEditOutput, FileEditTool.FileEditProgress> {
    
    private static final Logger logger = Logger.getLogger(FileEditTool.class.getName());
    
    // FileHistoryService 用于记录文件变更历史
    private static final FileHistoryService fileHistoryService = new FileHistoryService();
    
    @Override
    public String getName() {
        return "FileEditTool";
    }
    
    @Override
    public String getDescription() {
        return "编辑文件内容。支持基于 diff 的编辑操作，可以添加、修改或删除文件中的代码。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 FileEditTool 编辑文件内容。
               
               参数:
               - file_path: 要编辑的文件路径（必需）
               - old_content: 要替换的旧内容（必需）
               - new_content: 替换后的新内容（必需）
               - reason: 编辑原因（可选）
               
               示例:
               - 替换方法实现：{"file_path": "src/main.java", "old_content": "public void oldMethod() {}", "new_content": "public void newMethod() {}"}
               - 添加注释：{"file_path": "src/main.java", "old_content": "", "new_content": "// 这是一个新注释"}
               
               注意:
               - 如果 old_content 为空字符串，表示在文件开头插入新内容
                - 如果 new_content 为空字符串，表示删除 old_content
                - 支持多行内容的替换
                """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(FileEditInput.class);
    }
    
    @Override
    public TypeReference<FileEditInput> getInputType() {
        return new TypeReference<FileEditInput>() {};
    }
    
    @Override
    public TypeReference<FileEditOutput> getOutputType() {
        return new TypeReference<FileEditOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<FileEditOutput>> call(
            FileEditInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<FileEditProgress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error("输入验证失败: " + validation.getFormattedErrors());
                }
                
                // 检查权限
                if (!context.hasPermission("write", input.filePath())) {
                    return ToolResult.error("没有权限编辑文件: " + input.filePath());
                }
                
                // 解析文件路径
                Path filePath = resolveFilePath(input.filePath(), context);
                if (!Files.exists(filePath)) {
                    return ToolResult.error("文件不存在: " + filePath);
                }
                
                // 读取文件内容
                String fileContent = Files.readString(filePath);
                
                // 执行编辑
                EditResult editResult = editFile(fileContent, input);
                
                if (!editResult.success()) {
                    return ToolResult.error("编辑失败: " + editResult.errorMessage());
                }
                
                // 写入文件
                Files.writeString(filePath, editResult.newContent(), StandardOpenOption.TRUNCATE_EXISTING);
                
                // 记录文件变更历史
                fileHistoryService.recordFileChange(
                    filePath.toString(), 
                    fileContent, 
                    editResult.newContent(),
                    "FileEditTool: " + (input.reason() != null ? input.reason() : "edit")
                );
                
                // 构建输出
                FileEditOutput output = new FileEditOutput(
                    filePath.toString(),
                    editResult.oldContent(),
                    editResult.newContent(),
                    editResult.changesMade(),
                    null
                );
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                logger.severe("编辑文件失败: " + e.getMessage());
                return ToolResult.error("编辑文件失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(FileEditInput input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (input.filePath() == null || input.filePath().trim().isEmpty()) {
            builder.addError("file_path 是必需的");
        }
        
        if (input.oldContent() == null) {
            builder.addError("old_content 是必需的");
        }
        
        if (input.newContent() == null) {
            builder.addError("new_content 是必需的");
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(FileEditInput input) {
        return false; // 文件编辑是写操作
    }
    
    @Override
    public boolean isConcurrencySafe(FileEditInput input) {
        return false; // 文件编辑不是并发安全的
    }
    
    @Override
    public boolean isDestructive(FileEditInput input) {
        return true; // 文件编辑是破坏性操作
    }
    
    @Override
    public boolean requiresApproval(FileEditInput input) {
        return false; // 文件编辑跟随 PermissionChecker 配置（与 FileWriteTool 行为一致）
    }
    
    /**
     * 解析文件路径（带工作区安全校验）。
     */
    private Path resolveFilePath(String filePath, ToolExecutionContext context) {
        Path path = Paths.get(filePath);
        
        // 如果是相对路径，相对于工作目录
        if (!path.isAbsolute()) {
            Path workingDir = context.getWorkingDirectory();
            if (workingDir != null) {
                path = workingDir.resolve(path);
            }
        }
        
        Path resolved = path.normalize().toAbsolutePath();
        
        // 【工作区安全】校验路径在工作区内
        context.validatePath(resolved, getName());
        
        return resolved;
    }
    
    /**
     * 编辑文件
     */
    private EditResult editFile(String fileContent, FileEditInput input) {
        String oldContent = input.oldContent();
        String newContent = input.newContent();
        
        // 如果 oldContent 为空，在文件开头插入
        if (oldContent.isEmpty()) {
            String updatedContent = newContent + "\n" + fileContent;
            return new EditResult(true, fileContent, updatedContent, 1, null);
        }
        
        // 如果 newContent 为空，删除 oldContent
        if (newContent.isEmpty()) {
            if (!fileContent.contains(oldContent)) {
                return new EditResult(false, null, null, 0, "未找到要删除的内容");
            }
            String updatedContent = fileContent.replace(oldContent, "");
            return new EditResult(true, fileContent, updatedContent, 1, null);
        }
        
        // 替换内容 - 增强错误诊断
        if (!fileContent.contains(oldContent)) {
            // 提供更详细的诊断信息，帮助 AI 修正编辑
            StringBuilder errorMsg = new StringBuilder("未找到要替换的内容。\n");
            errorMsg.append("可能原因：\n");
            errorMsg.append("1. 文件已被其他工具修改\n");
            errorMsg.append("2. 你的 old_content 包含了文件实际内容中没有的内容（幻觉）\n");
            errorMsg.append("3. 缩进或空白字符不匹配\n\n");
            errorMsg.append("解决方案：\n");
            errorMsg.append("- 先使用 FileReadTool 重新读取文件，获取最新内容\n");
            errorMsg.append("- 基于实际文件内容重新构建编辑指令\n");
            errorMsg.append("- 检查 old_content 是否与文件中内容完全匹配（包括缩进）\n");
            
            // 提供一些上下文信息
            int snippetLength = Math.min(200, fileContent.length());
            String snippet = fileContent.substring(0, snippetLength);
            errorMsg.append("\n文件开头内容（仅供参考）：\n").append(snippet);
            
            return new EditResult(false, null, null, 0, errorMsg.toString());
        }
        
        String updatedContent = fileContent.replace(oldContent, newContent);
        return new EditResult(true, fileContent, updatedContent, 1, null);
    }
    
    /**
     * 编辑结果
     */
    private record EditResult(
        boolean success,
        String oldContent,
        String newContent,
        int changesMade,
        String errorMessage
    ) {}
    
    /**
     * 文件编辑进度
     */
    public static class FileEditProgress {
        private final String fileName;
        private final int progress;
        private final String operation;
        
        public FileEditProgress(String fileName, int progress, String operation) {
            this.fileName = fileName;
            this.progress = progress;
            this.operation = operation;
        }
        
        public String getFileName() { return fileName; }
        public int getProgress() { return progress; }
        public String getOperation() { return operation; }
    }
}

