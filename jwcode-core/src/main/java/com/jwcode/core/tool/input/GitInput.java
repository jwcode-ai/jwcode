package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Git 工具输入参数
 */
public record GitInput(
    
    /** Git 操作类型: "status", "diff", "commit", "branch", "log", "push", "pull" */
    @JsonProperty("operation")
    String operation,
    
    /** 提交信息 */
    @JsonProperty("message")
    String message,
    
    /** 分支名 */
    @JsonProperty("branch")
    String branch,
    
    /** 文件路径（用于 diff 或 add） */
    @JsonProperty("file")
    String file,
    
    /** 远程仓库名 */
    @JsonProperty("remote")
    String remote,
    
    /** 其他参数 */
    @JsonProperty("args")
    String args
) {}