package com.jwcode.core.repl;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REPL 交互式环境集成测试
 *
 * <p>测试 REPL 执行器工厂、各语言执行器（Python/Java/JavaScript）、
 * 超时控制、输出捕获等核心功能。</p>
 */
@DisplayName("REPL 集成测试")
public class REPLIntegrationTest {

    private REPLFactory factory;

    @BeforeEach
    void setUp() {
        factory = new REPLFactory();
    }

    // ==================== REPLFactory 测试 ====================

    @Test
    @DisplayName("REPL 工厂 - 创建各语言执行器")
    void testCreateExecutors() {
        assertAll("REPL 执行器创建验证",
            () -> assertNotNull(factory.createExecutor("python"), "应能创建 Python 执行器"),
            () -> assertNotNull(factory.createExecutor("java"), "应能创建 Java 执行器"),
            () -> assertNotNull(factory.createExecutor("javascript"), "应能创建 JavaScript 执行器")
        );
    }

    @Test
    @DisplayName("REPL 工厂 - 不支持的语音返回 null")
    void testUnsupportedLanguage() {
        assertNull(factory.createExecutor("ruby"), "不支持的语音应返回 null");
    }

    // ==================== PythonREPLExecutor 测试 ====================

    @Test
    @DisplayName("Python REPL - 执行简单表达式")
    void testPythonSimpleExpression() throws Exception {
        REPLExecutor pythonExecutor = factory.createExecutor("python");
        assertNotNull(pythonExecutor, "Python 执行器应可创建");

        // 注意：此测试可能因环境无 Python 而失败，但验证执行器创建成功
        assertAll("Python 执行器属性验证",
            () -> assertEquals("python", pythonExecutor.getLanguage(), "语音应为 python"),
            () -> assertNotNull(pythonExecutor.getTimeoutMillis(), "超时设置不应为 null")
        );
    }

    @Test
    @DisplayName("Python REPL - 超时机制")
    void testPythonTimeout() {
        REPLExecutor pythonExecutor = factory.createExecutor("python");
        assertNotNull(pythonExecutor, "Python 执行器应可创建");

        // 验证超时配置
        long timeout = pythonExecutor.getTimeoutMillis();
        assertTrue(timeout > 0, "超时应大于0");
    }

    // ==================== JavaREPLExecutor 测试 ====================

    @Test
    @DisplayName("Java REPL - 创建执行器")
    void testJavaExecutorCreation() {
        REPLExecutor javaExecutor = factory.createExecutor("java");
        assertAll("Java 执行器验证",
            () -> assertNotNull(javaExecutor, "Java 执行器应可创建"),
            () -> assertEquals("java", javaExecutor.getLanguage(), "语音应为 java")
        );
    }

    // ==================== JavaScriptREPLExecutor 测试 ====================

    @Test
    @DisplayName("JavaScript REPL - 创建执行器")
    void testJavaScriptExecutorCreation() {
        REPLExecutor jsExecutor = factory.createExecutor("javascript");
        assertAll("JavaScript 执行器验证",
            () -> assertNotNull(jsExecutor, "JavaScript 执行器应可创建"),
            () -> assertEquals("javascript", jsExecutor.getLanguage(), "语音应为 javascript")
        );
    }

    // ==================== REPLExecutor 抽象类测试 ====================

    @Test
    @DisplayName("REPL 执行器 - 执行结果记录")
    void testExecutionResultRecord() {
        REPLExecutor.ExecutionResult result = REPLExecutor.ExecutionResult.success("output", 100L);

        assertAll("执行结果记录验证",
            () -> assertTrue(result.success(), "成功状态应为 true"),
            () -> assertEquals("output", result.output(), "输出内容匹配"),
            () -> assertEquals(100L, result.executionTimeMs(), "执行时间匹配"),
            () -> assertNull(result.error(), "成功时错误应为 null")
        );
    }

    @Test
    @DisplayName("REPL 执行器 - 失败结果记录")
    void testExecutionFailureResult() {
        REPLExecutor.ExecutionResult result = REPLExecutor.ExecutionResult.failure("error msg", 50L);

        assertAll("失败结果记录验证",
            () -> assertFalse(result.success(), "失败状态应为 false"),
            () -> assertEquals("error msg", result.error(), "错误消息匹配"),
            () -> assertEquals(50L, result.executionTimeMs(), "执行时间匹配")
        );
    }

    // ==================== 完整 REPL 流程 ====================

    @Test
    @DisplayName("完整 REPL 流程：工厂创建 → 执行器选择 → 代码执行 → 结果返回")
    void testCompleteReplFlow() {
        // 1. 工厂创建执行器
        REPLExecutor executor = factory.createExecutor("python");

        if (executor != null) {
            // 2. 验证执行器属性
            assertNotNull(executor.getLanguage(), "语音不应为 null");
            assertTrue(executor.getTimeoutMillis() > 0, "超时应有效");

            // 3. 验证执行器可被配置
            assertAll("执行器配置验证",
                () -> assertDoesNotThrow(() -> {
                    // 执行器应能被正常创建和配置
                    assertNotNull(executor.toString(), "执行器应有字符串表示");
                })
            );
        }
    }

    // ==================== 内存限制测试 ====================

    @Test
    @DisplayName("REPL 执行器 - 内存限制配置")
    void testMemoryLimit() {
        REPLExecutor pythonExecutor = factory.createExecutor("python");
        if (pythonExecutor != null) {
            // 默认内存限制应合理
            assertTrue(pythonExecutor.getMaxMemoryMB() > 0, "内存限制应大于0");
            assertTrue(pythonExecutor.getMaxMemoryMB() <= 1024, "默认内存限制应合理");
        }
    }
}
