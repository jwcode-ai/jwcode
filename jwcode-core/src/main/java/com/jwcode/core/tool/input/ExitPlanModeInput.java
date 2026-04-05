package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ExitPlanModeV2 工具输入参数
 */
public record ExitPlanModeInput(
    
    /** 确认退出计划模式 */
    @JsonProperty("confirmed")
    Boolean confirmed,
    
    /** 摘要描述 */
    @JsonProperty("summary")
    String summary,
    
    /** 失败原因（如果退出失败） */
    @JsonProperty("reason")
    String reason
) {}