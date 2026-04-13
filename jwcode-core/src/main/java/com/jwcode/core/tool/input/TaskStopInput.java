package com.jwcode.core.tool.input;

/**
 * TaskStopTool 的输入参数
 * 
 * @param id 任务ID（必需）
 * @param force 是否强制停止（可选，默认 true）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskStopInput(
    String id,
    Boolean force
) {
    public TaskStopInput {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id is required");
        }
        if (force == null) {
            force = true;
        }
    }
    
    public TaskStopInput(String id) {
        this(id, true);
    }
}
