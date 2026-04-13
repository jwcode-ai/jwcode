package com.jwcode.core.tool.input;

import java.util.List;

/**
 * TaskCreateTool 的输入参数
 * 
 * @param title 任务标题（必需）
 * @param description 任务描述（可选）
 * @param priority 优先级 1-10（可选，默认 5）
 * @param tags 标签列表（可选）
 * @param parentId 父任务ID（可选，用于创建子任务）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskCreateInput(
    String title,
    String description,
    Integer priority,
    List<String> tags,
    String parentId
) {
    public TaskCreateInput {
        if (title == null) {
            throw new IllegalArgumentException("title is required");
        }
        if (priority == null) {
            priority = 5;
        }
        if (priority < 1 || priority > 10) {
            throw new IllegalArgumentException("priority must be between 1 and 10");
        }
    }
    
    public TaskCreateInput(String title) {
        this(title, null, 5, null, null);
    }
    
    public TaskCreateInput(String title, String description) {
        this(title, description, 5, null, null);
    }
}
