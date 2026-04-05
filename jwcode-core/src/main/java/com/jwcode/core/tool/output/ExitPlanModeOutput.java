package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ExitPlanModeV2 工具输出结果
 */
public record ExitPlanModeOutput(
    
    /** 是否成功退出计划模式 */
    @JsonProperty("success")
    boolean success,
    
    /** 当前模式 */
    @JsonProperty("mode")
    String mode,
    
    /** 摘要 */
    @JsonProperty("summary")
    String summary,
    
    /** 错误信息（如果有） */
    @JsonProperty("error")
    String error
) {
    public static ExitPlanModeOutput success(String mode, String summary) {
        return new ExitPlanModeOutput(true, mode, summary, null);
    }
    
    public static ExitPlanModeOutput failure(String error) {
        return new ExitPlanModeOutput(false, "plan", null, error);
    }
}