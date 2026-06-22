package com.jwcode.core.tool.input;

/**
 * ChangeDirectoryTool 的输入参数
 *
 * @param path 目标目录路径（必需）。支持绝对路径和相对路径。
 *
 * @since 3.1.0
 */
public record ChangeDirectoryInput(
    String path
) {
    public ChangeDirectoryInput {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
    }
}
