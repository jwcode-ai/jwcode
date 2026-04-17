package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * TodoWrite 工具输入参数
 * 用于创建、更新或删除待办事项
 * 
 * 支持两种格式：
 * 1. 简单的字符串格式（向后兼容）
 * 2. 结构化的 TodoItem 格式（推荐）
 */
public record TodoWriteInput(
    
    /** 操作类型: "add", "edit", "delete", "replace_all" */
    @JsonProperty("operation")
    String operation,
    
    /** 待办事项内容（简单字符串格式） */
    @JsonProperty("todo")
    String todo,
    
    /** 索引位置（用于编辑或删除） */
    @JsonProperty("index")
    Integer index,
    
    /** 要编辑的新内容 */
    @JsonProperty("newContent")
    String newContent,
    
    /** 完整的待办事项列表（结构化格式，用于 replace_all） */
    @JsonProperty("todos")
    List<TodoItem> todos,
    
    /** 源文件名（用于确定上下文） */
    @JsonProperty("sourceFile")
    String sourceFile,
    
    /** 行号 */
    @JsonProperty("lineNumber")
    Integer lineNumber
) {
    public TodoWriteInput {
        // 默认值
        if (operation == null) {
            operation = "add";
        }
    }
    
    /**
     * 便捷方法：获取 todos 作为字符串列表
     * 如果 todos 为空，返回带 todo 内容的单一列表
     */
    public List<TodoItem> getTodosOrDefault() {
        if (todos != null && !todos.isEmpty()) {
            return todos;
        }
        if (todo != null && !todo.isEmpty()) {
            return List.of(TodoItem.of(todo));
        }
        return List.of();
    }
}
