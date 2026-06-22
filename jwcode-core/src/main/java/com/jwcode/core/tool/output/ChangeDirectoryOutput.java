package com.jwcode.core.tool.output;

/**
 * ChangeDirectoryTool 的输出结果
 *
 * @param success 是否成功
 * @param newPath 切换后的绝对路径
 * @param message 结果消息
 *
 * @since 3.1.0
 */
public record ChangeDirectoryOutput(
    boolean success,
    String newPath,
    String message
) {
    public static ChangeDirectoryOutput success(String newPath, String message) {
        return new ChangeDirectoryOutput(true, newPath, message);
    }

    public static ChangeDirectoryOutput error(String message) {
        return new ChangeDirectoryOutput(false, null, message);
    }
}
