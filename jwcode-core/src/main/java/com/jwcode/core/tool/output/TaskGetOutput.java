package com.jwcode.core.tool.output;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TaskGetTool 的输出结果
 * 
 * @param success 是否成功
 * @param id 任务ID
 * @param title 任务标题
 * @param description 任务描述
 * @param status 任务状态
 * @param priority 优先级
 * @param tags 标签列表
 * @param parentId 父任务ID
 * @param progress 进度 0-100
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param startedAt 开始时间
 * @param completedAt 完成时间
 * @param message 错误消息（失败时）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskGetOutput(
    boolean success,
    String id,
    String title,
    String description,
    String status,
    int priority,
    List<String> tags,
    String parentId,
    int progress,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String message
) {
    public static TaskGetOutput fromTask(com.jwcode.core.task.Task task) {
        return new TaskGetOutput(
            true,
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus() != null ? task.getStatus().name() : null,
            task.getPriority(),
            task.getTags(),
            task.getParentId(),
            task.getProgress(),
            task.getCreatedAt(),
            task.getUpdatedAt(),
            task.getStartedAt(),
            task.getCompletedAt(),
            null
        );
    }
    
    public static TaskGetOutput error(String message) {
        return new TaskGetOutput(false, null, null, null, null, 0, null, null, 0, 
            null, null, null, null, message);
    }
}
