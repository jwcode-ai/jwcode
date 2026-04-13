package com.jwcode.core.tool.output;

/**
 * TaskUpdateTool 的输出结果
 * 
 * @param success 是否成功
 * @param id 任务ID
 * @param message 结果消息
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskUpdateOutput(
    boolean success,
    String id,
    String message
) {
    public static TaskUpdateOutput success(String id, String message) {
        return new TaskUpdateOutput(true, id, message);
    }
    
    public static TaskUpdateOutput error(String message) {
        return new TaskUpdateOutput(false, null, message);
    }
}
