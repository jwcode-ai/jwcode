package com.jwcode.cli.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.*;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ActivityTrackingToolExecutor 错误摘要测试
 */
class ActivityTrackingToolExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testFailedFileReadShowsFailureSummary() throws Exception {
        ToolRegistry registry = ToolRegistry.createDefault();
        ToolExecutor delegate = new ToolExecutor(registry);
        ActivityTrackingToolExecutor executor = new ActivityTrackingToolExecutor(delegate);

        String nonExistentPath = "C:/__jwcode_non_existent_abc123.txt";
        var inputJson = MAPPER.readTree("{\"file_path\":\"" + nonExistentPath + "\"}");
        ToolExecutionContext context = new ToolExecutionContext(
                null, Path.of(System.getProperty("user.dir")), null
        );

        var future = executor.execute(
                "FileReadTool", inputJson, context
        );

        ToolExecutor.ToolExecutionResult result = future.get();
        assertFalse(result.isSuccess(), "FileReadTool 失败应返回 isSuccess() == false");
        assertNotNull(result.getErrorMessage(), "应包含错误信息");
        assertTrue(result.getErrorMessage().contains("不存在") || result.getErrorMessage().contains("失败"),
                "错误信息应说明文件不存在: " + result.getErrorMessage());
    }

    @Test
    void testFailedBashShowsFailureSummary() throws Exception {
        ToolRegistry registry = ToolRegistry.createDefault();
        ToolExecutor delegate = new ToolExecutor(registry);
        ActivityTrackingToolExecutor executor = new ActivityTrackingToolExecutor(delegate);

        String os = System.getProperty("os.name").toLowerCase();
        String command = os.contains("win") ? "exit 1" : "exit 1";
        var inputJson = MAPPER.readTree("{\"command\":\"" + command + "\"}");
        ToolExecutionContext context = new ToolExecutionContext(
                null, Path.of(System.getProperty("user.dir")), null
        );

        var future = executor.execute(
                "BashTool", inputJson, context
        );

        ToolExecutor.ToolExecutionResult result = future.get();
        assertFalse(result.isSuccess(), "BashTool 非 0 退出码应返回 isSuccess() == false");
        assertNotNull(result.getErrorMessage(), "应包含错误信息");
        assertTrue(result.getErrorMessage().contains("exitCode=1") || result.getErrorMessage().contains("退出码"),
                "错误信息应包含退出码: " + result.getErrorMessage());
    }
}
