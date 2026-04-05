package com.jwcode.core.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GitService - Git 工具服务
 * 
 * 功能说明：
 * 封装 Git 操作，提供便捷的 Git 命令执行接口。
 * 支持常用的 Git 操作：status、diff、commit、branch、log 等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class GitService {
    
    private final ExecutorService executor;
    private Path workingDirectory;
    
    public GitService() {
        this.executor = Executors.newFixedThreadPool(4);
    }
    
    public GitService(String workingDirectory) {
        this.executor = Executors.newFixedThreadPool(4);
        this.workingDirectory = Paths.get(workingDirectory);
    }
    
    /**
     * 设置工作目录
     */
    public void setWorkingDirectory(String directory) {
        this.workingDirectory = Paths.get(directory);
    }
    
    /**
     * 获取 Git 状态
     */
    public CompletableFuture<GitResult> getStatus() {
        return executeGitCommandAsync("status", "--short");
    }
    
    /**
     * 获取当前分支
     */
    public CompletableFuture<String> getCurrentBranch() {
        return executeGitCommandAsync("rev-parse", "--abbrev-ref", "HEAD")
                .thenApply(result -> result.output.trim());
    }
    
    /**
     * 获取所有分支
     */
    public CompletableFuture<List<String>> getBranches() {
        return executeGitCommandAsync("branch", "-a")
                .thenApply(result -> {
                    List<String> branches = new ArrayList<>();
                    for (String line : result.output.split("\n")) {
                        if (!line.trim().isEmpty()) {
                            branches.add(line.trim().replace("*", "").trim());
                        }
                    }
                    return branches;
                });
    }
    
    /**
     * 创建新分支
     */
    public CompletableFuture<GitResult> createBranch(String branchName) {
        return executeGitCommandAsync("checkout", "-b", branchName);
    }
    
    /**
     * 切换分支
     */
    public CompletableFuture<GitResult> checkoutBranch(String branchName) {
        return executeGitCommandAsync("checkout", branchName);
    }
    
    /**
     * 提交更改
     */
    public CompletableFuture<GitResult> commit(String message, boolean all) {
        if (all) {
            return executeGitCommandAsync("commit", "-am", message);
        } else {
            return executeGitCommandAsync("commit", "-m", message);
        }
    }
    
    /**
     * 添加文件到暂存区
     */
    public CompletableFuture<GitResult> add(String... files) {
        List<String> args = new ArrayList<>();
        args.add("add");
        if (files.length == 0) {
            args.add(".");
        } else {
            for (String file : files) {
                args.add(file);
            }
        }
        return executeGitCommandAsync(args.toArray(new String[0]));
    }
    
    /**
     * 添加所有文件到暂存区
     */
    public CompletableFuture<GitResult> addAll() {
        return executeGitCommandAsync("add", "-A");
    }
    
    /**
     * 查看提交历史
     */
    public CompletableFuture<List<GitCommit>> getLog(int maxCount) {
        return executeGitCommandAsync("log", "--max-count=" + maxCount, 
                "--pretty=format:%H|%an|%ae|%ai|%s")
                .thenApply(result -> {
                    List<GitCommit> commits = new ArrayList<>();
                    for (String line : result.output.split("\n")) {
                        if (!line.trim().isEmpty()) {
                            String[] parts = line.split("\\|", 5);
                            if (parts.length >= 5) {
                                GitCommit commit = new GitCommit();
                                commit.hash = parts[0];
                                commit.authorName = parts[1];
                                commit.authorEmail = parts[2];
                                commit.date = parts[3];
                                commit.message = parts[4];
                                commits.add(commit);
                            }
                        }
                    }
                    return commits;
                });
    }
    
    /**
     * 查看文件差异
     */
    public CompletableFuture<String> getDiff(String file) {
        if (file != null && !file.isEmpty()) {
            return executeGitCommandAsync("diff", "--", file)
                    .thenApply(result -> result.output);
        } else {
            return executeGitCommandAsync("diff")
                    .thenApply(result -> result.output);
        }
    }
    
    /**
     * 暂存区差异
     */
    public CompletableFuture<String> getStagedDiff() {
        return executeGitCommandAsync("diff", "--cached")
                .thenApply(result -> result.output);
    }
    
    /**
     * 推送更改
     */
    public CompletableFuture<GitResult> push(String remote, String branch) {
        return executeGitCommandAsync("push", remote, branch);
    }
    
    /**
     * 拉取更改
     */
    public CompletableFuture<GitResult> pull(String remote, String branch) {
        return executeGitCommandAsync("pull", remote, branch);
    }
    
    /**
     * 合并分支
     */
    public CompletableFuture<GitResult> merge(String branch) {
        return executeGitCommandAsync("merge", branch);
    }
    
    /**
     * 获取远程仓库列表
     */
    public CompletableFuture<List<String>> getRemotes() {
        return executeGitCommandAsync("remote", "-v")
                .thenApply(result -> {
                    List<String> remotes = new ArrayList<>();
                    for (String line : result.output.split("\n")) {
                        if (!line.trim().isEmpty()) {
                            String[] parts = line.split("\\s+");
                            if (parts.length >= 2) {
                                remotes.add(parts[0]);
                            }
                        }
                    }
                    return remotes.stream().distinct().collect(java.util.stream.Collectors.toList());
                });
    }
    
    /**
     * 检查是否是 Git 仓库
     */
    public boolean isGitRepository() {
        if (workingDirectory == null) {
            return false;
        }
        return Files.exists(workingDirectory.resolve(".git"));
    }
    
    /**
     * 执行 Git 命令（异步）
     */
    private CompletableFuture<GitResult> executeGitCommandAsync(String... args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeGitCommand(args);
            } catch (IOException | InterruptedException e) {
                GitResult result = new GitResult();
                result.success = false;
                result.error = e.getMessage();
                result.exitCode = -1;
                return result;
            }
        }, executor);
    }
    
    /**
     * 执行 Git 命令（同步）
     */
    private GitResult executeGitCommand(String... args) throws IOException, InterruptedException {
        GitResult result = new GitResult();
        
        List<String> command = new ArrayList<>();
        command.add("git");
        for (String arg : args) {
            command.add(arg);
        }
        
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null && Files.exists(workingDirectory)) {
            pb.directory(workingDirectory.toFile());
        }
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        
        result.output = output.toString();
        result.error = error.toString();
        result.exitCode = exitCode;
        result.success = exitCode == 0;
        
        return result;
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * Git 状态结果
     */
    public static class GitStatus {
        public String branch;
        public List<String> modified;
        public List<String> staged;
        public List<String> untracked;
        public boolean isClean;
        public String rawOutput;
    }
    
    /**
     * Git 提交信息
     */
    public static class GitCommit {
        public String hash;
        public String authorName;
        public String authorEmail;
        public String date;
        public String message;
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "hash", hash,
                    "author", authorName,
                    "email", authorEmail,
                    "date", date,
                    "message", message
            );
        }
    }
    
    /**
     * Git 命令执行结果
     */
    public static class GitResult {
        public String output;
        public String error;
        public int exitCode;
        public boolean success;
        
        public Map<String, Object> toMap() {
            return Map.of(
                    "output", output,
                    "error", error,
                    "exitCode", exitCode,
                    "success", success
            );
        }
    }
}