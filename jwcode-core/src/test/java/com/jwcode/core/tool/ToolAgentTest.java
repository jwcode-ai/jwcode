package com.jwcode.core.tool;

import com.jwcode.core.tool.ErrorSummary;
import com.jwcode.core.tool.retry.RetryOrchestrator;
import com.jwcode.core.tool.retry.RetryPolicy;
import com.jwcode.core.tool.retry.RetryStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolAgent 泛型重构后的单元测试
 */
@DisplayName("ToolAgent 单元测试")
class ToolAgentTest {

    @Test
    @DisplayName("execute 应返回成功结果")
    void executeSuccess() {
        ToolAgent<String> agent = new ToolAgent<>();
        ToolAgentResult result = agent.execute("TestTool", () -> "hello world");

        assertTrue(result.isSuccess());
        assertEquals("TestTool", result.getToolName());
        assertEquals("hello world", result.getResult());
        assertTrue(result.getExecutionTimeMs() >= 0);
    }

    @Test
    @DisplayName("execute 应返回失败结果（操作抛出异常）")
    void executeFailure() {
        ToolAgent<String> agent = ToolAgent.fastFail();
        ToolAgentResult result = agent.execute("FailingTool", () -> {
            throw new RuntimeException("Something went wrong");
        });

        assertFalse(result.isSuccess());
        assertEquals("FailingTool", result.getToolName());
        assertNotNull(result.getErrorSummary());
        // fastFail: maxRetries=0, so selfHealAttempts should be 0 or 1 (initial attempt)
        assertTrue(result.getSelfHealAttempts() <= 1, "fastFail should have at most 1 attempt");
    }

    @Test
    @DisplayName("execute 应重试并在耗尽后返回失败")
    void executeRetryExhausted() {
        RetryPolicy retryOnce = RetryPolicy.builder()
                .maxRetries(2)
                .initialBackoffMs(10)
                .backoffMultiplier(1.0)
                .maxBackoffMs(100)
                .retryableErrorTypes(List.of("TIMEOUT"))
                .nonRetryableErrorTypes(List.of())
                .build();

        ToolAgent<String> agent = new ToolAgent<>(new RetryOrchestrator(), RetryStrategy.exponentialBackoff());
        AtomicInteger counter = new AtomicInteger(0);

        ToolAgentResult result = agent.execute("RetryTool", () -> {
            counter.incrementAndGet();
            throw new RuntimeException("TIMEOUT: connection timed out");
        });

        assertFalse(result.isSuccess());
        // RetryPolicy maxRetries=2 means 2 retries = 3 total attempts (initial + 2 retries)
        assertTrue(result.getSelfHealAttempts() >= 2, "Should have attempted at least 2 times");
    }

    @Test
    @DisplayName("execute 应在重试成功后返回成功")
    void executeRetrySuccess() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(3)
                .initialBackoffMs(10)
                .backoffMultiplier(1.0)
                .maxBackoffMs(100)
                .retryableErrorTypes(List.of("TIMEOUT"))
                .nonRetryableErrorTypes(List.of())
                .build();

        ToolAgent<String> agent = new ToolAgent<>(new RetryOrchestrator(), RetryStrategy.exponentialBackoff());
        AtomicInteger counter = new AtomicInteger(0);

        ToolAgentResult result = agent.execute("RetryOkTool", () -> {
            int attempt = counter.incrementAndGet();
            if (attempt < 2) {
                throw new RuntimeException("TIMEOUT: transient error");
            }
            return "success on attempt " + attempt;
        });

        assertTrue(result.isSuccess());
        assertEquals("success on attempt 2", result.getResult());
    }

    @Test
    @DisplayName("executeAsync 应返回成功结果")
    void executeAsyncSuccess() throws Exception {
        ToolAgent<String> agent = new ToolAgent<>();
        CompletableFuture<ToolAgentResult> future = agent.executeAsync("AsyncTool",
                () -> CompletableFuture.completedFuture("async result"));

        ToolAgentResult result = future.get();
        assertTrue(result.isSuccess());
        assertEquals("async result", result.getResult());
    }

    @Test
    @DisplayName("executeAsync 应返回失败结果")
    void executeAsyncFailure() throws Exception {
        ToolAgent<String> agent = ToolAgent.fastFail();
        CompletableFuture<ToolAgentResult> future = agent.executeAsync("FailingAsyncTool",
                () -> CompletableFuture.failedFuture(new RuntimeException("Async error")));

        ToolAgentResult result = future.get();
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorSummary());
    }

    @Test
    @DisplayName("withCustomRetry 应创建带自定义策略的 ToolAgent")
    void withCustomRetry() {
        RetryPolicy customPolicy = RetryPolicy.builder()
                .maxRetries(5)
                .initialBackoffMs(100)
                .backoffMultiplier(2.0)
                .maxBackoffMs(5000)
                .retryableErrorTypes(List.of("TIMEOUT", "RATE_LIMIT"))
                .nonRetryableErrorTypes(List.of("INVALID_INPUT"))
                .build();

        ToolAgent<String> agent = ToolAgent.withCustomRetry(customPolicy, RetryStrategy.exponentialBackoff());
        assertNotNull(agent);

        ToolAgentResult result = agent.execute("CustomRetryTool", () -> "ok");
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("fastFail 应创建不重试的 ToolAgent")
    void fastFail() {
        ToolAgent<String> agent = ToolAgent.fastFail();
        assertNotNull(agent);

        AtomicInteger counter = new AtomicInteger(0);
        ToolAgentResult result = agent.execute("FastFailTool", () -> {
            counter.incrementAndGet();
            throw new RuntimeException("error");
        });

        assertFalse(result.isSuccess());
        assertEquals(1, counter.get(), "Should only attempt once with fastFail");
    }

    @Test
    @DisplayName("execute 应支持整数类型操作")
    void executeWithIntegerType() {
        ToolAgent<Integer> agent = new ToolAgent<>();
        ToolAgentResult result = agent.execute("IntTool", () -> 42);

        assertTrue(result.isSuccess());
        assertEquals(42, result.getResult());
        assertTrue(result.getResult() instanceof Integer);
    }

    @Test
    @DisplayName("execute 应支持列表类型操作")
    void executeWithListType() {
        ToolAgent<List<String>> agent = new ToolAgent<>();
        ToolAgentResult result = agent.execute("ListTool", () -> List.of("a", "b", "c"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) result.getResult();
        assertEquals(3, list.size());
    }

    // ==================== 工作区安全校验测试 ====================

    @TempDir
    Path tempDir;

    private Path createWorkspace() throws IOException {
        Path ws = tempDir.resolve("project");
        Files.createDirectories(ws);
        Files.createFile(ws.resolve("pom.xml"));
        Files.createDirectories(ws.resolve("src"));
        return ws;
    }

    @Test
    @DisplayName("withWorkspace 应创建带工作区守卫的 ToolAgent")
    void withWorkspace_createsGuardedAgent() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = ToolAgent.withWorkspace(ws);

        assertNotNull(agent);
        assertTrue(agent.hasWorkspaceGuard());
        assertEquals(ws.normalize().toAbsolutePath(), agent.getWorkspaceRoot());
    }

    @Test
    @DisplayName("executeWithPathCheck 应允许工作区内的路径操作")
    void executeWithPathCheck_validPath() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = ToolAgent.withWorkspace(ws);

        Path validPath = ws.resolve("pom.xml");
        ToolAgentResult result = agent.executeWithPathCheck("FileReadTool", validPath,
            () -> "file content");

        assertTrue(result.isSuccess());
        assertEquals("file content", result.getResult());
    }

    @Test
    @DisplayName("executeWithPathCheck 应拦截工作区外的路径操作")
    void executeWithPathCheck_outsidePath() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = ToolAgent.withWorkspace(ws);

        Path outsidePath = ws.resolve("../secret.txt").normalize();
        AtomicInteger operationCalled = new AtomicInteger(0);

        ToolAgentResult result = agent.executeWithPathCheck("FileReadTool", outsidePath,
            () -> {
                operationCalled.incrementAndGet();
                return "should not reach here";
            });

        assertFalse(result.isSuccess(), "工作区外的操作应失败");
        assertEquals(0, operationCalled.get(), "操作不应被执行");
        assertNotNull(result.getErrorSummary());
        assertEquals("WORKSPACE_ACCESS_DENIED", result.getErrorSummary().getErrorType());
    }

    @Test
    @DisplayName("executeWithPathCheck(rawPath) 应解析相对路径并校验")
    void executeWithPathCheck_relativePath() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = ToolAgent.withWorkspace(ws);

        // 相对路径 "pom.xml" 基于 ws 解析
        ToolAgentResult result = agent.executeWithPathCheck("FileReadTool",
            "pom.xml", ws, () -> "content");

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("executeWithPathCheck(rawPath) 应拦截相对路径穿越")
    void executeWithPathCheck_relativeTraversal() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = ToolAgent.withWorkspace(ws);

        AtomicInteger operationCalled = new AtomicInteger(0);
        ToolAgentResult result = agent.executeWithPathCheck("FileReadTool",
            "../secret.txt", ws, () -> {
                operationCalled.incrementAndGet();
                return "should not reach";
            });

        assertFalse(result.isSuccess());
        assertEquals(0, operationCalled.get());
        assertEquals("WORKSPACE_ACCESS_DENIED", result.getErrorSummary().getErrorType());
    }

    @Test
    @DisplayName("没有工作区守卫时，executeWithPathCheck 应放行所有路径")
    void executeWithPathCheck_noGuard() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = new ToolAgent<>(); // 无守卫

        Path outsidePath = ws.resolve("../secret.txt").normalize();
        ToolAgentResult result = agent.executeWithPathCheck("FileReadTool", outsidePath,
            () -> "pass through");

        // 无守卫时不拦截，由工具自身处理
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("validatePath 应在路径越界时返回错误摘要")
    void validatePath_returnsErrorSummary() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = ToolAgent.withWorkspace(ws);

        Optional<ErrorSummary> error = agent.validatePath(
            ws.resolve("../../../etc/passwd").normalize(), "TestTool");
        assertTrue(error.isPresent());
        assertEquals("WORKSPACE_ACCESS_DENIED", error.get().getErrorType());
        assertFalse(error.get().isRetryable());
    }

    @Test
    @DisplayName("validatePath 应在路径合法时返回空")
    void validatePath_returnsEmpty() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = ToolAgent.withWorkspace(ws);

        Optional<ErrorSummary> error = agent.validatePath(
            ws.resolve("src"), "TestTool");
        assertTrue(error.isEmpty());
    }

    @Test
    @DisplayName("fastFailWithWorkspace 应创建快速失败且带守卫的 ToolAgent")
    void fastFailWithWorkspace() throws IOException {
        Path ws = createWorkspace();
        ToolAgent<String> agent = ToolAgent.fastFailWithWorkspace(ws);

        assertTrue(agent.hasWorkspaceGuard());
        assertEquals(ws.normalize().toAbsolutePath(), agent.getWorkspaceRoot());

        // 快速失败行为
        AtomicInteger counter = new AtomicInteger(0);
        ToolAgentResult result = agent.execute("FastTool", () -> {
            counter.incrementAndGet();
            throw new RuntimeException("fail");
        });

        assertFalse(result.isSuccess());
        assertEquals(1, counter.get(), "fastFail 应只尝试1次");
    }
}
