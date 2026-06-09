package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.GlobInput;
import com.jwcode.core.tool.output.GlobOutput;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.jwcode.core.tool.FileNameCache;

/**
 * Glob 文件搜索工具（重构后）
 * 
 * 用于使用 glob 模式搜索文件，支持：
 * - 标准 glob 模式匹配 (*, **, ?, [])
 * - 正则表达式匹配
 * - 排除模式
 * - 限制结果数量
 * 
 * 对标 JavaScript 项目的 GlobTool
 */
public class GlobTool implements Tool<GlobInput, GlobOutput, GlobTool.GlobProgress> {
    
    private static final Logger logger = Logger.getLogger(GlobTool.class.getName());
    
    private static final int DEFAULT_MAX_RESULTS = 500;
    private static final int MAX_MAX_RESULTS = 5000;

    /** 文件名索引缓存（加速重复搜索） */
    private FileNameCache fileNameCache;
    
    @Override
    public String getName() {
        return "GlobTool";
    }
    
    @Override
    public String getDescription() {
        return "使用 glob 模式搜索文件。支持通配符 (*, **, ?) 和正则表达式。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 GlobTool 搜索匹配模式的文件。
               
               参数:
               - pattern: glob 模式或正则表达式（必需）
               - path: 搜索的起始路径（可选，默认：当前目录）
               - is_regex: 是否使用正则表达式（可选，默认：false）
               - exclude: 排除模式（可选）
               - max_results: 最大结果数量（可选，默认：100）
               
               Glob 模式示例:
               - "*.java" - 所有 Java 文件
               - "**/*.java" - 所有目录下的 Java 文件
               - "src/**/*.ts" - src 目录下的所有 TypeScript 文件
               - "test_*.py" - 以 test_ 开头的 Python 文件
               - "**/*.{java,py}" - 所有 Java 和 Python 文件
               
               正则表达式示例:
               - ".*\\.java$" - 所有 Java 文件
               - "^test_.*\\.py$" - 以 test_ 开头的 Python 文件
               
               排除模式示例:
                - "**/node_modules/**" - 排除 node_modules 目录
               
               注意: 结果会被缓存，相同参数的重复搜索会直接返回缓存结果，无需重复调用。
                - "**/*.min.js" - 排除压缩的 JS 文件
                """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(GlobInput.class);
    }
    
    @Override
    public TypeReference<GlobInput> getInputType() {
        return new TypeReference<GlobInput>() {};
    }
    
    @Override
    public TypeReference<GlobOutput> getOutputType() {
        return new TypeReference<GlobOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<GlobOutput>> call(
            GlobInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<GlobProgress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error("输入验证失败: " + validation.getFormattedErrors());
                }
                
                // 检查权限
                if (!context.hasPermission("read", input.getPathOrDefault())) {
                    return ToolResult.error("没有权限读取目录: " + input.getPathOrDefault());
                }
                
                // 执行搜索
                return searchFiles(input, context);
                
            } catch (Exception e) {
                logger.severe("文件搜索失败: " + e.getMessage());
                return ToolResult.error("文件搜索失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(GlobInput input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (input.pattern() == null || input.pattern().trim().isEmpty()) {
            builder.addError("pattern 是必需的");
        }
        
        if (input.maxResults() != null && input.maxResults() < 1) {
            builder.addError("max_results 必须大于 0");
        }
        
        if (input.maxResults() != null && input.maxResults() > MAX_MAX_RESULTS) {
            builder.addWarning("max_results 超过最大限制 " + MAX_MAX_RESULTS + "，将使用 " + MAX_MAX_RESULTS);
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(GlobInput input) {
        return true;
    }
    
    @Override
    public boolean isConcurrencySafe(GlobInput input) {
        return true;
    }
    
    @Override
    public boolean isDestructive(GlobInput input) {
        return false;
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SEARCH;
    }

    @Override
    public Set<SideEffect> getSideEffects() {
        return Set.of(SideEffect.READ_ONLY);
    }
    
    /**
     * 获取或初始化 FileNameCache。
     */
    private FileNameCache getFileNameCache(ToolExecutionContext context) {
        if (fileNameCache == null) {
            java.nio.file.Path wd = context != null && context.getWorkingDirectory() != null
                ? context.getWorkingDirectory()
                : java.nio.file.Path.of(System.getProperty("user.dir"));
            // 找到项目根（包含 .jwcode 目录的父目录）
            java.nio.file.Path root = findProjectRoot(wd);
            fileNameCache = new FileNameCache(root);
            // 首次使用：如果缓存无效则重建
            if (!fileNameCache.isValid()) {
                fileNameCache.rebuild();
            } else {
                fileNameCache.refresh();
            }
        }
        return fileNameCache;
    }

    /**
     * 向上查找包含 .jwcode 目录的项目根。
     */
    private java.nio.file.Path findProjectRoot(java.nio.file.Path start) {
        java.nio.file.Path current = start.normalize().toAbsolutePath();
        while (current != null) {
            if (java.nio.file.Files.exists(current.resolve(".jwcode"))) {
                return current;
            }
            current = current.getParent();
        }
        return start.normalize().toAbsolutePath();
    }

    /**
     * 搜索文件
     */
    private ToolResult<GlobOutput> searchFiles(GlobInput input, ToolExecutionContext context) {
        try {
            String pattern = input.pattern();
             String searchPath = input.getPathOrDefault();
             boolean isRegex = input.isRegex();
             String excludePattern = input.exclude();
             int maxResults = input.getMaxResults();
             
             // 【修复】空路径处理 - 如果 path 为空或 null，使用当前目录 "."
             if (searchPath == null || searchPath.trim().isEmpty()) {
                 searchPath = "." ;
                 logger.fine("GlobTool: path 为空，使用当前目录: .");
             }
             
             Path startPath = Paths.get(searchPath);
            
            logger.fine("GlobTool: 搜索 pattern=" + pattern + ", path=" + startPath + ", isRegex=" + isRegex);

            // 【文件名缓存加速】优先使用 FileNameCache
            boolean useCache = true;
            // 如果搜索路径不是项目根目录，且不是以项目根开头的路径，则回退到全量遍历
            if (context != null && context.getWorkingDirectory() != null) {
                java.nio.file.Path wd = context.getWorkingDirectory();
                java.nio.file.Path root = findProjectRoot(wd);
                if (!startPath.normalize().toAbsolutePath().startsWith(root)) {
                    useCache = false;
                }
            }

            List<String> matchedFiles;
            if (useCache) {
                FileNameCache cache = getFileNameCache(context);
                matchedFiles = cache.search(pattern, isRegex, excludePattern, maxResults);
                logger.fine("GlobTool: 使用缓存搜索 | pattern=" + pattern + " | 结果数=" + matchedFiles.size());
            } else {
                matchedFiles = performSearch(startPath, pattern, isRegex, excludePattern, maxResults);
            }
            
            // 构建输出
            GlobOutput output = GlobOutput.success(
                pattern,
                startPath.toAbsolutePath().toString(),
                isRegex,
                excludePattern,
                maxResults,
                matchedFiles
            );
            
            return ToolResult.success(output);
            
        } catch (IOException e) {
            return ToolResult.error("搜索文件失败: " + e.getMessage());
        } catch (PatternSyntaxException e) {
            return ToolResult.error("正则表达式语法错误: " + e.getMessage());
        }
    }
    
    /**
     * 执行文件搜索
     */
    private List<String> performSearch(Path startPath, String pattern, boolean isRegex, 
                                     String excludePattern, int maxResults) throws IOException {
        List<String> results = new ArrayList<>();
        
        // 编译模式
        Pattern compiledPattern = isRegex ? Pattern.compile(pattern) : compileGlobPattern(pattern);
        Pattern compiledExclude = excludePattern != null ? 
            (excludePattern.startsWith("regex:") ? 
                Pattern.compile(excludePattern.substring(6)) : 
                compileGlobPattern(excludePattern)) : null;
        
        // 规范化起始路径
        final Path normalizedStartPath = startPath.normalize();
        if (!Files.exists(normalizedStartPath)) {
            throw new IOException("搜索路径不存在: " + normalizedStartPath);
        }
        
        // 如果是文件，直接检查
        if (Files.isRegularFile(normalizedStartPath)) {
            String relativePath = normalizedStartPath.getFileName().toString();
            if (matchesPattern(relativePath, compiledPattern) && 
                (compiledExclude == null || !matchesPattern(relativePath, compiledExclude))) {
                results.add(normalizedStartPath.toString());
            }
            return results;
        }
        
        // 遍历目录
        Files.walkFileTree(normalizedStartPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (results.size() >= maxResults) {
                    return FileVisitResult.TERMINATE;
                }
                
                try {
                    // 获取相对路径
                    Path relativePath = normalizedStartPath.relativize(file);
                    String relativePathStr = relativePath.toString();
                    
                    // 检查是否匹配
                    if (matchesPattern(relativePathStr, compiledPattern)) {
                        // 检查是否被排除
                        if (compiledExclude == null || !matchesPattern(relativePathStr, compiledExclude)) {
                            results.add(file.toString());
                        }
                    }
                } catch (Exception e) {
                    // 忽略单个文件的错误
                }
                
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                
                // 默认忽略的目录模式（这些目录通常不包含用户代码）
                Set<String> ignoredDirs = new HashSet<>(Arrays.asList(
                    // 版本控制
                    ".git", ".svn", ".hg", ".cvs",
                    // 构建输出
                    "target", "build", "dist", "out", "bin",
                    // 依赖目录
                    "node_modules", "bower_components", "vendor",
                    // Python
                    "__pycache__", ".venv", "venv", ".env",
                    // Java
                    ".gradle", ".idea",
                    // 其他
                    ".cache", ".temp", "tmp", ".tmp"
                ));
                
                // 跳过隐藏目录和常见的大型无关目录
                if (dirName.startsWith(".") && !dirName.equals("..")) {
                    // 但允许 .github, .vscode 等常见目录
                    if (!dirName.equals(".github") && !dirName.equals(".vscode") && 
                        !dirName.equals(".idea") && !dirName.equals(".settings")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                
                // 跳过常见的依赖目录
                if (ignoredDirs.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
        
        return results;
    }
    
    /**
     * 编译 glob 模式为正则表达式
     */
    private Pattern compileGlobPattern(String glob) {
        // 将 glob 模式转换为正则表达式
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            
            switch (c) {
                case '*':
                    // 检查是否是 **
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++; // 跳过第二个 *
                        // 如果后面是 /，则保留
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            i++; // 跳过 /
                        }
                    } else {
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                    regex.append("[^/]");
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                case '+':
                    regex.append("\\+");
                    break;
                case '^':
                    regex.append("\\^");
                    break;
                case '$':
                    regex.append("\\$");
                    break;
                case '(':
                case ')':
                case '|':
                    regex.append("\\").append(c);
                    break;
                case '[':
                    // 字符类，直接复制
                    regex.append('[');
                    i++;
                    while (i < glob.length() && glob.charAt(i) != ']') {
                        if (glob.charAt(i) == '\\') {
                            regex.append("\\\\");
                        } else {
                            regex.append(glob.charAt(i));
                        }
                        i++;
                    }
                    regex.append(']');
                    break;
                case '\\':
                    // 转义字符
                    if (i + 1 < glob.length()) {
                        regex.append("\\\\").append(glob.charAt(i + 1));
                        i++;
                    }
                    break;
                default:
                    regex.append(c);
            }
        }
        
        regex.append("$");
        return Pattern.compile(regex.toString());
    }
    
    /**
     * 【简化修复】检查路径是否匹配模式
     * 只检查完整路径匹配，避免过度复杂的检查导致性能问题
     */
    private boolean matchesPattern(String path, Pattern pattern) {
        // 直接检查完整路径是否匹配
        return pattern.matcher(path).matches();
    }
    
    /**
     * Glob 搜索进度
     */
    public static class GlobProgress {
        private final String pattern;
        private final int filesFound;
        private final int directoriesScanned;
        private final boolean completed;
        
        public GlobProgress(String pattern, int filesFound, int directoriesScanned, boolean completed) {
            this.pattern = pattern;
            this.filesFound = filesFound;
            this.directoriesScanned = directoriesScanned;
            this.completed = completed;
        }
        
        public String getPattern() { return pattern; }
        public int getFilesFound() { return filesFound; }
        public int getDirectoriesScanned() { return directoriesScanned; }
        public boolean isCompleted() { return completed; }
        public double getProgress() { return completed ? 1.0 : 0.5; }
    }
    
    /**
     * 输入类型（Tool 接口需要）
     */
    public static class Input {
        public String pattern;
        public String path;
        public Integer limit;
        
        public Input() {}
        
        public Input(String pattern) {
            this.pattern = pattern;
        }
    }
    
    /**
     * 输出类型（Tool 接口需要）
     */
    public static class Output {
        public java.util.List<String> files;
        
        public Output() {}
    }
    
    /**
     * 进度类型（Tool 接口需要）
     */
    public static class Progress {
        private final String pattern;
        private final int filesFound;
        private final int directoriesScanned;
        private final boolean completed;
        
        public Progress(String pattern, int filesFound, int directoriesScanned, boolean completed) {
            this.pattern = pattern;
            this.filesFound = filesFound;
            this.directoriesScanned = directoriesScanned;
            this.completed = completed;
        }
        
        public String getPattern() { return pattern; }
        public int getFilesFound() { return filesFound; }
        public int getDirectoriesScanned() { return directoriesScanned; }
        public boolean isCompleted() { return completed; }
        public double getProgress() { return completed ? 1.0 : 0.5; }
    }
}
