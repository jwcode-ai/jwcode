package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * /diff 命令 - 查看代码差异
 * 
 * 显示文件之间的差异，支持 git diff 和本地文件比较。
 */
public class DiffCommand implements Command {
    
    @Override
    public String getName() {
        return "diff";
    }
    
    @Override
    public String getDescription() {
        return "查看代码差异";
    }
    
    @Override
    public String getUsage() {
        return "/diff [文件 1] [文件 2] 或 /diff --git [commit1] [commit2] 或 /diff --staged";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        DiffOptions options = parseArgs(args);
        
        try {
            if (options.isGitDiff) {
                return executeGitDiff(options, context);
            } else if (options.isStaged) {
                return executeGitStagedDiff(context);
            } else if (options.file1 != null && options.file2 != null) {
                return executeFileDiff(options, context);
            } else if (options.file1 != null) {
                return executeGitFileDiff(options, context);
            } else {
                return executeGitWorkingDiff(context);
            }
        } catch (IOException | InterruptedException e) {
            return CommandResult.error("执行差异比较失败：" + e.getMessage());
        }
    }
    
    /**
     * 解析参数
     */
    private DiffOptions parseArgs(String args) {
        DiffOptions options = new DiffOptions();
        
        if (args == null || args.trim().isEmpty()) {
            return options;
        }
        
        String[] parts = args.trim().split("\\s+");
        List<String> files = new ArrayList<>();
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            
            if ("--git".equals(part)) {
                options.isGitDiff = true;
                if (i + 1 < parts.length) {
                    options.commit1 = parts[++i];
                    if (i + 1 < parts.length) {
                        options.commit2 = parts[++i];
                    }
                }
            } else if ("--staged".equals(part)) {
                options.isStaged = true;
            } else if ("--stat".equals(part)) {
                options.statOnly = true;
            } else if ("--cached".equals(part)) {
                options.isCached = true;
            } else if (!part.startsWith("-")) {
                files.add(part);
            }
        }
        
        if (files.size() >= 1) {
            options.file1 = files.get(0);
        }
        if (files.size() >= 2) {
            options.file2 = files.get(1);
        }
        
        return options;
    }
    
    /**
     * 执行 Git Diff
     */
    private CommandResult executeGitDiff(DiffOptions options, CommandContext context) 
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        
        if (options.statOnly) {
            command.add("--stat");
        }
        
        if (options.commit1 != null) {
            command.add(options.commit1);
            if (options.commit2 != null) {
                command.add(options.commit2);
            }
        }
        
        return executeCommand(command);
    }
    
    /**
     * 执行 Git Staged Diff
     */
    private CommandResult executeGitStagedDiff(CommandContext context) 
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        command.add("--cached");
        
        return executeCommand(command);
    }
    
    /**
     * 执行 Git Working Dir Diff
     */
    private CommandResult executeGitWorkingDiff(CommandContext context) 
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        command.add("HEAD");
        
        return executeCommand(command);
    }
    
    /**
     * 执行 Git File Diff
     */
    private CommandResult executeGitFileDiff(DiffOptions options, CommandContext context) 
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        command.add(options.file1);
        
        return executeCommand(command);
    }
    
    /**
     * 执行文件差异比较
     */
    private CommandResult executeFileDiff(DiffOptions options, CommandContext context) 
            throws IOException, InterruptedException {
        Path file1 = Paths.get(options.file1);
        Path file2 = Paths.get(options.file2);
        
        if (!Files.exists(file1)) {
            return CommandResult.error("文件不存在：" + options.file1);
        }
        if (!Files.exists(file2)) {
            return CommandResult.error("文件不存在：" + options.file2);
        }
        
        List<String> lines1 = Files.readAllLines(file1);
        List<String> lines2 = Files.readAllLines(file2);
        
        StringBuilder output = new StringBuilder();
        output.append("文件差异比较\n\n");
        output.append("文件 1: ").append(options.file1).append("\n");
        output.append("文件 2: ").append(options.file2).append("\n\n");
        
        // 简单 diff 实现
        output.append(generateSimpleDiff(lines1, lines2, options.file1, options.file2));
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 生成简单差异
     */
    private String generateSimpleDiff(List<String> lines1, List<String> lines2, 
                                       String file1, String file2) {
        StringBuilder diff = new StringBuilder();
        diff.append("--- ").append(file1).append("\n");
        diff.append("+++ ").append(file2).append("\n");
        
        int i = 0, j = 0;
        while (i < lines1.size() && j < lines2.size()) {
            String line1 = lines1.get(i);
            String line2 = lines2.get(j);
            
            if (line1.equals(line2)) {
                diff.append("  ").append(line1).append("\n");
                i++;
                j++;
            } else {
                // 查找匹配
                boolean found = false;
                for (int lookAhead = 1; lookAhead <= 5 && !found; lookAhead++) {
                    if (j + lookAhead < lines2.size() && lines1.get(i).equals(lines2.get(j + lookAhead))) {
                        // lines2 中插入了行
                        for (int k = 0; k < lookAhead; k++) {
                            diff.append("+ ").append(lines2.get(j + k)).append("\n");
                        }
                        j += lookAhead;
                        found = true;
                    } else if (i + lookAhead < lines1.size() && lines1.get(i + lookAhead).equals(lines2.get(j))) {
                        // lines1 中删除了行
                        for (int k = 0; k < lookAhead; k++) {
                            diff.append("- ").append(lines1.get(i + k)).append("\n");
                        }
                        i += lookAhead;
                        found = true;
                    }
                }
                
                if (!found) {
                    diff.append("- ").append(line1).append("\n");
                    diff.append("+ ").append(line2).append("\n");
                    i++;
                    j++;
                }
            }
        }
        
        // 处理剩余行
        while (i < lines1.size()) {
            diff.append("- ").append(lines1.get(i++)).append("\n");
        }
        while (j < lines2.size()) {
            diff.append("+ ").append(lines2.get(j++)).append("\n");
        }
        
        return diff.toString();
    }
    
    /**
     * 执行命令
     */
    private CommandResult executeCommand(List<String> command) 
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        process.waitFor();
        
        if (output.length() == 0) {
            return CommandResult.success("没有差异");
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * Diff 选项类
     */
    private static class DiffOptions {
        String file1;
        String file2;
        boolean isGitDiff;
        String commit1;
        String commit2;
        boolean isStaged;
        boolean statOnly;
        boolean isCached;
    }
}