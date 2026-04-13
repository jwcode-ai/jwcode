package com.jwcode.core.tool.output;

/**
 * TaskCreateTool 的输出结果
 * 
 * @param success 是否成功
 * @param id 创建的任务ID
 * @param message 结果消息
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskCreateOutput(
    boolean success,
    String id,
    String message
) {
    public static TaskCreateOutput success(String id, String message) {
        return new TaskCreateOutput(true, id, message);
    }
    
    public static TaskCreateOutput error(String message) {
        return new TaskCreateOutput(false, null, message);
    }
}
