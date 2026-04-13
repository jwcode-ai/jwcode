package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WorktreeList 工具输入参数
 * 用于列出所有 Git Worktree
 */
public record WorktreeListInput(
    
    /** 是否显示详细信息 */
    @JsonProperty("verbose")
    Boolean verbose,
    
    /** 源文件名（用于确定上下文） */
    @JsonProperty("sourceFile")
    String sourceFile,
    
    /** 行号 */
    @JsonProperty("lineNumber")
    Integer lineNumber
) {
    public WorktreeListInput {
        // 默认值
        if (verbose == null) {
            verbose = false;
        }
    }
}
