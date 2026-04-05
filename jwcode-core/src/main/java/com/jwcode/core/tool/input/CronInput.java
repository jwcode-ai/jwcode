package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cron 工具输入参数
 */
public record CronInput(
    
    /** 定时任务 ID（用于删除） */
    @JsonProperty("id")
    String id,
    
    /** cron 表达式 */
    @JsonProperty("cronExpression")
    String cronExpression,
    
    /** 要执行的命令 */
    @JsonProperty("command")
    String command,
    
    /** 任务描述 */
    @JsonProperty("description")
    String description
) {}