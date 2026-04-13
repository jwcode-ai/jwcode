package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * EnterWorktree 工具输入参数
 * 用于进入指定的 Git Worktree
 */
public record EnterWorktreeInput(
    
    /** Worktree 路径或名称 */
    @JsonProperty("path")
    String path,
    
    /** 是否在退出时恢复原始分支 */
    @JsonProperty("restoreBranch")
    Boolean restoreBranch,
    
    /** 源文件名（用于确定上下文） */
    @JsonProperty("sourceFile")
    String sourceFile,
    
    /** 行号 */
    @JsonProperty("lineNumber")
    Integer lineNumber
) {
    public EnterWorktreeInput {
        // 默认值
        if (restoreBranch == null) {
            restoreBranch = false;
        }
    }
}
