package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * TodoWrite 工具输入参数
 * 用于创建、更新或删除待办事项
 * 
 * 支持两种格式：
 * 1. 简单的字符串格式（向后兼容）
 * 2. 结构化的 TodoItem 格式（推荐）
 * 
 * 修复记录：2026/4/30 - 添加 @JsonAlias 支持 action 作为 operation 的别名
 */
public record TodoWriteInput(
    
    /** 操作类型: "add", "edit", "delete", "replace_all" */
    @JsonProperty("operation")
    @JsonAlias("action")  // 【修复】支持 action 作为 operation 的别名
    String operation,
    
    /** 待办事项内容（简单字符串格式） */
    @JsonProperty("todo")
    @JsonAlias("todos")   // 【修复】兼容 LLM 可能传入的 todos 字段
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
    
    /**
     * 便捷方法：兼容获取 operation
     * 如果 action 不为 null 但 operation 为 null，返回 action
     * 这是一个额外的容错层，防止 Jackson 别名解析失败
     */
    public String getOperation() {
        return operation;
    }
}
