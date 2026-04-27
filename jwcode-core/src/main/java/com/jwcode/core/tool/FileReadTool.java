package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.FileReadInput;
import com.jwcode.core.tool.output.FileReadOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * 文件读取工具（重构后）
 * 
 * 对标 JavaScript 项目的 FileReadTool
 * 支持文本文件、PDF、图片等多种格式
 */
public class FileReadTool implements Tool<FileReadInput, FileReadOutput, FileReadTool.FileReadProgress> {
    
    private static final Logger logger = Logger.getLogger(FileReadTool.class.getName());
    
    // 配置常量
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_LINES = 2000;
    private static final int MAX_TOKENS = 8000;
    
    // 支持的图片格式
    private static final List<String> IMAGE_EXTENSIONS = List.of(
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp"
    );
    
    // 支持的文档格式
    private static final List<String> DOCUMENT_EXTENSIONS = List.of(
        ".pdf", ".doc", ".docx", ".txt", ".md", ".java", ".py", ".js", ".ts", ".html", ".css"
    );
    
    @Override
    public String getName() {
        return "FileReadTool";
    }
    
    @Override
    public String getDescription() {
        return "读取文件内容。支持读取整个文件、指定行范围、或大文件的分块读取。也可以读取图片文件并返回 base64 编码。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 FileReadTool 读取文件内容。
               
               参数:
               - file_path: 要读取的文件路径（必需）
               - start_line: 起始行号，从 1 开始（可选，默认：1）
               - end_line: 结束行号（可选，默认：读取到文件末尾）
               - reason: 读取文件的原因（可选）
               - offset: 行偏移量（可选，默认：0）。在 start_line 基础上额外跳过的行数，用于大文件分块续读
               
               示例:
               - 读取整个文件：{"file_path": "src/main.java"}
               - 读取指定行：{"file_path": "src/main.java", "start_line": 1, "end_line": 50}
               - 读取最后 100 行：{"file_path": "log.txt", "start_line": -100}
               - 分块续读（跳过前 1000 行）：{"file_path": "log.txt", "start_line": 1, "offset": 1000, "end_line": 2000}
               
               注意:
               - 对于大文件，会自动截断以避免超出 token 限制
               - 图片文件会返回 base64 编码
               - PDF 文件会尝试提取文本内容
               - 不要猜测文件路径或文件名。如果不确定文件是否存在，先使用 GlobTool 搜索确认
                """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(FileReadInput.class);
    }
    
    @Override
    public TypeReference<FileReadInput> getInputType() {
        return new TypeReference<FileReadInput>() {};
    }
    
    @Override
    public TypeReference<FileReadOutput> getOutputType() {
        return new TypeReference<FileReadOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<FileReadOutput>> call(
            FileReadInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<FileReadProgress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error("输入验证失败: " + validation.getFormattedErrors());
                }
                
                // 检查权限
                if (!context.hasPermission("read", input.filePath())) {
                    return ToolResult.error("没有权限读取文件: " + input.filePath());
                }
                
                // 解析文件路径
                Path filePath = resolveFilePath(input.filePath(), context);
                if (!Files.exists(filePath)) {
                    return ToolResult.error("文件不存在: " + filePath);
                }
                
                // 检查文件大小
                long fileSize = Files.size(filePath);
                if (fileSize > MAX_FILE_SIZE) {
                    return ToolResult.error("文件太大（" + fileSize + " 字节），最大支持 " + MAX_FILE_SIZE + " 字节");
                }
                
                // 根据文件类型处理
                String fileName = filePath.getFileName().toString().toLowerCase();
                
                if (isImageFile(fileName)) {
                    return readImageFile(filePath, input);
                } else if (isDocumentFile(fileName)) {
                    return readDocumentFile(filePath, input);
                } else {
                    return readTextFile(filePath, input);
                }
                
            } catch (Exception e) {
                logger.severe("读取文件失败: " + e.getMessage());
                return ToolResult.error("读取文件失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(FileReadInput input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (input.filePath() == null || input.filePath().trim().isEmpty()) {
            builder.addError("file_path 是必需的");
        }
        
        if (input.startLine() != null && input.startLine() < 1) {
            builder.addError("start_line 必须大于 0");
        }
        
        if (input.endLine() != null && input.endLine() < 1) {
            builder.addError("end_line 必须大于 0");
        }
        
        if (input.startLine() != null && input.endLine() != null && input.startLine() > input.endLine()) {
            builder.addError("start_line 不能大于 end_line");
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(FileReadInput input) {
        return true;
    }
    
    @Override
    public boolean isConcurrencySafe(FileReadInput input) {
        return true;
    }
    
    @Override
    public boolean isDestructive(FileReadInput input) {
        return false;
    }
    
    /**
     * 解析文件路径
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
        
        return path.normalize();
    }
    
    /**
     * 检查是否是图片文件
     */
    private boolean isImageFile(String fileName) {
        return IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    /**
     * 检查是否是文档文件
     */
    private boolean isDocumentFile(String fileName) {
        return DOCUMENT_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    /**
     * 读取图片文件
     */
    private ToolResult<FileReadOutput> readImageFile(Path filePath, FileReadInput input) throws IOException {
        byte[] fileContent = Files.readAllBytes(filePath);
        String base64Data = Base64.getEncoder().encodeToString(fileContent);
        
        // 检测 MIME 类型
        String mimeType = detectMimeType(filePath);
        
        FileReadOutput output = FileReadOutput.image(
            filePath.toString(),
            mimeType,
            fileContent.length,
            base64Data
        );
        
        return ToolResult.success(output);
    }
    
    /**
     * 读取文档文件
     */
    private ToolResult<FileReadOutput> readDocumentFile(Path filePath, FileReadInput input) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        
        if (fileName.endsWith(".pdf")) {
            return readPdfFile(filePath, input);
        } else {
            return readTextFile(filePath, input);
        }
    }
    
    /**
     * 读取 PDF 文件
     */
    private ToolResult<FileReadOutput> readPdfFile(Path filePath, FileReadInput input) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            List<String> lines = text.lines().collect(Collectors.toList());
            int totalLines = lines.size();
            int startLine = 1;
            int endLine = totalLines;
            int linesToRead = totalLines;
            boolean truncated = false;
            String truncationReason = null;
            
            int estimatedTokens = estimateTokens(text);
            if (estimatedTokens > MAX_TOKENS) {
                truncated = true;
                truncationReason = "超过 token 限制 (" + MAX_TOKENS + " tokens)";
                int targetTokens = (int) (MAX_TOKENS * 0.8);
                TruncationResult result = truncateSmartly(lines, startLine, targetTokens);
                text = result.text;
                endLine = result.endLine;
                linesToRead = result.linesRead;
            }
            
            FileReadOutput output;
            if (truncated) {
                output = FileReadOutput.truncated(
                    filePath.toString(),
                    text,
                    totalLines,
                    startLine,
                    endLine,
                    linesToRead,
                    truncationReason
                );
            } else {
                output = FileReadOutput.text(
                    filePath.toString(),
                    text,
                    totalLines,
                    startLine,
                    endLine,
                    linesToRead
                );
            }
            return ToolResult.success(output);
        } catch (Exception e) {
            logger.severe("读取 PDF 失败: " + e.getMessage());
            return ToolResult.error("读取 PDF 失败: " + e.getMessage());
        }
    }
    
    /**
     * 读取文本文件
     */
    private ToolResult<FileReadOutput> readTextFile(Path filePath, FileReadInput input) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        int totalLines = lines.size();
        
        // 计算读取范围（支持 offset 分块续读）
        int effectiveOffset = input.getEffectiveOffset();
        int startLine = input.getEffectiveStartLine() + effectiveOffset;
        int endLine = input.endLine() != null ? input.endLine() : totalLines;
        
        // 调整范围
        if (startLine < 1) startLine = 1;
        if (endLine > totalLines) endLine = totalLines;
        if (startLine > endLine) startLine = endLine;
        
        // 检查是否需要截断
        int linesToRead = endLine - startLine + 1;
        boolean truncated = false;
        String truncationReason = null;
        
        if (linesToRead > MAX_LINES) {
            endLine = startLine + MAX_LINES - 1;
            linesToRead = MAX_LINES;
            truncated = true;
            truncationReason = "超过最大行数限制 (" + MAX_LINES + " 行)";
        }
        
        // 提取选中的行
        List<String> selectedLines = lines.subList(startLine - 1, endLine);
        
        // 读取内容
        StringBuilder content = new StringBuilder();
        for (String line : selectedLines) {
            content.append(line).append("\n");
        }
        
        // 检查 token 数量
        String text = content.toString();
        int estimatedTokens = estimateTokens(text);
        if (estimatedTokens > MAX_TOKENS) {
            truncated = true;
            truncationReason = "超过 token 限制 (" + MAX_TOKENS + " tokens)";
            int targetTokens = (int) (MAX_TOKENS * 0.8);
            TruncationResult result = truncateSmartly(selectedLines, startLine, targetTokens);
            text = result.text;
            endLine = result.endLine;
            linesToRead = result.linesRead;
        }
        
        // 构建输出
        FileReadOutput output;
        if (truncated) {
            output = FileReadOutput.truncated(
                filePath.toString(),
                text,
                totalLines,
                startLine,
                endLine,
                linesToRead,
                truncationReason
            );
        } else {
            output = FileReadOutput.text(
                filePath.toString(),
                text,
                totalLines,
                startLine,
                endLine,
                linesToRead
            );
        }
        
        return ToolResult.success(output);
    }
    
    /**
     * 检测 MIME 类型
     */
    private String detectMimeType(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".bmp")) return "image/bmp";
        if (fileName.endsWith(".webp")) return "image/webp";
        
        // 默认使用 Files.probeContentType
        String mimeType = Files.probeContentType(filePath);
        return mimeType != null ? mimeType : "application/octet-stream";
    }
    
    /**
     * 估算 token 数量
     */
    private int estimateTokens(String text) {
        // 简单估算：英文大约 4 个字符一个 token，中文大约 2 个字符一个 token
        int chineseChars = (int) text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        int otherChars = text.length() - chineseChars;
        
        return (chineseChars / 2) + (otherChars / 4);
    }
    
    /**
     * 智能截断结果
     */
    private static class TruncationResult {
        final String text;
        final int endLine;
        final int linesRead;
        
        TruncationResult(String text, int endLine, int linesRead) {
            this.text = text;
            this.endLine = endLine;
            this.linesRead = linesRead;
        }
    }
    
    /**
     * 边界类型
     */
    private enum BoundaryType {
        PARAGRAPH, METHOD, LINE
    }
    
    /**
     * 智能截断内容
     */
    private TruncationResult truncateSmartly(List<String> lines, int startLine, int targetTokens) {
        String marker = "\n\n[...内容已截断...]";
        int markerTokens = estimateTokens(marker);
        int budget = targetTokens - markerTokens;
        
        // 1. 尝试段落边界
        int linesKept = truncateAtBoundaries(lines, budget, BoundaryType.PARAGRAPH);
        if (linesKept > 0) {
            String text = buildText(lines, linesKept);
            return new TruncationResult(text + marker, startLine + linesKept - 1, linesKept);
        }
        
        // 2. 尝试方法/作用域边界
        linesKept = truncateAtBoundaries(lines, budget, BoundaryType.METHOD);
        if (linesKept > 0) {
            String text = buildText(lines, linesKept);
            return new TruncationResult(text + marker, startLine + linesKept - 1, linesKept);
        }
        
        // 3. 回退到行边界
        linesKept = truncateAtBoundaries(lines, budget, BoundaryType.LINE);
        String text = buildText(lines, linesKept);
        return new TruncationResult(text + marker, startLine + linesKept - 1, linesKept);
    }
    
    /**
     * 在指定边界类型处截断
     */
    private int truncateAtBoundaries(List<String> lines, int budget, BoundaryType type) {
        StringBuilder content = new StringBuilder();
        int lastValid = 0;
        
        for (int i = 0; i < lines.size(); i++) {
            content.append(lines.get(i)).append("\n");
            
            // 判断在此行之后是否可以截断
            boolean canBreak = false;
            if (i == lines.size() - 1) {
                canBreak = true; // 文件末尾
            } else if (type == BoundaryType.LINE) {
                canBreak = true; // 每行之后都可以截断
            } else if (type == BoundaryType.PARAGRAPH) {
                canBreak = lines.get(i).trim().isEmpty(); // 空行之后可以截断
            } else if (type == BoundaryType.METHOD) {
                String nextLine = lines.get(i + 1).trim();
                canBreak = lines.get(i).trim().isEmpty() ||
                    nextLine.startsWith("public ") || nextLine.startsWith("private ") ||
                    nextLine.startsWith("protected ") || nextLine.startsWith("def ") ||
                    nextLine.startsWith("function ") || nextLine.startsWith("class ") ||
                    nextLine.startsWith("interface ") || nextLine.contains("{") || nextLine.contains("}");
            }
            
            if (canBreak) {
                int tokens = estimateTokens(content.toString());
                if (tokens <= budget) {
                    lastValid = i + 1;
                } else {
                    break;
                }
            }
        }
        
        return lastValid;
    }
    
    /**
     * 根据行数构建文本
     */
    private String buildText(List<String> lines, int linesKept) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < linesKept; i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 文件读取进度
     */
    public static class FileReadProgress {
        private final int bytesRead;
        private final int totalBytes;
        private final String fileName;
        
        public FileReadProgress(int bytesRead, int totalBytes, String fileName) {
            this.bytesRead = bytesRead;
            this.totalBytes = totalBytes;
            this.fileName = fileName;
        }
        
        public int getBytesRead() { return bytesRead; }
        public int getTotalBytes() { return totalBytes; }
        public String getFileName() { return fileName; }
        public double getProgress() { return totalBytes > 0 ? (double) bytesRead / totalBytes : 0; }
    }
    
    /**
     * 输入类型（Tool 接口需要）
     */
    public static class Input {
        public String path;
        public Integer limit;
        public Boolean withMetadata;
        
        public Input() {}
        
        public Input(String path) {
            this.path = path;
        }
    }
    
    /**
     * 输出类型（Tool 接口需要）
     */
    public static class Output {
        public String content;
        public String path;
        public java.util.Map<String, Object> metadata;
        
        public Output() {}
    }
    
    /**
     * 进度类型（Tool 接口需要）
     */
    public static class Progress {
        private final int bytesRead;
        private final int totalBytes;
        private final String fileName;
        
        public Progress(int bytesRead, int totalBytes, String fileName) {
            this.bytesRead = bytesRead;
            this.totalBytes = totalBytes;
            this.fileName = fileName;
        }
        
        public int getBytesRead() { return bytesRead; }
        public int getTotalBytes() { return totalBytes; }
        public String getFileName() { return fileName; }
        public double getProgress() { return totalBytes > 0 ? (double) bytesRead / totalBytes : 0; }
    }
}
