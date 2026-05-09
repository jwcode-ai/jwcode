package com.jwcode.core.a2a.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskOutput 单元测试
 */
@DisplayName("TaskOutput 单元测试")
class TaskOutputTest {

    @Test
    @DisplayName("success() 应创建成功的输出")
    void success() {
        TaskOutput output = TaskOutput.success("Task completed successfully");
        assertTrue(output.isSuccess());
        assertEquals("Task completed successfully", output.getSummary());
    }

    @Test
    @DisplayName("success() 带数据应创建成功的输出")
    void successWithData() {
        TaskOutput output = TaskOutput.success("Done", Map.of("key", "value"));
        assertTrue(output.isSuccess());
        assertEquals("value", output.getData().get("key"));
    }

    @Test
    @DisplayName("failure() 应创建失败的输出")
    void failure() {
        TaskOutput output = TaskOutput.failure("Something went wrong");
        assertFalse(output.isSuccess(), "Failure output should not be successful");
        assertTrue(output.getSummary().startsWith("Task failed"));
        assertTrue(output.getMessages().stream().anyMatch(m -> m.contains("Something went wrong")));
    }

    @Test
    @DisplayName("isSuccess 对以 'Task failed' 开头的摘要应返回 false")
    void isSuccessWithFailedSummary() {
        TaskOutput output = TaskOutput.success("Task failed: some error");
        assertFalse(output.isSuccess());
    }

    @Test
    @DisplayName("isSuccess 对以 'Task timeout' 开头的摘要应返回 false")
    void isSuccessWithTimeoutSummary() {
        TaskOutput output = TaskOutput.success("Task timeout: test-001");
        assertFalse(output.isSuccess());
    }

    @Test
    @DisplayName("isSuccess 对空摘要应返回 false")
    void isSuccessWithEmptySummary() {
        TaskOutput output = TaskOutput.success("");
        assertFalse(output.isSuccess());
    }

    @Test
    @DisplayName("builder 应正确构建 TaskOutput")
    void builder() {
        TaskOutput output = TaskOutput.builder()
                .summary("Build test")
                .data(Map.of("count", 3))
                .fileChanges(List.of(
                        new TaskOutput.FileChange(
                                TaskOutput.FileChange.Operation.ADDED,
                                "src/main/Foo.java",
                                10, 0)))
                .messages(List.of("Step 1 done"))
                .build();

        assertEquals("Build test", output.getSummary());
        assertEquals(3, output.getData().get("count"));
        assertEquals(1, output.getFileChanges().size());
        assertEquals(1, output.getMessages().size());
    }

    @Test
    @DisplayName("FileChange 应正确存储变更信息")
    void fileChange() {
        TaskOutput.FileChange change = new TaskOutput.FileChange(
                TaskOutput.FileChange.Operation.MODIFIED,
                "src/main/Bar.java",
                5, 2);

        assertEquals(TaskOutput.FileChange.Operation.MODIFIED, change.getOperation());
        assertEquals("src/main/Bar.java", change.getFilePath());
        assertEquals(5, change.getLinesAdded());
        assertEquals(2, change.getLinesDeleted());
    }
}
