package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * TodoWrite 工具输出结果
 */
public record TodoWriteOutput(
    
    /** 操作是否成功 */
    @JsonProperty("success")
    boolean success,
    
    /** 当前所有待办事项 */
    @JsonProperty("todos")
    List<String> todos,
    
    /** 操作消息 */
    @JsonProperty("message")
    String message,
    
    /** 操作的索引 */
    @JsonProperty("index")
    Integer index
) {
    public static TodoWriteOutput success(List<String> todos, String message) {
        return new TodoWriteOutput(true, todos, message, null);
    }
    
    public static TodoWriteOutput success(List<String> todos, String message, int index) {
        return new TodoWriteOutput(true, todos, message, index);
    }
    
    public static TodoWriteOutput error(String message) {
        return new TodoWriteOutput(false, null, message, null);
    }
}