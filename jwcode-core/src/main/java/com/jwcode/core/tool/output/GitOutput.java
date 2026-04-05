package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Git 工具输出结果
 */
public record GitOutput(
    
    /** 操作是否成功 */
    @JsonProperty("success")
    boolean success,
    
    /** 操作类型 */
    @JsonProperty("operation")
    String operation,
    
    /** 命令输出 */
    @JsonProperty("output")
    String output,
    
    /** 错误信息 */
    @JsonProperty("error")
    String error,
    
    /** 分支列表（用于 branch 操作） */
    @JsonProperty("branches")
    List<BranchInfo> branches,
    
    /** 提交历史（用于 log 操作） */
    @JsonProperty("commits")
    List<CommitInfo> commits
) {
    public static GitOutput success(String operation, String output) {
        return new GitOutput(true, operation, output, null, null, null);
    }
    
    public static GitOutput success(String operation, String output, List<BranchInfo> branches) {
        return new GitOutput(true, operation, output, null, branches, null);
    }
    
    public static GitOutput success(String operation, String output, List<BranchInfo> branches, List<CommitInfo> commits) {
        return new GitOutput(true, operation, output, null, branches, commits);
    }
    
    public static GitOutput error(String operation, String error) {
        return new GitOutput(false, operation, null, error, null, null);
    }
    
    /**
     * 分支信息
     */
    public record BranchInfo(
        @JsonProperty("name")
        String name,
        
        @JsonProperty("isCurrent")
        boolean isCurrent,
        
        @JsonProperty("isRemote")
        boolean isRemote
    ) {}
    
    /**
     * 提交信息
     */
    public record CommitInfo(
        @JsonProperty("hash")
        String hash,
        
        @JsonProperty("shortHash")
        String shortHash,
        
        @JsonProperty("message")
        String message,
        
        @JsonProperty("author")
        String author,
        
        @JsonProperty("date")
        String date
    ) {}
}