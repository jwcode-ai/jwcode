package com.jwcode.core.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WorktreeManager - Git Worktree 管理器
 * 
 * 功能说明：
 * 封装 Git Worktree 操作，提供便捷的 Worktree 管理接口。
 * 支持列出、创建、删除 worktree，以及获取当前 worktree。
 * 
 * Git Worktree 命令参考：
 * - git worktree list
 * - git worktree add <path> <branch>
 * - git worktree remove <path>
 * - git worktree prune
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class WorktreeManager {
    
    private final ExecutorService executor;
    private Path workingDirectory;
    private Path currentWorktreePath;
    
    /**
     * 默认构造函数
     */
    public WorktreeManager() {
        this.executor = Executors.newFixedThreadPool(2);
        this.workingDirectory = Paths.get(System.getProperty("user.dir"));
        this.currentWorktreePath = this.workingDirectory;
    }
    
    /**
     * 构造函数
     * 
     * @param workingDirectory 工作目录
     */
    public WorktreeManager(String workingDirectory) {
        this.executor = Executors.newFixedThreadPool(2);
        this.workingDirectory = Paths.get(workingDirectory);
        this.currentWorktreePath = this.workingDirectory;
    }
    
    /**
     * 构造函数
     * 
     * @param workingDirectory 工作目录
     */
    public WorktreeManager(Path workingDirectory) {
        this.executor = Executors.newFixedThreadPool(2);
        this.workingDirectory = workingDirectory;
        this.currentWorktreePath = workingDirectory;
    }
    
    /**
     * 设置工作目录
     * 
     * @param directory 工作目录路径
     */
    public void setWorkingDirectory(String directory) {
        this.workingDirectory = Paths.get(directory);
        this.currentWorktreePath = this.workingDirectory;
    }
    
    /**
     * 设置当前 Worktree 路径
     * 
     * @param path Worktree 路径
     */
    public void setCurrentWorktreePath(Path path) {
        this.currentWorktreePath = path;
    }
    
    /**
     * 获取当前 Worktree 路径
     * 
     * @return 当前 Worktree 路径
     */
    public Path getCurrentWorktreePath() {
        return currentWorktreePath;
    }
    
    /**
     * 列出所有 Worktree
     * 
     * @return 所有 Worktree 信息的列表
     */
    public CompletableFuture<List<WorktreeInfo>> listWorktrees() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return listWorktreesSync();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Failed to list worktrees: " + e.getMessage(), e);
            }
        }, executor);
    }
    
    /**
     * 同步列出所有 Worktree
     * 
     * @return 所有 Worktree 信息的列表
     */
    public List<WorktreeInfo> listWorktreesSync() throws IOException, InterruptedException {
        List<WorktreeInfo> worktrees = new ArrayList<>();
        
        WorktreeResult result = executeWorktreeCommand("list", "--porcelain");
        
        if (!result.success) {
            throw new IOException("Failed to list worktrees: " + result.error);
        }
        
        // 解析 --porcelain 输出格式
        String[] lines = result.output.split("\n");
        Path worktreePath = null;
        String branch = null;
        String commit = null;
        boolean isBare = false;
        boolean isDetached = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                // 空行表示一个 worktree 记录的结束
                if (worktreePath != null) {
                    boolean isMain = isMainWorktree(worktreePath);
                    worktrees.add(new WorktreeInfo(worktreePath, branch, commit, 
                                                   isBare, isDetached, isMain));
                }
                // 重置状态
                worktreePath = null;
                branch = null;
                commit = null;
                isBare = false;
                isDetached = false;
            } else if (line.startsWith("worktree ")) {
                worktreePath = Paths.get(line.substring(9).trim());
            } else if (line.startsWith("branch ")) {
                branch = line.substring(7).trim();
                isDetached = false;
            } else if (line.startsWith("detached")) {
                isDetached = true;
                branch = null;
            } else if (line.startsWith("HEAD ")) {
                commit = line.substring(5).trim();
            } else if (line.startsWith("bare")) {
                isBare = true;
            }
        }
        
        // 处理最后一个 worktree（如果没有空行结尾）
        if (worktreePath != null) {
            boolean isMain = isMainWorktree(worktreePath);
            worktrees.add(new WorktreeInfo(worktreePath, branch, commit, 
                                           isBare, isDetached, isMain));
        }
        
        return worktrees;
    }
    
    /**
     * 创建新的 Worktree
     * 
     * @param branch 分支名称
     * @param path Worktree 路径
     * @return 创建结果
     */
    public CompletableFuture<WorktreeResult> createWorktree(String branch, String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createWorktreeSync(branch, path);
            } catch (IOException | InterruptedException e) {
                WorktreeResult result = new WorktreeResult();
                result.success = false;
                result.error = e.getMessage();
                return result;
            }
        }, executor);
    }
    
    /**
     * 同步创建新的 Worktree
     * 
     * @param branch 分支名称
     * @param path Worktree 路径
     * @return 创建结果
     */
    public WorktreeResult createWorktreeSync(String branch, String path) 
            throws IOException, InterruptedException {
        
        Path worktreePath = Paths.get(path);
        
        // 检查路径是否已存在
        if (Files.exists(worktreePath)) {
            WorktreeResult result = new WorktreeResult();
            result.success = false;
            result.error = "Path already exists: " + path;
            return result;
        }
        
        // 确保父目录存在
        Path parent = worktreePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        
        // 执行 git worktree add 命令
        WorktreeResult result = executeWorktreeCommand("add", path, branch);
        
        if (result.success) {
            result.output = "Worktree created successfully at: " + path + " for branch: " + branch;
        }
        
        return result;
    }
    
    /**
     * 创建新的 Worktree（基于当前分支创建新分支）
     * 
     * @param branch 新分支名称
     * @param path Worktree 路径
     * @param baseBranch 基于的分支
     * @return 创建结果
     */
    public CompletableFuture<WorktreeResult> createWorktreeWithNewBranch(
            String branch, String path, String baseBranch) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createWorktreeWithNewBranchSync(branch, path, baseBranch);
            } catch (IOException | InterruptedException e) {
                WorktreeResult result = new WorktreeResult();
                result.success = false;
                result.error = e.getMessage();
                return result;
            }
        }, executor);
    }
    
    /**
     * 同步创建新的 Worktree（基于当前分支创建新分支）
     * 
     * @param branch 新分支名称
     * @param path Worktree 路径
     * @param baseBranch 基于的分支
     * @return 创建结果
     */
    public WorktreeResult createWorktreeWithNewBranchSync(String branch, String path, String baseBranch) 
            throws IOException, InterruptedException {
        
        Path worktreePath = Paths.get(path);
        
        // 检查路径是否已存在
        if (Files.exists(worktreePath)) {
            WorktreeResult result = new WorktreeResult();
            result.success = false;
            result.error = "Path already exists: " + path;
            return result;
        }
        
        // 确保父目录存在
        Path parent = worktreePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        
        // 执行 git worktree add -b 命令（创建新分支）
        WorktreeResult result = executeWorktreeCommand("add", "-b", branch, path, baseBranch);
        
        if (result.success) {
            result.output = "Worktree created successfully at: " + path + " with new branch: " + branch;
        }
        
        return result;
    }
    
    /**
     * 删除 Worktree
     * 
     * @param path Worktree 路径
     * @return 删除结果
     */
    public CompletableFuture<WorktreeResult> removeWorktree(String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return removeWorktreeSync(path);
            } catch (IOException | InterruptedException e) {
                WorktreeResult result = new WorktreeResult();
                result.success = false;
                result.error = e.getMessage();
                return result;
            }
        }, executor);
    }
    
    /**
     * 同步删除 Worktree
     * 
     * @param path Worktree 路径
     * @return 删除结果
     */
    public WorktreeResult removeWorktreeSync(String path) throws IOException, InterruptedException {
        Path worktreePath = Paths.get(path).toAbsolutePath().normalize();
        Path currentPath = currentWorktreePath.toAbsolutePath().normalize();
        
        // 不能删除当前所在的 worktree
        if (worktreePath.equals(currentPath)) {
            WorktreeResult result = new WorktreeResult();
            result.success = false;
            result.error = "Cannot remove the current worktree. Please exit this worktree first.";
            return result;
        }
        
        WorktreeResult result = executeWorktreeCommand("remove", path);
        
        if (result.success) {
            result.output = "Worktree removed successfully: " + path;
        }
        
        return result;
    }
    
    /**
     * 强制删除 Worktree（即使包含未提交的更改）
     * 
     * @param path Worktree 路径
     * @return 删除结果
     */
    public CompletableFuture<WorktreeResult> forceRemoveWorktree(String path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return forceRemoveWorktreeSync(path);
            } catch (IOException | InterruptedException e) {
                WorktreeResult result = new WorktreeResult();
                result.success = false;
                result.error = e.getMessage();
                return result;
            }
        }, executor);
    }
    
    /**
     * 同步强制删除 Worktree
     * 
     * @param path Worktree 路径
     * @return 删除结果
     */
    public WorktreeResult forceRemoveWorktreeSync(String path) throws IOException, InterruptedException {
        Path worktreePath = Paths.get(path).toAbsolutePath().normalize();
        Path currentPath = currentWorktreePath.toAbsolutePath().normalize();
        
        // 不能删除当前所在的 worktree
        if (worktreePath.equals(currentPath)) {
            WorktreeResult result = new WorktreeResult();
            result.success = false;
            result.error = "Cannot remove the current worktree. Please exit this worktree first.";
            return result;
        }
        
        WorktreeResult result = executeWorktreeCommand("remove", "-f", path);
        
        if (result.success) {
            result.output = "Worktree force removed successfully: " + path;
        }
        
        return result;
    }
    
    /**
     * 清理无效的 Worktree 引用
     * 
     * @return 清理结果
     */
    public CompletableFuture<WorktreeResult> pruneWorktrees() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return pruneWorktreesSync();
            } catch (IOException | InterruptedException e) {
                WorktreeResult result = new WorktreeResult();
                result.success = false;
                result.error = e.getMessage();
                return result;
            }
        }, executor);
    }
    
    /**
     * 同步清理无效的 Worktree 引用
     * 
     * @return 清理结果
     */
    public WorktreeResult pruneWorktreesSync() throws IOException, InterruptedException {
        return executeWorktreeCommand("prune");
    }
    
    /**
     * 获取当前 Worktree 信息
     * 
     * @return 当前 Worktree 信息
     */
    public CompletableFuture<Optional<WorktreeInfo>> getCurrentWorktree() {
        return listWorktrees().thenApply(worktrees -> {
            Path currentPath = currentWorktreePath.toAbsolutePath().normalize();
            return worktrees.stream()
                    .filter(wt -> wt.getPath().toAbsolutePath().normalize().equals(currentPath))
                    .findFirst();
        });
    }
    
    /**
     * 检查指定路径是否为 Worktree
     * 
     * @param path 路径
     * @return true 如果是 Worktree
     */
    public CompletableFuture<Boolean> isWorktree(String path) {
        return listWorktrees().thenApply(worktrees -> {
            Path checkPath = Paths.get(path).toAbsolutePath().normalize();
            return worktrees.stream()
                    .anyMatch(wt -> wt.getPath().toAbsolutePath().normalize().equals(checkPath));
        });
    }
    
    /**
     * 检查指定路径是否为主工作树
     * 
     * @param path 路径
     * @return true 如果是主工作树
     */
    private boolean isMainWorktree(Path path) {
        // 主工作树包含 .git 目录而不是 .git 文件
        Path gitPath = path.resolve(".git");
        if (Files.exists(gitPath)) {
            return Files.isDirectory(gitPath);
        }
        return false;
    }
    
    /**
     * 执行 Git worktree 命令
     * 
     * @param args 命令参数
     * @return 执行结果
     */
    private WorktreeResult executeWorktreeCommand(String... args) 
            throws IOException, InterruptedException {
        
        WorktreeResult result = new WorktreeResult();
        
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("worktree");
        for (String arg : args) {
            command.add(arg);
        }
        
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null && Files.exists(workingDirectory)) {
            pb.directory(workingDirectory.toFile());
        }
        pb.redirectErrorStream(false);
        
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        
        // 读取标准输出
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // 读取错误输出
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        
        result.output = output.toString().trim();
        result.error = error.toString().trim();
        result.exitCode = exitCode;
        result.success = exitCode == 0;
        
        return result;
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * Worktree 操作结果
     */
    public static class WorktreeResult {
        public String output;
        public String error;
        public int exitCode;
        public boolean success;
        
        @Override
        public String toString() {
            return "WorktreeResult{" +
                   "success=" + success +
                   ", exitCode=" + exitCode +
                   ", output='" + output + '\'' +
                   ", error='" + error + '\'' +
                   '}';
        }
    }
}
