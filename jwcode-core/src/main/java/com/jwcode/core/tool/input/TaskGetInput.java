package com.jwcode.core.tool.input;

/**
 * TaskGetTool 的输入参数
 * 
 * @param id 任务ID（必需）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskGetInput(String id) {
    public TaskGetInput {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id is required");
        }
    }
}
