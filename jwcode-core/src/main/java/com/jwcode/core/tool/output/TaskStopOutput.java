package com.jwcode.core.tool.output;

/**
 * TaskStopTool 的输出结果
 * 
 * @param success 是否成功
 * @param id 任务ID
 * @param previousStatus 停止前的状态
 * @param message 结果消息
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskStopOutput(
    boolean success,
    String id,
    String previousStatus,
    String message
) {
    public static TaskStopOutput success(String id, String previousStatus, String message) {
        return new TaskStopOutput(true, id, previousStatus, message);
    }
    
    public static TaskStopOutput error(String message) {
        return new TaskStopOutput(false, null, null, message);
    }
}
