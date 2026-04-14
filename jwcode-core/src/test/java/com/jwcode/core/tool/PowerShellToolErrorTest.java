package com.jwcode.core.tool;

import com.jwcode.core.tool.context.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PowerShellTool 错误处理测试
 */
class PowerShellToolErrorTest {

    @Test
    void testNonZeroExitCodeReturnsError() throws Exception {
        PowerShellTool tool = new PowerShellTool();
        PowerShellTool.Input input = new PowerShellTool.Input();
        input.command = "exit 42";

        CompletableFuture<ToolResult<PowerShellTool.Output>> future = tool.call(
                input,
                new ToolExecutionContext(null, java.nio.file.Path.of(System.getProperty("user.dir")), null),
                (Consumer<ToolProgress<PowerShellTool.Progress>>) progress -> {}
        );

        ToolResult<PowerShellTool.Output> result = future.get();

        assertFalse(result.isSuccess(), "非 0 退出码应该返回 isSuccess() == false");
        assertNotNull(result.getContent(), "错误信息不应为空");
        assertTrue(result.getContent().contains("exitCode=42") || result.getContent().contains("退出码"),
                "错误信息应包含退出码: " + result.getContent());
    }

    @Test
    void testZeroExitCodeReturnsSuccess() throws Exception {
        PowerShellTool tool = new PowerShellTool();
        PowerShellTool.Input input = new PowerShellTool.Input();
        input.command = "Write-Output 'hello'";

        CompletableFuture<ToolResult<PowerShellTool.Output>> future = tool.call(
                input,
                new ToolExecutionContext(null, java.nio.file.Path.of(System.getProperty("user.dir")), null),
                (Consumer<ToolProgress<PowerShellTool.Progress>>) progress -> {}
        );

        ToolResult<PowerShellTool.Output> result = future.get();

        assertTrue(result.isSuccess(), "0 退出码应该返回 isSuccess() == true");
        assertNotNull(result.getData(), "成功结果应包含数据");
        assertEquals(0, result.getData().exitCode, "退出码应为 0");
    }
}
