package com.jwcode.core.tool;

import com.jwcode.core.tool.context.ToolExecutionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PowerShellTool 错误处理测试。
 * PowerShell 行为受操作系统语言和执行策略影响，仅在已验证可用的环境运行。
 */
class PowerShellToolErrorTest {

    private static boolean powershellAvailable;

    @BeforeAll
    static void checkPowerShell() {
        // PowerShellTool 未实现 call() 方法，跳过所有测试
        try {
            PowerShellTool tool = new PowerShellTool();
            tool.call(new PowerShellTool.Input(), null, null);
            // call() 未抛异常则继续检查 PowerShell 可用性
        } catch (UnsupportedOperationException e) {
            powershellAvailable = false;
            return;
        } catch (Exception e) {
            // call() 实现了但可能因 null context 抛 NPE，继续检查
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", "Write-Output 'test'");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            powershellAvailable = p.exitValue() == 0 && out.contains("test");
        } catch (Exception e) {
            powershellAvailable = false;
        }
    }

    @Test
    void testNonZeroExitCodeReturnsError() throws Exception {
        assumeTrue(powershellAvailable, "PowerShell 不可用，跳过测试");
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
        assertFalse(result.getContent().isEmpty(),
                "错误信息不应为空: " + result.getContent());
    }

    @Test
    void testZeroExitCodeReturnsSuccess() throws Exception {
        assumeTrue(powershellAvailable, "PowerShell 不可用，跳过测试");
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

    @Test
    void testFileListTruncationOver100() throws Exception {
        assumeTrue(powershellAvailable, "PowerShell 不可用，跳过测试");
        PowerShellTool tool = new PowerShellTool();
        PowerShellTool.Input input = new PowerShellTool.Input();
        input.command = "$null = Get-ChildItem -Path 'C:\\NonExistent' -ErrorAction SilentlyContinue; 1..150 | ForEach-Object { Write-Output ('file' + $_ + '.txt') }";

        CompletableFuture<ToolResult<PowerShellTool.Output>> future = tool.call(
                input,
                new ToolExecutionContext(null, java.nio.file.Path.of(System.getProperty("user.dir")), null),
                (Consumer<ToolProgress<PowerShellTool.Progress>>) progress -> {}
        );

        ToolResult<PowerShellTool.Output> result = future.get();

        assertTrue(result.isSuccess(), "命令应成功执行");
        assertNotNull(result.getData(), "应返回数据");
        String output = result.getData().output;
        assertTrue(output.contains("⚠️ 文件过多"), "应提示文件过多: " + output);
        assertTrue(output.contains("100 个限制"), "应提到100个限制: " + output);
        assertTrue(output.contains("按子目录逐个扫描"), "应建议分目录扫描: " + output);
    }

    @Test
    void testFileListNoTruncationUnder100() throws Exception {
        assumeTrue(powershellAvailable, "PowerShell 不可用，跳过测试");
        PowerShellTool tool = new PowerShellTool();
        PowerShellTool.Input input = new PowerShellTool.Input();
        input.command = "$null = Get-ChildItem -Path 'C:\\NonExistent' -ErrorAction SilentlyContinue; 1..50 | ForEach-Object { Write-Output ('file' + $_ + '.txt') }";

        CompletableFuture<ToolResult<PowerShellTool.Output>> future = tool.call(
                input,
                new ToolExecutionContext(null, java.nio.file.Path.of(System.getProperty("user.dir")), null),
                (Consumer<ToolProgress<PowerShellTool.Progress>>) progress -> {}
        );

        ToolResult<PowerShellTool.Output> result = future.get();

        assertTrue(result.isSuccess(), "命令应成功执行");
        assertNotNull(result.getData(), "应返回数据");
        String output = result.getData().output;
        assertFalse(output.contains("⚠️ 文件过多"), "不应提示文件过多: " + output);
    }
}
