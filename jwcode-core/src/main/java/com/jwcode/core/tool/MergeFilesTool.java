package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.MergeFilesInput;
import com.jwcode.core.tool.output.MergeFilesOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件合并工具
 *
 * <p>支持按 glob 模式搜索文件或将指定文件列表合并为一个文件。
 * 自动跳过二进制文件，按文件名排序，自动管理总大小。</p>
 */
public class MergeFilesTool implements Tool<MergeFilesInput, MergeFilesOutput, MergeFilesTool.MergeFilesProgress> {

    private static final Logger logger = Logger.getLogger(MergeFilesTool.class.getName());

    // 配置常量
    private static final long MAX_TOTAL_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_FILES = 100;
    private static final List<String> BINARY_EXTENSIONS = List.of(
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".ico",
        ".pdf", ".zip", ".jar", ".war", ".class", ".exe", ".dll", ".so",
        ".mp3", ".mp4", ".avi", ".mov", ".wav",
        ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx"
    );

    @Override
    public String getName() {
        return "MergeFilesTool";
    }

    @Override
    public String getDescription() {
        return "将多个文件按指定模式搜索或直接合并为一个文件。自动跳过二进制文件，按文件名排序。";
    }

    @Override
    public String getPrompt() {
        return """
               使用 MergeFilesTool 将多个文件合并为一个文件。

               参数:
               - source_pattern: 文件搜索模式（glob，如 "*.md" 或 "docs/**/*.md"）
               - source_paths: 文件路径列表（与 source_pattern 二选一）
               - output_path: 合并后的输出文件路径（必需）
               - separator: 文件之间的分隔符（可选，默认：\\n\\n---\\n\\n）

               示例:
               - 合并所有 md 文件：{"source_pattern": "*.md", "output_path": "merged.md"}
               - 合并指定文件：{"source_paths": ["a.md", "b.md"], "output_path": "merged.md", "separator": "\\n\\n"}

               注意:
               - 优先于"先列清单再逐个读取"的复杂策略，1 轮即可完成合并
               - 自动跳过二进制文件和超过大小限制的文件
               - 如果总内容超过 10MB，会自动截断并警告
               """;
    }

    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(MergeFilesInput.class);
    }

    @Override
    public TypeReference<MergeFilesInput> getInputType() {
        return new TypeReference<MergeFilesInput>() {};
    }

    @Override
    public TypeReference<MergeFilesOutput> getOutputType() {
        return new TypeReference<MergeFilesOutput>() {};
    }

    @Override
    public CompletableFuture<ToolResult<MergeFilesOutput>> call(
            MergeFilesInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<MergeFilesProgress>> onProgress) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error("输入验证失败: " + validation.getFormattedErrors());
                }

                Path outputPath = resolveFilePath(input.outputPath(), context);

                // 创建父目录
                Path parentDir = outputPath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }

                // 获取源文件列表
                List<Path> sourceFiles = resolveSourceFiles(input, context);
                if (sourceFiles.isEmpty()) {
                    return ToolResult.error("未找到任何源文件，请检查 source_pattern 或 source_paths");
                }

                String separator = input.getSeparator();
                List<String> skippedFiles = new ArrayList<>();
                StringBuilder mergedContent = new StringBuilder();
                long totalSize = 0;
                int filesMerged = 0;
                boolean truncated = false;

                for (Path sourcePath : sourceFiles) {
                    if (filesMerged >= MAX_FILES) {
                        skippedFiles.add(sourcePath.toString() + " (超过最大文件数限制 " + MAX_FILES + ")");
                        continue;
                    }

                    if (!Files.exists(sourcePath)) {
                        skippedFiles.add(sourcePath.toString() + " (文件不存在)");
                        continue;
                    }

                    if (isBinaryFile(sourcePath)) {
                        skippedFiles.add(sourcePath.toString() + " (二进制文件已跳过)");
                        continue;
                    }

                    long fileSize = Files.size(sourcePath);
                    if (totalSize + fileSize > MAX_TOTAL_SIZE) {
                        truncated = true;
                        skippedFiles.add(sourcePath.toString() + " (因总大小限制跳过)");
                        continue;
                    }

                    try {
                        String content = Files.readString(sourcePath);

                        if (mergedContent.length() > 0) {
                            mergedContent.append(separator);
                        }

                        mergedContent.append("<!-- ").append(sourcePath.getFileName()).append(" -->\n");
                        mergedContent.append(content);

                        totalSize += fileSize;
                        filesMerged++;

                    } catch (IOException e) {
                        logger.warning("读取文件失败: " + sourcePath + " - " + e.getMessage());
                        skippedFiles.add(sourcePath.toString() + " (读取失败: " + e.getMessage() + ")");
                    }
                }

                // 写入合并后的文件
                Files.writeString(outputPath, mergedContent.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                if (truncated) {
                    return ToolResult.success(MergeFilesOutput.truncated(
                        outputPath.toString(), filesMerged, totalSize, skippedFiles,
                        "部分文件因总大小限制（" + MAX_TOTAL_SIZE + " 字节）未合并"
                    ));
                } else {
                    return ToolResult.success(MergeFilesOutput.success(
                        outputPath.toString(), filesMerged, totalSize, skippedFiles
                    ));
                }

            } catch (Exception e) {
                logger.severe("MergeFilesTool 执行异常: " + e.getMessage());
                return ToolResult.error("合并文件失败: " + e.getMessage());
            }
        });
    }

    @Override
    public ToolValidationResult validate(MergeFilesInput input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();

        if ((input.sourcePattern() == null || input.sourcePattern().isBlank()) &&
            (input.sourcePaths() == null || input.sourcePaths().isEmpty())) {
            builder.addError("source_pattern 和 source_paths 必须至少提供一个");
        }

        if (input.outputPath() == null || input.outputPath().isBlank()) {
            builder.addError("output_path 是必需的");
        }

        return builder.build();
    }

    @Override
    public boolean isReadOnly(MergeFilesInput input) {
        return false;
    }

    @Override
    public boolean isConcurrencySafe(MergeFilesInput input) {
        return false;
    }

    @Override
    public boolean isDestructive(MergeFilesInput input) {
        return true;
    }

    @Override
    public boolean requiresApproval(MergeFilesInput input) {
        return false;
    }

    private List<Path> resolveSourceFiles(MergeFilesInput input, ToolExecutionContext context) throws IOException {
        List<Path> files = new ArrayList<>();

        if (input.sourcePaths() != null && !input.sourcePaths().isEmpty()) {
            for (String pathStr : input.sourcePaths()) {
                files.add(resolveFilePath(pathStr, context));
            }
        } else if (input.sourcePattern() != null && !input.sourcePattern().isBlank()) {
            Path searchPath = context.getWorkingDirectory() != null
                ? context.getWorkingDirectory()
                : Paths.get(".");

            // Windows 路径归一化：将反斜杠替换为正斜杠
            String pattern = input.sourcePattern().replace('\\', '/');
            boolean isRecursive = pattern.contains("**");

            try (Stream<Path> stream = isRecursive
                ? Files.walk(searchPath)
                : Files.list(searchPath)) {

                files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> matchesGlob(p, pattern, searchPath))
                    .limit(MAX_FILES)
                    .collect(Collectors.toList());
            }
        }

        // 按文件名排序，确保合并顺序稳定
        files.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return files;
    }

    private boolean matchesGlob(Path file, String pattern, Path basePath) {
        String fileName = file.getFileName().toString();
        String relativePath = basePath.relativize(file).toString().replace('\\', '/');

        // 简单 glob 匹配：支持 * 和 ?
        String regex = pattern
            .replace(".", "\\.")
            .replace("**", "<<<DOUBLESTAR>>>")
            .replace("*", "[^/]*")
            .replace("?", "[^/]")
            .replace("<<<DOUBLESTAR>>>", ".*");

        // 如果模式包含 /，匹配相对路径；否则只匹配文件名
        if (pattern.contains("/")) {
            return relativePath.matches(regex);
        } else {
            return fileName.matches(regex);
        }
    }

    private Path resolveFilePath(String filePath, ToolExecutionContext context) {
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            Path workingDir = context.getWorkingDirectory();
            if (workingDir != null) {
                path = workingDir.resolve(path);
            }
        }
        return path.normalize();
    }

    private boolean isBinaryFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return BINARY_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    public static class MergeFilesProgress {
        private final int filesProcessed;
        private final int totalFiles;
        private final String currentFile;

        public MergeFilesProgress(int filesProcessed, int totalFiles, String currentFile) {
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
