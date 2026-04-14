package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolExecutor 错误传播测试
 */
class ToolExecutorErrorPropagationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testFileWriteToolErrorPropagatedAsOuterError() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        // 使用一个通常无权限写入的路径（如系统根目录）来触发失败
        String invalidPath = "C:/Windows/System32/__jwcode_test_no_permission.txt";
        String content = "test";
        JsonNode inputJson = MAPPER.readTree("{\"path\":\"" + invalidPath + "\",\"content\":\"" + content + "\"}");

        ToolExecutionContext context = new ToolExecutionContext(
                null, Path.of(System.getProperty("user.dir")), null
        );

        CompletableFuture<ToolExecutor.ToolExecutionResult> future = executor.execute(
                "FileWriteTool", inputJson, context
        );

        ToolExecutor.ToolExecutionResult result = future.get();

        assertFalse(result.isSuccess(), "FileWriteTool 写入失败应传播为外层 execution error");
        assertNotNull(result.getErrorMessage(), "错误信息不应为空");
        assertTrue(result.getErrorMessage().contains("拒绝访问") || result.getErrorMessage().contains("denied") || result.getErrorMessage().contains("写入文件失败"),
                "错误信息应说明访问被拒绝或写入失败: " + result.getErrorMessage());
    }

    @Test
    void testFileReadToolErrorPropagatedAsOuterError() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        String nonExistentPath = "C:/__jwcode_non_existent_file_12345.txt";
        JsonNode inputJson = MAPPER.readTree("{\"file_path\":\"" + nonExistentPath + "\"}");

        ToolExecutionContext context = new ToolExecutionContext(
                null, Path.of(System.getProperty("user.dir")), null
        );

        CompletableFuture<ToolExecutor.ToolExecutionResult> future = executor.execute(
                "FileReadTool", inputJson, context
        );

        ToolExecutor.ToolExecutionResult result = future.get();

        assertFalse(result.isSuccess(), "FileReadTool 读取不存在文件应传播为外层 execution error");
        assertNotNull(result.getErrorMessage(), "错误信息不应为空");
        assertTrue(result.getErrorMessage().contains("不存在") || result.getErrorMessage().contains("not found") || result.getErrorMessage().contains("失败"),
                "错误信息应说明文件不存在: " + result.getErrorMessage());
    }

    @Test
    void testSuccessfulToolExecutionRemainsSuccess() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        JsonNode inputJson = MAPPER.readTree("{\"command\":\"echo hello\"}");

        ToolExecutionContext context = new ToolExecutionContext(
                null, Path.of(System.getProperty("user.dir")), null
        );

        CompletableFuture<ToolExecutor.ToolExecutionResult> future = executor.execute(
                "BashTool", inputJson, context
        );

        ToolExecutor.ToolExecutionResult result = future.get();

        assertTrue(result.isSuccess(), "成功的工具执行应保持为 success");
        assertNotNull(result.getResult(), "成功结果应包含 ToolResult");
        assertTrue(result.getResult().isSuccess(), "内部 ToolResult 也应标记为 success");
    }
}
