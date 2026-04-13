package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jwcode.core.git.WorktreeInfo;

/**
 * EnterWorktree 工具输出结果
 */
public record EnterWorktreeOutput(
    
    /** 操作是否成功 */
    @JsonProperty("success")
    boolean success,
    
    /** 操作消息 */
    @JsonProperty("message")
    String message,
    
    /** 进入的 Worktree 信息 */
    @JsonProperty("worktree")
    WorktreeInfo worktree,
    
    /** 原始工作目录（用于恢复） */
    @JsonProperty("originalDirectory")
    String originalDirectory,
    
    /** 错误信息 */
    @JsonProperty("error")
    String error
) {
    public static EnterWorktreeOutput success(WorktreeInfo worktree, String message, String originalDirectory) {
        return new EnterWorktreeOutput(true, message, worktree, originalDirectory, null);
    }
    
    public static EnterWorktreeOutput error(String error) {
        return new EnterWorktreeOutput(false, null, null, null, error);
    }
}
