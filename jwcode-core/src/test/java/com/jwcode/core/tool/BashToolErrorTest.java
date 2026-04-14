package com.jwcode.core.tool;

import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.BashInput;
import com.jwcode.core.tool.output.BashOutput;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BashTool 错误处理测试
 */
class BashToolErrorTest {

    @Test
    void testNonZeroExitCodeReturnsError() throws Exception {
        BashTool tool = new BashTool();
        // 使用跨平台的失败命令
        String os = System.getProperty("os.name").toLowerCase();
        BashInput input = new BashInput(os.contains("win") ? "exit 1" : "exit 1");

        CompletableFuture<ToolResult<BashOutput>> future = tool.call(
                input,
                new ToolExecutionContext(null, java.nio.file.Path.of(System.getProperty("user.dir")), null),
                (Consumer<ToolProgress<BashTool.BashProgress>>) progress -> {}
        );

        ToolResult<BashOutput> result = future.get();

        assertFalse(result.isSuccess(), "非 0 退出码应该返回 isSuccess() == false");
        assertNotNull(result.getContent(), "错误信息不应为空");
        assertTrue(result.getContent().contains("exitCode=1") || result.getContent().contains("退出码"),
                "错误信息应包含退出码: " + result.getContent());
    }

    @Test
    void testZeroExitCodeReturnsSuccess() throws Exception {
        BashTool tool = new BashTool();
        String os = System.getProperty("os.name").toLowerCase();
        BashInput input = new BashInput(os.contains("win") ? "echo hello" : "echo hello");

        CompletableFuture<ToolResult<BashOutput>> future = tool.call(
                input,
                new ToolExecutionContext(null, java.nio.file.Path.of(System.getProperty("user.dir")), null),
                (Consumer<ToolProgress<BashTool.BashProgress>>) progress -> {}
        );

        ToolResult<BashOutput> result = future.get();

        assertTrue(result.isSuccess(), "0 退出码应该返回 isSuccess() == true");
        assertNotNull(result.getData(), "成功结果应包含数据");
    }
}
