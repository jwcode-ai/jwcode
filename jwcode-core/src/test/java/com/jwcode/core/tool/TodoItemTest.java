package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.input.TodoItem;
import com.jwcode.core.tool.input.TodoWriteInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TodoItem 和 TodoWriteInput 的单元测试
 */
class TodoItemTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testTodoItemParsing() throws Exception {
        // 测试解析用户之前传入的 JSON 格式
        String json = """
            {
                "todos": [
                    {"id": "1", "content": "创建Java测试项目结构和文件", "status": "in_progress", "priority": "high"},
                    {"id": "2", "content": "测试文件操作工具", "status": "pending", "priority": "high"}
                ]
            }
            """;
        
        TodoWriteInput input = objectMapper.readValue(json, TodoWriteInput.class);
        
        assertNotNull(input);
        assertNotNull(input.todos());
        assertEquals(2, input.todos().size());
        
        TodoItem first = input.todos().get(0);
        assertEquals("1", first.id());
        assertEquals("创建Java测试项目结构和文件", first.content());
        assertEquals("in_progress", first.status());
        assertEquals("high", first.priority());
    }

    @Test
    void testTodoItemToMarkdown() {
        TodoItem item = new TodoItem("1", "测试任务", "in_progress", "high");
        String markdown = item.toMarkdown();
        
        assertTrue(markdown.contains("[~]")); // in_progress 状态
        assertTrue(markdown.contains("**#1**")); // ID
        assertTrue(markdown.contains("🔴")); // 高优先级
        assertTrue(markdown.contains("测试任务"));
    }

    @Test
    void testTodoItemFromMarkdown() {
        String markdown = "- [x] **#1** 完成的任务 🟢";
        TodoItem item = TodoItem.fromMarkdown(markdown);
        
        assertNotNull(item);
        assertEquals("1", item.id());
        assertEquals("完成的任务", item.content());
        assertEquals("completed", item.status());
        assertEquals("low", item.priority());
    }

    @Test
    void testBackwardCompatibility() throws Exception {
        // 测试旧格式：使用简单的 todo 字符串
        String json = """
            {
                "operation": "add",
                "todo": "这是一个简单的待办事项"
            }
            """;
        
        TodoWriteInput input = objectMapper.readValue(json, TodoWriteInput.class);
        
        assertNotNull(input);
        assertEquals("add", input.operation());
        assertEquals("这是一个简单的待办事项", input.todo());
    }

    @Test
    void testReplaceAllOperation() throws Exception {
        String json = """
            {
                "operation": "replace_all",
                "todos": [
                    {"id": "1", "content": "任务1", "status": "pending", "priority": "high"},
                    {"id": "2", "content": "任务2", "status": "completed", "priority": "medium"}
                ]
            }
            """;
        
        TodoWriteInput input = objectMapper.readValue(json, TodoWriteInput.class);
        
        assertEquals("replace_all", input.operation());
        assertEquals(2, input.todos().size());
    }
}
