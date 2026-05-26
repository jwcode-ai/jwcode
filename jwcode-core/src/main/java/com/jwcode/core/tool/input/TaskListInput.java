package com.jwcode.core.tool.input;

/**
 * TaskListTool 的输入参数
 * 
 * @param activeOnly 是否只显示活跃任务（可选，默认 false）
 * @param status 按状态过滤（可选）
 * @param tag 按标签过滤（可选）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskListInput(
    Boolean activeOnly,
    String status,
    String tag
) {
    public TaskListInput {
        if (activeOnly == null) {
            activeOnly = false;
        }
    }
    
    public TaskListInput() {
        this(false, null, null);
    }
}
