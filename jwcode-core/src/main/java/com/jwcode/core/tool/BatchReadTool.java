package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.BatchReadInput;
import com.jwcode.core.tool.output.BatchReadOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 批量文件读取工具
 *
 * <p>支持一次性读取多个文件内容，自动管理总内容大小，避免超出 token 限制。
 * 替代逐个调用 FileReadTool，显著减少对话轮次。</p>
 */
public class BatchReadTool implements Tool<BatchReadInput, BatchReadOutput, BatchReadTool.BatchReadProgress> {

    private static final Logger logger = Logger.getLogger(BatchReadTool.class.getName());

    // 配置常量
    private static final int MAX_FILES = 20;
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int DEFAULT_MAX_TOTAL_LINES = 2000;
    private static final int DEFAULT_MAX_TOTAL_TOKENS = 8000;
    private static final int MAX_LINES_PER_FILE = 500;
    private static final List<String> BINARY_EXTENSIONS = List.of(
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp",
        ".pdf", ".zip", ".jar", ".war", ".class", ".exe", ".dll"
    );

    @Override
    public String getName() {
        return "BatchReadTool";
    }

    @Override
    public String getDescription() {
        return "批量读取多个文件内容。支持自动截断总内容以避免超出 token 限制，自动跳过二进制文件。";
    }

    @Override
    public String getPrompt() {
        return """
               使用 BatchReadTool 批量读取多个文件内容。

               参数:
               - file_paths: 文件路径列表（必需，最多 20 个）
               - max_total_lines: 总共读取的最大行数（可选，默认：2000）
               - max_total_tokens: 总内容的最大估算 token 数（可选，默认：8000）

               示例:
               - 批量读取：{"file_paths": ["README.md", "docs/guide.md", "docs/api.md"]}
               - 限制总量：{"file_paths": ["a.md", "b.md"], "max_total_lines": 500}

               注意:
               - 优先于逐个调用 FileReadTool，可显著减少对话轮次
               - 自动跳过不存在的文件和二进制文件
               - 如果总内容超过限制，会自动截断并在结果中标注
               """;
    }

    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(BatchReadInput.class);
    }

    @Override
    public TypeReference<BatchReadInput> getInputType() {
        return new TypeReference<BatchReadInput>() {};
    }

    @Override
    public TypeReference<BatchReadOutput> getOutputType() {
        return new TypeReference<BatchReadOutput>() {};
    }

    @Override
    public CompletableFuture<ToolResult<BatchReadOutput>> call(
            BatchReadInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<BatchReadProgress>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error("输入验证失败: " + validation.getFormattedErrors());
                }

                List<String> filePaths = input.filePaths();
                int maxTotalLines = input.getMaxTotalLines();
                int maxTotalTokens = input.getMaxTotalTokens();

                List<BatchReadOutput.FileResult> results = new ArrayList<>();
                List<String> skippedFiles = new ArrayList<>();
                int totalLinesRead = 0;
                int totalTokens = 0;
                boolean truncated = false;
                String truncationReason = null;

                for (String filePathStr : filePaths) {
                    // 检查总限制
                    if (totalLinesRead >= maxTotalLines) {
                        truncated = true;
                        truncationReason = "超过总最大行数限制 (" + maxTotalLines + " 行)";
                        skippedFiles.add(filePathStr + " (因总内容限制跳过)");
                        continue;
                    }
                    if (totalTokens >= maxTotalTokens) {
                        truncated = true;
                        truncationReason = "超过总 token 限制 (" + maxTotalTokens + " tokens)";
                        skippedFiles.add(filePathStr + " (因总内容限制跳过)");
                        continue;
                    }

                    Path filePath = resolveFilePath(filePathStr, context);

                    if (!Files.exists(filePath)) {
                        skippedFiles.add(filePathStr + " (文件不存在)");
                        continue;
                    }

                    if (isBinaryFile(filePath)) {
                        skippedFiles.add(filePathStr + " (二进制文件已跳过)");
                        continue;
                    }

                    long fileSize = Files.size(filePath);
                    if (fileSize > MAX_FILE_SIZE) {
                        skippedFiles.add(filePathStr + " (文件超过大小限制)");
                        continue;
                    }

                    try {
                        List<String> lines = Files.readAllLines(filePath);
                        int totalLines = lines.size();

                        // 计算该文件可读取的行数
                        int remainingLines = maxTotalLines - totalLinesRead;
                        int linesToRead = Math.min(lines.size(), Math.min(remainingLines, MAX_LINES_PER_FILE));

                        StringBuilder contentBuilder = new StringBuilder();
                        for (int i = 0; i < linesToRead; i++) {
                            contentBuilder.append(lines.get(i)).append("\n");
                        }
                        String content = contentBuilder.toString();

                        int fileTokens = estimateTokens(content);
                        if (totalTokens + fileTokens > maxTotalTokens && !results.isEmpty()) {
                            // 至少保留第一个文件，后续超出限制则截断
                            truncated = true;
                            truncationReason = "超过总 token 限制 (" + maxTotalTokens + " tokens)";
                            skippedFiles.add(filePathStr + " (因总内容限制跳过)");
                            continue;
                        }

                        totalLinesRead += linesToRead;
                        totalTokens += fileTokens;

                        boolean fileTruncated = linesToRead < totalLines;
                        if (fileTruncated) {
                            content += "\n...[文件已截断，共 " + totalLines + " 行，已读 " + linesToRead + " 行]\n";
                        }

                        results.add(BatchReadOutput.FileResult.success(
                            filePath.toString(), content, totalLines, linesToRead
                        ));

                    } catch (IOException e) {
                        logger.warning("批量读取失败: " + filePath + " - " + e.getMessage());
                        results.add(BatchReadOutput.FileResult.error(filePath.toString(), e.getMessage()));
                    }
                }

                if (truncated) {
                    return ToolResult.success(BatchReadOutput.truncated(results, skippedFiles, truncationReason));
                } else {
                    return ToolResult.success(BatchReadOutput.success(results, skippedFiles));
                }

            } catch (Exception e) {
                logger.severe("BatchReadTool 执行异常: " + e.getMessage());
                return ToolResult.error("批量读取失败: " + e.getMessage());
            }
        });
    }

    @Override
    public ToolValidationResult validate(BatchReadInput input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();

        if (input.filePaths() == null || input.filePaths().isEmpty()) {
            builder.addError("file_paths 是必需的，且不能为空列表");
        } else if (input.filePaths().size() > MAX_FILES) {
            builder.addWarning("文件数量超过 " + MAX_FILES + " 个限制，将只处理前 " + MAX_FILES + " 个");
        }

        return builder.build();
    }

    @Override
    public boolean isReadOnly(BatchReadInput input) {
        return true;
    }

    @Override
    public boolean isConcurrencySafe(BatchReadInput input) {
        return true;
    }

    @Override
    public boolean isDestructive(BatchReadInput input) {
        return false;
    }

    private Path resolveFilePath(String filePath, ToolExecutionContext context) {
        Path path = Paths.get(filePath);
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

    private boolean isBinaryFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return BINARY_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private int estimateTokens(String text) {
        int chineseChars = (int) text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        int otherChars = text.length() - chineseChars;
        return (chineseChars / 2) + (otherChars / 4);
    }

    public static class BatchReadProgress {
        private final int filesProcessed;
        private final int totalFiles;
        private final String currentFile;

        public BatchReadProgress(int filesProcessed, int totalFiles, String currentFile) {
            this.filesProcessed = filesProcessed;
            this.totalFiles = totalFiles;
            this.currentFile = currentFile;
        }

        public int getFilesProcessed() { return filesProcessed; }
        public int getTotalFiles() { return totalFiles; }
        public String getCurrentFile() { return currentFile; }
        public double getProgress() { return totalFiles > 0 ? (double) filesProcessed / totalFiles : 0; }
    }
}
