package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.GrepInput;
import com.jwcode.core.tool.output.GrepOutput;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Grep 文本搜索工具（重构后）
 * 
 * 用于在文件中搜索文本内容，支持：
 * - 简单字符串匹配
 * - 正则表达式匹配
 * - 忽略大小写
 * - 上下文行显示
 * - 文件类型过滤
 * 
 * 对标 JavaScript 项目的 GrepTool
 */
public class GrepTool implements Tool<GrepInput, GrepOutput, GrepTool.GrepProgress> {
    
    private static final Logger logger = Logger.getLogger(GrepTool.class.getName());

    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final int MAX_MAX_RESULTS = 500;
    private static final int DEFAULT_CONTEXT_LINES = 2;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // Grep 结果缓存：减少重复搜索，TTL 30 秒
    private static final long CACHE_TTL_MS = 30_000;
    private static final java.util.concurrent.ConcurrentHashMap<String, GrepCacheEntry> grepCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class GrepCacheEntry {
        final GrepOutput output;
        final long timestamp;
        GrepCacheEntry(GrepOutput output) {
            this.output = output;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }
    
    @Override
    public String getName() {
        return "GrepTool";
    }
    
    @Override
    public String getDescription() {
        return "在文件中搜索文本内容。支持正则表达式、忽略大小写、上下文显示等功能。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 GrepTool 在文件中搜索文本内容。
               
               参数:
               - pattern: 搜索模式（必需）
               - path: 搜索的起始路径（可选，默认：当前目录）
               - is_regex: 是否使用正则表达式（可选，默认：false）
               - ignore_case: 忽略大小写（可选，默认：false）
               - file_pattern: 文件匹配模式（可选，如 "*.java"）
               - exclude: 排除模式（可选）
               - context: 上下文行数（可选，默认：2）
               - max_results: 最大结果数量（可选，默认：100）
               
               示例:
               - {"pattern": "TODO"} - 搜索所有 TODO
               - {"pattern": "TODO|FIXME", "is_regex": true} - 搜索 TODO 或 FIXME
               - {"pattern": "function.*test", "is_regex": true, "file_pattern": "*.js"} - 在 JS 文件中搜索
               - {"pattern": "TODO", "context": 5} - 显示 5 行上下文
               
                输出格式:
                每个匹配显示文件路径、行号和匹配内容，以及可选的上下文行。
                """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(GrepInput.class);
    }
    
    @Override
    public TypeReference<GrepInput> getInputType() {
        return new TypeReference<GrepInput>() {};
    }
    
    @Override
    public TypeReference<GrepOutput> getOutputType() {
        return new TypeReference<GrepOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<GrepOutput>> call(
            GrepInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<GrepProgress>> onProgress) {
        
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
                
                // 缓存检查：避免短时间内重复搜索
                String cacheKey = input.pattern() + "@" + input.getPathOrDefault()
                    + "#" + input.isRegex() + "#" + input.isIgnoreCase()
                    + "#" + (input.filePattern() != null ? input.filePattern() : "");
                GrepCacheEntry cached = grepCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    logger.fine("Grep cache hit: " + cacheKey);
                    return ToolResult.success(cached.output);
                }

                // 执行搜索
                ToolResult<GrepOutput> result = searchFiles(input, context);

                // 缓存成功结果
                if (result.isSuccess()) {
                    grepCache.put(cacheKey, new GrepCacheEntry(result.getData()));
                }

                return result;

            } catch (Exception e) {
                logger.severe("文本搜索失败: " + e.getMessage());
                return ToolResult.error("文本搜索失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(GrepInput input) {
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
        
        if (input.context() != null && input.context() < 0) {
            builder.addError("context 不能为负数");
        }
        
        if (input.context() != null && input.context() > 10) {
            builder.addWarning("context 超过建议值 10，可能会影响性能");
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(GrepInput input) {
        return true;
    }
    
    @Override
    public boolean isConcurrencySafe(GrepInput input) {
        return true;
    }
    
    @Override
    public boolean isDestructive(GrepInput input) {
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
     * 搜索文件
     */
    private ToolResult<GrepOutput> searchFiles(GrepInput input, ToolExecutionContext toolContext) {
        try {
            String pattern = input.pattern();
            String searchPath = input.getPathOrDefault();
            boolean isRegex = input.isRegexBoolean();
            boolean ignoreCase = input.isIgnoreCaseBoolean();
            String filePattern = input.filePattern();
            String excludePattern = input.exclude();
            int contextLines = input.getContext();
            int maxResults = input.getMaxResults();
            
            Path startPath = Paths.get(searchPath);
            
            logger.info("GrepTool: 搜索 pattern=" + pattern + ", path=" + startPath + ", isRegex=" + isRegex);
            
            // 编译搜索模式
            Pattern compiledPattern = compileSearchPattern(pattern, isRegex, ignoreCase);
            
            // 编译文件过滤模式
            Pattern compiledFilePattern = filePattern != null ? compileGlobPattern(filePattern) : null;
            Pattern compiledExclude = excludePattern != null ? compileGlobPattern(excludePattern) : null;
            
            // 执行搜索
            List<GrepOutput.GrepMatch> matches = performSearch(
                startPath, compiledPattern, compiledFilePattern, compiledExclude, contextLines, maxResults);
            
            // 构建输出
            GrepOutput output = GrepOutput.success(
                pattern,
                startPath.toAbsolutePath().toString(),
                isRegex,
                ignoreCase,
                filePattern,
                excludePattern,
                contextLines,
                maxResults,
                matches
            );
            
            return ToolResult.success(output);
            
        } catch (PatternSyntaxException e) {
            return ToolResult.error("正则表达式语法错误: " + e.getMessage());
        } catch (IOException e) {
            return ToolResult.error("搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 编译搜索模式
     */
    private Pattern compileSearchPattern(String pattern, boolean isRegex, boolean ignoreCase) 
            throws PatternSyntaxException {
        if (isRegex) {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            return Pattern.compile(pattern, flags);
        } else {
            // 转义特殊字符，作为普通字符串搜索
            String escaped = Pattern.quote(pattern);
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            return Pattern.compile(escaped, flags);
        }
    }
    
    /**
     * 编译 glob 文件模式
     */
    private Pattern compileGlobPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
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
                case '[':
                case ']':
                case '{':
                case '}':
                    regex.append("\\").append(c);
                    break;
                case '\\':
                    if (i + 1 < glob.length()) {
                        regex.append("\\\\").append(glob.charAt(i + 1));
                        i++;
                    }
                    break;
                default:
                    regex.append(c);
            }
        }
        
        return Pattern.compile("^" + regex.toString() + "$");
    }
    
    /**
     * 执行搜索
     */
    private List<GrepOutput.GrepMatch> performSearch(Path startPath, Pattern searchPattern, 
                                                     Pattern filePattern, Pattern excludePattern,
                                                     int context, int maxResults) throws IOException {
        List<GrepOutput.GrepMatch> results = new ArrayList<>();
        
        final Path normalizedStartPath = startPath.normalize();
        if (!Files.exists(normalizedStartPath)) {
            throw new IOException("搜索路径不存在: " + normalizedStartPath);
        }
        
        // 如果是单个文件
        if (Files.isRegularFile(normalizedStartPath)) {
            if (matchesFilePattern(normalizedStartPath.getFileName().toString(), filePattern, excludePattern)) {
                List<GrepOutput.GrepMatch> matches = searchFile(normalizedStartPath, searchPattern, context);
                results.addAll(matches);
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
                    String fileName = file.getFileName().toString();
                    
                    if (matchesFilePattern(fileName, filePattern, excludePattern)) {
                        // 检查文件大小
                        if (attrs.size() <= MAX_FILE_SIZE) {
                            List<GrepOutput.GrepMatch> matches = searchFile(file, searchPattern, context);
                            for (GrepOutput.GrepMatch match : matches) {
                                if (results.size() >= maxResults) {
                                    return FileVisitResult.TERMINATE;
                                }
                                results.add(match);
                            }
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
                
                // 跳过隐藏目录和常见的大型无关目录
                if (dirName.startsWith(".") && !dirName.equals("..")) {
                    if (!dirName.equals(".github") && !dirName.equals(".vscode") && 
                        !dirName.equals(".idea") && !dirName.equals(".settings")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                
                // 跳过常见的依赖目录
                if (dirName.equals("node_modules") || dirName.equals("target") || 
                    dirName.equals("build") || dirName.equals("dist") ||
                    dirName.equals(".git") || dirName.equals("__pycache__") ||
                    dirName.equals("venv") || dirName.equals(".venv") ||
                    dirName.equals("bin") || dirName.equals("out")) {
                    if (!dir.startsWith(normalizedStartPath)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
        
        return results;
    }
    
    /**
     * 检查文件是否匹配模式
     */
    private boolean matchesFilePattern(String fileName, Pattern includePattern, Pattern excludePattern) {
        // 检查排除模式
        if (excludePattern != null && excludePattern.matcher(fileName).matches()) {
            return false;
        }
        
        // 检查包含模式
        if (includePattern != null) {
            return includePattern.matcher(fileName).matches();
        }
        
        return true;
    }
    
    /**
     * 搜索单个文件
     */
    private List<GrepOutput.GrepMatch> searchFile(Path file, Pattern pattern, int context) {
        List<GrepOutput.GrepMatch> matches = new ArrayList<>();
        
        try {
            Charset charset = detectCharset(file);
            List<String> lines = Files.readAllLines(file, charset);
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                
                if (matcher.find()) {
                    // 获取上下文行
                    List<String> beforeContext = new ArrayList<>();
                    List<String> afterContext = new ArrayList<>();
                    
                    int startContext = Math.max(0, i - context);
                    int endContext = Math.min(lines.size() - 1, i + context);
                    
                    for (int j = startContext; j < i; j++) {
                        beforeContext.add(lines.get(j));
                    }
                    for (int j = i + 1; j <= endContext; j++) {
                        afterContext.add(lines.get(j));
                    }
                    
                    // 创建高亮显示的行
                    String highlightedLine = highlightMatch(line, matcher.start(), matcher.end());
                    
                    matches.add(new GrepOutput.GrepMatch(
                        file.toString(),
                        i + 1,  // 行号从 1 开始
                        line,
                        matcher.start(),
                        matcher.end(),
                        beforeContext,
                        afterContext,
                        highlightedLine
                    ));
                }
            }
        } catch (IOException e) {
            // 忽略无法读取的文件
        }
        
        return matches;
    }
    
    /**
     * 检测文件编码
     */
    private Charset detectCharset(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            // 检查 BOM
            if (bytes.length >= 3 && 
                (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return StandardCharsets.UTF_8;
            }
            if (bytes.length >= 2 && 
                (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            if (bytes.length >= 2 && 
                (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
            // 默认使用 UTF-8
            return StandardCharsets.UTF_8;
        } catch (IOException e) {
            return StandardCharsets.UTF_8;
        }
    }
    
    /**
     * 高亮匹配部分
     */
    private String highlightMatch(String line, int start, int end) {
        if (start < 0 || end > line.length() || start >= end) {
            return line;
        }
        
        StringBuilder result = new StringBuilder();
        result.append(line.substring(0, start));
        result.append("[[[").append(line.substring(start, end)).append("]]]");
        result.append(line.substring(end));
        
        return result.toString();
    }
    
    /**
     * Grep 搜索进度
     */
    public static class GrepProgress {
        private final String pattern;
        private final int filesScanned;
        private final int matchesFound;
        private final boolean completed;
        
        public GrepProgress(String pattern, int filesScanned, int matchesFound, boolean completed) {
            this.pattern = pattern;
            this.filesScanned = filesScanned;
            this.matchesFound = matchesFound;
            this.completed = completed;
        }
        
        public String getPattern() { return pattern; }
        public int getFilesScanned() { return filesScanned; }
        public int getMatchesFound() { return matchesFound; }
        public boolean isCompleted() { return completed; }
        public double getProgress() { return completed ? 1.0 : 0.5; }
    }
    
    /**
     * 输入类型（Tool 接口需要）
     */
    public static class Input {
        public String pattern;
        public String path;
        public Boolean isRegex;
        public Integer maxResults;
        
        public Input() {}
        
        public Input(String pattern) {
            this.pattern = pattern;
        }
    }
    
    /**
     * 输出类型（Tool 接口需要）
     */
    public static class Output {
        public java.util.List<GrepOutput.GrepMatch> matches;
        
        public Output() {}
    }
    
    /**
     * 进度类型（Tool 接口需要）
     */
    public static class Progress {
        private final String pattern;
        private final int filesScanned;
        private final int matchesFound;
        private final boolean completed;
        
        public Progress(String pattern, int filesScanned, int matchesFound, boolean completed) {
            this.pattern = pattern;
            this.filesScanned = filesScanned;
            this.matchesFound = matchesFound;
            this.completed = completed;
        }
        
        public String getPattern() { return pattern; }
        public int getFilesScanned() { return filesScanned; }
        public int getMatchesFound() { return matchesFound; }
        public boolean isCompleted() { return completed; }
        public double getProgress() { return completed ? 1.0 : 0.5; }
    }
}
