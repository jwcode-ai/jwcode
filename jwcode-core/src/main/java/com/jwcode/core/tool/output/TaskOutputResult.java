package com.jwcode.core.tool.output;

/**
 * TaskOutputTool 的输出结果
 * 
 * @param success 是否成功
 * @param id 任务ID
 * @param output 输出内容
 * @param totalLength 输出总长度
 * @param offset 本次返回的起始偏移量
 * @param hasMore 是否还有更多内容
 * @param message 错误消息（失败时）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskOutputResult(
    boolean success,
    String id,
    String output,
    int totalLength,
    int offset,
    boolean hasMore,
    String message
) {
    public static TaskOutputResult success(String id, String output, int totalLength, int offset, boolean hasMore) {
        return new TaskOutputResult(true, id, output, totalLength, offset, hasMore, null);
    }
    
    public static TaskOutputResult error(String message) {
        return new TaskOutputResult(false, null, null, 0, 0, false, message);
    }
}
