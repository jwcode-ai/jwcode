package com.jwcode.core.tool.input;

/**
 * TaskOutputTool 的输入参数
 * 
 * @param id 任务ID（必需）
 * @param offset 输出内容偏移量（可选，默认 0）
 * @param limit 最大返回字符数（可选，默认 -1 表示全部）
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public record TaskOutputInput(
    String id,
    Integer offset,
    Integer limit
) {
    public TaskOutputInput {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id is required");
        }
        if (offset == null || offset < 0) {
            offset = 0;
        }
        if (limit == null) {
            limit = -1;
        }
    }
    
    public TaskOutputInput(String id) {
        this(id, 0, -1);
    }
}
