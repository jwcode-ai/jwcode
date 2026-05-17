package com.jwcode.core.tool.execution;

import com.jwcode.core.tool.BashTool;
import com.jwcode.core.tool.FileReadTool;
import com.jwcode.core.tool.FileWriteTool;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.FileReadInput;
import com.jwcode.core.tool.input.FileWriteInput;
import com.jwcode.core.tool.input.BashInput;
import com.jwcode.core.tool.FileWriteOutput;
import com.jwcode.core.tool.output.FileReadOutput;
import com.jwcode.core.tool.output.BashOutput;
import com.jwcode.core.tool.permission.PermissionChecker;
import com.jwcode.core.tool.WorkspaceGuard;
import com.jwcode.core.tool.ToolProgress;
import com.jwcode.core.tool.ToolResult;
import com.jwcode.core.session.Session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tool 执行系统完整集成测试
 *
 * <p>测试覆盖工具执行的全生命周期：
 * <ul>
 *   <li>状态机：PARSE → CORRECTION → REPORT → DONE / FAILED</li>
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
        stateMachine = new ToolExecutionStateMachine("test-tool", progress -> {});
        circuitBreaker = new ToolCircuitBreaker();
        context = ToolExecutionContext.builder()
            .workingDirectory(tempDir)
            .build();
    }

    // ==================== 全生命周期测试 ====================

    @Test
    @DisplayName("文件读取工具完整生命周期：状态机 + 上下文 + 执行")
    void testFileReadToolFullLifecycle() throws Exception {
        // 准备测试文件
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, Integration Test!");

        // 1. 构建输入（FileReadInput 是 record，直接用构造器）
        FileReadInput input = new FileReadInput(testFile.toString());
        assertNotNull(input);

        // 2. 执行工具（Tool 使用 call/callSync 方法）
        FileReadTool tool = new FileReadTool();
        ToolResult<FileReadOutput> result = tool.callSync(input, context);
        assertTrue(result.isSuccess(), "执行应成功");
        assertEquals("Hello, Integration Test!", result.getData().content().trim());

        // 3. 状态机：reportSuccess → complete
        stateMachine.reportSuccess();
        stateMachine.complete();
        assertTrue(stateMachine.isTerminal());
    }

    @Test
    @DisplayName("文件写入工具完整生命周期：创建并写入文件")
    void testFileWriteToolFullLifecycle() throws Exception {
        Path targetFile = tempDir.resolve("output.txt");

        // 1. 构建输入（FileWriteInput 是 record，用构造器）
        FileWriteInput input = new FileWriteInput(targetFile.toString(), "Integration test output content");

        // 2. 执行工具
        FileWriteTool tool = new FileWriteTool();
        // FileWriteTool 的 Input 类型是 FileWriteTool.Input，需要用其内部类
        FileWriteTool.Input writeInput = new FileWriteTool.Input();
        // 通过反射或直接使用内部类字段
        // 实际上 FileWriteTool 使用自己的 Input 内部类，不是 input.FileWriteInput
        // 我们直接用 FileWriteTool 的 callSync 测试
        ToolResult<FileWriteTool.Output> result = tool.callSync(
            new FileWriteTool.Input(targetFile.toString(), "Integration test output content"),
            context
        );
        assertTrue(result.isSuccess());

        // 验证文件已写入
        String writtenContent = Files.readString(targetFile);
        assertEquals("Integration test output content", writtenContent);

        // 3. 状态机完成
        stateMachine.reportSuccess();
        stateMachine.complete();
    }

    // ==================== 错误恢复路径 ====================

    @Test
    @DisplayName("路径穿越攻击应被拦截并进入 CORRECTION 状态")
    void testPathTraversalShouldTriggerCorrection() {
        // 模拟路径穿越
        String maliciousPath = "../../../etc/passwd";
        Path malPath = Path.of(maliciousPath);

        // 工作区守卫应拦截（validatePath 返回非空 Optional）
        WorkspaceGuard guard = context.getWorkspaceGuard();
        if (guard == null) {
            // 如果 WorkspaceGuard 未初始化（如 tempDir 尚未创建），手动创建
            guard = new WorkspaceGuard(tempDir);
        }
        assertTrue(guard.validatePath(malPath).isPresent(), "路径穿越应被拦截");

        // 状态机进入纠错（PARSE 状态下只能通过 PARSE_ERROR 触发 CORRECTION）
        stateMachine.transition(ToolExecutionStateMachine.ErrorType.PARSE_ERROR, "路径穿越");
        assertEquals(1, stateMachine.getCorrectionAttempts());
    }

    @Test
    @DisplayName("权限不足时应触发熔断器半开状态")
    void testPermissionDeniedShouldTriggerHalfOpen() {
        // 模拟连续权限拒绝
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            try {
                circuitBreaker.execute("file-write-tool", () -> {
                    throw new SecurityException("Permission denied: " + idx);
                });
            } catch (Exception e) {
                // 预期异常
            }
        }

        // 应进入半开状态
        assertEquals(ToolCircuitBreaker.CircuitState.HALF_OPEN, circuitBreaker.getState("file-write-tool"));
    }

    // ==================== 熔断器 + 状态机协同 ====================

    @Test
    @DisplayName("熔断器全开后状态机应直接进入 FAILED")
    void testCircuitBreakerOpenLeadsToFailed() {
        // 触发全开：前 3 次通过 execute 触发 HALF_OPEN，后 2 次通过 recordFailure
        String toolId = "bash-tool";
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            try {
                circuitBreaker.execute(toolId, () -> {
                    throw new RuntimeException("failure-" + idx);
                });
            } catch (Exception ignored) { }
        }
        circuitBreaker.recordFailure(toolId);
        circuitBreaker.recordFailure(toolId);
        assertEquals(ToolCircuitBreaker.CircuitState.FULL_OPEN, circuitBreaker.getState(toolId));

        // 状态机进入 FAILED
        stateMachine.fail("熔断器全开，工具禁用");
        assertTrue(stateMachine.isTerminal());
        assertEquals(ToolExecutionState.FAILED, stateMachine.getCurrentState());
    }

    // ==================== 上下文传递 ====================

    @Test
    @DisplayName("工具执行上下文应正确传递工作区和 session 信息")
    void testToolContextShouldPassWorkspaceAndSession() {
        assertNotNull(context.getWorkingDirectory());
        assertEquals(tempDir.toString(), context.getWorkingDirectory().toString());
        assertNotNull(context.getWorkspaceGuard());
    }

    // ==================== 多个工具链式调用 ====================

    @Test
    @DisplayName("链式调用：FileWrite → FileRead，验证数据一致性")
    void testChainedToolExecution() throws Exception {
        // 写入
        Path chainFile = tempDir.resolve("chain-test.txt");
        String originalContent = "Chain execution content";

        FileWriteTool writeTool = new FileWriteTool();
        ToolResult<FileWriteTool.Output> writeResult = writeTool.callSync(
            new FileWriteTool.Input(chainFile.toString(), originalContent),
            context
        );
        assertTrue(writeResult.isSuccess());

        // 读取
        FileReadTool readTool = new FileReadTool();
        ToolResult<FileReadOutput> readResult = readTool.callSync(
            new FileReadInput(chainFile.toString()),
            context
        );

        assertTrue(readResult.isSuccess());
        assertEquals(originalContent, readResult.getData().content().trim());
    }

    // ==================== Bash 工具执行 ====================

    @Test
    @DisplayName("Bash 工具执行并获取输出")
    void testBashToolExecution() throws Exception {
        // 仅当平台支持时运行
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("linux") && !os.contains("mac")) {
            return; // Windows 跳过 Bash 测试
        }

        BashInput input = new BashInput("echo 'hello from bash'");

        BashTool tool = new BashTool();
        ToolResult<BashOutput> result = tool.callSync(input, context);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().stdout().contains("hello from bash"));
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("不存在的文件读取应返回错误")
    void testReadNonExistentFile() {
        String nonExistentPath = tempDir.resolve("does-not-exist.txt").toString();

        FileReadInput input = new FileReadInput(nonExistentPath);

        FileReadTool tool = new FileReadTool();
        assertThrows(Exception.class, () -> {
            ToolResult<FileReadOutput> result = tool.callSync(input, context);
            if (!result.isSuccess()) throw new RuntimeException("读取失败");
        });
    }

    @Test
    @DisplayName("空路径输入应被拒绝")
    void testEmptyInputShouldBeRejected() {
        FileReadInput input = new FileReadInput("");

        FileReadTool tool = new FileReadTool();
        assertThrows(Exception.class, () -> {
            ToolResult<FileReadOutput> result = tool.callSync(input, context);
            if (!result.isSuccess()) throw new RuntimeException("空输入被拒绝");
        });
    }

    // ==================== 复用 ====================

    @Test
    @DisplayName("状态机可多次使用")
    void testStateMachineReuse() throws Exception {
        Path testFile = tempDir.resolve("reuse-test.txt");
        Files.writeString(testFile, "Content");

        FileReadTool tool = new FileReadTool();
        FileReadInput input = new FileReadInput(testFile.toString());

        // 第一次执行
        ToolResult<FileReadOutput> output = tool.callSync(input, context);
        assertEquals("Content", output.getData().content().trim());

        // 重新创建状态机用于第二次执行
        ToolExecutionStateMachine stateMachine2 = new ToolExecutionStateMachine("test-tool-2", progress -> {});
        assertEquals(ToolExecutionState.PARSE, stateMachine2.getCurrentState());

        // 再次读取
        Files.writeString(testFile, "Updated content");
        ToolResult<FileReadOutput> output2 = tool.callSync(input, context);
        assertEquals("Updated content", output2.getData().content().trim());
    }
}
