package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jwcode.core.git.WorktreeInfo;

import java.util.List;

/**
 * WorktreeList 工具输出结果
 */
public record WorktreeListOutput(
    
    /** 操作是否成功 */
    @JsonProperty("success")
    boolean success,
    
    /** Worktree 列表 */
    @JsonProperty("worktrees")
    List<WorktreeInfo> worktrees,
    
    /** 当前 Worktree 索引 */
    @JsonProperty("currentIndex")
    Integer currentIndex,
    
    /** 当前 Worktree 路径 */
    @JsonProperty("currentPath")
    String currentPath,
    
    /** 操作消息 */
    @JsonProperty("message")
    String message,
    
    /** 错误信息 */
    @JsonProperty("error")
    String error
) {
    public static WorktreeListOutput success(List<WorktreeInfo> worktrees, Integer currentIndex, 
                                              String currentPath, String message) {
        return new WorktreeListOutput(true, worktrees, currentIndex, currentPath, message, null);
    }
    
    public static WorktreeListOutput error(String error) {
        return new WorktreeListOutput(false, null, null, null, null, error);
    }
}
