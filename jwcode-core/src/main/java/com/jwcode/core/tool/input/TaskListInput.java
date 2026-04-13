package com.jwcode.core.tool.input;

/**
 * TaskListTool 的输入参数
 * 
 * @param activeOnly 是否只显示活跃任务（可选，默认 false）
 * @param status 按状态过滤（可选）
 * @param page 页码（可选，默认 1）
 * @param pageSize 每页大小（可选，默认 20）
 * @param tag 按标签过滤（可选）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskListInput(
    Boolean activeOnly,
    String status,
    Integer page,
    Integer pageSize,
    String tag
) {
    public TaskListInput {
        if (activeOnly == null) {
            activeOnly = false;
        }
        if (page == null || page < 1) {
            page = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
    }
    
    public TaskListInput() {
        this(false, null, 1, 20, null);
    }
}
