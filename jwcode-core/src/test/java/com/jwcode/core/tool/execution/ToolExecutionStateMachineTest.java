package com.jwcode.core.tool.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolExecutionStateMachine 集成测试
 *
 * <p>测试覆盖状态机完整生命周期：
 * <ul>
 *   <li>正常路径：PARSE → VALIDATE → EXECUTE → REPORT → DONE</li>
 *   <li>纠错路径：PARSE → CORRECTION → VALIDATE → CORRECTION → FAILED</li>
 *   <li>边界条件：空输入、无效状态转移、重复调用</li>
 *   <li>并发安全性：多线程下状态一致性</li>
 *   <li>最大纠错次数限制（2次）</li>
 * </ul>
 */
class ToolExecutionStateMachineTest {

    private ToolExecutionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ToolExecutionStateMachine();
    }

    // ==================== 正常路径 ====================

    @Test
    @DisplayName("完整正常执行路径：PARSE → VALIDATE → EXECUTE → REPORT → DONE")
    void testNormalExecutionPath() {
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());

        stateMachine.transition(ToolExecutionState.VALIDATE);
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());

        stateMachine.transition(ToolExecutionState.EXECUTE);
        assertEquals(ToolExecutionState.EXECUTE, stateMachine.getCurrentState());

        stateMachine.transition(ToolExecutionState.REPORT);
        assertEquals(ToolExecutionState.REPORT, stateMachine.getCurrentState());

        stateMachine.transition(ToolExecutionState.DONE);
        assertEquals(ToolExecutionState.DONE, stateMachine.getCurrentState());
        assertTrue(stateMachine.isCompleted());
    }

    // ==================== 纠错路径 ====================

    @Test
    @DisplayName("PARSE 失败后进入 CORRECTION，恢复后继续执行")
    void testParseFailureThenCorrectionAndContinue() {
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());

        // 模拟 JSON 解析失败，进入纠错
        stateMachine.transition(ToolExecutionState.CORRECTION);
        assertEquals(ToolExecutionState.CORRECTION, stateMachine.getCurrentState());
        assertEquals(1, stateMachine.getCorrectionAttempts());

        // 纠错成功，回到 VALIDATE
        stateMachine.transition(ToolExecutionState.VALIDATE);
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());

        // 继续正常执行
        stateMachine.transition(ToolExecutionState.EXECUTE);
        stateMachine.transition(ToolExecutionState.REPORT);
        stateMachine.transition(ToolExecutionState.DONE);
        assertTrue(stateMachine.isCompleted());
    }

    @Test
    @DisplayName("连续 2 次 CORRECTION 后进入 FAILED（最大纠错限制）")
    void testMaxCorrectionsLeadsToFailed() {
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());

        // 第一次纠错
        stateMachine.transition(ToolExecutionState.CORRECTION);
        assertEquals(1, stateMachine.getCorrectionAttempts());

        // VALIDATE 再次失败，第二次纠错
        stateMachine.transition(ToolExecutionState.CORRECTION);
        assertEquals(2, stateMachine.getCorrectionAttempts());

        // 第三次失败，应该进入 FAILED
        stateMachine.transition(ToolExecutionState.FAILED);
        assertEquals(ToolExecutionState.FAILED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isFailed());
    }

    // ==================== 非法状态转移 ====================

    @Test
    @DisplayName("从 DONE 无法转移到其他状态")
    void testTransitionFromDoneShouldThrow() {
        stateMachine.transition(ToolExecutionState.VALIDATE);
        stateMachine.transition(ToolExecutionState.EXECUTE);
        stateMachine.transition(ToolExecutionState.REPORT);
        stateMachine.transition(ToolExecutionState.DONE);

        assertThrows(IllegalStateException.class, () ->
            stateMachine.transition(ToolExecutionState.CORRECTION));
    }

    @Test
    @DisplayName("从 FAILED 无法转移到其他状态")
    void testTransitionFromFailedShouldThrow() {
        stateMachine.transition(ToolExecutionState.CORRECTION);
        stateMachine.transition(ToolExecutionState.CORRECTION);
        stateMachine.transition(ToolExecutionState.FAILED);

        assertThrows(IllegalStateException.class, () ->
            stateMachine.transition(ToolExecutionState.DONE));
    }

    // ==================== 边界条件 ====================

    @Test
    @DisplayName("跳过 VALIDATE 直接从 PARSE 到 EXECUTE 应被拒绝")
    void testSkipValidationShouldBeRejected() {
        // 预期抛异常或状态不变
        assertThrows(IllegalStateException.class, () ->
            stateMachine.transition(ToolExecutionState.EXECUTE));
    }

    @Test
    @DisplayName("空状态机初始状态应为 PARSE")
    void testInitialStateIsParse() {
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());
        assertFalse(stateMachine.isCompleted());
        assertFalse(stateMachine.isFailed());
        assertEquals(0, stateMachine.getCorrectionAttempts());
    }

    @Test
    @DisplayName("状态机重置后可重新执行")
    void testResetStateMachine() {
        stateMachine.transition(ToolExecutionState.VALIDATE);
        stateMachine.transition(ToolExecutionState.EXECUTE);
        stateMachine.transition(ToolExecutionState.REPORT);
        stateMachine.transition(ToolExecutionState.DONE);
        assertTrue(stateMachine.isCompleted());

        // 重置
        stateMachine.reset();
        assertEquals(ToolExecutionState.PARSE, stateMachine.getCurrentState());
        assertEquals(0, stateMachine.getCorrectionAttempts());
        assertFalse(stateMachine.isCompleted());
        assertFalse(stateMachine.isFailed());
    }

    // ==================== 并发安全性 ====================

    @Test
    @DisplayName("多线程并发状态转移应保持一致性")
    void testConcurrentTransitions() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                stateMachine.transition(ToolExecutionState.VALIDATE);
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        // 即使多线程调用，最终状态也应该是 VALIDATE（只有一个成功）
        assertEquals(ToolExecutionState.VALIDATE, stateMachine.getCurrentState());
    }

    // ==================== 参数化测试 ====================

    @ParameterizedTest
    @DisplayName("所有状态枚举值均有效")
    @EnumSource(ToolExecutionState.class)
    void testAllStatesAreValid(ToolExecutionState state) {
        assertNotNull(state);
        assertNotNull(state.name());
    }

    @Test
    @DisplayName("CORRECTION 循环中状态机应记录每次尝试")
    void testCorrectionAttemptCounting() {
        assertEquals(0, stateMachine.getCorrectionAttempts());

        stateMachine.transition(ToolExecutionState.CORRECTION);
        assertEquals(1, stateMachine.getCorrectionAttempts());

        // 从 CORRECTION 回到 VALIDATE
        stateMachine.transition(ToolExecutionState.VALIDATE);
        assertEquals(1, stateMachine.getCorrectionAttempts()); // 不应增加

        // 再次失败
        stateMachine.transition(ToolExecutionState.CORRECTION);
        assertEquals(2, stateMachine.getCorrectionAttempts());

        stateMachine.transition(ToolExecutionState.FAILED);
        assertTrue(stateMachine.isFailed());
    }
}
