package com.jwcode.core.tool.output;

import java.util.List;

/**
 * TaskListTool 的输出结果
 * 
 * @param success 是否成功
 * @param tasks 任务列表
 * @param total 总数
 * @param message 错误消息（失败时）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskListOutput(
    boolean success,
    List<TaskSummary> tasks,
    int total,
    String message
) {
    public TaskListOutput {
        if (tasks == null) {
            tasks = List.of();
        }
    }
    
    public static TaskListOutput success(List<TaskSummary> tasks, int total) {
        return new TaskListOutput(true, tasks, total, null);
    }
    
    public static TaskListOutput error(String message) {
        return new TaskListOutput(false, List.of(), 0, message);
    }
    
    /**
     * 任务摘要信息
     */
    public record TaskSummary(
        String id,
        String title,
        String status,
        int priority,
        int progress,
        String createdAt
    ) {
        public static TaskSummary fromTask(com.jwcode.core.task.Task task) {
            return new TaskSummary(
                task.getId(),
                task.getTitle(),
                task.getStatus() != null ? task.getStatus().name() : "UNKNOWN",
                task.getPriority(),
                task.getProgress(),
                task.getCreatedAt() != null ? task.getCreatedAt().toString() : null
            );
        }
    }
}
