package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ExitWorktree 工具输入参数
 * 用于退出当前 Git Worktree 并返回主工作树
 */
public record ExitWorktreeInput(
    
    /** 是否恢复原始分支 */
    @JsonProperty("restoreBranch")
    Boolean restoreBranch,
    
    /** 是否强制退出（忽略未提交的更改警告） */
    @JsonProperty("force")
    Boolean force,
    
    /** 源文件名（用于确定上下文） */
    @JsonProperty("sourceFile")
    String sourceFile,
    
    /** 行号 */
    @JsonProperty("lineNumber")
    Integer lineNumber
) {
    public ExitWorktreeInput {
        // 默认值
        if (restoreBranch == null) {
            restoreBranch = true;
        }
        if (force == null) {
            force = false;
        }
    }
}
