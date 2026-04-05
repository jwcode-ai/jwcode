package com.jwcode.core.tool;

import com.jwcode.core.tool.input.PatternInput;
import com.jwcode.core.tool.output.PatternOutput;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.*;

/**
 * Pattern 工具
 * 用于高级正则表达式模式匹配和替换
 */
public class PatternTool implements Tool<PatternInput, PatternOutput, PatternOutput.MatchInfo> {
    
    public PatternTool() {}
    
    @Override
    public String getName() {
        return "Pattern";
    }
    
    @Override
    public String getDescription() {
        return "Advanced regex pattern matching and replacement across files.";
    }
    
    @Override
    public String getPrompt() {
        return "Use this tool for advanced pattern matching using regular expressions. " +
               "Supports finding and replacing patterns across multiple files. " +
               "Can search recursively and filter by file types.";
    }
    
    @Override
    public CompletableFuture<ToolResult<PatternOutput>> call(
            PatternInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<PatternOutput.MatchInfo>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                if (input.pattern() == null || input.pattern().isEmpty()) {
                    return ToolResult.error("搜索模式不能为空");
                }
                
                // 如果有替换文本，执行替换
                if (input.replacement() != null && input.file() != null) {
                    return performReplacement(input);
                }
                
                // 否则执行搜索
                return performSearch(input);
                
            } catch (Exception e) {
                return ToolResult.error("操作失败: " + e.getMessage());
            }
        });
    }
    
    private ToolResult<PatternOutput> performSearch(PatternInput input) throws Exception {
        Path basePath = Paths.get(System.getProperty("user.dir"));
        
        // 构建正则表达式
        int flags = 0;
        if (Boolean.TRUE.equals(input.ignoreCase())) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        
        Pattern pattern = Pattern.compile(input.pattern(), flags);
        List<PatternOutput.MatchInfo> matches = new ArrayList<>();
        
        // 确定搜索路径
        Path searchPath = input.file() != null ? basePath.resolve(input.file()) : basePath;
        
        // 收集文件
        List<Path> files = collectFiles(searchPath, input);
        
        for (Path file : files) {
            searchFile(file, pattern, matches, flags);
        }
        
        return ToolResult.success(PatternOutput.success(matches, matches.size()));
    }
    
    private void searchFile(Path file, Pattern pattern, List<PatternOutput.MatchInfo> matches, int flags) {
        try {
            List<String> lines = Files.readAllLines(file);
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                
                while (matcher.find()) {
                    matches.add(new PatternOutput.MatchInfo(
                            file.toString(),
                            i + 1,
                            line,
                            matcher.start(),
                            matcher.end(),
                            matcher.group()
                    ));
                }
            }
        } catch (Exception e) {
            // 跳过无法读取的文件
        }
    }
    
    private List<Path> collectFiles(Path searchPath, PatternInput input) throws Exception {
        List<Path> files = new ArrayList<>();
        
        boolean recursive = Boolean.TRUE.equals(input.recursive());
        
        if (Files.isRegularFile(searchPath)) {
            files.add(searchPath);
            return files;
        }
        
        String filter = input.fileFilter();
        Set<String> extensions = new HashSet<>();
        if (filter != null && !filter.isEmpty()) {
            for (String ext : filter.split(",")) {
                extensions.add(ext.trim().replace("*", "").replace(".", ""));
            }
        }
        
        // 简化实现：使用 Files.walk
        if (recursive) {
            try (java.util.stream.Stream<Path> stream = Files.walk(searchPath)) {
                stream.filter(Files::isRegularFile)
                      .filter(file -> {
                          String name = file.getFileName().toString();
                          return extensions.isEmpty() || extensions.contains(getExtension(name));
                      })
                      .forEach(files::add);
            }
        } else {
            Files.list(searchPath).forEach(p -> {
                if (Files.isRegularFile(p)) {
                    files.add(p);
                }
            });
        }
        
        return files;
    }
    
    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
    
    private ToolResult<PatternOutput> performReplacement(PatternInput input) throws Exception {
        Path filePath = Paths.get(System.getProperty("user.dir")).resolve(input.file());
        
        if (!Files.exists(filePath)) {
            return ToolResult.error("文件不存在: " + input.file());
        }
        
        String content = Files.readString(filePath);
        
        int flags = 0;
        if (Boolean.TRUE.equals(input.ignoreCase())) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        
        Pattern pattern = Pattern.compile(input.pattern(), flags);
        Matcher matcher = pattern.matcher(content);
        
        String replacement = input.replacement();
        boolean global = !Boolean.FALSE.equals(input.global());
        
        String result;
        if (global) {
            result = matcher.replaceAll(replacement);
        } else {
            result = matcher.replaceFirst(replacement);
        }
        
        // 备份原文件
        Path backup = filePath.resolveSibling(filePath.getFileName() + ".bak");
        Files.copy(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
        
        // 写入新内容
        Files.writeString(filePath, result);
        
        long count = pattern.matcher(content).results().count();
        
        return ToolResult.success(PatternOutput.success(result, (int) count));
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<PatternInput> getInputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<PatternOutput> getOutputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public ToolValidationResult validate(PatternInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        if (input.pattern() == null || input.pattern().isEmpty()) {
            return ToolValidationResult.invalid("搜索模式不能为空");
        }
        
        // 验证正则表达式
        try {
            Pattern.compile(input.pattern());
        } catch (PatternSyntaxException e) {
            return ToolValidationResult.invalid("无效的正则表达式: " + e.getMessage());
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(PatternInput input) {
        return input.replacement() == null;
    }
    
    @Override
    public boolean isDestructive(PatternInput input) {
        return input.replacement() != null;
    }
    
    @Override
    public boolean requiresApproval(PatternInput input) {
        return input.replacement() != null;
    }
}