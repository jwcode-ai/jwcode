package com.jwcode.core.tool;

import com.jwcode.core.tool.input.GitInput;
import com.jwcode.core.tool.output.GitOutput;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Git 工具
 * 提供 Git 版本控制操作支持
 */
public class GitTool implements Tool<GitInput, GitOutput, Void> {
    
    private final String workingDirectory;
    
    public GitTool() {
        this.workingDirectory = System.getProperty("user.dir");
    }
    
    public GitTool(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }
    
    @Override
    public String getName() {
        return "Git";
    }
    
    @Override
    public String getDescription() {
        return "Run git commands for version control operations.";
    }
    
    @Override
    public String getPrompt() {
        return "Use this tool to perform Git operations like checking status, " +
               "viewing diffs, making commits, managing branches, and pushing/pulling.";
    }
    
    @Override
    public CompletableFuture<ToolResult<GitOutput>> call(
            GitInput input,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // ===== 智能参数归一化层（容错处理）=====
                // 如果 operation 为空，尝试根据其他字段推断
                String operation = inferOperation(input);
                
                return switch (operation) {
                    case "status" -> handleStatus();
                    case "diff" -> handleDiff(input);
                    case "commit" -> handleCommit(input);
                    case "branch" -> handleBranch(input);
                    case "log" -> handleLog(input);
                    case "push" -> handlePush(input);
                    case "pull" -> handlePull(input);
                    default -> ToolResult.error("未知操作: " + operation);
                };
                
            } catch (Exception e) {
                return ToolResult.error("Git 操作失败: " + e.getMessage());
            }
        });
    }
    
    private ToolResult<GitOutput> handleStatus() {
        String output = runGitCommand("git status --porcelain");
        return ToolResult.success(GitOutput.success("status", output));
    }
    
    private ToolResult<GitOutput> handleDiff(GitInput input) {
        String file = input.file();
        String[] cmd = file != null 
                ? new String[]{"git", "diff", file}
                : new String[]{"git", "diff"};
        
        String output = runGitCommand(cmd);
        return ToolResult.success(GitOutput.success("diff", output));
    }
    
    private ToolResult<GitOutput> handleCommit(GitInput input) {
        String message = input.message();
        if (message == null || message.isEmpty()) {
            return ToolResult.error("提交信息不能为空");
        }
        
        // 执行 git add .
        runGitCommand("git add -A");
        
        // 执行 git commit
        String output = runGitCommand(new String[]{"git", "commit", "-m", message});
        
        return ToolResult.success(GitOutput.success("commit", "提交完成\n" + output));
    }
    
    private ToolResult<GitOutput> handleBranch(GitInput input) {
        String branch = input.branch();
        String operation = input.args();
        
        String output;
        
        if (branch != null && !branch.isEmpty()) {
            if ("-d".equals(operation) || "--delete".equals(operation)) {
                // 删除分支
                output = runGitCommand(new String[]{"git", "branch", "-d", branch});
            } else if ("-D".equals(operation)) {
                // 强制删除分支
                output = runGitCommand(new String[]{"git", "branch", "-D", branch});
            } else {
                // 创建并切换分支
                output = runGitCommand(new String[]{"git", "checkout", "-b", branch});
            }
        } else {
            // 列出所有分支
            output = runGitCommand("git branch -a");
            
            // 解析分支列表
            List<GitOutput.BranchInfo> branches = new ArrayList<>();
            String currentBranch = "";
            
            // 获取当前分支
            String current = runGitCommand("git branch --show-current");
            currentBranch = current.trim();
            
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                boolean isCurrent = line.startsWith("*");
                String name = line.replace("*", "").trim();
                boolean isRemote = name.startsWith("remotes/") || name.startsWith("origin/");
                
                branches.add(new GitOutput.BranchInfo(name, isCurrent, isRemote));
            }
            
            return ToolResult.success(GitOutput.success("branch", output, branches));
        }
        
        return ToolResult.success(GitOutput.success("branch", output));
    }
    
    private ToolResult<GitOutput> handleLog(GitInput input) {
        String args = input.args();
        String limit = args != null ? args : "-20";
        
        String output = runGitCommand("git log " + limit + " --oneline");
        
        // 解析提交历史
        List<GitOutput.CommitInfo> commits = new ArrayList<>();
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split(" ", 2);
            if (parts.length >= 1) {
                String hash = parts[0];
                String message = parts.length > 1 ? parts[1] : "";
                
                commits.add(new GitOutput.CommitInfo(
                        hash,
                        hash.substring(0, Math.min(7, hash.length())),
                        message,
                        "",
                        ""
                ));
            }
        }
        
        return ToolResult.success(GitOutput.success("log", output, null, commits));
    }
    
    private ToolResult<GitOutput> handlePush(GitInput input) {
        String remote = input.remote();
        String branch = input.branch();
        
        String[] cmd;
        if (remote != null && branch != null) {
            cmd = new String[]{"git", "push", remote, branch};
        } else if (remote != null) {
            cmd = new String[]{"git", "push", remote};
        } else {
            cmd = new String[]{"git", "push"};
        }
        
        String output = runGitCommand(cmd);
        return ToolResult.success(GitOutput.success("push", output));
    }
    
    private ToolResult<GitOutput> handlePull(GitInput input) {
        String remote = input.remote();
        String branch = input.branch();
        
        String[] cmd;
        if (remote != null && branch != null) {
            cmd = new String[]{"git", "pull", remote, branch};
        } else if (remote != null) {
            cmd = new String[]{"git", "pull", remote};
        } else {
            cmd = new String[]{"git", "pull"};
        }
        
        String output = runGitCommand(cmd);
        return ToolResult.success(GitOutput.success("pull", output));
    }
    
    /**
     * 智能推断操作类型
     * 如果 operation 为空，根据其他字段推断应该执行的操作
     */
    private String inferOperation(GitInput input) {
        String op = input.operation();
        
        // 如果 operation 已存在，直接返回
        if (op != null && !op.isEmpty()) {
            return op;
        }
        
        // 尝试根据其他字段推断
        if (input.message() != null && !input.message().isEmpty()) {
            return "commit";
        }
        if (input.branch() != null && !input.branch().isEmpty()) {
            return "branch";
        }
        if (input.file() != null && !input.file().isEmpty()) {
            return "diff";
        }
        if (input.remote() != null && !input.remote().isEmpty()) {
            if (input.args() != null && input.args().contains("push")) {
                return "push";
            }
            return "pull";
        }
        if (input.args() != null && !input.args().isEmpty()) {
            String lower = input.args().toLowerCase();
            if (lower.contains("push")) return "push";
            if (lower.contains("pull")) return "pull";
            if (lower.contains("log")) return "log";
            if (lower.contains("branch")) return "branch";
        }
        
        // 默认返回 status
        return "status";
    }
    
    private String runGitCommand(String command) {
        return runGitCommand(command.split(" "));
    }
    
    private String runGitCommand(String[] command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(Paths.get(workingDirectory).toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                String output = reader.lines().collect(Collectors.joining("\n"));
                process.waitFor();
                
                return output;
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<GitInput> getInputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public com.fasterxml.jackson.core.type.TypeReference<GitOutput> getOutputType() {
        return new com.fasterxml.jackson.core.type.TypeReference<>() {};
    }
    
    @Override
    public ToolValidationResult validate(GitInput input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        if (input.operation() == null || input.operation().isEmpty()) {
            return ToolValidationResult.invalid("操作类型不能为空");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(GitInput input) {
        return List.of("status", "diff", "branch", "log").contains(input.operation());
    }
    
    @Override
    public boolean isDestructive(GitInput input) {
        return List.of("branch -D", "push", "push --force").contains(
                input.operation() + (input.args() != null ? " " + input.args() : ""));
    }
    
    @Override
    public boolean requiresApproval(GitInput input) {
        return isDestructive(input);
    }
}