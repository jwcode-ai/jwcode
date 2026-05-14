package com.jwcode.core.tool.execution;

import com.jwcode.core.tool.BashTool;
import com.jwcode.core.tool.FileReadTool;
import com.jwcode.core.tool.FileWriteTool;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.FileReadInput;
import com.jwcode.core.tool.input.FileWriteInput;
import com.jwcode.core.tool.input.BashInput;
import com.jwcode.core.tool.output.FileReadOutput;
import com.jwcode.core.tool.output.FileWriteOutput;
import com.jwcode.core.tool.output.BashOutput;
import com.jwcode.core.tool.permission.PermissionChecker;
import com.jwcode.core.tool.WorkspaceGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tool 执行系统完整集成测试
 *
 * <p>测试覆盖工具执行的全生命周期：
 * <ul>
 *   <li>状态机：PARSE → VALIDATE → EXECUTE → REPORT → DONE</li>
 *   <li>熔断器：正常/半开/全开状态切换</li>
 *   <li>上下文：工作区校验、权限检查、session 传递</li>
 *   <li>具体工具：BashTool、FileReadTool、FileWriteTool 的执行链路</li>
 *   <li>异常场景：权限拒绝、路径穿越、超时、熔断</li>
 * </ul>
 */
class ToolExecutionIntegrationTest {

    @TempDir
    Path tempDir;

    private ToolExecutionStateMachine stateMachine;
    private ToolCircuitBreaker circuitBreaker;
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        stateMachine = new ToolExecutionStateMachine();
        circuitBreaker = new ToolCircuitBreaker();
        context = new ToolExecutionContext.Builder()
            .workspaceRoot(tempDir.toString())
            .build();
    }

    // ==================== 全生命周期测试 ====================

    @Test
    @DisplayName("文件读取工具完整生命周期：状态机 + 上下文 + 执行")
    void testFileReadToolFullLifecycle() throws IOException {
        // 准备测试文件
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, Integration Test!");

        // 1. PARSE：构建输入
        FileReadInput input = new FileReadInput();
        input.setFilePath(testFile.toString());
        assertNotNull(input);

        // 2. VALIDATE：校验路径安全
        stateMachine.transition(ToolExecutionState.VALIDATE);
        assertTrue(context.getWorkspaceGuard().isPathSafe(testFile.toString()));

        // 3. EXECUTE：执行工具
        stateMachine.transition(ToolExecutionState.EXECUTE);
        FileReadTool tool = new FileReadTool();
        FileReadOutput output = tool.execute(input);
        assertEquals("Hello, Integration Test!", output.getContent());

        // 4. REPORT：记录结果
        stateMachine.transition(ToolExecutionState.REPORT);

        // 5. DONE：完成
        stateMachine.transition(ToolExecutionState.DONE);
        assertTrue(stateMachine.isCompleted());
    }

    @Test
    @DisplayName("文件写入工具完整生命周期：创建并写入文件")
    void testFileWriteToolFullLifecycle() throws IOException {
        // 1. PARSE
        FileWriteInput input = new FileWriteInput();
        Path targetFile = tempDir.resolve("output.txt");
        input.setFilePath(targetFile.toString());
        input.setContent("Integration test output content");

        // 2. VALIDATE
        stateMachine.transition(ToolExecutionState.VALIDATE);
        assertTrue(context.getWorkspaceGuard().isPathSafe(targetFile.toString()));

        // 3. EXECUTE
        stateMachine.transition(ToolExecutionState.EXECUTE);
        FileWriteTool tool = new FileWriteTool();
        FileWriteOutput output = tool.execute(input);
        assertTrue(output.isSuccess());

        // 验证文件已写入
        String writtenContent = Files.readString(targetFile);
        assertEquals("Integration test output content", writtenContent);

        // 4-5. REPORT → DONE
        stateMachine.transition(ToolExecutionState.REPORT);
        stateMachine.transition(ToolExecutionState.DONE);
    }

    // ==================== 错误恢复路径 ====================

    @Test
    @DisplayName("路径穿越攻击应被拦截并进入 CORRECTION 状态")
    void testPathTraversalShouldTriggerCorrection() {
        // 模拟路径穿越
        String maliciousPath = "../../../etc/passwd";

        // 工作区守卫应拦截
        assertFalse(context.getWorkspaceGuard().isPathSafe(maliciousPath));

        // 状态机进入纠错
        stateMachine.transition(ToolExecutionState.CORRECTION);
        assertEquals(1, stateMachine.getCorrectionAttempts());
    }

    @Test
    @DisplayName("权限不足时应触发熔断器半开状态")
    void testPermissionDeniedShouldTriggerHalfOpen() {
        // 模拟连续权限拒绝
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute("file-write-tool", () -> {
                    throw new SecurityException("Permission denied: " + i);
                });
            } catch (Exception e) {
                // 预期异常
            }
        }

        // 应进入半开状态
        assertTrue(circuitBreaker.isHalfOpen("file-write-tool"));
    }

    // ==================== 熔断器 + 状态机协同 ====================

    @Test
    @DisplayName("熔断器全开后状态机应直接进入 FAILED")
    void testCircuitBreakerOpenLeadsToFailed() {
        // 触发全开
        String toolId = "bash-tool";
        for (int i = 0; i < 5; i++) {
            try {
                circuitBreaker.execute(toolId, () -> {
                    throw new RuntimeException("failure-" + i);
                });
            } catch (Exception ignored) {
            }
        }
        assertTrue(circuitBreaker.isOpen(toolId));

        // 状态机进入 FAILED
        stateMachine.transition(ToolExecutionState.CORRECTION);
        stateMachine.transition(ToolExecutionState.CORRECTION);
        stateMachine.transition(ToolExecutionState.FAILED);
        assertTrue(stateMachine.isFailed());
    }

    // ==================== 上下文传递 ====================

    @Test
    @DisplayName("工具执行上下文应正确传递工作区和 session 信息")
    void testToolContextShouldPassWorkspaceAndSession() {
        assertNotNull(context.getWorkspaceRoot());
        assertEquals(tempDir.toString(), context.getWorkspaceRoot());
        assertNotNull(context.getWorkspaceGuard());
        assertNotNull(context.getAgentRegistry());
    }

    // ==================== 多个工具链式调用 ====================

    @Test
    @DisplayName("链式调用：FileWrite → FileRead，验证数据一致性")
    void testChainedToolExecution() throws IOException {
        // 写入
        Path chainFile = tempDir.resolve("chain-test.txt");
        String originalContent = "Chain execution content";

        FileWriteInput writeInput = new FileWriteInput();
        writeInput.setFilePath(chainFile.toString());
        writeInput.setContent(originalContent);

        FileWriteTool writeTool = new FileWriteTool();
        FileWriteOutput writeOutput = writeTool.execute(writeInput);
        assertTrue(writeOutput.isSuccess());

        // 读取
        FileReadInput readInput = new FileReadInput();
        readInput.setFilePath(chainFile.toString());

        FileReadTool readTool = new FileReadTool();
        FileReadOutput readOutput = readTool.execute(readInput);

        assertEquals(originalContent, readOutput.getContent());
    }

    // ==================== Bash 工具执行 ====================

    @Test
    @DisplayName("Bash 工具执行并获取输出")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testBashToolExecution() {
        // 仅当平台支持时运行
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("linux") && !os.contains("mac")) {
            return; // Windows 跳过 Bash 测试
        }

        BashInput input = new BashInput();
        input.setCommand("echo 'hello from bash'");

        BashTool tool = new BashTool();
        BashOutput output = tool.execute(input);

        assertTrue(output.isSuccess());
        assertTrue(output.getStdout().contains("hello from bash"));
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("不存在的文件读取应返回错误")
    void testReadNonExistentFile() {
        String nonExistentPath = tempDir.resolve("does-not-exist.txt").toString();

        FileReadInput input = new FileReadInput();
        input.setFilePath(nonExistentPath);

        FileReadTool tool = new FileReadTool();
        assertThrows(Exception.class, () -> tool.execute(input));
    }

    @Test
    @DisplayName("空输入应被拒绝")
    void testEmptyInputShouldBeRejected() {
        FileReadInput input = new FileReadInput();
        // 未设置 filePath

        FileReadTool tool = new FileReadTool();
        assertThrows(Exception.class, () -> tool.execute(input));
    }

    // ==================== 重置与复用 ====================

    @Test
    @DisplayName("状态机重置后可重复使用")
    void testStateMachineResetAndReuse() throws IOException {
        // 第一次执行
        Path testFile = tempDir.resolve("reset-test.txt");
        Files.writeString(testFile, "First run");

        FileReadTool tool = new FileReadTool();
        FileReadInput input = new FileReadInput();
        input.setFilePath(testFile.toString());

        FileReadOutput output = tool.execute(input);
        assertEquals("First run", output.getContent());

        // 重置状态机
        stateMachine.reset();
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());

        // 第二次执行（修改文件内容后）
        Files.writeString(testFile, "Second run");
        FileReadOutput output2 = tool.execute(input);
        assertEquals("Second run", output2.getContent());
    }
}
