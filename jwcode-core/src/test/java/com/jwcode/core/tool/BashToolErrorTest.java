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
        assertFalse(result.getContent().isEmpty(),
                "错误信息不应为空: " + result.getContent());
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

    @Test
    void testFileListTruncationOver100() throws Exception {
        BashTool tool = new BashTool();
        String os = System.getProperty("os.name").toLowerCase();
        String command;
        // 命令中包含 "dir " 以触发文件列表过滤逻辑
        if (os.contains("win")) {
            command = "echo dummy dir marker >nul & for /L %i in (1,1,150) do @echo file%i.txt";
        } else {
            command = "echo 'dummy dir marker' >/dev/null; seq 1 150 | sed 's/^/file/' | sed 's/$/.txt/'";
        }
        BashInput input = new BashInput(command);

        CompletableFuture<ToolResult<BashOutput>> future = tool.call(
                input,
                new ToolExecutionContext(null, java.nio.file.Path.of(System.getProperty("user.dir")), null),
                (Consumer<ToolProgress<BashTool.BashProgress>>) progress -> {}
        );

        ToolResult<BashOutput> result = future.get();

        assertTrue(result.isSuccess(), "命令应成功执行");
        assertNotNull(result.getData(), "应返回数据");
        String stdout = result.getData().stdout();
        assertTrue(stdout.contains("⚠️ 文件过多"), "应提示文件过多: " + stdout);
        assertTrue(stdout.contains("100 个限制"), "应提到100个限制: " + stdout);
        assertTrue(stdout.contains("按子目录逐个扫描"), "应建议分目录扫描: " + stdout);
    }

    @Test
    void testFileListNoTruncationUnder100() throws Exception {
        BashTool tool = new BashTool();
        String os = System.getProperty("os.name").toLowerCase();
        String command;
        // 命令中包含 "dir " 以触发文件列表过滤逻辑
        if (os.contains("win")) {
            command = "echo dummy dir marker >nul & for /L %i in (1,1,50) do @echo file%i.txt";
        } else {
            command = "echo 'dummy dir marker' >/dev/null; seq 1 50 | sed 's/^/file/' | sed 's/$/.txt/'";
        }
        BashInput input = new BashInput(command);

        CompletableFuture<ToolResult<BashOutput>> future = tool.call(
                input,
                new ToolExecutionContext(null, java.nio.file.Path.of(System.getProperty("user.dir")), null),
                (Consumer<ToolProgress<BashTool.BashProgress>>) progress -> {}
        );

        ToolResult<BashOutput> result = future.get();

        assertTrue(result.isSuccess(), "命令应成功执行");
        assertNotNull(result.getData(), "应返回数据");
        String stdout = result.getData().stdout();
        assertFalse(stdout.contains("⚠️ 文件过多"), "不应提示文件过多: " + stdout);
    }
}
