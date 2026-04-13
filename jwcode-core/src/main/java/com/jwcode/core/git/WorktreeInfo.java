package com.jwcode.core.git;

import java.nio.file.Path;
import java.util.Objects;

/**
 * WorktreeInfo - Git Worktree 信息模型
 * 
 * 功能说明：
 * 封装 Git Worktree 的基本信息，包括路径、分支、提交哈希等。
 * 用于表示一个 Git Worktree 的状态和属性。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class WorktreeInfo {
    
    /** Worktree 路径 */
    private final Path path;
    
    /** 关联的分支名称 */
    private final String branch;
    
    /** 当前提交哈希 */
    private final String commit;
    
    /** 是否为 bare 仓库 */
    private final boolean isBare;
    
    /** 是否为 detached HEAD 状态 */
    private final boolean isDetached;
    
    /** 是否为主工作树 */
    private final boolean isMain;
    
    /**
     * 构造函数
     * 
     * @param path Worktree 路径
     * @param branch 分支名称
     * @param commit 提交哈希
     * @param isBare 是否为 bare 仓库
     * @param isDetached 是否为 detached HEAD
     * @param isMain 是否为主工作树
     */
    public WorktreeInfo(Path path, String branch, String commit, 
                       boolean isBare, boolean isDetached, boolean isMain) {
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.branch = branch;
        this.commit = commit;
        this.isBare = isBare;
        this.isDetached = isDetached;
        this.isMain = isMain;
    }
    
    /**
     * 获取 Worktree 路径
     * 
     * @return Worktree 路径
     */
    public Path getPath() {
        return path;
    }
    
    /**
     * 获取关联的分支名称
     * 
     * @return 分支名称，如果是 detached 状态可能返回 null
     */
    public String getBranch() {
        return branch;
    }
    
    /**
     * 获取当前提交哈希
     * 
     * @return 提交哈希
     */
    public String getCommit() {
        return commit;
    }
    
    /**
     * 检查是否为 bare 仓库
     * 
     * @return true 如果是 bare 仓库
     */
    public boolean isBare() {
        return isBare;
    }
    
    /**
     * 检查是否为 detached HEAD 状态
     * 
     * @return true 如果是 detached HEAD
     */
    public boolean isDetached() {
        return isDetached;
    }
    
    /**
     * 检查是否为主工作树
     * 
     * @return true 如果是主工作树
     */
    public boolean isMain() {
        return isMain;
    }
    
    /**
     * 验证 Worktree 是否有效
     * 检查路径是否存在且包含 .git 文件或目录
     * 
     * @return true 如果 Worktree 有效
     */
    public boolean isValid() {
        if (!java.nio.file.Files.exists(path)) {
            return false;
        }
        
        // 检查是否存在 .git 文件（worktree 使用 .git 文件指向主仓库）
        // 或者是主工作树中的 .git 目录
        Path gitPath = path.resolve(".git");
        return java.nio.file.Files.exists(gitPath);
    }
    
    /**
     * 获取分支名称（简化版）
     * 如果是 detached 状态，返回提交的短哈希
     * 
     * @return 分支名称或提交短哈希
     */
    public String getBranchName() {
        if (isDetached || branch == null) {
            return commit != null && commit.length() >= 7 ? commit.substring(0, 7) : "detached";
        }
        // 移除 "refs/heads/" 前缀
        if (branch.startsWith("refs/heads/")) {
            return branch.substring(11);
        }
        return branch;
    }
    
    /**
     * 获取 Worktree 的简短描述
     * 
     * @return 描述字符串
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(path.toString());
        if (isMain) {
            sb.append(" (main)");
        }
        sb.append(" [").append(getBranchName()).append("]");
        if (isDetached) {
            sb.append(" (detached)");
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "WorktreeInfo{" +
               "path=" + path +
               ", branch='" + branch + '\'' +
               ", commit='" + (commit != null ? commit.substring(0, 7) : null) + '\'' +
               ", isBare=" + isBare +
               ", isDetached=" + isDetached +
               ", isMain=" + isMain +
               '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorktreeInfo that = (WorktreeInfo) o;
        return Objects.equals(path, that.path);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
}
