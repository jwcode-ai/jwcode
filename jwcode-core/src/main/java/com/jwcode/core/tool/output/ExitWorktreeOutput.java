package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ExitWorktree 工具输出结果
 */
public record ExitWorktreeOutput(
    
    /** 操作是否成功 */
    @JsonProperty("success")
    boolean success,
    
    /** 操作消息 */
    @JsonProperty("message")
    String message,
    
    /** 返回到的目录 */
    @JsonProperty("returnedToDirectory")
    String returnedToDirectory,
    
    /** 是否恢复了原始分支 */
    @JsonProperty("branchRestored")
    Boolean branchRestored,
    
    /** 原始分支 */
    @JsonProperty("originalBranch")
    String originalBranch,
    
    /** 错误信息 */
    @JsonProperty("error")
    String error
) {
    public static ExitWorktreeOutput success(String message, String returnedToDirectory, 
                                              boolean branchRestored, String originalBranch) {
        return new ExitWorktreeOutput(true, message, returnedToDirectory, branchRestored, originalBranch, null);
    }
    
    public static ExitWorktreeOutput error(String error) {
        return new ExitWorktreeOutput(false, null, null, null, null, error);
    }
}
