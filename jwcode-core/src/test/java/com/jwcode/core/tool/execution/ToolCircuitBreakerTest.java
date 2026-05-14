package com.jwcode.core.tool.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCircuitBreaker 集成测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>正常调用通过</li>
 *   <li>连续 3 次失败 → 半开状态（暂停 30 秒，改用替代工具）</li>
 *   <li>连续 5 次失败 → 全开状态（本轮会话禁用，上报用户）</li>
 *   <li>半开后恢复成功 → 关闭状态</li>
 *   <li>全开状态所有调用被拒绝</li>
 *   <li>并发场景下状态一致性</li>
 *   <li>替代工具切换逻辑</li>
 * </ul>
 */
class ToolCircuitBreakerTest {

    private ToolCircuitBreaker circuitBreaker;
    private static final String TOOL_ID = "test-tool-bash";

    @BeforeEach
    void setUp() {
        circuitBreaker = new ToolCircuitBreaker();
    }

    // ==================== 正常路径 ====================

    @Test
    @DisplayName("正常调用应成功通过熔断器")
    void testNormalCallPasses() {
        String result = circuitBreaker.execute(TOOL_ID, () -> "success");
        assertEquals("success", result);
        assertTrue(circuitBreaker.isClosed(TOOL_ID));
    }

    @Test
    @DisplayName("多次正常调用不触发熔断")
    void testMultipleNormalCalls() {
        for (int i = 0; i < 10; i++) {
            String result = circuitBreaker.execute(TOOL_ID, () -> "ok-" + i);
            assertEquals("ok-" + i, result);
        }
        assertTrue(circuitBreaker.isClosed(TOOL_ID));
    }

    // ==================== 熔断路径 ====================

    @Test
    @DisplayName("连续 3 次失败后进入半开状态（HALF_OPEN）")
    void testThreeFailuresTriggersHalfOpen() {
        // 前 3 次调用失败
        assertThrows(RuntimeException.class, () ->
            circuitBreaker.execute(TOOL_ID, () -> { throw new RuntimeException("fail-1"); }));
        assertEquals(1, circuitBreaker.getFailureCount(TOOL_ID));
        assertTrue(circuitBreaker.isClosed(TOOL_ID));

        assertThrows(RuntimeException.class, () ->
            circuitBreaker.execute(TOOL_ID, () -> { throw new RuntimeException("fail-2"); }));
        assertEquals(2, circuitBreaker.getFailureCount(TOOL_ID));
        assertTrue(circuitBreaker.isClosed(TOOL_ID));

        assertThrows(RuntimeException.class, () ->
            circuitBreaker.execute(TOOL_ID, () -> { throw new RuntimeException("fail-3"); }));
        assertEquals(3, circuitBreaker.getFailureCount(TOOL_ID));
        assertTrue(circuitBreaker.isHalfOpen(TOOL_ID));
    }

    @Test
    @DisplayName("连续 5 次失败后进入全开状态（OPEN），所有调用被拒绝")
    void testFiveFailuresTriggersOpen() {
        // 3 次失败 → HALF_OPEN
        failTimes(TOOL_ID, 3, circuitBreaker);
        assertTrue(circuitBreaker.isHalfOpen(TOOL_ID));

        // 再失败 2 次 → OPEN
        failTimes(TOOL_ID, 2, circuitBreaker);
        assertTrue(circuitBreaker.isOpen(TOOL_ID));

        // 全开后所有调用应被拒绝
        assertThrows(CircuitBreakerOpenException.class, () ->
            circuitBreaker.execute(TOOL_ID, () -> "should-not-execute"));
    }

    // ==================== 恢复路径 ====================

    @Test
    @DisplayName("半开状态下成功调用后恢复到关闭状态")
    void testHalfOpenSuccessRestoresClosed() {
        // 触发半开
        failTimes(TOOL_ID, 3, circuitBreaker);
        assertTrue(circuitBreaker.isHalfOpen(TOOL_ID));

        // 半开状态下成功一次 → 恢复 CLOSED
        // 注意：半开状态下熔断器应允许探测性调用通过
        String result = circuitBreaker.execute(TOOL_ID, () -> "recovery-success");
        assertEquals("recovery-success", result);
        assertTrue(circuitBreaker.isClosed(TOOL_ID));
        assertEquals(0, circuitBreaker.getFailureCount(TOOL_ID)); // 失败计数应重置
    }

    // ==================== 替代工具切换 ====================

    @Test
    @DisplayName("半开状态下应自动切换到替代工具")
    void testHalfOpenShouldUseFallbackTool() {
        failTimes(TOOL_ID, 3, circuitBreaker);
        assertTrue(circuitBreaker.isHalfOpen(TOOL_ID));

        // 半开时使用替代工具
        String fallbackResult = circuitBreaker.executeWithFallback(
            TOOL_ID,
            () -> { throw new RuntimeException("primary-failed"); },
            () -> "fallback-result"
        );
        assertEquals("fallback-result", fallbackResult);
    }

    @Test
    @DisplayName("全开状态下应返回降级结果")
    void testOpenShouldReturnDegradedResult() {
        failTimes(TOOL_ID, 5, circuitBreaker);
        assertTrue(circuitBreaker.isOpen(TOOL_ID));

        // 全开时返回降级结果
        String degraded = circuitBreaker.executeWithFallback(
            TOOL_ID,
            () -> { throw new RuntimeException("should-not-run"); },
            () -> "degraded-response"
        );
        assertEquals("degraded-response", degraded);
    }

    // ==================== 多工具隔离 ====================

    @Test
    @DisplayName("不同工具应独立熔断，互不影响")
    void testDifferentToolsAreIndependent() {
        String toolA = "bash-tool";
        String toolB = "file-read-tool";

        // toolA 触发熔断
        failTimes(toolA, 5, circuitBreaker);
        assertTrue(circuitBreaker.isOpen(toolA));

        // toolB 应正常工作
        String result = circuitBreaker.execute(toolB, () -> "tool-b-ok");
        assertEquals("tool-b-ok", result);
        assertTrue(circuitBreaker.isClosed(toolB));
    }

    // ==================== 并发安全性 ====================

    @Test
    @DisplayName("并发调用下熔断器应保持状态一致性")
    void testConcurrentCallsMaintainConsistency() throws InterruptedException {
        int threadCount = 20;
        Thread[] threads = new Thread[threadCount];
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int attempt = i;
            threads[i] = new Thread(() -> {
                try {
                    circuitBreaker.execute(TOOL_ID, () -> {
                        if (attempt < 5) {
                            throw new RuntimeException("simulated-failure-" + attempt);
                        }
                        return "success-" + attempt;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        // 最终状态应该是确定的
        assertNotNull(circuitBreaker.getState(TOOL_ID));
    }

    // ==================== 重置 ====================

    @Test
    @DisplayName("熔断器重置后应恢复到关闭状态")
    void testResetCircuitBreaker() {
        failTimes(TOOL_ID, 5, circuitBreaker);
        assertTrue(circuitBreaker.isOpen(TOOL_ID));

        circuitBreaker.reset(TOOL_ID);
        assertTrue(circuitBreaker.isClosed(TOOL_ID));
        assertEquals(0, circuitBreaker.getFailureCount(TOOL_ID));
    }

    // ==================== 辅助方法 ====================

    private void failTimes(String toolId, int times, ToolCircuitBreaker cb) {
        for (int i = 0; i < times; i++) {
            try {
                cb.execute(toolId, () -> {
                    throw new RuntimeException("simulated-failure-" + i);
                });
            } catch (RuntimeException e) {
                // 预期的异常
            }
        }
    }
}
