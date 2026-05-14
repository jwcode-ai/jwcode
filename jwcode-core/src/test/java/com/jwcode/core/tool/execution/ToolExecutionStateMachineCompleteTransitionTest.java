package com.jwcode.core.tool.execution;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ToolExecutionStateMachine 状态机完整性集成测试。
 * 
 * <p>覆盖以下状态流转路径：</p>
 * <ul>
 *   <li>✅ 正向路径：PARSE → VALIDATE → EXECUTE → REPORT → DONE</li>
 *   <li>🔄 纠错路径：任意阶段 → CORRECTION（最多3次）→ 恢复或 FAILED</li>
 *   <li>🚫 边界条件：初始状态、纠错封顶、重复纠错、异常消费</li>
 * </ul>
 * 
 * <h3>测试策略</h3>
 * <ul>
 *   <li>{@link ErrorType} 使用 Mock 枚举模拟（INVALID_JSON / FIELD_MISSING / TIMEOUT / NON_ZERO_EXIT）</li>
 *   <li>{@link ToolProgress} 使用 mock Consumer 回调验证 REPORT→DONE 结果写入</li>
 *   <li>状态转移后的 {@code lastErrorMessage} 内容断言</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolExecutionStateMachineCompleteTransitionTest {

    private ToolExecutionStateMachine stateMachine;
    private Consumer<ToolProgress> progressConsumer;
    private ArgumentCaptor<ToolProgress> progressCaptor;

    @BeforeEach
    void setUp() {
        progressConsumer = mock(Consumer.class);
        progressCaptor = ArgumentCaptor.forClass(ToolProgress.class);
        stateMachine = new ToolExecutionStateMachine(progressConsumer);
    }

    // ========================================================================
    // 1. 初始状态验证
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("初始状态应为 PARSE")
    void testInitialStateIsParse() {
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState(),
                "状态机初始化后应处于 PARSE 状态");
        assertEquals(0, stateMachine.getCorrectionAttempts(),
                "初始纠错次数应为 0");
        assertTrue(stateMachine.canCorrect(),
                "初始状态下应允许纠错");
    }

    // ========================================================================
    // 2. 正向流转路径（Happy Path）
    // ========================================================================

    @Test
    @Order(2)
    @DisplayName("PARSE → VALIDATE: 正确JSON应成功进入校验阶段")
    void testParseSuccessToValidate() {
        stateMachine.transition(ErrorType.INVALID_JSON, "{\"tool\":\"bash\"}");

        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState(),
                "JSON解析成功应从 PARSE 进入 VALIDATE");
    }

    @Test
    @Order(3)
    @DisplayName("VALIDATE → EXECUTE: 字段齐全应成功进入执行阶段")
    void testValidateSuccessToExecute() {
        // 先进入 VALIDATE 状态
        stateMachine.transition(ErrorType.INVALID_JSON, "{\"tool\":\"bash\",\"args\":[\"ls\"]}");
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());

        // VALIDATE 成功 → EXECUTE
        stateMachine.transition(ErrorType.NON_ZERO_EXIT, "字段校验通过");
        assertEquals(ToolExecutionState.EXECUTE, stateMachine.getCurrentState(),
                "字段校验成功应从 VALIDATE 进入 EXECUTE");
    }

    @Test
    @Order(4)
    @DisplayName("EXECUTE → REPORT: 执行完成应生成报告")
    void testExecuteSuccessToReport() {
        navigateToExecute();

        stateMachine.transition(ErrorType.INVALID_JSON, "执行完成，返回码 0");
        assertEquals(ToolExecutionState.REPORT, stateMachine.getCurrentState(),
                "执行完成应从 EXECUTE 进入 REPORT");
    }

    @Test
    @Order(5)
    @DisplayName("REPORT → DONE: 报告生成完毕应进入完成状态")
    void testReportSuccessToDone() {
        navigateToReport();

        stateMachine.transition(ErrorType.INVALID_JSON, "报告已生成");
        assertEquals(ToolExecutionState.DONE, stateMachine.getCurrentState(),
                "报告生成完毕应从 REPORT 进入 DONE");

        // 验证 progressConsumer 被调用
        verify(progressConsumer, times(1)).accept(progressCaptor.capture());
        ToolProgress capturedProgress = progressCaptor.getValue();
        assertNotNull(capturedProgress, "进度回调不应为空");
    }

    @Test
    @Order(6)
    @DisplayName("完整正向路径：PARSE→VALIDATE→EXECUTE→REPORT→DONE")
    void testFullHappyPathFlow() {
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());

        // Step 1: PARSE → VALIDATE
        stateMachine.transition(ErrorType.INVALID_JSON, "{\"tool\":\"ls\"}");
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());
        assertEquals(0, stateMachine.getCorrectionAttempts());

        // Step 2: VALIDATE → EXECUTE
        stateMachine.transition(ErrorType.NON_ZERO_EXIT, "字段校验通过");
        assertEquals(ToolExecutionState.EXECUTE, stateMachine.getCurrentState());

        // Step 3: EXECUTE → REPORT
        stateMachine.transition(ErrorType.INVALID_JSON, "执行成功，返回码 0");
        assertEquals(ToolExecutionState.REPORT, stateMachine.getCurrentState());

        // Step 4: REPORT → DONE
        stateMachine.transition(ErrorType.INVALID_JSON, "报告生成完成");
        assertEquals(ToolExecutionState.DONE, stateMachine.getCurrentState());

        // 验证整个链路回调只触发一次（在 REPORT→DONE 时）
        verify(progressConsumer, times(1)).accept(any());
    }

    // ========================================================================
    // 3. 错误纠正路径（Correction Flow）
    // ========================================================================

    @Test
    @Order(7)
    @DisplayName("PARSE → CORRECTION: JSON非法应进入纠正阶段")
    void testParseFailureToCorrection() {
        stateMachine.transition(ErrorType.FIELD_MISSING, "JSON 解析失败：格式错误");

        assertEquals(ToolExecutionState.CORRECTION, stateMachine.getCurrentState(),
                "JSON解析失败应从 PARSE 进入 CORRECTION");
        assertNotNull(stateMachine.getLastErrorMessage(),
                "应记录错误消息");
        assertTrue(stateMachine.getLastErrorMessage().contains("JSON 解析失败"),
                "错误消息应包含解析失败信息");
    }

    @Test
    @Order(8)
    @DisplayName("VALIDATE → CORRECTION: 字段缺失应进入纠正阶段")
    void testValidateFieldMissingToCorrection() {
        // 先进入 VALIDATE
        stateMachine.transition(ErrorType.INVALID_JSON, "{\"tool\":\"test\"}");
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());

        // 字段缺失 → CORRECTION
        stateMachine.transition(ErrorType.FIELD_MISSING, "缺少必填字段 args");

        assertEquals(ToolExecutionState.CORRECTION, stateMachine.getCurrentState(),
                "字段校验失败应从 VALIDATE 进入 CORRECTION");
        assertTrue(stateMachine.getLastErrorMessage().contains("缺少必填字段"),
                "错误消息应包含字段缺失信息");
    }

    @Test
    @Order(9)
    @DisplayName("EXECUTE → CORRECTION: 执行超时应进入纠正阶段")
    void testExecuteTimeoutToCorrection() {
        navigateToExecute();

        stateMachine.transition(ErrorType.TIMEOUT, "执行超时：超过 30s 限制");

        assertEquals(ToolExecutionState.CORRECTION, stateMachine.getCurrentState(),
                "执行超时后应从 EXECUTE 进入 CORRECTION");
        assertTrue(stateMachine.getLastErrorMessage().contains("执行超时"),
                "错误消息应包含超时信息");
    }

    // ========================================================================
    // 4. 纠错重试边界条件
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("CORRECTION 第1次重试：回到 PARSE，尝试次数+1")
    void testCorrectionFirstTimeRetry() {
        triggerCorrection();

        // 第1次纠错完成，回到 PARSE
        stateMachine.transition(ErrorType.INVALID_JSON, "已修正，重新解析");

        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState(),
                "纠错完成后应回到 PARSE 重新开始");
        assertEquals(1, stateMachine.getCorrectionAttempts(),
                "纠错尝试次数应增加为 1");
        assertTrue(stateMachine.canCorrect(),
                "第1次纠错后应仍可继续纠错");
    }

    @Test
    @Order(11)
    @DisplayName("CORRECTION 第2次重试：仍然可以继续纠错")
    void testCorrectionSecondTimeRetry() {
        // 触发两次纠错
        triggerCorrection();   // 第1次纠错→回到PARSE
        stateMachine.transition(ErrorType.INVALID_JSON, "已修正");

        triggerCorrection();   // 第2次纠错→回到PARSE
        stateMachine.transition(ErrorType.INVALID_JSON, "已修正");

        assertEquals(2, stateMachine.getCorrectionAttempts(),
                "第2次纠错后尝试次数应增加为 2");
        assertTrue(stateMachine.canCorrect(),
                "第2次纠错后应仍可继续纠错（最多3次）");
    }

    @Test
    @Order(12)
    @DisplayName("CORRECTION 第3次封顶：进入 FAILED 状态，不再纠错")
    void testCorrectionExceededToFailed() {
        // 触发三次纠错
        triggerCorrection(); stateMachine.transition(ErrorType.INVALID_JSON, "纠正1");
        triggerCorrection(); stateMachine.transition(ErrorType.INVALID_JSON, "纠正2");
        triggerCorrection(); stateMachine.transition(ErrorType.INVALID_JSON, "纠正3");

        // 第4次失败 → 应进入 FAILED
        stateMachine.transition(ErrorType.TIMEOUT, "第4次失败，纠正封顶");

        assertEquals(ToolExecutionState.FAILED, stateMachine.getCurrentState(),
                "纠错超过3次后应进入 FAILED 状态");
        assertEquals(3, stateMachine.getCorrectionAttempts(),
                "纠错尝试次数应封顶在 3");
        assertFalse(stateMachine.canCorrect(),
                "达到封顶后 canCorrect() 应返回 false");
    }

    @Test
    @Order(13)
    @DisplayName("完整纠错恢复路径：失败→纠正→恢复→最终成功")
    void testFullErrorRecoveryFlow() {
        // 第1次：PARSE 失败
        stateMachine.transition(ErrorType.INVALID_JSON, "JSON 格式错误");
        assertEquals(ToolExecutionState.CORRECTION, stateMachine.getCurrentState());

        // 纠正后回到 PARSE
        stateMachine.transition(ErrorType.INVALID_JSON, "已修复 JSON 格式");
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());
        assertEquals(1, stateMachine.getCorrectionAttempts());

        // 第2次：VALIDATE 失败
        stateMachine.transition(ErrorType.FIELD_MISSING, "{\"tool\":\"bash\",\"args\":[\"ls\"]}");
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());
        stateMachine.transition(ErrorType.FIELD_MISSING, "缺少必填参数");
        assertEquals(ToolExecutionState.CORRECTION, stateMachine.getCurrentState());

        // 纠正后回到 PARSE
        stateMachine.transition(ErrorType.INVALID_JSON, "已补全参数");
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());
        assertEquals(2, stateMachine.getCorrectionAttempts());

        // 第3次：正向走到完成
        stateMachine.transition(ErrorType.INVALID_JSON, "{\"tool\":\"bash\",\"args\":[\"ls\"]}");
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());
        stateMachine.transition(ErrorType.NON_ZERO_EXIT, "字段校验通过");
        assertEquals(ToolExecutionState.EXECUTE, stateMachine.getCurrentState());
        stateMachine.transition(ErrorType.INVALID_JSON, "执行成功");
        assertEquals(ToolExecutionState.REPORT, stateMachine.getCurrentState());
        stateMachine.transition(ErrorType.INVALID_JSON, "报告完成");
        assertEquals(ToolExecutionState.DONE, stateMachine.getCurrentState());

        // 最终纠错次数为 2（使用了两轮纠错机会）
        assertEquals(2, stateMachine.getCorrectionAttempts(),
                "经过2次纠错后恢复成功");
        verify(progressConsumer, times(1)).accept(any());
    }

    // ========================================================================
    // 5. 状态一致性验证（State Consistency）
    // ========================================================================

    @Test
    @Order(14)
    @DisplayName("DONE 状态后不应再触发纠错")
    void testDoneStateShouldNotTransition() {
        // 正向走到 DONE
        stateMachine.transition(ErrorType.INVALID_JSON, "{\"tool\":\"x\"}");
        stateMachine.transition(ErrorType.NON_ZERO_EXIT, "ok");
        stateMachine.transition(ErrorType.INVALID_JSON, "done");
        stateMachine.transition(ErrorType.INVALID_JSON, "report");
        assertEquals(ToolExecutionState.DONE, stateMachine.getCurrentState());

        // DONE 后尝试纠错 — 应保持 DONE
        stateMachine.transition(ErrorType.TIMEOUT, "不应再转移");
        assertEquals(ToolExecutionState.DONE, stateMachine.getCurrentState(),
                "DONE 状态应为终态，不应再发生转移");
    }

    @Test
    @Order(15)
    @DisplayName("FAILED 状态后不应再触发纠错")
    void testFailedStateShouldNotTransition() {
        // 触发四次失败达到 FAILED
        triggerCorrection(); stateMachine.transition(ErrorType.INVALID_JSON, "纠正1");
        triggerCorrection(); stateMachine.transition(ErrorType.INVALID_JSON, "纠正2");
        triggerCorrection(); stateMachine.transition(ErrorType.INVALID_JSON, "纠正3");
        stateMachine.transition(ErrorType.TIMEOUT, "第4次失败");
        assertEquals(ToolExecutionState.FAILED, stateMachine.getCurrentState());

        // FAILED 后尝试纠错 — 应保持 FAILED
        stateMachine.transition(ErrorType.INVALID_JSON, "不应再转移");
        assertEquals(ToolExecutionState.FAILED, stateMachine.getCurrentState(),
                "FAILED 状态应为终态，不应再发生转移");
    }

    @Test
    @Order(16)
    @DisplayName("纠错尝试次数线程安全")
    void testCorrectionAttemptsThreadSafety() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        AtomicInteger totalTransitions = new AtomicInteger(0);

        // 快速连续触发纠错（模拟并发场景）
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 5; j++) {
                    try {
                        stateMachine.transition(ErrorType.INVALID_JSON, "并发测试");
                        totalTransitions.incrementAndGet();
                    } catch (Exception e) {
                        // 预期可能抛出异常
                    }
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join(5000);

        // 验证线程安全：纠错次数不应超过 MAX_CORRECTION（3）
        assertTrue(stateMachine.getCorrectionAttempts() <= 3,
                "纠错尝试次数不应超过 3，实际值: " + stateMachine.getCorrectionAttempts());
    }

    // ========================================================================
    // 辅助方法：导航到特定状态
    // ========================================================================

    /**
     * 导航到 EXECUTE 状态（用于测试 EXECUTE 之后的转移）
     */
    private void navigateToExecute() {
        stateMachine.transition(ErrorType.INVALID_JSON, "{\"tool\":\"x\",\"args\":[\"y\"]}");
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());
        stateMachine.transition(ErrorType.NON_ZERO_EXIT, "字段校验通过");
        assertEquals(ToolExecutionState.EXECUTE, stateMachine.getCurrentState());
    }

    /**
     * 导航到 REPORT 状态（用于测试 REPORT 之后的转移）
     */
    private void navigateToReport() {
        navigateToExecute();
        stateMachine.transition(ErrorType.INVALID_JSON, "执行成功，返回码 0");
        assertEquals(ToolExecutionState.REPORT, stateMachine.getCurrentState());
    }

    /**
     * 触发一次 CORRECTION 状态
     */
    private void triggerCorrection() {
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState(),
                "触发纠错时应在 PARSE 状态");
        stateMachine.transition(ErrorType.INVALID_JSON, "触发纠错");
        assertEquals(ToolExecutionState.CORRECTION, stateMachine.getCurrentState());
    }
}
