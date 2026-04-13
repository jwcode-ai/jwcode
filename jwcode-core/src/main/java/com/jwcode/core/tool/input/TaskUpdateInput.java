package com.jwcode.core.tool.input;

import java.util.List;

/**
 * TaskUpdateTool 的输入参数
 * 
 * @param id 任务ID（必需）
 * @param title 新标题（可选）
 * @param description 新描述（可选）
 * @param status 新状态（可选）
 * @param priority 新优先级（可选）
 * @param tags 新标签列表（可选）
 * @param progress 新进度（可选，0-100）
 * @param outputAppend 追加的输出内容（可选）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskUpdateInput(
    String id,
    String title,
    String description,
    String status,
    Integer priority,
    List<String> tags,
    Integer progress,
    String outputAppend
) {
    public TaskUpdateInput {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id is required");
        }
    }
}
