package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * TodoWrite 工具输入参数
 * 用于创建、更新或删除待办事项
 */
public record TodoWriteInput(
    
    /** 操作类型: "add", "edit", "delete", "replace_all" */
    @JsonProperty("operation")
    String operation,
    
    /** 待办事项内容 */
    @JsonProperty("todo")
    String todo,
    
    /** 索引位置（用于编辑或删除） */
    @JsonProperty("index")
    Integer index,
    
    /** 要编辑的新内容 */
    @JsonProperty("newContent")
    String newContent,
    
    /** 完整的待办事项列表（用于 replace_all） */
    @JsonProperty("todos")
    List<String> todos,
    
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
}